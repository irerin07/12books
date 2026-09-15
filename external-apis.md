# 외부 API 계약

외부 API가 **실제로 어떻게 동작하는지**를 재본 기록. 문서에 적힌 것이 아니라 재서 확인한 것만
여기 남긴다.

## 왜 이 문서가 있나

`GET /books/search`에 `MAX_PAGE = 50`이라는 상한이 있었다. 근거는 코드 주석 한 줄이었다 —
"카카오가 1~50만 받고 넘기면 4xx를 준다". 카카오 공식 문서에도 `page`는 1~50으로 적혀 있으니
그럴듯했고, 그래서 아무도 다시 묻지 않았다.

**실제로 쳐 보니 51쪽도 500쪽도 HTTP 200이었다.** 그 상한 때문에 닿을 수 있는 결과의 절반을
버리고 있었고, 50쪽 응답에 `hasNext: true`를 실어 놓고 51쪽 요청을 400으로 거절하고 있었다.

문서를 읽어도 못 잡았을 함정이다 — 문서가 실제와 달랐기 때문이다. **실물을 쳐봐야만** 나왔다.

## 규칙

- 외부 API의 동작에 기대는 코드를 쓸 때는 **그 동작을 실제로 호출해 확인하고 여기 남긴다.**
  "문서에 그렇게 적혀 있다"는 근거가 아니다. 문서와 실제가 다를 수 있고, 실제로 달랐다.
- 상한·범위·오류 코드처럼 **숫자나 분기를 만드는 값**은 특히 그렇다. 그런 값이 코드에 박히면
  다음 사람은 그것을 사실로 믿는다.
- 재현 명령을 함께 적는다. 반년 뒤에 이 값이 아직 맞는지 확인하려면 그때 쓴 명령이 필요하다.
- 새 `*Client.java`를 추가하면 여기 절도 함께 추가한다. `ExternalApiContractTest`가 강제한다.

---

## KakaoBookClient

**실측일:** 2026-09-11
**대상:** `GET https://dapi.kakao.com/v3/search/book`
**재현:**

```bash
for p in 1 50 51 99 100 500; do
  curl -s "https://dapi.kakao.com/v3/search/book?query=%EC%82%AC%EB%9E%91&page=$p" \
    -H "Authorization: KakaoAK $KAKAO_REST_API_KEY" | jq -c '{page: '"$p"', meta}'
done
```

**결과** (`query=사랑`, `size` 미지정 → 기본 10):

| page | HTTP | documents | is_end | pageable_count | total_count |
|---|---|---|---|---|---|
| 1 | 200 | 10 | false | 1000 | 52388 |
| 50 | 200 | 10 | false | 1000 | 52388 |
| 51 | 200 | 10 | false | 1000 | 52388 |
| 99 | 200 | 10 | false | 1000 | 52388 |
| 100 | 200 | 10 | **true** | 1000 | 52388 |
| 500 | 200 | 10 | true | 1000 | 52388 |

**읽어낸 것**

- **`page`에 상한이 없다.** 공식 문서는 1~50이라고 하지만 51쪽도 500쪽도 200을 준다.
  4xx는 어느 쪽에서도 오지 않았다. 그래서 우리도 상한을 두지 않는다.
- **`is_end`는 `pageable_count ÷ size` 지점에서 뒤집힌다.** 1000 ÷ 10 = 100쪽. 99쪽까지 false,
  100쪽부터 true. 멈추는 판단은 이 값에 맡기면 된다.
- **`pageable_count`는 모든 쪽에서 1000으로 고정이다.** 공식 문서의 "처음부터 요청 페이지까지의
  노출 가능 문서 수"라는 서술과 다르다. 누적이 아니라 상한으로 동작한다.
- **`total_count`(52388)와 도달 가능 수(1000)는 다르다.** 화면에 "전체 5만 건"을 보여줘도
  실제로 넘길 수 있는 것은 1000건까지다.
- 도달 한계 너머(101·150·500)도 200에 문서 10건을 준다. `is_end`만 참이다.
  즉 **`is_end`를 무시하면 무한히 넘길 수 있다** — 클라이언트가 그 신호를 봐야 한다.

### 응답에 있는데 우리가 안 쓰는 필드 (2026-09-11 측정)

`documents`에는 우리가 파싱하지 않는 필드가 더 있다. Phase 6.5에서 쓰기로 한 것들이라 재 뒀다.

**재현:**

```bash
for q in 사랑 자바 소설 역사; do
  curl -s "https://dapi.kakao.com/v3/search/book?query=$(python -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$q")&size=50"     -H "Authorization: KakaoAK $KAKAO_REST_API_KEY"     | jq '[.documents[] | {c: (.contents != ""), u: (.url != ""), t: (.translators|length > 0)}] | {n: length, contents: map(select(.c))|length, url: map(select(.u))|length, translators: map(select(.t))|length}'
done
```

| 필드 | 채움률 (4개 검색어 × 50건) | 비고 |
|---|---|---|
| `contents` | **186 / 200 (93%)** | **약 250자에서 잘린 발췌.** 최소 56 · 중앙 253 · 최대 261 |
| `url` | 200 / 200 | 다음 책 페이지 링크 |
| `price` · `sale_price` · `status` | 200 / 200 | 판매 정보 |
| `translators` | 44 / 200 | 번역서만 채워진다 — 정상 |

**읽어낸 것** — `contents`는 전문이 아니라 발췌다. 카드에 두세 줄 띄우기엔 충분하지만
"책 소개"라 부르면 과장이다. 길이가 261에서 멈추는 것으로 보아 카카오가 자른 값이다.

### target — 검색 범위 좁히기 (2026-09-11 측정)

문서상 `title` · `isbn` · `publisher` · `person`. 실제로 동작하고 의미 있게 좁혀진다.

**재현:**

```bash
for t in "" title person publisher; do
  u="https://dapi.kakao.com/v3/search/book?query=%EB%B0%95%EA%B2%BD%EB%A6%AC&size=5"
  [ -n "$t" ] && u="$u&target=$t"
  curl -s "$u" -H "Authorization: KakaoAK $KAKAO_REST_API_KEY" | jq -c '{target: "'"$t"'", total: .meta.total_count}'
done
```

| target | `박경리`의 total_count |
|---|---|
| (없음) | 628 |
| `title` | 228 |
| `person` | 568 |
| `publisher` | 0 |

**읽어낸 것** — 저자로 좁히면 "박경리가 쓴 책", 제목으로 좁히면 "제목에 박경리가 든 책"으로
갈린다. 섞여 있으면 둘 다 묻힌다. 우리는 `ALL` · `TITLE` · `AUTHOR`만 노출하고
`AUTHOR`를 카카오의 `person`에 매핑한다 — 상류의 어휘(`person`=인명)가 우리 API에 새지
않게 하려는 것이다. **좁히지 않을 때는 파라미터를 아예 빼야 한다**(빈 값을 어떻게 다루는지는
재보지 않았다).

**아직 안 재본 것**

- `target=isbn`·`publisher`. 지금 노출하지 않으므로 쓰지 않는다.
- `sort`(정확도순·발간일순). 기본값인 정확도순을 그대로 쓴다.
- `size`를 명시했을 때의 동작. 지금은 보내지 않아 기본 10을 쓴다. 문서상 1~50이고,
  `size=50`이면 같은 1000건에 20쪽으로 닿을 것으로 보이나 **확인하지 않았다.**
- 검색어가 없거나 빈 문자열일 때의 응답. 컨트롤러가 `@NotBlank`로 먼저 막아 도달하지 않는다.
- 인증 실패(401)·쿼터 초과(429)의 실제 응답 본문. 어떤 실패든 `E001`/502로 바꾸므로
  형태에 기대는 코드가 없다.

---

## ResendMailClient

비밀번호 재설정 메일을 보낸다. `POST https://api.resend.com/emails`.

**실측일:** 2026-09-15

**재현:**

```bash
KEY=<Resend API 키>

# 1) 기본 발신 주소 — 도메인 인증 없이 되는가
curl -s -w '
HTTP %{http_code}
' -X POST https://api.resend.com/emails   -H "Authorization: Bearer $KEY" -H "Content-Type: application/json"   -d '{"from":"onboarding@resend.dev","to":["delivered@resend.dev"],"subject":"t","text":"t"}'

# 2) 인증 안 된 도메인을 from에 쓰면
curl -s -w '
HTTP %{http_code}
' -X POST https://api.resend.com/emails   -H "Authorization: Bearer $KEY" -H "Content-Type: application/json"   -d '{"from":"no-reply@12books.local","to":["delivered@resend.dev"],"subject":"t","text":"t"}'

# 3) 예약 도메인으로 보내면
curl -s -w '
HTTP %{http_code}
' -X POST https://api.resend.com/emails   -H "Authorization: Bearer $KEY" -H "Content-Type: application/json"   -d '{"from":"onboarding@resend.dev","to":["nobody@example.com"],"subject":"t","text":"t"}'

# 4) 키가 틀리면
curl -s -w '
HTTP %{http_code}
' -X POST https://api.resend.com/emails   -H "Authorization: Bearer re_invalid_key_for_measurement" -H "Content-Type: application/json"   -d '{"from":"onboarding@resend.dev","to":["delivered@resend.dev"],"subject":"t","text":"t"}'
```

**잰 것**

| 보낸 것 | 결과 |
|---|---|
| `from=onboarding@resend.dev` → `delivered@resend.dev` | **200** `{"id":"66ffb53a-…"}` |
| `from=no-reply@12books.local` | **403** `validation_error` — "The 12books.local domain is not verified." |
| `to=nobody@example.com` | **422** `validation_error` — 예약 도메인이라 테스트 주소를 쓰라고 거절 |
| 잘못된 키 | **401** `validation_error` — "API key is invalid" |
| `from=onboarding@resend.dev` → **실제 Gmail 주소** | **200**. 도착은 했으나 **스팸함**. 보낸 사람은 `onboarding@resend.dev` 그대로 |

**읽어낸 것**

- **도메인 인증 없이도 `onboarding@resend.dev`로는 보낼 수 있다.** 그래서 QA는 도메인 준비
  없이 시작할 수 있다. 우리가 정한 `MAIL_FROM`을 쓰려면 그 도메인을 먼저 인증해야 한다 —
  안 하면 403이고, **발송만 실패하고 응답은 204라 조용히 안 나간다.**
- 실패가 **상태 코드 + `name` + 사람이 읽을 수 있는 `message`**로 온다. SDK는 이것을
  `ResendException`의 `getStatusCode()`·`getErrorName()`·`getResponseBody()`로 준다 —
  SMTP였다면 거절 코드 한 줄이었을 자리다.
- 성공하면 **메시지 id**가 온다. 로그에 남겨 두면 나중에 "그 메일이 어떻게 됐나"를 물을 수 있다.
- 테스트 주소가 있다(`delivered@` · `bounced@` · `complained@` · `suppressed@resend.dev`).
  가짜 주소로 보내거나 가짜 SMTP를 세우는 대신 이쪽을 쓴다. **쿼터는 차감된다.**
- **도메인 인증 없이 보내면 스팸함으로 간다.** 실제 Gmail로 두 번 보내 두 번 다 스팸함이었다
  (Gmail 안내: "이전에 스팸으로 확인된 메일과 유사합니다"). 앞서 `to=nobody@example.com`이
  422였던 것은 외부 주소가 막혀서가 아니라 **`example.com`이 예약 도메인**이라서다 —
  일반 주소로는 나간다.
- 한글 제목·본문은 **UTF-8로 보내면 그대로** 온다. 별도 charset 지정이 필요 없었다.

> **측정하다 만난 함정.** 첫 발송에서 제목과 본문이 통째로 깨져 왔다(`[12books] ??й?8 ?缳??`).
> 원인은 API가 아니라 **측정 도구**였다 — Windows 셸에서 `curl -d '한글…'`로 인라인 전달하면
> cp949로 망가진 바이트가 나간다. 본문을 UTF-8 파일에 써서 `--data-binary @파일`로 보내니
> 정상이었다. 여기서 재는 사람은 **깨진 결과를 API 탓으로 적기 전에 보낸 바이트부터 확인**할 것.

**아직 안 재본 것**

- **도메인을 인증하면 받은편지함에 들어가는지.** 인증 전에는 스팸함이라는 것까지만 쟀다.
- 한도(무료 월 3,000 · 일 100)를 넘겼을 때의 응답. 값은 요금제 페이지에서 확인했고
  (2026-09-15), **초과 시 무엇이 오는지는 안 재봤다.**
- 재시도·타임아웃 동작. SDK 내부 HTTP 클라이언트의 기본값을 확인하지 않았다.

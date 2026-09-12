# 12books

읽고 있는 책의 감상을 **읽은 분량만큼** 기록하고 공유하는 독서 SNS. 백엔드 API.

- `spec.md` — 제품 요구사항 정의서(PRD). 무엇을 왜 만드는가.
- `plan.md` — Phase 0~9 개발 로드맵. 기술 기반(T1~T7)과 공통 규약.

지금 무엇을 만들지 모르겠으면 `plan.md`의 Phase 표를 보고 **아직 안 된 가장 앞 Phase**를 잡는다.

## 환경

Windows / PowerShell. Java 21, Spring Boot 4.1.1, MySQL 8.4, Redis 7.

```powershell
docker compose up -d                 # MySQL + Redis (통합 테스트에도 Docker가 필요하다)
.\gradlew.bat build                  # 빌드 + 전체 테스트
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

QA용 시드 데이터가 필요하면 앱을 띄운 뒤:

```powershell
python tools/seed-qa.py              # 계정 5개 + 책·서재·감상평·팔로우·목표
```

**공개 API만 써서 넣는다** — DB에 직접 쓰지 않으므로 시드가 성공한다는 것 자체가 그 경로들이
살아 있다는 뜻이다. 여러 번 돌려도 같은 상태가 된다(책은 업서트, 중복 담기·팔로우는 409를
성공으로, 감상평은 같은 본문이면 건너뜀). 계정 비밀번호는 전부 `123456789`이고 **로컬 QA 전용**이다.

## 불변 규칙

이 네 가지는 예외 없이 지킨다. 훅과 GitHub ruleset이 실제로 강제한다.

### 1. main에 직접 쓰지 않는다

모든 변경은 브랜치에서 시작한다. 브랜치명은 `feat/…`, `fix/…`, `chore/…`.
main에서 `src/**`를 수정하거나 커밋·푸시를 시도하면 훅이 막는다.

> `.claude/hooks/guard-main.ps1`은 **과속방지턱이지 보안 경계가 아니다.** 도구 호출 전에 한 번
> 판정할 뿐이라 원리상 뚫리는 경로가 남는다. 실제 강제는 `.githooks/pre-push`(로컬)와
> GitHub ruleset(서버)이 한다. 훅의 판정 로직은 `.claude/hooks/guard-main.tests.ps1`이 지킨다 —
> 훅을 고치면 이 테스트를 먼저 돌린다.

기능 하나를 시작할 때는 `/feature <설명>`을 쓴다. 브랜치 생성부터 PR 생성·자동 머지 예약까지 한다.

### 2. 테스트를 먼저 쓴다

프로덕션 코드를 고치기 전에 **실패하는 테스트를 먼저 쓰고 실패를 눈으로 확인한다.**
"구현하고 나중에 테스트 추가"는 TDD가 아니다.

- RED — 원하는 동작을 표현하는 가장 작은 테스트를 쓰고 **실행해서 실패를 확인한다.**
  실패 메시지가 기대한 이유(기능 없음)인지 본다. 오타·컴파일 에러로 실패한 건 RED가 아니다.
- GREEN — 그 테스트를 통과시키는 최소한의 코드만 쓴다. 요청 범위 밖의 기능을 얹지 않는다.
- REFACTOR — 지금 작업이 필요로 할 때만. 초록불을 유지한 채로.

통합 테스트는 `AbstractIntegrationTest`(Testcontainers + 실제 Flyway 마이그레이션)를 상속한다.
순수 로직(토큰 생성, 해시태그 파서, 상태 전이)은 컨테이너 없이 단위 테스트로.
외부 API(카카오)는 `MockRestServiceServer`로 스텁한다 — 테스트가 네트워크·API 키에 의존하면 CI에서 깨진다.

### 3. 커밋 메시지에 트레일러를 붙이지 않는다

한국어 본문만 쓴다. `Co-Authored-By`, `Claude-Session`, `Generated with` 같은 줄을 **넣지 않는다.**

```
feat: 감상평 작성 API

읽은 분량(fromPage~toPage)과 함께 감상을 남길 수 있게 한다.
서재에 없는 책이면 reading을 READING 상태로 자동 생성해 연결한다.
```

제목은 `feat|fix|chore|refactor|test|docs: 요약`. 본문은 **무엇을 했는지가 아니라 왜 그렇게 했는지**를 쓴다.

### 4. 승인 없이 머지되지 않는다

`main`은 ruleset으로 보호된다. PR은 필수 체크 두 개가 모두 초록이어야 머지된다.

- `build` — CI(빌드 + 전체 테스트)
- `review-gate` — 사용자가 PR에 `/approve <head 커밋 SHA>` 코멘트를 남겨야 초록이 된다.
  SHA를 요구하는 이유는 승인을 그 코드에 묶기 위해서다 — 승인 직후 새 커밋이 올라와도
  옛 승인이 새 코드를 승인하지 않는다

`/feature`는 PR 생성 후 `gh pr merge --auto --squash`로 **머지를 예약만** 한다.
승인이 들어오는 순간 GitHub이 알아서 머지한다. 사람이 직접 머지 버튼을 누를 일은 없다.

**새 커밋을 push하면 `review-gate`는 자동으로 다시 잠긴다.** 리뷰 반영 후에는 재승인이 필요하다.

## 작업 흐름

| 상황 | 할 일 |
|---|---|
| 새 기능 시작 | `/feature <설명>` |
| PR에 리뷰가 달림 | `/review-fix [PR번호]` |
| 리뷰 승인 | 사용자가 PR에 `/approve <head SHA>` 코멘트 → 자동 머지 |
| 급하게 자리 밖에서 | PR에 `@claude …` 멘션 → GitHub Action이 대응 |

> `@claude` Action이 만든 커밋은 `GITHUB_TOKEN`으로 push되므로 **다른 워크플로를 트리거하지 않는다.**
> 그 커밋 뒤에 CI를 다시 돌리려면 로컬에서 빈 커밋을 하나 밀어야 한다.

## 코드 규약 (`plan.md` T4·T5 요약)

**패키지** — 기술 레이어가 아니라 기능(도메인) 단위 수직 분할.
`common / auth / user / book / reading / post / follow / tag / feed`, 각 패키지 안에
`domain · repository · service · controller · dto`.

**에러** — `ErrorCode` enum이 HTTP 상태 + 코드 + 기본 메시지를 함께 소유한다.
호출부는 `throw new BusinessException(ErrorCode.POST_NOT_FOUND)`만 하고,
`GlobalExceptionHandler`가 `{ code, message, fieldErrors }`로 변환한다.
**응답에 스택트레이스나 내부 메시지를 절대 싣지 않는다.**

**목록** — 전부 커서 페이징(`CursorPage<T>`). `size + 1`건을 조회해 `hasNext`를 판정한다.
`size`는 기본 20, 최대 50으로 컨트롤러에서 clamp.

**카운터** — `like_count` 같은 반정규화 카운터는 **읽고-더하고-쓰지 않는다**(동시 요청에 유실됨).
반드시 `update ... set c = c + 1 where id = :id` 원자적 UPDATE.

**중복** — 중복 좋아요·팔로우는 "먼저 조회해서 있으면 스킵"이 아니라 **DB 유니크 제약을 1차 방어선**으로
삼고 `DataIntegrityViolationException`을 409로 변환한다.

**빈 값** — 응답 DTO는 `@JsonInclude(NON_NULL)`을 단다. 모르는 값은 `null`로 싣지 않고 **키째 뺀다** —
`null`과 0·빈 문자열을 클라이언트가 헷갈리지 않게. 일부 DTO만 다르게 굴면 같은 필드가 화면에 따라
`null`로도 오고 없기도 해서, 클라이언트가 둘 다 다뤄야 한다.

> 테스트에서 키가 빠졌는지 볼 때는 `doesNotHaveJsonPath()`를 쓴다. `doesNotExist()`는 값이 `null`이어도
> 통과해서 아무것도 지키지 못한다.

**기본 키** — 모든 테이블은 `bigint auto_increment` 대리 키 하나를 PK로 갖는다. **복합 PK를 쓰지 않는다** —
관계·조인 테이블도 예외가 아니고, 유일성은 `unique` 제약이 맡는다. 복합 PK는 커서 페이징을 깨고,
식별자를 직접 넣으면 JPA `save()`가 insert 대신 merge로 나가 **유니크 제약이 발동하지 못한다**
(중복 요청이 409 대신 조용히 성공한다).

> 이 규약은 `PrimaryKeyConventionTest`가 강제한다. 마이그레이션의 `primary key (a, b)`와 엔티티의
> `@IdClass`·`@EmbeddedId`·`@Id` 두 개를 잡아 `build`를 빨갛게 만든다. **정말 필요하면** 그 줄 앞에
> 사유와 함께 `allow-composite-pk: <이유>`를 남긴다 — 사유가 없거나 짧으면 면제되지 않는다.

**외부 API** — 외부 API의 동작에 기대는 코드를 쓰기 전에 **실제로 호출해 확인하고 `external-apis.md`에
남긴다.** "공식 문서에 그렇게 적혀 있다"는 근거가 아니다 — 카카오 문서는 `page`를 1~50이라 하지만
실제로는 500쪽도 200을 준다. 그 한 줄을 안 재본 탓에 닿을 수 있는 결과의 절반을 버리고 있었다.
상한·범위·오류 코드처럼 **숫자나 분기를 만드는 값**은 특히 재본다. 코드에 박히는 순간 다음 사람은
그것을 사실로 믿는다.

> `ExternalApiContractTest`가 강제한다. `*Client.java`마다 `external-apis.md`에 실측일과
> **붙여넣어 실행 가능한** 재현 명령이 있어야 `build`가 초록이 된다. 다만 이 검사는 기록이
> **있는지**만 본다 — 적힌 내용이 참인지는 판정할 수 없으니 그건 리뷰가 본다.

**삭제** — **행을 지우지 않는다.** 사용자에게 "삭제"인 동작은 `deleted_at` 시각을 세우고, 조회는
`deleted_at is null`로 좁힌다. 법적 요구(개인정보 파기 등)나 그에 준하는 별도 사유가 있을 때만
실제 삭제를 검토하고, 그때도 먼저 사유를 적는다.

> 지운 뒤에 오는 질문이 있다 — "잘못 지웠으니 되살려 달라", "이 사람이 뭘 썼다가 지웠나",
> "지난달 통계가 왜 달라졌나". 행을 지우면 답할 방법이 없고, 백업에서 한 행만 꺼내 오는 일은
> 실무에서 사실상 불가능하다. `boolean` 대신 시각을 쓰는 것은 "언제"까지 공짜로 남기 때문이다.

**그런데 먼저 물을 것이 있다 — 그게 정말 삭제인가.** 사용자가 되돌릴 수 있고, 되돌리는 것이
복구가 아니라 그냥 다음 행동이면 그건 삭제가 아니라 **도메인 상태**다. 서재의 "빼기"가 그렇다
(`readings.in_bookshelf`) — 담았다 뺐다 하는 것은 정상적인 사용이고, 다시 담으면 진도가 그대로
남는다. 이런 자리에 `deleted_at`을 쓰면 되살리기 코드가 예외처럼 읽히고, 이름이 약속한 것과
동작이 어긋난다(삭제라면서 진도를 남기거나, 상태라면서 진도를 지우거나).

쓰지 않을 `deleted_at`을 미리 두지 않는다. 실제로 세우는 경로가 없으면 항상 `null`인 컬럼이
되고, 모든 조회가 **아무것도 거르지 않는 조건**을 하나 더 달게 된다 — 빠뜨려도 테스트가 잡지
못하는 소음이다(`comments.parent_id`를 만들지 않은 것과 같은 이유).

유니크 제약이 걸린 테이블은 **다시 넣는 경로**가 필요하다. 남아 있는 행이 키를 붙들고 있어
같은 값을 다시 insert하면 제약에 걸린다(`readings`의 `uk(user_id, book_id)`). 제약을 푸는 대신
"다시 담기 = 그 행의 상태를 바꾸기"로 다룬다 — 제약의 본뜻은 그대로 옳다. MySQL에서 플래그를
유니크 키에 넣는 회피는 통하지 않는다: 유니크 인덱스가 NULL을 서로 다른 값으로 보기 때문에
살아 있는 행의 유일성이 오히려 깨진다.

> 그 경로에서 **사전 확인은 id만 읽는다.** 엔티티를 읽으면 영속성 컨텍스트에 올라가고, 뒤이은
> 잠금 조회가 잠금만 잡은 채 그 인스턴스를 그대로 돌려준다 — 잠갔는데도 옛 값으로 판단한다.

**스키마** — 단일 진실 공급원은 Flyway다(`ddl-auto: validate`). **적용된 마이그레이션은 절대 수정하지 않고**
항상 새 버전 파일을 추가한다. 모든 테이블 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`.

**N+1** — 목록 응답에 작성자·책이 항상 붙는다. `@EntityGraph`나 fetch join으로 함께 가져온다.
페이지 크기를 바꿔도 쿼리 수가 늘지 않아야 한다.

## 하지 않는 것

`plan.md`의 "의도적으로 하지 않는 것" 표를 따른다. 피드 팬아웃 쓰기, Redis 캐시,
팔로워 반정규화 카운터, 대댓글, Elasticsearch는 **실측 근거가 생기기 전까지 넣지 않는다.**
요청받지 않은 최적화·문서·리팩터링을 곁들이지 않는다.

## 최초 1회 세팅

새로 클론했다면 로컬 git 훅을 연결한다 (main 직접 push 차단):

```powershell
git config core.hooksPath .githooks
```

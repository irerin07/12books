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

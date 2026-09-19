# 12books 작업 목록

> `plan.md`의 Phase를 실행 가능한 단위로 쪼갠 체크리스트.
> 설계 근거와 "왜"는 `plan.md`에 있다. 이 문서는 **무엇이 남았는지**만 본다.

## 이 문서를 쓰는 법

- **한 Phase = 한 브랜치 = 한 PR.** `/feature <설명>`으로 시작한다.
- 체크(`[x]`)는 **PR이 main에 머지된 시점**에 한다. 작업 중에는 체크하지 않는다.
- 모든 구현 항목은 **테스트를 먼저 쓰고 실패를 확인한 뒤** 만든다 (`CLAUDE.md` 불변 규칙 2).
- 각 Phase 마지막의 **완료 기준**이 초록불이 아니면 다음 Phase로 넘어가지 않는다.
- 하위 항목이 애매하면 `plan.md`의 해당 Phase "기술 상세"를 읽는다. 거기 이미 결정되어 있다.

## 진행 상황

| 단계 | 내용 | 마이그레이션 | 이 시점의 제품 | 상태 |
|---|---|---|---|---|
| H | 개발 하네스 | — | (궤도) | **완료** ([#1](https://github.com/irerin07/12books/pull/1)) |
| 0 | 인프라·설정·공통·테스트 하네스 | — | (뼈대) | **완료** ([#3](https://github.com/irerin07/12books/pull/3)) |
| 1 | 사용자 · JWT 인증 | `V1__users` | 계정 | **완료** ([#6](https://github.com/irerin07/12books/pull/6)) |
| 2 | 카카오 책 검색 · 등록 | `V2__books` | 책 찾기 | **완료** ([#9](https://github.com/irerin07/12books/pull/9)) |
| 3 | 서재 · 독서 기록 · 목표 | `V3__readings` | **개인 독서 기록 앱** | **완료** ([#14](https://github.com/irerin07/12books/pull/14)) |
| 4 | 감상평 · 책별 목록 · 탐색 피드 | `V4__posts` | **공개된 독서 기록** | **완료** ([#16](https://github.com/irerin07/12books/pull/16)) |
| 5 | 팔로우 · 타임라인 | `V5__follows` | **SNS** | **완료** ([#18](https://github.com/irerin07/12books/pull/18)) |
| 6 | 좋아요 · 댓글 | `V6__reactions` | 소셜 루프 완성 | **완료** ([#31](https://github.com/irerin07/12books/pull/31)) |
| 6.5 | 책 상세 보강 (카카오 발췌 · 국중도 쪽수) | `V6_1__book_details` | 책 페이지가 채워짐 | |
| 7 | 해시태그 탐색 · 사람 검색 | `V7__hashtags` | 주제·이름 기반 발견 | |
| 8 | 프로필 · 서재 통계 | — | **지적 허영 완성** | |
| 9 | 문서화 · 성능 · 보안 마감 | — | 출시 가능 | |
| 10 | 알림 | `V9__notifications` | 돌아올 이유 | **완료** ([#41](https://github.com/irerin07/12books/pull/41)) |

**번호 순서대로 하지 않았다.** Phase 10(알림)이 6.5~9보다 먼저 들어갔고, 그 사이에 아래
"정식 공개 전에 해야 하는 것"의 L1·L2가 여럿 끝났다(요청 제한 [#42](https://github.com/irerin07/12books/pull/42),
신고·운영자 처리 [#43](https://github.com/irerin07/12books/pull/43),
비밀번호 재설정 [#47](https://github.com/irerin07/12books/pull/47),
회원탈퇴 [#49](https://github.com/irerin07/12books/pull/49)). 그래서 **"아직 안 된 가장 앞 Phase"가 곧
다음 할 일은 아니다** — 공개가 가까우면 L 목록이 먼저다.

> 이 표는 Phase만 센다. L1·L2·L3의 진행은 아래 "정식 공개 전에 해야 하는 것"의 체크박스가
> 진실이다. 둘을 합쳐서 보려고 여기에 옮겨 적지 않는다 — 같은 사실을 두 곳에 두면 갈라진다.

---

# H. 개발 하네스

> PR [#1](https://github.com/irerin07/12books/pull/1). 기능을 만들기 전에 만드는 방식을 고정한다.

- [x] `CLAUDE.md` — 불변 규칙 4개, 코드 규약, 작업 흐름
- [x] `.claude/settings.json` — 권한 허용 목록, PreToolUse 훅 등록
- [x] `.claude/hooks/guard-main.ps1` — main에서 소스 수정·커밋·푸시 차단 (8개 경로 검증 완료)
- [x] `.githooks/pre-push` — 사람 손으로 치는 main 직접 push 차단
- [x] `.claude/commands/feature.md` — 브랜치→TDD→빌드→커밋→PR→자동머지 예약
- [x] `.claude/commands/review-fix.md` — 리뷰 수집→판단→TDD 수정→push→답글
- [x] `.github/workflows/ci.yml` — PR마다 빌드 + 전체 테스트 (체크 이름 `build`)
- [x] `.github/workflows/review-gate.yml` — `/approve` 코멘트를 커밋 상태로 변환
- [x] `.github/workflows/claude.yml` — `@claude` 멘션 대응 (보조 경로)
- [x] `.github/workflows/release.yml` + `Dockerfile` — main 머지 시 GHCR 이미지
- [x] `.github/ruleset-main.json` — main 보호 규칙 정의
- [x] `.github/pull_request_template.md`
- [x] **PR #1 리뷰 · 머지**
- [x] 저장소 public 전환 — 비밀값 히스토리 점검 완료, 깨끗
- [x] auto-merge · squash 전용 · 머지 후 브랜치 삭제 설정
- [x] ruleset 적용 (`gh api repos/irerin07/12books/rulesets -X POST --input .github/ruleset-main.json`)
      — `main-protection` active
- [x] `ANTHROPIC_API_KEY` 시크릿 — **등록하지 않기로 했다.** 빠뜨린 것이 아니다.
      이 키를 쓰는 곳은 `.github/workflows/claude.yml`(`@claude` 멘션 대응) 하나뿐이고,
      그건 자리에 없을 때의 보조 수단이다. 평소 경로는 로컬 세션의 `/review-fix`이고
      별도 API 사용료가 붙는다. 그래서 워크플로는 계속 `skipping`인 채로 둔다 —
      쓰기로 마음이 바뀌면 `gh secret set ANTHROPIC_API_KEY` 한 번이면 살아난다.
- [x] 사소한 PR 하나로 승인 → 자동머지 전 구간 리허설 ([#2](https://github.com/irerin07/12books/pull/2))

---

# Phase 0 — 걸어다니는 뼈대

> PR [#3](https://github.com/irerin07/12books/pull/3). 기능은 없지만 인프라·설정·공통 코드·테스트
> 하네스가 전부 연결된 상태. 이후 모든 Phase가 이 위에 얹힌다.

## T1. 의존성 (`build.gradle`)

- [x] `spring-boot-starter-restclient` — Boot 4에서 RestClient가 별도 스타터로 분리됨
- [x] `jjwt-api:0.12.6` (implementation) + `jjwt-impl`·`jjwt-jackson` (runtimeOnly)
- [x] `springdoc-openapi-starter-webmvc-ui:3.1.0` — **2.8.x는 Boot 3 전용이라 깨진다**
- [x] `spring-boot-testcontainers`, `testcontainers-mysql`, `testcontainers-junit-jupiter`
      (버전은 Boot BOM이 관리하므로 명시하지 않는다 — 4.1.1 기준 **2.0.5**.
      `org.testcontainers:mysql` 꼴의 옛 좌표는 해결되지 않는다)

## T2. 로컬 인프라

- [x] `docker-compose.yml` — MySQL 8.4 (utf8mb4, healthcheck, named volume) + Redis 7-alpine
- [x] `docker compose up -d`로 두 컨테이너가 healthy 되는지 확인

## T3. 설정

- [x] `application.yaml` — `ddl-auto: validate`, `open-in-view: false`,
      `default_batch_fetch_size: 100`, flyway
      (actuator 노출 범위 좁히기는 Phase 9 보안 마무리에서)
- [x] `application.yaml`에 `twelvebooks.jwt.*` / `twelvebooks.kakao.*` 환경변수 주입
- [x] `application-local.yaml` — docker-compose를 가리키는 datasource/redis (비밀값 없음)
- [x] `JwtProperties`, `KakaoProperties` — `record` + `@ConfigurationProperties`

> **하지 않기로 함**: "Phase 2 전까지 카카오 키 없이도 앱이 뜨게 기본값 처리".
> 비밀값에 기본값을 주면 운영에서 더미 키로 조용히 뜨는 사고가 난다. 대신 실행할 때
> `JWT_SECRET`·`KAKAO_REST_API_KEY`를 넣도록 `plan.md` Phase 0 완료 기준에 명시했다.

## T4·T5. 공통 코드 (`com.irene.twelvebooks.common`)

- [x] `entity/BaseTimeEntity` — `@MappedSuperclass`, `createdAt`/`updatedAt`
- [x] `config/JpaConfig` — `@EnableJpaAuditing`
- [x] `error/ErrorCode` — HTTP 상태 + 코드 + 기본 메시지를 함께 소유하는 enum.
      **지금 쓰이는 것만 넣는다** (`INVALID_INPUT`, `INTERNAL_ERROR`).
      `UNAUTHORIZED`·`NOT_FOUND`·`CONFLICT` 같은 코드는 처음 쓰는 Phase에서 추가한다 —
      쓰지 않는 코드를 미리 늘어놓으면 어느 것이 실제로 나가는 응답인지 알 수 없다
- [x] `error/BusinessException`, `error/ErrorResponse{ code, message, fieldErrors }`
- [x] `error/GlobalExceptionHandler` — `BusinessException`,
      `MethodArgumentNotValidException`(→ fieldErrors), 그 외 `Exception`(→500).
      **스택트레이스·내부 메시지가 응답에 새지 않을 것**
      (`DataIntegrityViolationException`→409는 유니크 제약이 처음 생기는 Phase 6에서)
- [x] `support/CursorPage<T>` — `size + 1`건으로 `hasNext`를 판정하는 정적 팩터리.
      `size <= 0`은 입구에서 `IllegalArgumentException`
- [x] `config/RedisConfig` — 키·해시키 `StringRedisSerializer`
      (값 직렬화는 쓰는 쪽에서 정한다)
- [x] `auth/SecurityConfig` (최소) — `csrf.disable()`, STATELESS,
      `/actuator/health` permitAll, 나머지 authenticated

## T6·T7. 마이그레이션 규칙 · 테스트 하네스

- [x] `src/main/resources/db/migration/` 디렉터리 생성 (Phase 0에는 파일 없음)
- [x] `AbstractIntegrationTest` — `@SpringBootTest` + `@ServiceConnection`,
      static MySQL 컨테이너를 static 초기화 블록에서 직접 start해 전체 테스트가 하나를 공유.
      **H2 쓰지 않는다**. `@Testcontainers`는 컨테이너를 테스트 클래스 단위로 관리하므로 쓰지 않는다
- [x] Redis 컨테이너도 함께 (`@ServiceConnection`) — actuator health가 Redis 상태를 집계하므로
      없으면 헬스 체크가 DOWN이 된다
- [x] `TwelvebooksApplicationTests`를 `AbstractIntegrationTest` 상속으로 전환

## 완료 기준

- [x] `.\gradlew.bat build` **초록불** (하네스 도입 후 첫 초록)
- [x] `GET /actuator/health` = `{"status":"UP"}` (인증 없이 200)
- [x] 에러 응답에 스택트레이스가 없다

> 화이트리스트 밖 경로의 401은 Phase 1 완료 기준("토큰 없이 호출 시 401")에서 검증한다.
> Phase 0에는 인증이 필요한 엔드포인트가 아직 하나도 없다.

---

# Phase 1 — 사용자와 인증

> PR [#6](https://github.com/irerin07/12books/pull/6). 가입하고 로그인해서 토큰으로 보호된 엔드포인트를 호출할 수 있다.

- [x] `V1__users.sql`
- [x] `user/User` — email(uk), passwordHash, handle(uk), displayName, bio, avatarUrl
- [x] `auth/JwtProvider` — jjwt 0.12.x, subject=userId, claim=handle,
      `Keys.hmacShaKeyFor`. **컨테이너 없는 단위 테스트로 검증**
- [x] `auth/JwtAuthenticationFilter` — `OncePerRequestFilter`, Bearer 파싱.
      **실패해도 예외를 던지지 않고** 익명 통과 → `AuthenticationEntryPoint`가 401을 만든다
- [x] `auth/SecurityConfig` 확장 — `BCryptPasswordEncoder`, 공개 경로
      (`/api/v1/auth/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`)
- [x] `auth/@AuthUser` + `HandlerMethodArgumentResolver`
- [x] `AuthController` — signup / login / reissue / logout (**reissue·logout은 POST 전용**)
- [x] Refresh 발급 — `SecureRandom` 32바이트 불투명 문자열, **HttpOnly 쿠키**로 전달
      (`Secure`·`SameSite=Strict`·`Path=/api/v1/auth`). 서명하지 않는다
- [x] Refresh 저장 — `refresh:{토큰해시} → {userId, 발급시각}` TTL 14일.
      **SHA-256 해시해서 저장**. 역인덱스 `refresh:user:{userId} → 해시 집합`
- [x] **기기별 다중 세션** — reissue 시 rotation(옛 해시 삭제), logout은 그 세션만 삭제
- [x] access 블랙리스트는 만들지 않는다 (로그아웃 후 최대 10분 잔존을 받아들인다)
- [x] 전체 기기 로그아웃 API는 이 Phase에 만들지 않는다 (역인덱스만 준비)
- [x] `UserController` — `GET /users/{handle}`, `PATCH /me`
- [x] handle 검증 `^[a-z0-9_]{3,20}$`, 이메일/handle 중복은 유니크 제약 + 사전 조회
- [x] **비밀번호가 어떤 DTO·로그·응답에도 실리지 않는지 확인**

**완료 기준**

- [x] E2E: 가입 → 로그인 → access로 `PATCH /me` 성공
- [x] 토큰 없이 호출 시 401
- [x] 두 번 로그인 후 한쪽만 logout해도 다른 쪽 reissue는 성공
- [x] reissue로 새 토큰 발급, logout 후 같은 refresh로 reissue 시 401

---

# Phase 2 — 책 검색과 등록

> PR [#9](https://github.com/irerin07/12books/pull/9). 카카오에서 검색하고, 고른 책을 내부 DB에 확정한다.

- [x] `V2__books.sql` — `isbn13`·`sourceKey` 둘 다 nullable + unique
- [x] `book/Book` — isbn13, sourceKey, title, authors, publisher, thumbnailUrl, publishedAt
      — `pageCount`는 Phase 3에서 `readings`로 옮기고 `books`에서 걷어냈다(V3). 판본마다
      다르고 사용자가 채우는 값이라 공용 `books`가 가질 것이 아니었다
- [x] `book/KakaoBookClient` — `RestClient`, `Authorization: KakaoAK {key}`,
      타임아웃 연결 2초 / 읽기 3초
- [x] 카카오 호출 실패 → `EXTERNAL_API_ERROR`(502) 변환 + 원인 로깅.
      **검색이 죽어도 나머지 API는 살아 있어야 한다**
- [x] `BookController` — `GET /books/search`, `POST /books`, `GET /books/{id}`
- [x] **검색 결과는 저장하지 않는다.** `POST /books` 시점에만 업서트
- [x] 업서트 키: ISBN13 있으면 그것, 없으면 `sha256(title|authors|publisher)`
- [x] 동시 등록 경쟁: insert 실패(`DataIntegrityViolationException`) 시 **재조회해 기존 행 반환**
- [x] `authors` 배열은 콤마 조인 문자열로 저장

**완료 기준**

- [x] `MockRestServiceServer`로 스텁한 검색이 동작 (외부 네트워크·API 키에 의존하지 않을 것)
- [x] 같은 책 재등록 시 **새 행이 생기지 않고 동일 id 반환**
- [x] `GET /books/{id}` 조회

---

# Phase 3 — 내 서재

> PR [#14](https://github.com/irerin07/12books/pull/14). 여기서 처음으로 혼자 쓰는 독서 기록 앱으로 완결된다.

- [x] `V3__readings.sql` — `readings`, `reading_goals`. `uk(user_id, book_id)`
- [x] `reading/Reading` + `ReadingStatus`
      (`WANT_TO_READ / READING / FINISHED / PAUSED / DROPPED`)
- [x] `reading/ReadingGoal`
- [x] **상태 전이를 엔티티 메서드에 캡슐화** (`changeStatus`, `applyProgress`, `apply`).
      서비스가 필드를 직접 세팅하지 않는다 — **컨테이너 없는 단위 테스트 대상**
      — 상태·총 쪽수·진도는 `apply` 하나로 함께 받는다. 따로 적용하면 최종 상태가 멀쩡한
      요청도 중간 상태에 걸린다
  - [x] → `READING`: `startedAt`이 비어 있으면 지금으로 채운다
  - [x] → `FINISHED`: `finishedAt` 기록, `pageCount`를 알면 `currentPage`를 맞춘다
  - [x] `FINISHED` → 다른 상태: `finishedAt`을 비운다 (재독)
- [x] `currentPage`는 감소도 허용. 0 이상, `pageCount`가 있으면 그 이하
- [x] `ReadingController` — `POST /readings`, `PATCH /readings/{id}`, `DELETE /readings/{id}`
- [x] `GET /users/{handle}/library?year=&status=`
- [x] `PUT /me/goals/{year}` — 가입 시 행을 미리 만들지 않는다
  > 미설정 연도를 **기본 12권**으로 답하는 것은 조회의 몫이라 Phase 8(프로필 통계)에 있다.
> 여기서 할 일이 아니라 자리를 가리키는 줄이라 체크박스를 두지 않는다.
- [x] 수정·삭제 소유자 검증 (남의 것 → 403)

**완료 기준**

- [x] 책 담기 → 진도 갱신 → `FINISHED` 시 `finishedAt` 채워짐 → `READING`으로 되돌리면 비워짐
- [x] 서재 조회에 반영
- [x] 남의 `reading` 수정 시도 403

---

# Phase 4 — 감상평

> PR [#16](https://github.com/irerin07/12books/pull/16). 제품의 핵심 콘텐츠. 기록이 공개된 글이 되고 첫 피드가 생긴다.

- [x] `V4__posts.sql` — 인덱스 `posts(author_id, id DESC)`, `posts(book_id, id DESC)`를
      **이 마이그레이션에서** 만든다
- [x] `post/Post` — authorId, bookId, readingId, content, fromPage, toPage,
      spoiler, likeCount (저장 카운터), commentCount (응답에서만 제공하는 공개 댓글 집계)
- [x] **`Reading` 자동 생성** — 작성 시 (user, book)이 없으면 `READING`으로 만들어 연결.
      "책 담기를 잊어도 글은 써진다"는 제품 원칙의 코드상 구현 지점
- [x] 검증: 본문 1~1000자, `fromPage ≤ toPage`(둘 다 있을 때만) — 커스텀 `@AssertTrue`
- [x] `PostController` — `POST /posts`, `GET /posts/{id}`, `DELETE /posts/{id}` (작성자만)
- [x] `GET /books/{id}/posts?cursor=`
- [x] `GET /feed/explore?cursor=` — 전체 최신순 *(Phase 5에서 `/feed` 홈으로 흡수)*
- [x] **N+1 방어**: 목록에 작성자·책이 항상 붙는다.
      **여기서 안 잡으면 Phase 5 피드에서 폭발한다**
      — `@EntityGraph`가 아니라 **페이지의 작성자·책을 한 번에 모아 읽는** 방식이다. 엔티티가
      연관관계 없이 id만 갖는 Phase 3의 선택을 유지했고, 페이지가 20건이든 50건이든 쿼리는
      셋이다. `PostFeedTest`가 Hibernate `Statistics`로 지킨다

**완료 기준**

- [x] 서재에 없는 책으로 작성 → `reading` 자동 생성·연결
- [x] `GET /books/{id}/posts`와 피드에 노출
- [x] 커서로 2페이지 조회 시 중복·누락 없음
- [x] 남의 글 삭제 시도 403
- [x] 페이지 크기를 바꿔도 쿼리 수가 늘지 않음

---

# Phase 5 — 팔로우와 타임라인

> 여기서 SNS가 된다.

- [x] `V5__follows.sql` — 대리 키 `id` + `uk(follower_id, followee_id)`, `idx(followee_id, id DESC)`
- [x] `follow/Follow`
- [x] `POST|DELETE /users/{handle}/follow`
- [x] `GET /users/{handle}/followers`, `/followings`
- [x] `GET /feed/following?cursor=` — **fan-out on read**: 팔로잉 ID로 `author_id IN (...)` + 커서
- [x] `GET /feed?cursor=` — 홈. 내 글과 **팔로잉 글까지** 제외한다 (`author_id NOT IN (나, 팔로잉)`)
      → 팔로잉 글은 `/feed/following`에만 있다. 한 화면에 섞여 어수선하다는 판단으로 나눴다
- [x] 어느 쪽에도 **본인 글은 넣지 않는다.** 내 글은 `/users/{handle}/posts`
- [x] `GET /users/{handle}/posts?cursor=` — 프로필 글 목록 = 내 글만 보기
- [x] 자기 자신 팔로우 400, 중복 팔로우는 유니크 제약이 막고 409로 변환
- [x] 프로필의 팔로워/팔로잉 수는 `count` 쿼리로 시작 (반정규화는 나중에)
- [x] 프로필과 팔로워·팔로잉 목록에 `isFollowing` — 한 페이지의 대상 ID를 모아 한 번에 조회
- [x] 책 검색은 제목·저자를 나눠 좁힐 수 있다 (`target=TITLE|AUTHOR`)

**완료 기준**

- [x] A가 B를 팔로우 → A의 `/feed/following`에 B의 글만, A와 C의 글은 안 보임
- [x] 같은 상황에서 A의 `/feed`에는 C의 글만 — B도 A 자신도 안 보임
- [x] 언팔로우하면 `/feed/following`에서 B의 글이 사라진다
- [x] 자기 팔로우 400, 중복 팔로우 409

---

# Phase 6 — 반응 (좋아요·댓글)

> 소셜 루프를 닫는다.

- [x] `V6__reactions.sql` — `post_likes`(대리 키 + `uk(post_id, user_id)`), `comments`
- [x] `post/PostLike`, `post/Comment` (**대댓글 없음. `parent_id`를 만들지 않는다**)
- [x] `POST|DELETE /posts/{id}/likes`
- [x] `GET|POST /posts/{id}/comments`, `DELETE /comments/{id}`
- [x] 좋아요 카운터는 **반드시 원자적 UPDATE**. 읽고-더하고-쓰지 않는다
- [x] 중복 좋아요: insert 시도 → `DataIntegrityViolationException` → 409
      ("먼저 조회해서 있으면 스킵"은 경쟁 조건에서 샌다)
- [x] 좋아요 취소는 **delete 반환 행 수가 1일 때만** 카운터 감소
- [x] **좋아요는 `posts` 행을 먼저 잠근다** — 좋아요 등록은 카운터 UPDATE를 자식
      insert보다 앞세우고(자식 insert가 FK 때문에 부모에 잡는 공유 잠금이 뒤따르는 UPDATE의
      배타 잠금과 물린다), 좋아요 취소는 지운 행 수를 봐야 하므로 `findByIdForUpdate`로
      잠금만 먼저 잡는다. 순서가 엇갈리면 같은 사람의 연속 클릭이 교착이 된다
- [x] `likedByMe` — 게시글 조회 projection의 EXISTS로 함께 조회 (별도 좋아요 조회 없음)
- [x] 댓글 수는 공개 댓글을 조회 시 집계한다. 댓글 쓰기·삭제·신고·탈퇴에서 카운터 보정이나 명시적 게시글 잠금을 하지 않는다.
- [x] 댓글 삭제 권한: 댓글 작성자 **또는** 글 작성자

**완료 기준**

- [x] 좋아요 → 1, 같은 사용자가 다시 → 409, 취소 → 0
- [x] **동시성 테스트**: N명이 동시에 눌러도 카운터가 정확히 N
- [x] 댓글 작성 시 `commentCount` 증가, 삭제 시 감소
- [x] 피드 응답의 `likedByMe`가 정확

---

# Phase 6.5 — 책 상세 보강

> 책 페이지가 표지·제목·저자뿐인 상태를 벗어난다. 카카오 발췌와 국중도 쪽수·소개를
> 한 덩어리로 — 건드리는 곳(`books`·등록 경로·서명)이 같다.

- [ ] **착수 전: 국중도 `BOOK_INTRODUCTION` 채움률 측정.** 낮으면 소개는 카카오 발췌만
- [ ] `V6_1__book_details.sql` — `books` 상세 컬럼
- [ ] `book/NlSeojiClient` — ISBN13으로 서지정보 조회
  - [ ] `PAGE` 파서: **마지막 아라비아 숫자**를 취한다 (`"x, 282 p."` → 282)
  - [ ] `EBOOK_YN=Y`·세트·파싱 결과 5 미만은 버린다
  - [ ] `external-apis.md`에 절 추가 (`ExternalApiContractTest`가 강제)
  - [ ] `MockRestServiceServer`로 스텁 — CI가 키·네트워크에 의존하지 않게
- [ ] 카카오 `contents`(발췌 250자)·`url` 파싱 — 지금은 버리고 있다
- [ ] `BookRegisterRequest`·`BookSignature` 범위 확장 (공용 `books` 오염 방지)
- [ ] `GET /books/{id}` 응답에 상세
- [ ] 서재에 담을 때 `readings.page_count` 초기값 복사 — **최종 값은 여전히 `readings`**
- [ ] 국중도 장애 시 등록은 성공하고 상세만 빈다
- [ ] 소급 배치는 **만들지 않는다** — upsert가 빈 필드만 채운다

**완료 기준**

- [ ] 등록 시 카카오 발췌·링크 저장, 국중도 쪽수 채워짐
- [ ] `GET /books/{id}`에 상세가 실림
- [ ] 담으면 `readings.page_count`가 그 값으로 시작하고, 고치면 그 기록만 바뀜
- [ ] 국중도가 죽어도 등록 성공, 쪽수만 빔
- [ ] 상세가 비었던 책이 재등록 시 채워짐

---

# Phase 7 — 해시태그 탐색과 사람 검색

> 팔로우 관계 밖에서 주제로 글을, 이름으로 사람을 만난다.

- [ ] `V7__hashtags.sql` — `hashtags`, `post_hashtags`, `idx_users_display_name`
- [ ] `tag/HashtagParser` — `#([0-9A-Za-z가-힣_]{1,30})`. **한글이 1급 시민.**
      소문자 정규화, 글 내 중복 제거, 글당 최대 10개.
      외부 의존성 없는 순수 함수 → **컨테이너 없는 단위 테스트**
- [ ] `tag/domain/Hashtag`, `PostHashtag`
- [ ] 태그 업서트 경쟁 조건 처리 (insert 실패 시 재조회)
- [ ] 글 삭제 시 `post_hashtags`는 CASCADE, `hashtags.post_count`는 **서비스에서 원자적 감소**
- [ ] `GET /tags/{name}/posts?cursor=`
- [ ] `GET /tags/trending` — MVP는 `post_count DESC` 단순 정렬
- [ ] 기존 글 소급 파싱 배치는 **만들지 않는다**
- [ ] `GET /users/search?q=&cursor=` — `handle`·`displayName` **접두** 매칭
  - [ ] 부분 일치(`%q%`)는 하지 않는다 — 인덱스를 못 탄다
  - [ ] `q`는 2자 이상, 소문자 정규화
  - [ ] 자기 자신은 결과에서 제외
  - [ ] 응답은 `CursorPage<FollowItemResponse>` (`isFollowing` 포함)

**완료 기준**

- [ ] `#소설 #SF` 포함 글 작성 → 두 태그 생성, `post_count` 1
- [ ] `GET /tags/소설/posts`에 노출
- [ ] 글 삭제 시 `post_count` 감소
- [ ] `#소설`과 `#SOSEOL`처럼 대소문자만 다른 태그가 하나로 합쳐짐
- [ ] `q=ire`로 `irene`을 찾고 `isFollowing`을 보고 바로 팔로우
- [ ] 한 글자 검색은 400, 결과에 자기 자신 없음

---

# Phase 8 — 프로필과 서재 통계

> 지적 허영의 완성. 새 테이블 없이 기존 데이터를 집계하는 읽기 전용 Phase.

- [ ] `GET /users/{handle}` 확장 — 팔로워/팔로잉 수, 올해 완독 수, 목표 권수,
      달성률, 현재 읽는 중인 책 목록
- [ ] `GET /users/{handle}/library?year=&status=` 완성 — 표지 그리드용 최소 필드
      (bookId, title, thumbnailUrl, finishedAt). **감상평 본문 같은 불필요한 필드를 싣지 않는다**
- [ ] 통계는 엔티티를 로딩하지 않고 **DTO projection 집계 쿼리로**
- [ ] 연도 필터는 `finished_at >= :yearStart and < :nextYearStart`.
      **`YEAR(finished_at) = :year`는 인덱스를 못 쓴다**
- [ ] 달성률은 저장하지 않고 매번 계산. 목표 미설정 시 분모 12
- [ ] Redis 캐시는 **넣지 않는다** (실측 없이 캐시하면 무효화 버그만 생긴다)

**완료 기준**

- [ ] 3권 완독 → `finishedThisYear=3, goal=12, rate=25%`
- [ ] 목표를 6권으로 바꾸면 50%
- [ ] 작년 완독 책은 올해 통계에 안 잡힘
- [ ] 서재 조회가 `status`/`year` 필터에 정확히 반응

---

# Phase 9 — 마감

> 남에게 넘길 수 있는 상태로 만든다.

- [ ] springdoc 문서화 — 주요 컨트롤러에 `@Tag`/`@Operation`, bearer `SecurityScheme` 등록
- [ ] **N+1 전수 점검** — `hibernate.generate_statistics=true`로 피드·책별 목록·프로필의
      실제 쿼리 수를 세고, 페이지 크기를 바꿔도 늘지 않는지 확인
- [ ] **인덱스 검증** — 주요 목록 쿼리에 `EXPLAIN`을 걸어 의도한 인덱스를 타는지 확인
- [ ] 보안 마무리 — 공개 경로 화이트리스트 재점검, actuator는 `health`만,
      에러 응답에 스택트레이스·내부 메시지가 새지 않는지 확인
- [ ] **전체 E2E 시나리오 1개** — 가입 → 로그인 → 검색 → 등록 → 담기 → 감상평 →
      팔로우 → 피드 노출 → 좋아요 → 댓글 → 태그 탐색 → 프로필 통계 반영

**완료 기준**

- [ ] `.\gradlew.bat build` 전체 초록불
- [ ] Swagger UI에서 모든 엔드포인트 수동 호출 성공

---

# Phase 10 — 알림

> 반응이 있었는데 본인이 모르면 반응이 없는 것과 같다.

- [x] `V9__notifications.sql` — `uk(recipient_id, type, actor_id, target_type, target_id)`
- [x] `notification/Notification`
- [x] 좋아요 · 댓글 · 팔로우에서 알림 생성
- [x] **생성은 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`** —
      같은 트랜잭션에 두면 알림 저장 실패가 좋아요를 롤백시킨다
- [x] **같은 스레드에서 `REQUIRES_NEW`로 하지 않는다** — 커밋 시점에 커넥션이 둘 필요해져
      동시 요청이 풀 크기에 닿으면 전부 멈춘다. 동시 좋아요 10건에서 실제로 겪었다
- [x] **안 읽은 개수에 카운터를 두지 않는다** — `users`에 더하면 Phase 6에서 세 번 겪은
      잠금 승격 교착이 그대로 온다. `count` 쿼리로 시작
- [x] 같은 대상에 반복해도 **한 건** (제약 위반은 "이미 알렸다"이므로 삼킨다)
- [x] 팔로우 알림의 대상을 비우지 않는다 — 유니크 인덱스가 NULL을 서로 다른 값으로 봐서
      제약이 발동하지 않는다
- [x] **취소해도 알림은 남긴다** — 알림은 있었던 일의 기록이다
- [x] 본인 행동은 알리지 않는다
- [x] `GET /notifications?cursor=` · `GET /notifications/unread-count`
- [x] `PATCH /notifications/{id}/read` · `POST /notifications/read-all`
- [x] 남의 알림은 403이 아니라 404 — 알림은 공개물이 아니라 존재 자체가 사적이다
- [x] 행위자·글은 페이지 전체를 모아 한 번씩 (N+1 금지)

**완료 기준**

- [x] 남이 좋아요·댓글·팔로우하면 알림이 하나 생긴다
- [x] 내 행동은 알림을 만들지 않는다
- [x] 같은 사람이 같은 대상에 반복해도 한 건이다
- [x] 안 읽은 개수가 맞고 읽음 처리하면 0이 된다
- [x] 목록 크기를 바꿔도 쿼리 수가 늘지 않는다

---

# 정식 공개 전에 해야 하는 것

설계와 근거는 `plan.md`의 같은 이름 절에 있다. 2026-09-13 출시 준비 리뷰 결과이고,
판정은 **클로즈드 베타 GO · 불특정 다수 공개 NO-GO**였다.

막는 것은 UI가 아니다. 화면 다듬기는 이 목록 뒤로 미룬다.

## L1 — 클로즈드 베타도 이게 없으면 위험하다

**요청 제한** · Backend

- [x] Redis 고정 윈도 카운터 (새 인프라 없이). 세기와 만료를 한 스크립트로 — 나눠 부르면
      그 사이에 죽었을 때 만료 없는 키가 남아 그 사람이 영원히 막힌다
- [x] 대상: 로그인 · 가입 · 감상평 · 댓글 · 좋아요 · 팔로우
- [x] `server.forward-headers-strategy` — 안 켜면 프록시 뒤에서 **전 사용자가 한 버킷**을 쓴다.
      다만 **기본은 꺼짐**이고 환경변수로만 켠다 — 켜면 헤더를 믿게 되어, 프록시가 클라이언트가
      보낸 값을 덮어쓰지 않으면 헤더 한 줄로 우회된다
- [x] 실패만 세는 경로도 **먼저 세고 성공하면 돌려준다** — 보고 나서 세면 그 틈에 여러 요청이
      함께 통과해 한도를 넘긴다
- [x] 429 + `Retry-After`, `ErrorCode` 추가
- [x] 로그인 실패는 IP 기준. **이메일 단위 잠금은 계정 열거 통로다**
- [x] **로그인은 실패만 센다** — 성공까지 세면 막는 것이 없고(무차별 대입은 실패로 이뤄진다)
      한 곳에서 여러 계정으로 로그인하는 도구만 걸린다
- [x] 한도는 설정이 아니라 엔드포인트 위에 붙인다 — 따로 두면 읽는 사람이 제한의 존재를 모른다
- [x] 테스트 사이에 Redis를 비운다 — 카운터가 남으면 앞 테스트 때문에 뒤 테스트가 429를 받는다

**비밀번호 재설정** · Backend · Frontend · Infra

- [x] `POST /auth/password-reset` — **언제나 204**(계정 유무를 알려주지 않는다).
      발송도 기다리지 않는다 — 기다리면 **응답 시간 차이만으로** 계정 유무가 드러난다
- [x] `POST /auth/password-reset/confirm` — 토큰 + 새 비밀번호. 실패는 전부 `A005`/401로
      같게 답한다(없음·만료·이미 씀을 구분하지 않는다)
- [x] 토큰은 Redis에 해시로, TTL 30분, 1회용. 읽기와 지우기를 **한 스크립트로** — 나눠 부르면
      같은 링크를 두 번 누른 두 요청이 모두 통과한다
- [x] 재설정 성공 시 그 사용자의 refresh 세션 전부 무효화
- [x] **자격증명 지문** — 무효화만으로는 부족하다. 옛 비밀번호로 이미 검증을 통과한 로그인이
      무효화 **뒤에** 만든 세션은 끊긴 적이 없어 계속 재발급된다. 세션에 **검증한 해시의
      지문**(전체 SHA-256)을 적고 재발급 때 현재 해시의 지문과 비교한다. 별도 버전 컬럼을
      두지 않는 이유 — 해시와 번호를 나누면 둘을 따로 읽게 되어 *옛 해시에 새 번호*가 붙고,
      증가 시점·동시 변경·수명을 따로 관리해야 한다. 지문은 현재 해시의 순수 함수다
- [x] **전환 정책: 지문 없는 세션은 거절**(전원 재로그인). 통과시키면 구버전 인스턴스가
      무효화 뒤에 발급한 세션이 살아남는 **배포 겹침 창**이 열린다. QA 시드 계정뿐인 지금이
      가장 싸다
- [x] 발송자는 **Resend**, 전송은 공식 **SDK**(`com.resend:resend-java`). 실패가 상태 코드·
      사유로 오고 성공 시 메시지 id가 남는다. 대가는 발송자가 코드에 박히는 것
- [x] 실측을 `external-apis.md`에 남겼다 — 도메인 인증 없이 `onboarding@resend.dev`로는
      보내지고, 인증 안 된 도메인을 `from`에 쓰면 403이다
- [x] 실제 Gmail로 보내 확인했다 — **도착하지만 스팸함**이다(도메인 인증이 없어서).
      한글은 UTF-8로 보내면 그대로 온다
- [ ] **도메인 인증(SPF·DKIM).** 그전까지 QA에서는 스팸함을 열어야 재설정 링크를 받는다.
      다시 볼 시점 = 정식 공개 전(L3) 또는 QA 참여자가 늘어 스팸함 안내가 부담이 될 때

**신고와 관리자 처리** · Backend · Policy

- [x] `V10__reports.sql` — `uk(reporter_id, target_type, target_id)`로 중복 신고 차단
      (알림이 V9를 썼다)
- [x] `POST /posts/{id}/reports` · `/comments/{id}/reports` · `/users/{handle}/reports`.
      대상이 없으면 404, 자기 것이면 400 — 없는 대상의 신고는 운영자 목록에 열어 볼 수
      없는 줄을 만든다
- [x] `GET /admin/reports?status=PENDING` · `PATCH /admin/reports/{id}`. 목록에 **신고당한
      내용을 함께** 싣는다 — 내린 글은 일반 조회로 열리지 않으므로 id만 주면 판단할 수 없다
- [x] `users`에 역할 컬럼. **권한은 매 요청 DB에서 본다** — 토큰에 실으면 권한을 뺏어도
      토큰이 만료될 때까지 관리자로 남는다
- [x] **운영자 숨김은 `deleted_at`을 재활용하지 않는다** — 별도 컬럼(`V11__admin_hide.sql`).
      작성자 삭제와 다른 사건이고 기각 시 되돌려야 한다
- [x] 댓글을 내리면 댓글 수도 함께 줄고, 되돌리면 돌아온다 — 숫자만 남으면 "댓글 1개"를
      눌렀는데 아무것도 없는 화면이 된다
- [x] 신고도 요청 제한 대상이다(30/1분, 사람 단위) — 자동화되면 운영자 화면이 막힌다

## L2 — 공개 직전

**회원탈퇴** · Backend · Policy

- [x] **예외를 발동하지 않기로 했다.** 계획서는 하드 삭제를 권고했지만, 탈퇴도 `deleted_at`으로
      처리한다 — 지운 뒤에 오는 질문에 답할 수 없고 되돌릴 방법도 없다. 보관은 1~3년
- [x] `V13__withdrawal.sql` — `users.deleted_at` + **부분 유니크**(생성 컬럼 `active_email`
      ·`active_handle`). 행이 남으면 유니크가 이메일을 붙잡아 **탈퇴가 영구 추방**이 된다
- [x] `DELETE /me` + 비밀번호 재확인. 되돌릴 수 없는 일이라 토큰만으로 받지 않는다
- [x] 탈퇴하면 **세션 전부 무효화**, 로그인도 막힌다(`A002` — 탈퇴했다고 알려주지 않는다)
- [x] 탈퇴자의 글·댓글·프로필이 조회에서 빠진다. `hidden_at`을 재활용하지 않는다 —
      그건 운영자 숨김 전용이라, 신고 기각으로 되돌릴 때 탈퇴자의 글까지 살아난다
- [x] 같은 이메일·handle로 재가입 가능
- [ ] **탈퇴 커밋과 쓰기가 정확히 엇갈리는 창.** 탈퇴가 댓글을 센 뒤, 그전에 시작한 댓글
      쓰기가 커밋되면 숫자가 하나 더 남는다(목록에는 없다). 닫으려면 쓰기 경로가 `users`
      행을 잠가야 하는데, 가장 뜨거운 경로에 잠금을 하나 더 얹는 값이 더 크다고 봤다.
      다시 볼 시점 = 실제로 어긋난 숫자가 관측될 때
- [ ] **보관 기간(1~3년) 뒤의 실제 파기.** 배치·스케줄러가 아직 없다. 다시 볼 시점 =
      첫 탈퇴로부터 1년이 가까워질 때, 또는 스케줄러가 다른 이유로 생길 때
- [ ] **본인인증 도입 시 개인정보 암호화.** 다시 볼 시점 = 본인인증을 넣을 때

**차단** · Backend — *이 목록에서 가장 큰 작업*

- [ ] `V10__blocks.sql` — `uk` + 자기 차단 금지 CHECK
- [ ] 일곱 쿼리에 조건 추가: `findHomePage` · `findTimelinePage` · `findBookPage` ·
      `findAuthorPage` · `findPostPage` · `findFollowerPage` · `findFolloweePage`
- [ ] **규약 테스트** — 목록 쿼리가 차단 조건을 빠뜨리면 빌드가 빨개진다.
      소프트 삭제 때 못 만든 하네스를 여기서 만든다

**약관 · 정책 · 연령** · Policy · Frontend

- [ ] 이용약관 · 개인정보처리방침 · 운영정책
- [ ] 가입 화면에서 확인 가능
- [ ] 14세 미만 정책 명시
- [ ] **동의 시각과 약관 버전을 저장** — 약관이 바뀌면 누가 어느 버전에 동의했는지가 필요하다

**이메일 인증** · Backend · Frontend

- [ ] 가입 → 미인증 → 인증 후 활성
- [ ] **미인증 계정의 권한 범위 결정** (다 막으면 이탈, 다 열면 무의미)

## L3 — 공개와 병행 가능

- [ ] 감상평·댓글 **수정**(`PATCH`) — 지금 삭제만 있다
- [ ] 기록하기 플로우 정리 + 작성 중 이탈 확인
- [ ] 자체 도메인 · support 주소 · 메일 발송 도메인
- [ ] 카카오 약관 확인 (아래)
- [ ] QA 시드 계정 정리
- [ ] 광고는 **출시 후에** — 지금 넣으면 동의 관리 요건이 공개를 늦춘다

## 미해결 전제 — 카카오 도서 데이터

확인된 것: **키는 번들에 없고**(백엔드 환경변수 + 서버 호출), **표지 이미지는 저장하지
않는다**(`thumbnail_url`만). 남는 질문 하나.

- [ ] 검색 결과를 DB에 영구 축적하고 이후 우리 DB에서 제공하는 것이 약관 범위인가 —
      카카오에 문의해 `external-apis.md`에 기록. 답에 따라 `books`를 TTL 있는 캐시로
      바꿔야 할 수 있다

---

# 의도적으로 하지 않는 것

체크박스가 아니다. **하지 않기로 한 결정**이므로, 필요해지면 근거와 함께 다시 연다.

| 항목 | 미루는 이유 |
|---|---|
| 피드 팬아웃 쓰기 / Redis 타임라인 | fan-out on read로 충분한 규모. 실측 지연을 본 뒤에 |
| 팔로워/팔로잉 반정규화 카운터 | `count` 쿼리로 시작. 프로필 조회가 느려지면 그때 |
| 프로필 Redis 캐시 | 무효화 로직이 버그를 부른다. 트래픽 확인 후 |
| 댓글 `parent_id` | 대댓글이 MVP 밖. 안 쓸 컬럼은 부채 |
| Elasticsearch | 내부 검색 요구가 없다. 검색은 카카오가 한다 |
| 태그 소급 파싱 배치 | MVP 데이터량에서 불필요 |
| 알림 묶기 ("외 3명") | 읽음 처리가 복잡해진다. 넣게 되면 행을 합치지 말고 조회 시점에 묶는다 |
| 알림 실시간 전달 | 폴링으로 시작. 실제 요구가 관측된 뒤에 |
| 실 서버 배포 (CD 후반) | 배포 대상 미정. 지금은 GHCR 이미지 발행까지 |
| 프로필 동시 수정 방어 (`@Version`·`@DynamicUpdate`) | 같은 사용자의 드문 동시 수정이고 프로필은 갱신 유실을 감수할 수 있다. 필요해지면 서로 다른 필드만 보존하면 되는 경우는 `@DynamicUpdate`, 같은 필드 충돌까지 감지해야 하면 `@Version` + 409. 실제 충돌 사례나 자동 저장 기능이 생길 때 |

`spec.md` §3.2의 제외 항목(소셜 로그인, 이미지 업로드, 대댓글, DM, 독서 모임,
추천 알고리즘)도 범위 밖이다.

**차단·신고는 예외로 들어왔다** — UGC 서비스를 공개하는 조건이라 위 L1-3·L2-2가 맡는다.
프론트엔드도 별도 리포로 존재한다.

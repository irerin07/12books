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
| H | 개발 하네스 | — | (궤도) | 진행 중 |
| 0 | 인프라·설정·공통·테스트 하네스 | — | (뼈대) | **완료** ([#3](https://github.com/irerin07/12books/pull/3)) |
| 1 | 사용자 · JWT 인증 | `V1__users` | 계정 | |
| 2 | 카카오 책 검색 · 등록 | `V2__books` | 책 찾기 | |
| 3 | 서재 · 독서 기록 · 목표 | `V3__readings` | **개인 독서 기록 앱** | |
| 4 | 감상평 · 책별 목록 · 탐색 피드 | `V4__posts` | **공개된 독서 기록** | |
| 5 | 팔로우 · 타임라인 | `V5__follows` | **SNS** | |
| 6 | 좋아요 · 댓글 | `V6__reactions` | 소셜 루프 완성 | |
| 7 | 해시태그 탐색 | `V7__hashtags` | 주제 기반 발견 | |
| 8 | 프로필 · 서재 통계 | — | **지적 허영 완성** | |
| 9 | 문서화 · 성능 · 보안 마감 | — | 출시 가능 | |

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
- [ ] **PR #1 리뷰 · 머지**
- [ ] 저장소 public 전환 — 비밀값 히스토리 점검 완료, 깨끗
- [ ] auto-merge · squash 전용 · 머지 후 브랜치 삭제 설정
- [ ] ruleset 적용 (`gh api repos/irerin07/12books/rulesets -X POST --input .github/ruleset-main.json`)
- [ ] `ANTHROPIC_API_KEY` 시크릿 등록
- [ ] 사소한 PR 하나로 승인 → 자동머지 전 구간 리허설

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

> 가입하고 로그인해서 토큰으로 보호된 엔드포인트를 호출할 수 있다.

- [ ] `V1__users.sql`
- [ ] `user/domain/User` — email(uk), passwordHash, handle(uk), displayName, bio, avatarUrl
- [ ] `auth/JwtProvider` — jjwt 0.12.x, subject=userId, claim=handle,
      `Keys.hmacShaKeyFor`. **컨테이너 없는 단위 테스트로 검증**
- [ ] `auth/JwtAuthenticationFilter` — `OncePerRequestFilter`, Bearer 파싱.
      **실패해도 예외를 던지지 않고** 익명 통과 → `AuthenticationEntryPoint`가 401을 만든다
- [ ] `auth/SecurityConfig` 확장 — `BCryptPasswordEncoder`, 공개 경로
      (`/api/v1/auth/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`)
- [ ] `auth/@AuthUser` + `HandlerMethodArgumentResolver`
- [ ] `AuthController` — signup / login / reissue / logout (**reissue·logout은 POST 전용**)
- [ ] Refresh 발급 — `SecureRandom` 32바이트 불투명 문자열, **HttpOnly 쿠키**로 전달
      (`Secure`·`SameSite=Strict`·`Path=/api/v1/auth`). 서명하지 않는다
- [ ] Refresh 저장 — `refresh:{토큰해시} → {userId, 발급시각}` TTL 14일.
      **SHA-256 해시해서 저장**. 역인덱스 `refresh:user:{userId} → 해시 집합`
- [ ] **기기별 다중 세션** — reissue 시 rotation(옛 해시 삭제), logout은 그 세션만 삭제
- [ ] access 블랙리스트는 만들지 않는다 (로그아웃 후 최대 10분 잔존을 받아들인다)
- [ ] 전체 기기 로그아웃 API는 이 Phase에 만들지 않는다 (역인덱스만 준비)
- [ ] `UserController` — `GET /users/{handle}`, `PATCH /me`
- [ ] handle 검증 `^[a-z0-9_]{3,20}$`, 이메일/handle 중복은 유니크 제약 + 사전 조회
- [ ] **비밀번호가 어떤 DTO·로그·응답에도 실리지 않는지 확인**

**완료 기준**

- [ ] E2E: 가입 → 로그인 → access로 `PATCH /me` 성공
- [ ] 토큰 없이 호출 시 401
- [ ] 두 번 로그인 후 한쪽만 logout해도 다른 쪽 reissue는 성공
- [ ] reissue로 새 토큰 발급, logout 후 같은 refresh로 reissue 시 401

---

# Phase 2 — 책 검색과 등록

> 카카오에서 검색하고, 고른 책을 내부 DB에 확정한다.

- [ ] `V2__books.sql` — `isbn13`·`sourceKey` 둘 다 nullable + unique
- [ ] `book/domain/Book` — isbn13, sourceKey, title, authors, publisher, thumbnailUrl,
      pageCount(nullable), publishedAt
- [ ] `book/client/KakaoBookClient` — `RestClient`, `Authorization: KakaoAK {key}`,
      타임아웃 연결 2초 / 읽기 3초
- [ ] 카카오 호출 실패 → `EXTERNAL_API_ERROR`(502) 변환 + 원인 로깅.
      **검색이 죽어도 나머지 API는 살아 있어야 한다**
- [ ] `BookController` — `GET /books/search`, `POST /books`, `GET /books/{id}`
- [ ] **검색 결과는 저장하지 않는다.** `POST /books` 시점에만 업서트
- [ ] 업서트 키: ISBN13 있으면 그것, 없으면 `sha256(title|authors|publisher)`
- [ ] 동시 등록 경쟁: insert 실패(`DataIntegrityViolationException`) 시 **재조회해 기존 행 반환**
- [ ] `authors` 배열은 콤마 조인 문자열로 저장

**완료 기준**

- [ ] `MockRestServiceServer`로 스텁한 검색이 동작 (외부 네트워크·API 키에 의존하지 않을 것)
- [ ] 같은 책 재등록 시 **새 행이 생기지 않고 동일 id 반환**
- [ ] `GET /books/{id}` 조회

---

# Phase 3 — 내 서재

> 여기서 처음으로 혼자 쓰는 독서 기록 앱으로 완결된다.

- [ ] `V3__readings.sql` — `readings`, `reading_goals`. `uk(user_id, book_id)`
- [ ] `reading/domain/Reading` + `ReadingStatus`
      (`WANT_TO_READ / READING / FINISHED / PAUSED / DROPPED`)
- [ ] `reading/domain/ReadingGoal`
- [ ] **상태 전이를 엔티티 메서드에 캡슐화** (`changeStatus`, `updateProgress`).
      서비스가 필드를 직접 세팅하지 않는다 — **컨테이너 없는 단위 테스트 대상**
  - [ ] → `READING`: `startedAt`이 비어 있으면 지금으로 채운다
  - [ ] → `FINISHED`: `finishedAt` 기록, `pageCount`를 알면 `currentPage`를 맞춘다
  - [ ] `FINISHED` → 다른 상태: `finishedAt`을 비운다 (재독)
- [ ] `currentPage`는 감소도 허용. 0 이상, `pageCount`가 있으면 그 이하
- [ ] `ReadingController` — `POST /readings`, `PATCH /readings/{id}`, `DELETE /readings/{id}`
- [ ] `GET /users/{handle}/library?year=&status=`
- [ ] `PUT /me/goals/{year}` — 미설정 시 조회 계층에서 **기본 12권**. 가입 시 행을 미리 만들지 않는다
- [ ] 수정·삭제 소유자 검증 (남의 것 → 403)

**완료 기준**

- [ ] 책 담기 → 진도 갱신 → `FINISHED` 시 `finishedAt` 채워짐 → `READING`으로 되돌리면 비워짐
- [ ] 서재 조회에 반영
- [ ] 남의 `reading` 수정 시도 403

---

# Phase 4 — 감상평

> 제품의 핵심 콘텐츠. 기록이 공개된 글이 되고 첫 피드가 생긴다.

- [ ] `V4__posts.sql` — 인덱스 `posts(author_id, id DESC)`, `posts(book_id, id DESC)`를
      **이 마이그레이션에서** 만든다
- [ ] `post/domain/Post` — authorId, bookId, readingId, content, fromPage, toPage,
      spoiler, likeCount, commentCount (카운터는 0으로 시작, Phase 6에서 쓰임)
- [ ] **`Reading` 자동 생성** — 작성 시 (user, book)이 없으면 `READING`으로 만들어 연결.
      "책 담기를 잊어도 글은 써진다"는 제품 원칙의 코드상 구현 지점
- [ ] 검증: 본문 1~1000자, `fromPage ≤ toPage`(둘 다 있을 때만) — 커스텀 `@AssertTrue`
- [ ] `PostController` — `POST /posts`, `GET /posts/{id}`, `DELETE /posts/{id}` (작성자만)
- [ ] `GET /books/{id}/posts?cursor=`
- [ ] `GET /feed/explore?cursor=` — 전체 최신순
- [ ] **N+1 방어**: 목록에 작성자·책이 항상 붙는다 → `@EntityGraph`/fetch join.
      **여기서 안 잡으면 Phase 5 피드에서 폭발한다**

**완료 기준**

- [ ] 서재에 없는 책으로 작성 → `reading` 자동 생성·연결
- [ ] `GET /books/{id}/posts`와 `/feed/explore`에 노출
- [ ] 커서로 2페이지 조회 시 중복·누락 없음
- [ ] 남의 글 삭제 시도 403
- [ ] 페이지 크기를 바꿔도 쿼리 수가 늘지 않음

---

# Phase 5 — 팔로우와 타임라인

> 여기서 SNS가 된다.

- [ ] `V5__follows.sql` — 복합 PK(follower_id, followee_id), `idx(followee_id)`
- [ ] `follow/domain/Follow`
- [ ] `POST|DELETE /users/{handle}/follow`
- [ ] `GET /users/{handle}/followers`, `/followings`
- [ ] `GET /feed?cursor=` — **fan-out on read**: 팔로잉 ID로 `author_id IN (...)` + 커서
- [ ] 피드에 **본인 글도 포함**한다
- [ ] 자기 자신 팔로우 400, 중복 팔로우는 복합 PK가 막고 409로 변환
- [ ] 프로필의 팔로워/팔로잉 수는 `count` 쿼리로 시작 (반정규화는 나중에)

**완료 기준**

- [ ] A가 B를 팔로우 → A의 `/feed`에 B와 A의 글만, C의 글은 안 보임
- [ ] 언팔로우하면 B의 글이 사라짐
- [ ] 자기 팔로우 400, 중복 팔로우 409

---

# Phase 6 — 반응 (좋아요·댓글)

> 소셜 루프를 닫는다.

- [ ] `V6__reactions.sql` — `post_likes`(복합 PK), `comments`
- [ ] `post/domain/PostLike`, `post/domain/Comment` (**대댓글 없음. `parent_id`를 만들지 않는다**)
- [ ] `POST|DELETE /posts/{id}/likes`
- [ ] `GET|POST /posts/{id}/comments`, `DELETE /comments/{id}`
- [ ] 카운터는 **반드시 원자적 UPDATE**. 읽고-더하고-쓰지 않는다
- [ ] 중복 좋아요: insert 시도 → `DataIntegrityViolationException` → 409
      ("먼저 조회해서 있으면 스킵"은 경쟁 조건에서 샌다)
- [ ] 좋아요 취소는 **delete 반환 행 수가 1일 때만** 카운터 감소
- [ ] `likedByMe` — 페이지의 postId 집합으로 **한 번에 조회**해 Set으로 매핑 (N+1 금지)
- [ ] 댓글 삭제 권한: 댓글 작성자 **또는** 글 작성자

**완료 기준**

- [ ] 좋아요 → 1, 같은 사용자가 다시 → 409, 취소 → 0
- [ ] **동시성 테스트**: N명이 동시에 눌러도 카운터가 정확히 N
- [ ] 댓글 작성 시 `commentCount` 증가, 삭제 시 감소
- [ ] 피드 응답의 `likedByMe`가 정확

---

# Phase 7 — 해시태그 탐색

> 팔로우 관계 밖에서 주제로 글을 만난다.

- [ ] `V7__hashtags.sql` — `hashtags`, `post_hashtags`
- [ ] `tag/HashtagParser` — `#([0-9A-Za-z가-힣_]{1,30})`. **한글이 1급 시민.**
      소문자 정규화, 글 내 중복 제거, 글당 최대 10개.
      외부 의존성 없는 순수 함수 → **컨테이너 없는 단위 테스트**
- [ ] `tag/domain/Hashtag`, `PostHashtag`
- [ ] 태그 업서트 경쟁 조건 처리 (insert 실패 시 재조회)
- [ ] 글 삭제 시 `post_hashtags`는 CASCADE, `hashtags.post_count`는 **서비스에서 원자적 감소**
- [ ] `GET /tags/{name}/posts?cursor=`
- [ ] `GET /tags/trending` — MVP는 `post_count DESC` 단순 정렬
- [ ] 기존 글 소급 파싱 배치는 **만들지 않는다**

**완료 기준**

- [ ] `#소설 #SF` 포함 글 작성 → 두 태그 생성, `post_count` 1
- [ ] `GET /tags/소설/posts`에 노출
- [ ] 글 삭제 시 `post_count` 감소
- [ ] `#소설`과 `#SOSEOL`처럼 대소문자만 다른 태그가 하나로 합쳐짐

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
| 실 서버 배포 (CD 후반) | 배포 대상 미정. 지금은 GHCR 이미지 발행까지 |
| 프로필 동시 수정 방어 (`@Version`·`@DynamicUpdate`) | 같은 사용자의 드문 동시 수정이고 프로필은 갱신 유실을 감수할 수 있다. 필요해지면 서로 다른 필드만 보존하면 되는 경우는 `@DynamicUpdate`, 같은 필드 충돌까지 감지해야 하면 `@Version` + 409. 실제 충돌 사례나 자동 저장 기능이 생길 때 |

`spec.md` §3.2의 제외 항목(소셜 로그인, 알림, 이미지 업로드, 대댓글, 차단·신고, DM,
독서 모임, 추천 알고리즘, 프론트엔드)도 범위 밖이다.

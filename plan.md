# 12books — 단계별 개발 계획

> `spec.md`(PRD)의 구현 로드맵. 버전 0.1 · 2026-09-03

## 이 문서의 원칙

1. **매 Phase는 동작하는 제품이다.** 레이어(엔티티 전부 → 서비스 전부 → 컨트롤러 전부)로
   자르지 않고 **기능 세로 단면**으로 자른다. 각 Phase가 끝나면 실제로 호출 가능한 API가 늘어난다.
2. **제품이 자라는 순서를 따른다.** 개인 기록 앱(P1~P4) → 공개 글(P4) → SNS(P5~P7) → 정체성(P8).
   중간에 멈춰도 그 자체로 쓸모 있는 상태여야 한다.
3. **스키마도 같이 자란다.** `V1__init.sql`에 전체 스키마를 몰아넣지 않고
   **Phase마다 마이그레이션을 하나씩 추가**한다. (spec.md §5의 단일 `V1__init.sql` 서술을 이 문서가 대체)
4. **완료 기준은 "테스트 통과"다.** 각 Phase 끝에 `.\gradlew.bat build`가 초록불이어야 다음으로 간다.

---

# 기술 기반

## T1. 의존성

현재 `build.gradle`에 이미 있는 것: `actuator`, `data-jpa`, `data-redis`, `flyway`,
`security`, `validation`, `webmvc`, `flyway-mysql`, `lombok`, `devtools`, `mysql-connector-j`.

**추가할 것** (Maven Central에서 Boot 4.1.1 호환 확인 완료):

```groovy
dependencies {
    // 카카오 책 검색 API 호출 — Boot 4에서 RestClient는 별도 스타터로 분리됨
    implementation 'org.springframework.boot:spring-boot-starter-restclient'

    // JWT — api는 컴파일, impl/jackson은 런타임에만 필요
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly   'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly   'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // API 문서 — 반드시 3.x. 2.8.x는 Spring Boot 3 / Framework 6 전용이라 Boot 4에서 깨진다
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0'

    // 테스트용 실 MySQL — 버전은 Boot 4.1.1 BOM이 관리하므로 명시하지 않는다
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:mysql'
    testImplementation 'org.testcontainers:junit-jupiter'
}
```

> **주의**: `springdoc-openapi-starter-webmvc-ui`는 Maven Central 검색 UI에서 2.8.6이 최신처럼
> 보이지만 그건 Boot 3 라인이다. 3.1.0의 부모 POM이 `spring-boot-starter-parent:4.1.0`임을 확인했다.

## T2. 로컬 인프라 — `docker-compose.yml`

```yaml
services:
  mysql:
    image: mysql:8.4
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: twelvebooks
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot"]
      interval: 5s
      retries: 20
    volumes: ["mysql-data:/var/lib/mysql"]
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
volumes:
  mysql-data:
```

한글 제목·감상평이 핵심 데이터이므로 `utf8mb4`는 타협 대상이 아니다.

## T3. 설정 파일

`application.yaml` — 프로필 공통 + 환경변수 주입:

```yaml
spring:
  application: { name: twelvebooks }
  jpa:
    hibernate.ddl-auto: validate      # 스키마의 단일 진실 공급원은 Flyway
    open-in-view: false               # 지연 로딩이 뷰까지 새는 것을 막는다
    properties.hibernate.default_batch_fetch_size: 100   # N+1 1차 방어선
  flyway: { enabled: true, baseline-on-migrate: true }

twelvebooks:
  jwt:
    secret: ${JWT_SECRET}
    access-token-ttl: PT30M
    refresh-token-ttl: P14D
  kakao:
    rest-api-key: ${KAKAO_REST_API_KEY}
    base-url: https://dapi.kakao.com
```

`application-local.yaml` — docker-compose를 가리키는 datasource/redis 접속 정보.
비밀값은 파일에 넣지 않고 환경변수(`JWT_SECRET`, `KAKAO_REST_API_KEY`)로만 주입한다.

`@ConfigurationProperties`로 바인딩할 레코드 두 개: `JwtProperties`, `KakaoProperties`.

## T4. 패키지 구조

기술 레이어가 아니라 **기능(도메인) 단위 수직 분할**. 각 패키지 안에
`domain / repository / service / controller / dto`를 둔다.

```
com.irene.twelvebooks
├─ common/      config, error, response, entity(BaseTimeEntity), support(CursorPage)
├─ auth/        SecurityConfig, JwtProvider, JwtAuthenticationFilter, @AuthUser, 가입·로그인
├─ user/        User, 프로필, 통계 집계
├─ book/        Book, KakaoBookClient, 검색·업서트
├─ reading/     Reading, ReadingGoal
├─ post/        Post, PostLike, Comment
├─ follow/      Follow
├─ tag/         Hashtag, PostHashtag
└─ feed/        타임라인 조회 (읽기 전용 조합 서비스)
```

## T5. 공통 규약

### 에러 처리
`ErrorCode` enum이 **HTTP 상태 + 코드 + 기본 메시지를 함께 소유**한다.
컨트롤러/서비스는 `throw new BusinessException(ErrorCode.POST_NOT_FOUND)` 만 하고,
`@RestControllerAdvice GlobalExceptionHandler`가 `{ code, message, fieldErrors }`로 변환한다.
`MethodArgumentNotValidException`도 여기서 잡아 `fieldErrors`를 채운다.

### 커서 페이징
목록은 전부 `CursorPage<T> { List<T> items; Long nextCursor; boolean hasNext; }`.
PK가 auto-increment이므로 `id DESC`가 곧 최신순이다. 별도 정렬 컬럼이 필요 없다.

```java
// size + 1건을 조회해서 hasNext를 판정하고, 마지막 1건은 버린다
@Query("select p from Post p where (:cursor is null or p.id < :cursor) order by p.id desc")
List<Post> findPage(@Param("cursor") Long cursor, Pageable pageable);
```

`size`는 기본 20, 최대 50으로 컨트롤러에서 clamp한다.

### 반정규화 카운터
`posts.like_count` / `comment_count`는 읽기 성능을 위한 반정규화다.
**엔티티 필드를 읽고-더하고-쓰지 않는다** (동시 요청에 유실됨). 반드시 원자적 UPDATE:

```java
@Modifying
@Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id")
void incrementLikeCount(@Param("id") Long id);
```

중복 좋아요는 `post_likes` 복합 PK 제약이 막고, `DataIntegrityViolationException`을 409로 변환한다.

## T6. 마이그레이션 규칙

- 파일명 `V{n}__{목적}.sql`, `src/main/resources/db/migration/`.
- **적용된 마이그레이션은 절대 수정하지 않는다.** 변경은 항상 새 버전 파일로.
- 모든 테이블 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`.
- FK는 걸되 `ON DELETE CASCADE`는 자식 데이터가 명백히 종속일 때만
  (`post_likes`, `post_hashtags`, `comments`). `posts.book_id`처럼 참조 대상이 독립 생명주기를
  가지면 `RESTRICT`.

## T7. 테스트 전략

- **`AbstractIntegrationTest`** — `@SpringBootTest` + `@Testcontainers` + `@ServiceConnection`으로
  MySQL 컨테이너를 띄우고 **실제 Flyway 마이그레이션을 그대로 태운다**.
  static 필드로 선언해 전체 테스트에서 컨테이너 하나를 재사용한다.
  H2는 MySQL 전용 DDL과 호환되지 않으므로 쓰지 않는다.
- **MockMvc E2E** — Phase마다 그 시점의 핵심 여정 하나를 끝까지 통과시킨다.
- **단위 테스트** — `JwtProvider`, 해시태그 파서, 독서 상태 전이 등 순수 로직은 컨테이너 없이.
- **카카오 API는 `MockRestServiceServer`로 스텁.** 테스트가 외부 네트워크·API 키에
  의존하면 CI에서 깨진다.

---

# 개발 Phase

각 Phase는 `목표 → 산출물 → 기술 상세 → 완료 기준` 순서다.

---

## Phase 0 — 걸어다니는 뼈대

**목표**: 아무 기능도 없지만 **인프라·설정·공통 코드·테스트 하네스가 전부 연결된** 상태.
이후 모든 Phase가 이 위에 얹힌다.

**산출물**
- `build.gradle` 의존성 추가 (T1)
- `docker-compose.yml` (T2)
- `application.yaml`, `application-local.yaml`, `JwtProperties`, `KakaoProperties` (T3)
- `common/entity/BaseTimeEntity` — `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener)`,
  `createdAt`/`updatedAt`. `@EnableJpaAuditing`은 `JpaConfig`에.
- `common/error/` — `ErrorCode`, `BusinessException`, `ErrorResponse`, `GlobalExceptionHandler`
- `common/support/CursorPage`
- `common/config/RedisConfig` — 키는 `StringRedisSerializer`
- `AbstractIntegrationTest`

**완료 기준**

`docker compose up -d`로 인프라를 올린 뒤:

```powershell
$env:JWT_SECRET = "<32바이트 이상의 임의 문자열>"
$env:KAKAO_REST_API_KEY = "<카카오 REST API 키>"
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

`GET /actuator/health` = `{"status":"UP"}`. 컨텍스트 로딩 테스트 1개 통과.

`local` 프로필이 없으면 datasource 접속 정보가 없어 뜨지 않는다. 비밀값 두 개도
`application.yaml`이 환경변수로만 받으므로 미리 넣어야 한다 — 애플리케이션에
기본 프로필을 박는 대신 실행하는 쪽에서 명시한다.

---

## Phase 1 — 사용자와 인증

**목표**: 가입하고 로그인해서 **토큰으로 보호된 엔드포인트를 호출**할 수 있다.

**산출물**
- `V1__users.sql`
- `user/domain/User` — email(uk), passwordHash, handle(uk), displayName, bio, avatarUrl
- `auth/JwtProvider` — jjwt 0.12.x API (`Jwts.builder()...signWith(key)`),
  subject에 userId, claim에 handle. `SecretKey`는 `Keys.hmacShaKeyFor(secret bytes)`.
- `auth/JwtAuthenticationFilter` — `OncePerRequestFilter`, `Authorization: Bearer` 파싱,
  검증 성공 시 `SecurityContext`에 인증 객체 주입. **실패해도 여기서 예외를 던지지 않고**
  익명으로 통과시켜 `AuthenticationEntryPoint`가 401을 만들게 한다.
- `auth/SecurityConfig` — Spring Security 7 `SecurityFilterChain` 빈.
  `csrf.disable()`, `sessionManagement STATELESS`, `BCryptPasswordEncoder`,
  공개 경로: `/api/v1/auth/**`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`.
- `auth/@AuthUser` + `HandlerMethodArgumentResolver` — 컨트롤러가 `@AuthUser Long userId`로 받게.
- `AuthController`: signup / login / reissue / logout
- `UserController`: `GET /users/{handle}`, `PATCH /me`

**기술 상세**
- Refresh 토큰은 Redis에 `refresh:{userId}` 키로 TTL 14일 저장.
  reissue 시 저장값과 대조하고 **새 refresh로 교체(rotation)**, logout 시 `DELETE`.
  이렇게 해야 stateless를 유지하면서도 로그아웃이 즉시 유효해진다.
- `handle` 검증: `^[a-z0-9_]{3,20}$`. 이메일/handle 중복은 DB 유니크 제약 + 사전 조회 둘 다.
- 비밀번호는 어떤 DTO·로그·응답에도 절대 실리지 않게 한다.

**완료 기준**
E2E: 가입 → 로그인 → access 토큰으로 `PATCH /me` 성공 → 토큰 없이 호출 시 401 →
reissue로 새 토큰 발급 → logout 후 같은 refresh로 reissue 시 401.

---

## Phase 2 — 책 검색과 등록

**목표**: 카카오에서 책을 검색하고, 고른 책을 내부 DB에 확정할 수 있다.

**산출물**
- `V2__books.sql`
- `book/domain/Book` — isbn13, sourceKey, title, authors, publisher, thumbnailUrl,
  pageCount, publishedAt
- `book/client/KakaoBookClient` — `RestClient` 기반, `Authorization: KakaoAK {key}` 헤더
- `BookController`: `GET /books/search`, `POST /books`, `GET /books/{id}`

**기술 상세**
- **검색 결과는 저장하지 않는다.** 사용자가 서재에 담는 순간(`POST /books`)에만 업서트한다.
  그래야 `books` 테이블에 아무도 안 읽는 책이 쌓이지 않는다.
- 업서트 키: ISBN13이 있으면 `isbn13`, 없으면 `sha256(title|authors|publisher)`를 `sourceKey`로.
  두 컬럼 모두 nullable + unique (MySQL은 NULL 중복을 허용하므로 이 조합이 성립).
- 동시 등록 경쟁: `findByIsbn13` → 없으면 insert → `DataIntegrityViolationException` 발생 시
  **한 번 더 조회해서 기존 행을 반환**. 락 없이 안전하다.
- 카카오 `authors`는 배열이지만 MVP는 콤마 조인 문자열로 저장한다(정렬·검색 요구가 없음).
- 카카오 호출 실패는 `ErrorCode.EXTERNAL_API_ERROR`(502)로 변환하고 원인을 로깅.
  타임아웃은 연결 2초 / 읽기 3초. **검색이 죽어도 나머지 API는 살아 있어야 한다.**
- 카카오는 총 쪽수를 주지 않는다. `pageCount`는 nullable이며 사용자가 나중에 입력한다
  (spec.md §10-2 미해결 이슈).

**완료 기준**
`MockRestServiceServer`로 스텁한 검색 → `POST /books` 등록 → 같은 책 재등록 시
**새 행이 생기지 않고 동일 id 반환** → `GET /books/{id}` 조회.

---

## Phase 3 — 내 서재

**목표**: 여기서 처음으로 **혼자 쓰는 독서 기록 앱**으로 제품이 완결된다. 아직 SNS는 아니다.

**산출물**
- `V3__readings.sql` (`readings`, `reading_goals`)
- `reading/domain/Reading` + `ReadingStatus` enum
  (`WANT_TO_READ / READING / FINISHED / PAUSED / DROPPED`)
- `reading/domain/ReadingGoal`
- `ReadingController`: `POST /readings`, `PATCH /readings/{id}`, `DELETE /readings/{id}`
- `GET /users/{handle}/library?year=&status=`
- `PUT /me/goals/{year}`

**기술 상세**
- `uk(user_id, book_id)` — 한 사람이 같은 책을 두 번 담을 수 없다. 재독은 상태를 되돌려 재사용.
- **상태 전이는 엔티티 메서드에 캡슐화**한다 (`reading.changeStatus(...)`, `reading.updateProgress(...)`).
  서비스가 필드를 직접 세팅하면 규칙이 흩어진다.
  - → `READING`: `startedAt`이 비어 있으면 지금으로 채운다
  - → `FINISHED`: `finishedAt` 기록, `pageCount`를 알면 `currentPage`를 거기에 맞춘다
  - `FINISHED` → 다른 상태: `finishedAt`을 비운다 (재독 시작)
- `currentPage`는 **감소도 허용**한다(되돌아가 읽기). 0 이상, `pageCount`가 있으면 그 이하.
- 연간 목표 미설정 시 조회 계층에서 **기본 12권**으로 간주한다. 가입 시 행을 미리 만들지 않는다.
- 수정·삭제는 소유자 검증 필수 (`reading.userId != authUserId` → 403).

**완료 기준**
책 담기 → 진도 갱신 → `FINISHED` 전환 시 `finishedAt` 채워짐 → `READING`으로 되돌리면 비워짐 →
서재 조회에 반영. 남의 `reading` 수정 시도 403.

---

## Phase 4 — 감상평

**목표**: 제품의 핵심 콘텐츠. 기록이 **공개된 글**이 되고, 첫 피드가 생긴다.

**산출물**
- `V4__posts.sql`
- `post/domain/Post` — authorId, bookId, readingId, content, fromPage, toPage, spoiler,
  likeCount, commentCount (카운터는 0으로 시작, Phase 6에서 쓰임)
- `PostController`: `POST /posts`, `GET /posts/{id}`, `DELETE /posts/{id}`
- `GET /books/{id}/posts?cursor=`
- `GET /feed/explore?cursor=` — 전체 최신순

**기술 상세**
- **`Reading` 자동 생성**: 작성 시 (user, book)으로 조회해 없으면 `READING` 상태로 만들어 연결한다.
  "책 담기를 잊어도 글은 써진다"는 제품 원칙(spec.md §1.4)의 코드상 구현 지점.
- 본문 1~1000자, `fromPage ≤ toPage` (둘 다 있을 때만). 커스텀 `@AssertTrue` 검증 메서드로.
- `book_id`를 `posts`에 비정규화해 둔 덕분에 책별 조회가 `readings` 조인 없이 끝난다.
- 인덱스 `posts(author_id, id DESC)`, `posts(book_id, id DESC)`를 이 마이그레이션에서 만든다.
- 목록 응답에는 작성자(handle/displayName/avatar)와 책(title/thumbnail)이 항상 붙는다.
  → **N+1 주의**. `@EntityGraph` 또는 fetch join으로 `author`, `book`을 함께 가져온다.
  Phase 4에서 잡아두지 않으면 Phase 5의 피드에서 폭발한다.
- 탐색 피드(`/feed/explore`)를 팔로우보다 먼저 만드는 이유: 팔로우 관계가 없어도
  피드가 성립해야 신규 사용자가 빈 화면을 보지 않는다.
- 삭제는 작성자 본인만. 연관 삭제는 FK `ON DELETE CASCADE`에 맡긴다.

**완료 기준**
서재에 없는 책으로 감상평 작성 → `reading`이 자동 생성되어 연결됨 →
`GET /books/{id}/posts`와 `/feed/explore`에 노출 → 커서로 2페이지 조회 시 중복·누락 없음 →
남의 글 삭제 시도 403.

---

## Phase 5 — 팔로우와 타임라인

**목표**: **여기서 SNS가 된다.** 내가 고른 사람들의 글만 흐르는 타임라인.

**산출물**
- `V5__follows.sql`
- `follow/domain/Follow` — 복합 PK(follower_id, followee_id), `idx(followee_id)`
- `POST|DELETE /users/{handle}/follow`
- `GET /users/{handle}/followers`, `/followings`
- `GET /feed?cursor=` — 팔로잉 + 본인

**기술 상세**
- MVP는 **fan-out on read**: 팔로잉 ID 목록을 뽑아 `posts.author_id IN (...)` + 커서 조건.
  `posts(author_id, id DESC)` 인덱스가 이걸 커버한다.
  팬아웃 쓰기·Redis 타임라인은 **실제 지연이 관측된 뒤에** 도입한다 (조기 최적화 금지).
- 팔로잉 ID 조회는 매 요청마다 발생하므로 Redis 캐시 후보이지만,
  Phase 5에서는 넣지 않는다. 팔로잉 수천 명 이전에는 문제가 되지 않는다.
- 자기 자신 팔로우 차단(400). 중복 팔로우는 복합 PK가 막고 409로 변환.
- 피드에 **본인 글도 포함**한다. 자기 글이 안 보이는 타임라인은 어색하다.
- 프로필의 팔로워/팔로잉 수는 이 단계에서 `count` 쿼리로 시작한다.
  반정규화 카운터는 필요해지면 그때.

**완료 기준**
A가 B를 팔로우 → A의 `/feed`에 B의 글과 A 자신의 글만 보이고 C의 글은 안 보임 →
언팔로우하면 B의 글이 사라짐 → 자기 자신 팔로우 400, 중복 팔로우 409.

---

## Phase 6 — 반응 (좋아요·댓글)

**목표**: 소셜 루프를 닫는다. 기록이 반응을 얻는다.

**산출물**
- `V6__reactions.sql` (`post_likes`, `comments`)
- `post/domain/PostLike` (복합 PK), `post/domain/Comment`
- `POST|DELETE /posts/{id}/likes`
- `GET|POST /posts/{id}/comments`, `DELETE /comments/{id}`

**기술 상세**
- 카운터 갱신은 **반드시 원자적 UPDATE** (T5 참고). 읽고-더하고-쓰면 동시 좋아요가 유실된다.
- 중복 좋아요: insert 시도 → `DataIntegrityViolationException` → 409.
  "먼저 조회해서 있으면 스킵"은 경쟁 조건에서 새므로 제약을 1차 방어선으로 삼는다.
- 좋아요 취소는 `delete` 반환 행 수가 1일 때만 카운터를 감소시킨다.
  0이면 애초에 누른 적이 없으므로 카운터를 건드리지 않는다.
- 댓글은 MVP에서 **1단계**(대댓글 없음). `parent_id`를 미리 만들지 않는다 —
  쓰지 않을 컬럼은 부채다.
- 게시글 응답에 `likedByMe` 필드 필요. 목록에서 글마다 조회하면 N+1이므로
  **페이지의 postId 집합으로 한 번에 조회**해 Set으로 만들어 매핑한다.
- 댓글 삭제 권한: 댓글 작성자 **또는 글 작성자**.

**완료 기준**
좋아요 → `likeCount` 1, 같은 사용자가 다시 → 409, 취소 → 0 →
동시에 N명이 좋아요를 눌러도 카운터가 정확히 N (동시성 테스트) →
댓글 작성 시 `commentCount` 증가, 삭제 시 감소 → 피드 응답의 `likedByMe`가 정확.

---

## Phase 7 — 해시태그 탐색

**목표**: 팔로우 관계 밖에서 **주제로** 글을 만난다.

**산출물**
- `V7__hashtags.sql` (`hashtags`, `post_hashtags`)
- `tag/HashtagParser`, `tag/domain/Hashtag`, `PostHashtag`
- `GET /tags/{name}/posts?cursor=`, `GET /tags/trending`

**기술 상세**
- 파서 정규식: `#([0-9A-Za-z가-힣_]{1,30})` — **한글이 1급 시민**이다.
  추출 후 소문자 정규화, 한 글에서 중복 제거, 글당 최대 10개로 제한.
- 파서는 외부 의존성 없는 순수 함수 → 컨테이너 없이 단위 테스트.
- 태그 업서트도 Phase 2의 책과 같은 경쟁 조건: insert 실패 시 재조회.
- 글 삭제 시 `post_hashtags`는 `ON DELETE CASCADE`로 정리되지만
  `hashtags.post_count`는 서비스에서 원자적으로 감소시킨다.
- `GET /tags/trending`은 MVP에서 `post_count DESC` 단순 정렬.
  시간 가중(최근 N일) 인기도는 후속 (spec.md §10-3).
- **Phase 4에서 작성된 기존 글에는 태그가 없다.** 소급 파싱 배치는 만들지 않는다 —
  MVP 단계에서 데이터가 얼마 없고, 신규 글부터 적용되면 충분하다.

**완료 기준**
`#소설 #SF` 포함 글 작성 → 두 태그 생성, `post_count` 1 →
`GET /tags/소설/posts`에 노출 → 글 삭제 시 `post_count` 감소 →
`#소설`과 `#SOSEOL`처럼 대소문자만 다른 태그가 하나로 합쳐짐.

---

## Phase 8 — 프로필과 서재 통계

**목표**: **지적 허영의 완성.** "나는 이런 책을 읽는 사람"이 눈에 보이게 만든다.
새 테이블 없이 기존 데이터를 집계하는 읽기 전용 Phase.

**산출물**
- `GET /users/{handle}` 응답 확장 — 팔로워/팔로잉 수, 올해 완독 수,
  목표 권수, 달성률, 현재 읽는 중인 책 목록
- `GET /users/{handle}/library?year=&status=` 완성 — 표지 그리드용 최소 필드
  (bookId, title, thumbnailUrl, finishedAt)

**기술 상세**
- 통계는 **엔티티를 로딩하지 않고 집계 쿼리로** 뽑는다. 프로필 하나에 여러 count가 필요하므로
  DTO projection을 쓴다.
- 연도 필터는 `finished_at >= :yearStart and finished_at < :nextYearStart` —
  `YEAR(finished_at) = :year`처럼 컬럼에 함수를 씌우면 인덱스를 못 쓴다.
- 달성률은 저장하지 않고 매번 계산한다. 목표 미설정 시 분모 12.
- 서재 응답은 표지 그리드용이므로 감상평 본문 등 불필요한 필드를 싣지 않는다.
- 프로필은 트래픽이 몰리는 읽기 경로다. Redis 캐시 후보로 표시해 두되
  **이번에는 넣지 않는다** — 실측 없이 캐시를 넣으면 무효화 버그만 생긴다.

**완료 기준**
3권 완독한 사용자의 프로필에 `finishedThisYear=3, goal=12, rate=25%` →
목표를 6권으로 바꾸면 50% → 작년 완독 책은 올해 통계에 안 잡힘 →
서재 조회가 `status`/`year` 필터에 정확히 반응.

---

## Phase 9 — 마감

**목표**: 남에게 넘길 수 있는 상태로 만든다.

**할 일**
- **springdoc 문서화** — 주요 컨트롤러에 `@Tag`/`@Operation`, `SecurityScheme`(bearer) 등록.
  `/swagger-ui.html`에서 전체 API를 브라우저로 검증 가능하게.
- **N+1 전수 점검** — `spring.jpa.properties.hibernate.generate_statistics=true`로
  피드·책별 목록·프로필의 실제 쿼리 수를 세고, 페이지 크기를 바꿔도 쿼리 수가
  늘지 않는지 확인한다.
- **인덱스 검증** — 주요 목록 쿼리에 `EXPLAIN`을 걸어 의도한 인덱스를 타는지 본다.
- **보안 마무리** — 공개 경로 화이트리스트 재점검, actuator는 `health`만 노출,
  에러 응답에 스택트레이스·내부 메시지가 새지 않는지 확인.
- **전체 E2E 시나리오 테스트 1개** — 가입 → 로그인 → 검색 → 등록 → 담기 → 감상평 →
  팔로우 → 피드 노출 → 좋아요 → 댓글 → 태그 탐색 → 프로필 통계 반영.

**완료 기준**
`.\gradlew.bat build` 전체 초록불, Swagger UI에서 모든 엔드포인트 수동 호출 성공.

---

# 실행 방법

```powershell
# 1. 인프라 기동 (Docker 29.2.1 확인됨)
docker compose up -d

# 2. 빌드 + 전체 테스트
.\gradlew.bat build

# 3. 앱 실행
$env:JWT_SECRET = "<32바이트 이상의 임의 문자열>"
$env:KAKAO_REST_API_KEY = "<카카오 developers에서 발급한 REST API 키>"
.\gradlew.bat bootRun --args='--spring.profiles.active=local'

# 4. 확인
# http://localhost:8080/swagger-ui.html   (Phase 9 이후)
# http://localhost:8080/actuator/health   (Phase 0 이후)
```

**카카오 API 키**: https://developers.kakao.com 에서 애플리케이션 생성 후 REST API 키 발급.
Phase 2 전까지는 없어도 되지만, 없으면 `KakaoProperties` 바인딩이 실패하므로
Phase 2부터는 더미 값이라도 넣어야 앱이 뜬다.

---

# Phase 요약

| Phase | 내용 | 마이그레이션 | 이 시점의 제품 |
|---|---|---|---|
| 0 | 인프라·설정·공통·테스트 하네스 | — | (뼈대) |
| 1 | 사용자 · JWT 인증 | `V1__users` | 계정 |
| 2 | 카카오 책 검색 · 등록 | `V2__books` | 책 찾기 |
| 3 | 서재 · 독서 기록 · 목표 | `V3__readings` | **개인 독서 기록 앱** |
| 4 | 감상평 · 책별 목록 · 탐색 피드 | `V4__posts` | **공개된 독서 기록** |
| 5 | 팔로우 · 타임라인 | `V5__follows` | **SNS** |
| 6 | 좋아요 · 댓글 | `V6__reactions` | 소셜 루프 완성 |
| 7 | 해시태그 탐색 | `V7__hashtags` | 주제 기반 발견 |
| 8 | 프로필 · 서재 통계 | — | **지적 허영 완성** |
| 9 | 문서화 · 성능 · 보안 마감 | — | 출시 가능 |

---

# 의도적으로 하지 않는 것

조기 최적화를 막기 위해 **명시적으로 미룬** 항목들이다. 필요해지면 그때 근거와 함께 도입한다.

| 항목 | 미루는 이유 |
|---|---|
| 피드 팬아웃 쓰기 / Redis 타임라인 | fan-out on read로 충분한 규모다. 실측 지연을 본 뒤에. |
| 팔로워/팔로잉 반정규화 카운터 | `count` 쿼리로 시작. 프로필 조회가 느려지면 그때. |
| 프로필 Redis 캐시 | 무효화 로직이 버그를 부른다. 트래픽 확인 후. |
| 댓글 `parent_id` | 대댓글이 MVP 밖이다. 안 쓸 컬럼은 부채. |
| Elasticsearch | 내부 검색 요구가 아직 없다. 검색은 카카오가 한다. |
| 태그 소급 파싱 배치 | MVP 데이터량에서 불필요. |

spec.md §3.2의 제외 항목(소셜 로그인, 알림, 이미지 업로드, 대댓글, 차단·신고, DM,
독서 모임, 추천 알고리즘, 프론트엔드)도 이 계획의 범위 밖이다.

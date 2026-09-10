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
    testImplementation 'org.testcontainers:testcontainers-mysql'
    testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
}
```

> **주의**: `springdoc-openapi-starter-webmvc-ui`는 Maven Central 검색 UI에서 2.8.6이 최신처럼
> 보이지만 그건 Boot 3 라인이다. 3.1.0의 부모 POM이 `spring-boot-starter-parent:4.1.0`임을 확인했다.

> **주의**: Testcontainers는 흔히 보이는 `org.testcontainers:mysql` / `:junit-jupiter`가 아니다.
> Boot 4.1.1 BOM이 관리하는 버전은 **2.0.5**이고, 2.x에서 아티팩트가 `testcontainers-` 접두사로,
> 패키지가 `org.testcontainers.mysql`로 바뀌었다. `MySQLContainer`도 더 이상 제네릭이 아니라
> `MySQLContainer<?>`로 선언하면 컴파일이 깨진다.

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
  flyway: { enabled: true }         # baseline-on-migrate는 켜지 않는다 (아래 주의)

twelvebooks:
  jwt:
    secret: ${JWT_SECRET}
    access-token-ttl: PT10M
    refresh-token-ttl: P14D
  kakao:
    rest-api-key: ${KAKAO_REST_API_KEY}
    base-url: https://dapi.kakao.com
```

> **주의**: `baseline-on-migrate`를 켜면 히스토리 테이블이 없는 DB에 붙었을 때 Flyway가
> 현재 상태를 baseline으로 찍는다. 새로 시작하는 서비스에서 "히스토리가 없다"는 건 곧
> "마이그레이션이 아직 하나도 적용되지 않았다"는 뜻이므로, baseline은 누락을 숨기는 쪽으로만 작동한다.

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

### 기본 키
**모든 테이블은 `bigint auto_increment` 대리 키 하나를 PK로 갖는다. 복합 PK를 쓰지 않는다.**
관계·조인 테이블도 예외가 아니다 — 유일성은 `unique` 제약이 맡는다.

복합 PK는 관계 테이블의 교과서적 기본값이지만 이 프로젝트와는 두 군데서 부딪힌다.

- **커서 페이징.** 단조 증가 키가 없으면 커서와 정렬이 다른 컬럼을 빌려 쓰게 되고,
  "최근에 생긴 것이 먼저"가 성립하지 않는다. 목록이 전부 id 커서라는 아래 규약이 깨진다.
- **JPA.** 식별자를 우리가 정해서 넣으면 `save()`가 insert가 아니라 merge로 나간다
  (select 후 update). 그러면 **유니크 제약이 발동할 기회조차 없어** "DB를 1차 방어선으로 삼는다"는
  규약이 조용히 무력화된다. 중복 요청이 409 대신 성공으로 답하고, 카운터를 함께 올리는
  경로라면 숫자까지 어긋난다.

성능 때문에 복합 PK를 고르고 싶어지면 먼저 인덱스를 보라. `uk(a, b)`가 대개 같은 조회를
그대로 커버한다 — 좁히는 컬럼과 읽는 컬럼이 둘 다 인덱스 안에 있으면 클러스터 인덱스든
아니든 테이블을 보지 않는다.

이 규약은 문서로만 두지 않는다. `PrimaryKeyConventionTest`가 마이그레이션과 엔티티를 훑어
복합 PK를 잡고, 그 테스트는 `build`에 얹혀 있어 머지 필수 체크다. 훅과 달리 우회할 경로가 없다.
정말 필요한 자리라면 해당 줄 앞에 `allow-composite-pk: <이유>`를 남겨 면제받되, **사유를 적어야만**
면제된다 — 침묵시키는 용도로 쓰이면 규약이 무의미해진다.

### 커서 페이징
목록은 전부 `CursorPage<T> { List<T> items; Long nextCursor; boolean hasNext; }`.
PK가 auto-increment이므로 `id DESC`가 곧 최신순이다. 별도 정렬 컬럼이 필요 없다.

```java
// size + 1건을 조회해서 hasNext를 판정하고, 마지막 1건은 버린다
@Query("select p from Post p where (:cursor is null or p.id < :cursor) order by p.id desc")
List<Post> findPage(@Param("cursor") Long cursor, Pageable pageable);
```

`size`는 기본 20, 최대 50으로 컨트롤러에서 clamp한다.

**예외는 카카오 책 검색뿐이다.** 원본이 쪽번호 기반이라 커서를 지어낼 수 없어
`BookSearchPage { items, page, hasNext, totalCount }`로 답한다. 커서 대신 쪽번호를 주되
항목 이름(`items`, `hasNext`)은 맞춰, 클라이언트가 같은 모양으로 다루게 한다.
`hasNext`는 카카오 `meta.is_end`를 뒤집은 값이고 `totalCount`는 `meta.total_count`다 —
맨 배열로 내려주면 다음 페이지가 있는지, 결과가 빈 것이 끝이어서인지 알 수 없다.

### 반정규화 카운터
`posts.like_count` / `comment_count`는 읽기 성능을 위한 반정규화다.
**엔티티 필드를 읽고-더하고-쓰지 않는다** (동시 요청에 유실됨). 반드시 원자적 UPDATE:

```java
@Modifying
@Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id")
void incrementLikeCount(@Param("id") Long id);
```

중복 좋아요는 `post_likes`의 `uk(post_id, user_id)`가 막고, `DataIntegrityViolationException`을
409로 변환한다.

## T6. 마이그레이션 규칙

- 파일명 `V{n}__{목적}.sql`, `src/main/resources/db/migration/`.
- **적용된 마이그레이션은 절대 수정하지 않는다.** 변경은 항상 새 버전 파일로.
- 모든 테이블 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`.
- FK는 걸되 `ON DELETE CASCADE`는 자식 데이터가 명백히 종속일 때만
  (`post_likes`, `post_hashtags`, `comments`). `posts.book_id`처럼 참조 대상이 독립 생명주기를
  가지면 `RESTRICT`.

## T7. 테스트 전략

- **`AbstractIntegrationTest`** — `@SpringBootTest` + `@ServiceConnection`으로 MySQL과 Redis
  컨테이너를 띄우고 **실제 Flyway 마이그레이션을 그대로 태운다**.
  H2는 MySQL 전용 DDL과 호환되지 않으므로 쓰지 않는다.
  Redis까지 띄우는 이유는 actuator health가 Redis 상태를 집계하기 때문이다 —
  없으면 헬스 체크가 DOWN이 된다.
  컨테이너는 `static` 필드에 두고 static 초기화 블록에서 직접 start해 JVM 하나 안의
  모든 통합 테스트가 같은 컨테이너를 재사용한다. 라이프사이클을 이렇게 잡았으므로
  `@Testcontainers`(+`@Container`)는 쓰지 않는다 — 그 조합은 컨테이너를 테스트 클래스
  단위로 관리해 매 클래스마다 새로 띄운다.
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
- Access는 JWT로 응답 바디에, **Refresh는 불투명 랜덤 문자열(SecureRandom 32바이트)로
  HttpOnly 쿠키**에 실어 보낸다 (`Secure`·`SameSite=Strict`·`Path=/api/v1/auth`).
  refresh를 JS가 읽을 수 없게 해 XSS로 세션을 통째로 잃는 경로를 막는다.
- Refresh는 서명하지 않는다. Redis와 대조해야만 유효하므로 서명·exp 검증이 중복이고,
  진실 공급원이 하나(Redis)뿐이면 검증 경로가 단순해진다.
- Redis에 `refresh:{토큰해시} → {userId, 발급시각}`을 TTL 14일로 저장한다.
  **토큰은 SHA-256으로 해시해서 저장**한다 — Redis 덤프가 곧 세션 탈취가 되지 않게.
  전체 로그아웃을 위해 역인덱스 `refresh:user:{userId} → 토큰해시 집합`도 둔다.
- **기기별 다중 세션.** 키가 토큰 단위라 폰에서 로그인해도 노트북 세션이 끊기지 않는다.
  reissue는 대조 후 **새 refresh로 교체(rotation)**하고 옛 해시를 지운다.
  logout은 그 세션 하나만 지우고 쿠키를 만료시킨다.
- Access 토큰 블랙리스트는 두지 않는다. 로그아웃 후 최대 10분간 기존 access가 살아있지만,
  매 인증 요청에 Redis 왕복을 넣어 stateless를 포기하는 대가가 더 크다.
- 쿠키를 쓰므로 `reissue`·`logout`이 CSRF 표적이 된다. `SameSite=Strict`와 POST 전용으로
  막고 `csrf.disable()`은 유지한다 — 나머지 엔드포인트는 전부 Bearer라 무관하다.
- 전체 기기 로그아웃 엔드포인트는 이 Phase에 만들지 않는다. 역인덱스만 준비해 둔다.
- `handle` 검증: `^[a-z0-9_]{3,20}$`. 이메일/handle 중복은 DB 유니크 제약 + 사전 조회 둘 다.
- 비밀번호는 어떤 DTO·로그·응답에도 절대 실리지 않게 한다.

**완료 기준**
E2E: 가입 → 로그인 → access 토큰으로 `PATCH /me` 성공 → 토큰 없이 호출 시 401 →
refresh 쿠키로 reissue해 새 access와 **새 refresh 쿠키**를 받음 → 직전 refresh로 reissue 시 401
→ logout 후 같은 refresh로 reissue 시 401.
두 기기를 흉내 낸 로그인 두 번 뒤 한쪽만 logout해도 다른 쪽 reissue가 성공해야 한다.

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
  — userId, bookId, status, currentPage, pageCount, startedAt, finishedAt, rating
- `reading/domain/ReadingGoal` — userId, year, targetCount
- `ReadingController`: `POST /readings`, `PATCH /readings/{id}`, `DELETE /readings/{id}`
- `GET /users/{handle}/library?year=&startedYear=&finishedYear=&status=`
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

**총 쪽수는 `readings`가 갖는다.** `page_count`를 `books`가 아니라 `readings`에 두고 사용자가
직접 입력한다 — spec.md §5의 `readings` 스키마에는 없는 컬럼이라 `V3`에서 더한다.
같은 마이그레이션에서 **`books.page_count`는 지운다.** `V2`에 만들어졌지만 카카오가 값을 주지
않아 한 번도 채워진 적이 없고, 남겨두면 "책 쪽수는 어느 쪽인가"를 매번 되묻게 된다. 지금 전부
null이라 잃는 데이터도 없다. 나중에 다른 출처로 채우게 되면 그때 다시 만든다.

카카오가 총 쪽수를 주지 않아 누군가는 채워야 하는데, 공용 `books`를 사용자가 고치게 하면
Phase 2에서 서명으로 막은 오염 경로가 그대로 되살아난다. 판본마다 쪽수가 다르다는 점에서도
이쪽이 맞다 — 같은 책을 읽어도 내 책의 쪽수는 내 것이다. 진도 상한과 `FINISHED` 전환 시
`currentPage` 자동 맞춤은 모두 이 값을 기준으로 한다.

**서재는 `id desc`로 정렬한다.** 커서는 `id` 하나라 단순하다. `finished_at desc` 정렬이
"올해 읽은 순서"로는 더 자연스럽지만 커서가 `(finished_at, id)` 복합이 되고, `finished_at`이
없는 상태(`READING` 등)의 정렬 위치를 따로 정해야 한다.

**연도 필터는 셋으로 나눈다.** 값은 모두 연도(`2026`)다.

| 파라미터 | 거르는 것 |
|---|---|
| `startedYear` | `started_at`의 연도 — 그 해에 읽기 시작한 책 |
| `finishedYear` | `finished_at`의 연도 — 그 해에 다 읽은 책 |
| `year` | 위 둘 중 **하나라도** 그 해인 책 — 그 해에 손댄 책 전부 |

하나로 뭉치면 조합이 조용히 무의미해진다. `year`를 완독 연도로만 두면 `year=2026&status=READING`
— 사람이 보기엔 "올해 읽고 있는 책"이라는 자연스러운 요청 — 이 **항상 빈 목록**이 된다. 읽는
중인 책은 `finished_at`이 없어 연도 조건에 걸릴 수가 없기 때문이다. 셋으로 나누면 각 파라미터가
스스로 무엇을 거르는지 이름으로 말하고, `year`가 그 둘을 합친 편의 필터가 된다.

주어진 필터는 전부 AND로 묶는다. `startedYear=2025&finishedYear=2026`은 "재작년에 시작해 작년에
끝낸 책"이고, 이건 하나짜리 파라미터로는 표현할 수 없던 질문이다.

연간 목표 달성률은 이 필터와 무관하게 **완독 기준으로만** 센다. `year`가 시작을 포함하게 되면서
"`year`로 조회한 권수"와 "달성률의 분자"가 달라질 수 있는데, 후자의 정의는 처음부터
"그 해에 다 읽은 책 수"다.

이름을 `started_at`이 아니라 `startedYear`로 둔 것은 값이 날짜가 아니라 연도이기 때문이다.
컬럼명 그대로가 낫다면 바꾸겠다.

**별점은 이 Phase에 포함한다.** `readings.rating`은 nullable 정수 1~5이고 `PATCH`에서 함께 받는다.
컬럼만 만들고 API를 미루면 쓰이지 않는 컬럼이 남고, 나중에 더하면 마이그레이션이 하나 늘어난다.

**엔티티는 정적 팩토리로 통일한다.** `Reading.want(userId, bookId)`처럼 의미 있는 이름을 주고,
생성자는 private으로 둔다. Lombok은 `build.gradle`에 선언만 되어 있고 쓰는 파일이 없었는데,
여기서 도입하면 이후 모든 엔티티의 선례가 된다 — 새 의존성 없이 `User.create(...)`라는 현재
다수파에 맞추는 쪽을 택했다. 이 Phase에서 `Book`의 수기 Builder도 함께 옮긴다.

**나머지 규약** — 중복 담기는 유니크 제약을 1차 방어선으로 두고 `409`. `PATCH`는 프로필과 같은
부분 수정이라 보내지 않은 필드(null)는 바꾸지 않는다. `DELETE`는 행을 지운다(Phase 4에서
감상평이 `reading`을 참조하기 시작하면 그때 다시 본다). `PUT /me/goals/{year}`의 `targetCount`는
1~1000, `year`는 2000~2100.

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
- `follow/Follow` — 대리 키 `id` + `uk(follower_id, followee_id)`, `idx(followee_id, id DESC)`
- `POST|DELETE /users/{handle}/follow`
- `GET /users/{handle}/followers`, `/followings`
- `GET /feed?cursor=` — 팔로잉 + 본인

**기술 상세**
- MVP는 **fan-out on read**: 팔로잉 ID 목록을 뽑아 `posts.author_id IN (...)` + 커서 조건.
  `posts(author_id, id DESC)` 인덱스가 이걸 커버한다.
  팬아웃 쓰기·Redis 타임라인은 **실제 지연이 관측된 뒤에** 도입한다 (조기 최적화 금지).
- 팔로잉 ID 조회는 매 요청마다 발생하므로 Redis 캐시 후보이지만,
  Phase 5에서는 넣지 않는다. 팔로잉 수천 명 이전에는 문제가 되지 않는다.
- 자기 자신 팔로우 차단(400). 중복 팔로우는 유니크 제약이 막고 409로 변환.
- **대리 키를 둔다.** 관계 자체는 (누가, 누구를)로 이미 유일해 복합 PK로도 되지만, 그러면 목록에
  쓸 단조 증가 키가 없어 커서 페이징과 최신순 정렬이 상대방의 user_id를 빌려 쓰게 된다 —
  "모든 목록은 id 커서"라는 T5 규약과, 나머지 테이블이 전부 `bigint id`라는 점에 어긋난다.
  복합 PK의 클러스터 인덱스 이점은 `uk(follower_id, followee_id)`가 그대로 대신한다.
  피드가 매 요청 하는 "내 팔로잉" 조회는 이 인덱스만 읽고 끝난다.
- 피드에 **본인 글도 포함**한다. 자기 글이 안 보이는 타임라인은 어색하다.
- 프로필의 팔로워/팔로잉 수는 이 단계에서 `count` 쿼리로 시작한다.
  반정규화 카운터는 필요해지면 그때.
- **팔로워·팔로잉 목록에도 같은 값을 싣는다.** 목록 한 줄마다 팔로우 버튼이 붙으므로 프로필과
  같은 문제가 스무 번 반복된다. 페이지의 id를 모아 한 번에 묻고 Set으로 맞춘다 — Phase 6의
  `likedByMe`와 같은 모양이다. 목록 전용 DTO(`FollowItemResponse`)를 따로 두는 이유는
  `UserSummaryResponse`가 `PostResponse.author`에도 쓰이는데 거기서는 이 관계를 계산하지 않기
  때문이다. 계산하지 않는 자리에 필드가 따라가면 항상 거짓인 값이 실린다.
- **프로필에 `isFollowing`을 싣는다.** 없으면 화면이 팔로우 버튼을 처음 그릴 때 상태를 정할 수
  없다. 목록을 받아 와 뒤지는 것은 팔로잉이 500명이면 요청이 수십 번 나가고, 일단 그려 두고
  409를 보고 뒤집는 것은 사용자에게 틀린 상태를 먼저 보여주는 것이다.
  `uk(follower_id, followee_id)`를 타는 exists 한 번이면 된다.

**완료 기준**
A가 B를 팔로우 → A의 `/feed`에 B의 글과 A 자신의 글만 보이고 C의 글은 안 보임 →
언팔로우하면 B의 글이 사라짐 → 자기 자신 팔로우 400, 중복 팔로우 409.

---

## Phase 6 — 반응 (좋아요·댓글)

**목표**: 소셜 루프를 닫는다. 기록이 반응을 얻는다.

**산출물**
- `V6__reactions.sql` (`post_likes`, `comments`)
- `post/PostLike` — 대리 키 + `uk(post_id, user_id)`, `post/Comment`
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

## Phase 6.5 — 책 상세 보강

**목표**: 책 페이지가 표지·제목·저자뿐인 상태를 벗어난다. 그리고 서재의 진도가 "총 몇 쪽인지
모르는 채로" 시작되지 않게 한다.

소수점을 쓰는 이유는 뒤 Phase의 번호를 밀지 않기 위해서다. `V2_1__books_identity_check.sql`이
같은 이유로 이미 그렇게 되어 있다.

**왜 한 덩어리인가** — 카카오 발췌와 국중도 쪽수·소개는 건드리는 곳이 같다. `books` 테이블,
등록 경로(`POST /books`), 그리고 등록 서명. 따로 하면 마이그레이션이 두 번, 서명 범위 변경이
두 번이다.

**산출물**
- `V6_1__book_details.sql` — `books`에 상세 컬럼 추가
- `book/NlSeojiClient` — 국립중앙도서관 서지정보 API (ISBN13으로 조회)
- `BookRegisterRequest`·`BookSignature` 범위 확장
- `GET /books/{id}` 응답에 상세 필드
- 서재에 담을 때 `readings.page_count` 초기값 복사

**실측 근거** (2026-09-11, `external-apis.md` 참고)
- 카카오 `contents`: 채움률 186/200(93%). **약 250자에서 잘린 발췌**다(최소 56·중앙 253·최대 261).
  전문이 아니므로 "책 소개"가 아니라 "발췌"로 다뤄야 한다.
- 카카오 `url`: 200/200. 다음 책 페이지 링크.
- 국중도 `PAGE`: 종이책 단행본 약 50/54(90%대). 전자책·오디오북·세트는 거의 비어 있고
  값이 `"620 p."` · `"x, 282 p."` · `"24"` 등으로 흔들려 **마지막 아라비아 숫자**를 취한다.
- **국중도 `BOOK_INTRODUCTION`의 채움률은 아직 재지 않았다.** 착수 전에 먼저 잰다.
  낮으면 소개는 카카오 발췌만으로 간다.

**기술 상세**
- **상세 필드도 서명 범위에 넣는다.** `books`는 공용이라, 서명 밖에 두면 먼저 등록하는 사람이
  아무 문장이나 써넣을 수 있고 그것이 그 책을 담는 모든 사용자에게 보인다 — Phase 2가 서명으로
  막은 바로 그 경로다. 발췌가 250자라 요청이 조금 커지지만 HMAC에는 문제가 없다.
- **쪽수는 `books`가 갖되 사용자가 고치지 못한다.** Phase 3에서 `books.page_count`를 걷어낸
  이유는 "카카오가 주지 않아 사용자가 채워야 하는데 공용 `books`를 사용자가 고치게 되면
  오염된다"였다. 출처가 외부 API로 바뀌면 그 이유가 사라진다. 판본마다 쪽수가 다르다는 사실은
  그대로이므로 **최종 값은 여전히 `readings.page_count`** 이고, `books` 값은 담을 때 복사되는
  기본값일 뿐이다.
- **국중도가 죽으면 그냥 비운다.** 상세는 있으면 좋은 값이지 등록의 필수 조건이 아니다.
  책 등록이 외부 API 하나 더에 묶여 실패하면 안 된다.
- **소급 배치는 만들지 않는다.** 이미 등록된 책은 상세가 비어 있다. 등록이 upsert이므로
  **빈 필드만 채우는** 방식으로 자연스럽게 메워지게 한다 — 누군가 그 책을 다시 등록하는 순간
  채워진다. Phase 7의 태그 소급 파싱을 만들지 않기로 한 것과 같은 판단이다.
- `NlSeojiClient`는 외부 API 클라이언트이므로 `external-apis.md`에 절이 필요하다.
  `ExternalApiContractTest`가 없으면 빌드를 깨뜨린다.

**완료 기준**
검색 → 등록 시 카카오 발췌·링크가 함께 저장되고 국중도 쪽수가 채워짐 →
`GET /books/{id}`에 상세가 실림 → 서재에 담으면 `readings.page_count`가 그 값으로 시작하고
사용자가 고치면 그 기록만 바뀜 → 국중도가 죽어도 등록은 성공하고 쪽수만 빔 →
상세가 빈 채로 등록됐던 책이 재등록 시 채워짐.

---

## Phase 7 — 해시태그 탐색과 사람 검색

**목표**: 팔로우 관계 밖에서 **주제로 글을, 이름으로 사람을** 만난다.

**산출물**
- `V7__hashtags.sql` (`hashtags`, `post_hashtags`, `idx_users_display_name`)
- `tag/HashtagParser`, `tag/Hashtag`, `PostHashtag` — 대리 키 + `uk(post_id, hashtag_id)`
- `GET /tags/{name}/posts?cursor=`, `GET /tags/trending`
- `GET /users/search?q=&cursor=`

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

**사람 검색이 왜 여기 있나** — 탐색 피드와 책별 목록은 *모르는 사람을 발견하는* 경로다.
콘텐츠를 보고 그 작성자를 팔로우하게 된다. 하지만 *아는 사람을 찾는* 경로가 없었다.
handle을 정확히 알아야만 프로필에 갈 수 있어서, 가입하고도 친구를 못 찾는다.
발견과 검색은 다른 요구이고, 둘 다 "팔로우 관계 밖에서 만난다"는 이 Phase의 목표에 속한다.

- **접두 매칭만 한다.** `handle LIKE 'q%' or display_name LIKE 'q%'`. 부분 일치(`%q%`)는
  인덱스를 못 타서 사용자가 늘면 그대로 full scan이 된다. 부분 일치가 정말 필요해지면
  그때가 검색 엔진을 검토할 시점이다(spec.md §3.2에서 ES를 MVP 밖으로 둔 이유이기도 하다).
- `handle`은 유니크 인덱스가 이미 있어 접두 검색이 그대로 인덱스를 탄다.
  `display_name`에는 없으므로 `V7`에서 인덱스를 추가한다 — 지금 출시하는 기능이 쓰는
  인덱스이므로 조기 최적화가 아니다.
- **`q`는 2자 이상.** 한 글자로는 결과가 사실상 전체가 되어 검색이 아니다.
  handle이 소문자만 허용하므로 입력을 소문자로 정규화한 뒤 맞춘다.
- **자기 자신은 결과에서 뺀다.** 팔로우할 수 없는 사람이 목록에 있으면 버튼 상태가 애매해진다.
- 응답은 `CursorPage<FollowItemResponse>` — Phase 5에서 만든 그 타입이다.
  검색 결과에서 바로 팔로우 버튼을 그릴 수 있어야 하므로 `isFollowing`이 함께 나가야 한다.
- 정렬과 커서는 규약대로 `users.id desc`다. 관련도 순은 MVP 밖이다 — 접두 매칭에서 무엇을
  더 가깝다고 볼지는 기준을 새로 만들어야 하는 일이고, 그 기준 없이 정렬하면 임의가 된다.

**완료 기준**
`#소설 #SF` 포함 글 작성 → 두 태그 생성, `post_count` 1 →
`GET /tags/소설/posts`에 노출 → 글 삭제 시 `post_count` 감소 →
`#소설`과 `#SOSEOL`처럼 대소문자만 다른 태그가 하나로 합쳐짐.
`GET /users/search?q=ire`로 `irene`을 찾고 그 결과의 `isFollowing`을 보고 바로 팔로우 →
한 글자 검색은 400 → 검색 결과에 자기 자신이 없음.

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
$env:BOOK_SIGNATURE_SECRET = "<32바이트 이상의 임의 문자열, JWT_SECRET과 다른 값>"
.\gradlew.bat bootRun --args='--spring.profiles.active=local'

# 4. 확인
# http://localhost:8080/swagger-ui.html   (Phase 9 이후)
# http://localhost:8080/actuator/health   (Phase 0 이후)
```

**카카오 API 키**: https://developers.kakao.com 에서 애플리케이션 생성 후 REST API 키 발급.
`KakaoProperties`가 기본값 없이 바인딩하므로 **Phase 0부터 없으면 앱이 뜨지 않는다.**
카카오를 실제로 호출하는 것은 Phase 2지만, 그 전에도 더미 값이라도 넣어야 한다.

기본값을 주지 않는 이유는 운영에서 키 주입을 빠뜨렸을 때 앱이 조용히 뜨는 쪽이
더 나쁘기 때문이다. 그 경우 실패는 첫 책 검색 요청의 401로 뒤늦게 나타난다.
없어야 할 값이 아니라 반드시 있어야 할 값이므로, 없으면 부팅에서 죽는다.

**책 서명 키**(`BOOK_SIGNATURE_SECRET`): 검색 응답에 붙는 HMAC 서명의 키다. `books`는 공용
테이블이라 등록 본문을 그대로 믿으면 인증된 사용자 누구나 실제 ISBN에 지어낸 제목·저자를
붙여 먼저 등록할 수 있고, unique 제약 때문에 이후 그 책을 담는 모든 사용자가 오염된 행을
받는다. 서명이 있으면 서버는 등록 시 카카오를 다시 부르지 않고도 "이 조합은 우리 검색이 준
것"임을 확인할 수 있다. JWT 키와 **다른 값**을 쓴다 — 한 키가 새면 피해가 인증까지 번진다.
**서명에는 만료가 없다.** 한 번 받은 서명은 키를 바꿀 때까지 계속 쓸 수 있고, 그것이 의도다.
서명은 권한이 아니라 출처 표시이기 때문이다 — 인증된 사용자라면 누구나 같은 책을 다시 검색해
같은 서명을 얼마든지 다시 받을 수 있으므로, 만료를 붙여도 막히는 공격이 없다. 서명이 막는 것은
"검색이 준 적 없는 조합"이지 "오래된 조합"이 아니다.

바꿔야 할 상황은 만료가 아니라 버전으로 처리한다. 서명 문자열 앞의 `v1`이 그 자리다 —
키나 직렬화 방식, 서명 대상 필드가 바뀌면 접두사를 올려 옛 서명을 한꺼번에 무효로 만든다.
키를 바꾸는 경우도 같아서, 그 시점에 열려 있던 검색 결과로 등록하면 한 번 400이 나고
검색을 다시 하면 풀린다.

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
| 6.5 | 책 상세 보강 (카카오 발췌 · 국중도 쪽수) | `V6_1__book_details` | 책 페이지가 채워짐 |
| 7 | 해시태그 탐색 · 사람 검색 | `V7__hashtags` | 주제·이름 기반 발견 |
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
| 프로필 동시 수정 방어 (`@Version`·`@DynamicUpdate`) | 같은 사용자의 드문 동시 수정이고 프로필은 갱신 유실을 감수할 수 있다. 필요해지면 서로 다른 필드만 보존하면 되는 경우는 `@DynamicUpdate`, 같은 필드 충돌까지 감지해야 하면 `@Version` + 409. 실제 충돌 사례나 자동 저장 기능이 생길 때. |

spec.md §3.2의 제외 항목(소셜 로그인, 알림, 이미지 업로드, 대댓글, 차단·신고, DM,
독서 모임, 추천 알고리즘, 프론트엔드)도 이 계획의 범위 밖이다.

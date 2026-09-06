# 12books — 제품 요구사항 정의서 (PRD)

> 버전 0.1 · 2026-09-03 · 대상 릴리스: MVP (백엔드 API)

## 1. 제품 개요

### 1.1 한 줄 정의

**12books는 지금 읽고 있는 책의 감상을 읽은 분량만큼 기록하고 공유하는, 독서인을 위한 SNS다.**

### 1.2 배경과 문제

- 사람들은 책을 다 읽어야만 리뷰를 쓸 수 있다고 느낀다. 그래서 대부분 아무것도 쓰지 않는다.
- 기존 독서 서비스는 **완독 후 별점·서평** 중심이라, 읽는 과정 자체가 콘텐츠가 되지 못한다.
- 반면 SNS는 기록의 문턱이 매우 낮다. 이 낮은 문턱을 독서에 그대로 가져오는 것이 이 제품의 착안점이다.

### 1.3 해결 방식

1. **분량 단위 기록** — "완독"이 아니라 "오늘 읽은 47~92쪽"에 대해 두세 문장을 남긴다.
2. **소셜 루프** — 팔로우 기반 타임라인, 좋아요, 댓글로 기록이 반응을 얻는다.
3. **지적 성취의 시각화** — 프로필의 서재(표지 그리드)와 연간 목표 달성률이
   "나는 이런 책을 읽는 사람"이라는 정체성을 눈에 보이게 만든다.

### 1.4 제품 원칙

| 원칙 | 의미 |
|---|---|
| 기록의 문턱은 최대한 낮게 | 감상평은 짧아도 된다. 별점·완독은 선택이다. |
| 진도는 강요하지 않는다 | 한 달 한 권, 두 달 한 권, 일주일 한 권 모두 정상이다. 목표는 기본값일 뿐 압박이 아니다. |
| 서재는 자랑스러워야 한다 | 프로필은 통계 대시보드가 아니라 전시 공간이다. |
| 책이 대화의 단위다 | 같은 책을 읽는 사람들이 자연스럽게 만나야 한다. |

### 1.5 성공 지표 (MVP)

- **활성화**: 가입 후 7일 내 첫 감상평 작성률 ≥ 40%
- **리텐션**: 첫 감상평 작성자의 4주차 잔존율 ≥ 25%
- **핵심 습관**: 주간 활성 사용자의 주 평균 감상평 2건 이상
- **소셜 루프**: 감상평당 평균 반응(좋아요 + 댓글) ≥ 1

---

## 2. 사용자

### 2.1 타깃 페르소나

| 페르소나 | 설명 | 핵심 니즈 |
|---|---|---|
| **꾸준한 기록가** | 매달 1~2권을 읽고 메모 습관이 있음 | 흩어진 독서 메모를 한 곳에, 남에게 보여줄 수 있는 형태로 |
| **완독 실패자** | 사놓고 못 읽는 책이 쌓임 | 부담 없는 기록 단위와, 남이 보고 있다는 가벼운 강제력 |
| **취향 전시자** | 읽는 책이 곧 자기 표현 | 서재·통계로 드러나는 정체성, 반응 |

### 2.2 핵심 사용자 스토리

- 서점에서 산 책을 검색해 **내 서재에 담고**, 상태를 "읽는 중"으로 둔다.
- 지하철에서 30쪽 읽고 **"47~77쪽. 화자가 갑자기 믿을 수 없어진다. #소설"** 을 올린다.
- 내가 팔로우한 사람들의 감상평이 **타임라인**에 시간순으로 흐른다.
- 어떤 감상평이 인상 깊어서 **좋아요와 댓글**을 남기고, 그 사람을 팔로우한다.
- 읽고 있는 책의 페이지에 들어가 **같은 책을 읽는 사람들의 감상평**을 본다.
- 내 프로필에서 **올해 읽은 책 표지 그리드와 목표 달성률(8/12권)** 을 확인한다.

---

## 3. 범위

### 3.1 MVP 포함

| # | 기능 | 설명 |
|---|---|---|
| F1 | 회원가입 / 로그인 | 이메일 + 비밀번호, JWT access/refresh |
| F2 | 책 검색 | 카카오 책 검색 API 프록시, 선택 시 내부 DB 업서트 |
| F3 | 서재 & 독서 기록 | 책 담기, 상태(읽고싶다/읽는중/완독/보류/중단), 현재 쪽수, 별점 |
| F4 | 감상평 작성 | 본문 + 읽은 구간(from~to 쪽) + 스포일러 플래그 + 해시태그 |
| F5 | 팔로우 | 팔로우/언팔로우, 팔로워·팔로잉 목록 |
| F6 | 반응 | 좋아요, 댓글(1단계) |
| F7 | 피드 | 팔로잉 타임라인, 전체 탐색 피드 (커서 페이징) |
| F8 | 프로필 & 통계 | 서재 그리드, 연간 목표(기본 12권) 대비 달성률, 팔로워 수 |
| F9 | 책 페이지 | 책 상세 + 그 책에 달린 모든 감상평 |
| F10 | 해시태그 탐색 | 태그별 감상평 목록, 인기 태그 |

### 3.2 MVP 제외 (후속)

소셜 로그인(OAuth2), 알림, 이미지 업로드(S3), 대댓글, 차단·신고, DM,
독서 모임·챌린지, 추천 알고리즘, 검색 엔진(ES) 도입, 프론트엔드 클라이언트.

### 3.3 비기능 요구사항

- 피드 조회 p95 < 300ms (팔로잉 500명 기준)
- 인증은 stateless — 서버 수평 확장 가능
- 모든 목록 API는 커서 페이징 (기본 20건, 최대 50건)
- 외부 API(카카오) 장애가 서비스 전체를 막지 않을 것 — 검색만 실패하고 나머지는 동작

---

## 4. 기능 상세

### 4.1 인증 (F1)

- 이메일 형식 검증, 비밀번호 8자 이상. 저장은 BCrypt.
- `handle`(예: `irene`)은 가입 시 지정하는 고유 URL 식별자. 영소문자·숫자·`_`, 3~20자.
- Access 토큰 10분, Refresh 토큰 14일.
- Access는 응답 바디로, **Refresh는 HttpOnly 쿠키**로 전달한다
  (`Secure`·`SameSite=Strict`·`Path=/api/v1/auth`). XSS로 refresh를 훔칠 수 없게 한다.
- Refresh는 불투명 랜덤 문자열이며 Redis가 유일한 진실 공급원이다.
  `refresh:{토큰해시}`에 세션을 저장하고, 전체 로그아웃을 위한 역인덱스로
  `refresh:user:{userId}`에 해시 집합을 둔다. **토큰은 해시해서 저장**한다 —
  Redis 덤프가 곧 세션 탈취가 되지 않게.
- **기기별 다중 세션**을 허용한다. 폰에서 로그인해도 노트북 세션이 끊기지 않는다.
  reissue 때마다 refresh를 교체(rotation)하고, 로그아웃은 그 세션만 삭제한다.
- Access 토큰 블랙리스트는 두지 않는다. 로그아웃 후 최대 10분간 기존 access가 살아있지만,
  매 요청 Redis 조회를 넣어 stateless를 포기하는 대가가 더 크다.

### 4.2 책 (F2)

- `GET /books/search`는 카카오 응답을 **저장하지 않고** 그대로 변환해 반환한다.
- 사용자가 특정 책을 선택해 서재에 담는 순간 `POST /books`로 **ISBN13 기준 업서트**하고
  내부 `bookId`를 발급한다. 즉, 내부 `books` 테이블에는 **누군가 실제로 읽는 책만** 쌓인다.
- ISBN이 없는 책은 `title|authors|publisher` 해시를 `source_key`로 대체 유니크 키로 쓴다.
- 카카오 호출 실패 시 `502 EXTERNAL_API_ERROR`로 변환하고 원인을 로깅한다.

### 4.3 독서 기록 (F3)

- `readings`는 (user, book) 당 1건. 같은 책을 다시 읽으면 상태를 되돌려 재사용한다.
- 상태 전이 규칙:
  - 어떤 상태에서든 `READING`으로 전환 가능 → `started_at`이 비어 있으면 이때 채운다.
  - `FINISHED` 전환 시 `finished_at` 기록, `current_page`를 `page_count`로 맞춘다.
  - `FINISHED`에서 다른 상태로 되돌리면 `finished_at`을 비운다(재독).
- `current_page`는 감소도 허용한다(되돌아가 읽기). 단 0 이상, `page_count` 이하.
- 연간 목표(`reading_goals`)는 사용자가 설정하지 않으면 **기본 12권**으로 간주한다.

### 4.4 감상평 (F4)

- 본문 1~1000자. `from_page`/`to_page`는 선택이며, 둘 다 있으면 `from ≤ to` 검증.
- 작성 시 `bookId`는 필수. `readingId`는 서버가 (user, book)으로 조회해 자동 연결하고,
  없으면 `READING` 상태로 자동 생성한다 — **책 담기를 잊어도 글은 써진다.**
- 스포일러 플래그가 켜지면 클라이언트가 본문을 가릴 수 있도록 응답에 그대로 노출한다.
- 본문의 `#태그`를 정규식으로 파싱해 소문자 정규화 후 `hashtags` 업서트 → `post_hashtags` 연결.
- 삭제는 작성자 본인만. 삭제 시 연결된 좋아요·댓글·태그 연결도 함께 제거한다.

### 4.5 피드 (F7)

- `GET /feed` = 내가 팔로우한 사람 + 나 자신의 감상평을 `id DESC` 정렬, 커서 페이징.
- MVP는 **fan-out on read**(팔로잉 ID `IN` 조건 + `posts(author_id, id)` 인덱스).
  팬아웃 쓰기나 Redis 타임라인은 실제 성능 문제가 관측된 뒤에 도입한다.
- `GET /feed/explore` = 전체 최신순. 신규 사용자가 빈 화면을 보지 않게 하는 안전장치.

### 4.6 프로필 & 통계 (F8)

- 프로필 응답: handle, displayName, bio, avatarUrl, 팔로워/팔로잉 수,
  올해 완독 수, 목표 권수, 달성률, 현재 읽는 중인 책 목록.
- 서재(`GET /users/{handle}/library`)는 `year`·`status` 필터를 받아 표지 그리드용
  최소 정보(bookId, title, thumbnailUrl, finishedAt)를 반환한다.

---

## 5. 데이터 모델

| 테이블 | 핵심 컬럼 | 비고 |
|---|---|---|
| `users` | id, email(uk), password_hash, handle(uk), display_name, bio, avatar_url, created_at | |
| `books` | id, isbn13(uk), source_key(uk), title, authors, publisher, thumbnail_url, page_count, published_at | 카카오 응답 캐시 |
| `readings` | id, user_id, book_id, status, current_page, started_at, finished_at, rating | uk(user_id, book_id) |
| `reading_goals` | id, user_id, year, target_count | uk(user_id, year), 기본 12 |
| `posts` | id, author_id, book_id, reading_id, content, from_page, to_page, spoiler, like_count, comment_count, created_at | book_id 비정규화 |
| `post_likes` | post_id, user_id, created_at | 복합 PK |
| `comments` | id, post_id, author_id, content, created_at | 1단계(대댓글 없음) |
| `follows` | follower_id, followee_id, created_at | 복합 PK |
| `hashtags` | id, name(uk, 소문자), post_count | |
| `post_hashtags` | post_id, hashtag_id | 복합 PK |

**인덱스**: `posts(author_id, id DESC)`, `posts(book_id, id DESC)`, `comments(post_id, id)`,
`readings(user_id, status)`, `follows(followee_id)`, `post_hashtags(hashtag_id, post_id DESC)`.

**카운터 정합성**: `like_count`/`comment_count`는 반정규화 컬럼.
`UPDATE posts SET like_count = like_count + 1 WHERE id = ?` 원자적 UPDATE로 갱신하고,
좋아요 중복은 `post_likes` 복합 PK 제약(`DataIntegrityViolationException` → 409)으로 방어한다.

---

## 6. API 명세 (`/api/v1`)

### auth

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/auth/signup` | 가입 |
| POST | `/auth/login` | access + refresh 발급 |
| POST | `/auth/reissue` | refresh로 재발급 |
| POST | `/auth/logout` | refresh 폐기 |

### user & 서재

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/users/{handle}` | 프로필 + 통계 |
| PATCH | `/me` | displayName / bio / avatarUrl |
| GET | `/users/{handle}/library?year=&status=` | 서재 |
| PUT | `/me/goals/{year}` | 연간 목표 설정 |

### book

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/books/search?q=&page=` | 카카오 프록시 |
| POST | `/books` | 내부 업서트, bookId 반환 |
| GET | `/books/{id}` | 책 상세 |
| GET | `/books/{id}/posts?cursor=` | 그 책의 감상평 |

### reading

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/readings` | 책 담기 |
| PATCH | `/readings/{id}` | 진도·상태·별점 |
| DELETE | `/readings/{id}` | 서재에서 제거 |

### post

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/posts` | 감상평 작성 |
| GET | `/posts/{id}` | 단건 조회 |
| DELETE | `/posts/{id}` | 삭제 |
| POST / DELETE | `/posts/{id}/likes` | 좋아요 |
| GET / POST | `/posts/{id}/comments` | 댓글 |
| DELETE | `/comments/{id}` | 댓글 삭제 |

### social & 탐색

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST / DELETE | `/users/{handle}/follow` | 팔로우 |
| GET | `/users/{handle}/followers` · `/followings` | 관계 목록 |
| GET | `/feed?cursor=` | 팔로잉 타임라인 |
| GET | `/feed/explore?cursor=` | 전체 최신 |
| GET | `/tags/{name}/posts?cursor=` | 태그별 |
| GET | `/tags/trending` | 인기 태그 |

### 공통 규약

- 목록 응답: `{ items: [...], nextCursor: <id 또는 null>, hasNext: bool }`
- 에러 응답: `{ code, message, fieldErrors }` — `ErrorCode` enum이 HTTP 상태와 코드를 함께 소유
- 인증: `Authorization: Bearer <accessToken>`

---

## 7. 기술 아키텍처

- **스택**: Java 21, Spring Boot 4.1.1, Spring Security 7, JPA/Hibernate, MySQL 8.4,
  Redis 7, Flyway, Gradle
- **패키지 구조**: 기술 레이어가 아닌 **기능(도메인) 단위 수직 분할**.
  각 패키지 안에 `domain / repository / service / controller / dto`.

```
com.irene.twelvebooks
├─ common/      config(Jpa·Redis·RestClient·OpenApi), error, response, entity(BaseTimeEntity)
├─ auth/        SecurityConfig, JwtProvider, JwtAuthenticationFilter, @AuthUser, 가입·로그인
├─ user/        User, 프로필, 서재·통계 집계
├─ book/        Book, KakaoBookClient, 검색·업서트
├─ reading/     Reading, ReadingGoal
├─ post/        Post, PostLike, Comment
├─ follow/      Follow
├─ tag/         Hashtag, PostHashtag
└─ feed/        타임라인 조회 (읽기 전용 조합 서비스)
```

- **DB 스키마**: Flyway `V1__init.sql`로 관리. JPA는 `ddl-auto: validate`.
- **설정**: `application.yaml` + `application-local.yaml`.
  카카오 키·JWT 시크릿은 환경변수(`KAKAO_REST_API_KEY`, `JWT_SECRET`)로 주입.
- **로컬 인프라**: `docker-compose.yml` (MySQL 8.4 + Redis 7).

---

## 8. 구현 마일스톤

각 마일스톤의 완료 기준은 **컴파일 + 테스트 통과**다.

| # | 내용 |
|---|---|
| **M0** | 의존성 추가(jjwt, restclient, testcontainers, springdoc), `docker-compose.yml`, 설정 파일, `common/` 전체(BaseTimeEntity·ErrorCode·GlobalExceptionHandler·CursorPage), `V1__init.sql` 전체 스키마 |
| **M1** | `User` 엔티티, `SecurityConfig`(stateless + BCrypt), `JwtProvider`, `JwtAuthenticationFilter`, `@AuthUser` 리졸버, signup/login/reissue/logout, 프로필 조회·수정 |
| **M2** | `KakaoBookClient`, 책 검색·업서트, `Reading` CRUD + 진도 갱신, `ReadingGoal` |
| **M3** | `Post` 작성/조회/삭제, 해시태그 파싱·업서트·연결, 책별 감상평, 태그별 조회 |
| **M4** | `Follow`, 좋아요, 댓글(카운터 원자적 갱신), 팔로잉 피드 / 탐색 피드 커서 페이징 |
| **M5** | 서재 그리드 API, 연간 달성률 집계, springdoc 문서 노출, actuator 공개 경로 정리 |

---

## 9. 테스트 전략

- **`@DataJpaTest` + Testcontainers MySQL** — 실제 Flyway 마이그레이션을 그대로 태워
  스키마와 엔티티 매핑 불일치를 잡는다. H2는 MySQL 전용 DDL과 호환되지 않으므로 쓰지 않는다.
  `@ServiceConnection` 기반 `AbstractIntegrationTest` 베이스 클래스로 컨테이너를 재사용한다.
- **`@SpringBootTest` + MockMvc** — 마일스톤별 E2E 시나리오
  (가입 → 로그인 → 책 담기 → 감상평 → 팔로우 → 피드 노출 → 좋아요 → 통계 반영).
- **단위 테스트** — `JwtProvider`, 해시태그 파서, 독서 상태 전이 규칙 등 순수 로직.
- 카카오 API는 `MockRestServiceServer`로 스텁 — 테스트가 외부 네트워크에 의존하지 않게 한다.

---

## 10. 미해결 이슈 / 향후 결정 필요

1. 감상평의 공개 범위(전체 공개 / 팔로워 공개 / 비공개) — MVP는 **전체 공개 단일 정책**으로 시작.
2. 카카오 API가 제공하지 않는 `page_count`(총 쪽수) 확보 방안 — MVP는 사용자가 직접 입력.
3. 인기 태그(`/tags/trending`) 집계 주기 — MVP는 `hashtags.post_count` 단순 정렬.
4. 소셜 로그인 도입 시점 및 기존 이메일 계정과의 연동 방식.

# 조회 목적에 비해 과도한 엔티티 로딩 정리

## 목적과 범위

조회한 엔티티를 변경하지 않고 표시 필드·ID·존재 여부만 사용하는 경로를 찾아, 목적에 맞는 DTO projection 또는 스칼라 조회로 줄인다.

예를 들어 작성자의 핸들과 이미지가 필요한데 User 전체를 읽으면 이메일·비밀번호 해시 등 사용하지 않는 값까지 가져온다. 신고 대상을 확인할 때 작성자 ID만 필요하다면 본문을 포함한 Post 전체를 읽을 이유도 없다.

- 범위: auth, book, feed, follow, notification, post, reading, report, user의 패키지별 리뷰.
- 기준 커밋: `4d44d0515d4b6f894eddcdfeeec284684476042f`.
- 정적 코드 검토 결과이며, 구현 변경·실행 SQL 검증·성능 측정은 하지 않았다.
- [#51 쿼리 분할 정리](https://github.com/irerin07/12books/issues/51)와 함께 진행한다. **projection 전환만으로 쿼리 수가 줄지는 않는다.** 엔티티 조회 4개를 DTO 조회 4개로 바꾸면 왕복은 그대로다.
- [#50 설계 전제 재검토](https://github.com/irerin07/12books/issues/50)의 카운터·잠금 필요성 판단이 먼저인 곳은 별도로 표시한다.
- feed/post 공통 목록, 다른 패키지의 User 조회를 user 패키지에서 다시 언급한 부분은 중복 결함으로 세지 않는다.

## 판단 기준

| 실제 목적 | 기본 방향 |
|---|---|
| 화면 표시 | 필요한 컬럼의 DTO projection |
| ID·해시 등 값 하나 | 스칼라 조회 |
| 존재 여부·개수 | EXISTS·COUNT |
| 조회한 상태를 변경하고 불변식을 적용 | 엔티티 유지 |
| 잠금만 필요 | 잠금 필요성을 먼저 판단하고, 필요하면 최소 컬럼 잠금 조회를 검증 |
| 방금 생성·수정한 엔티티로 응답 생성 | 그대로 변환. DTO를 얻으려고 다시 SELECT하지 않음 |

엔티티 조회가 언제나 잘못이라는 뜻은 아니다. 큰 엔티티를 좁은 목적으로 읽는 곳과, 응답이 대부분의 필드를 사용하는 곳의 개선 규모는 다르다. 새 프레임워크·CQRS 계층을 도입하는 작업도 아니다.

## 패키지별 요약

| 패키지 | 주요 대상 | 유지하거나 별도로 검토할 부분 |
|---|---|---|
| auth | 가입 중복, 재설정 발송 대상, 로그인·재발급 인증 정보 | 실제 비밀번호 변경, 신규 사용자 저장 |
| book | 상세, 기존 책 반환, 충돌 후 재조회 | 신규 Book 저장 후 응답 변환 |
| feed | Post·User·Book 목록 조립 | 팔로잉·좋아요 ID 조회는 이미 스칼라 |
| follow | handle→ID 확인, Follow·User 목록 조립 | 신규 Follow 저장, 직접 DELETE |
| notification | Notification 목록 | ActorView·PostView는 이미 projection |
| post | 상세·목록, ID·권한 확인, 작성 응답용 정보, 알림 수신자 ID | 신규 저장과 잠금 의미를 구분 |
| reading | 서재 주인 ID, 서재·단건 기록, 연결 후보 ID | 상태 변경 엔티티 유지, 잠금용 로딩 별도 검증 |
| report | 신고 대상 ID, 관리자 여부, Report·User 목록 | 판단을 변경하는 Report 유지 |
| user | 공개 프로필, 탈퇴 검증용 해시 | 실제 프로필 수정 엔티티 유지 |

## 1. auth

[auth/AuthService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/auth/AuthService.java)

### 전환 대상

1. **가입 중복 검사:** `findByEmail().isPresent()`, `findByHandle().isPresent()`는 엔티티 필드를 전혀 사용하지 않는다. 두 존재 여부를 반환한다. #51과 함께 한 SQL로 통합할 수 있다.
2. **재설정 요청:** `requestPasswordReset()`은 사용자 ID와 저장된 이메일만 사용한다. 발송 대상 projection으로 충분하다.
3. **로그인:** ID·핸들·비밀번호 해시만 사용한다. 내부 인증 DTO로 조회한다.
4. **재발급:** 활성 사용자 조건으로 ID·핸들·비밀번호 해시를 조회한다. 로그인과 결과 형태를 공유할 수 있다.

### 보존 사항

- 가입 중복은 활성 계정 기준, 이메일 오류 우선순위, DB 유니크 제약과 충돌 처리를 유지한다.
- 발송 주소는 현재처럼 DB에 저장된 이메일을 사용한다.
- 비밀번호 해시는 인증에 실제 필요한 값이다. 내부 DTO에 포함하되 외부 응답이나 로그에 노출하지 않는다.
- 로그인은 **검증한 바로 그 해시**에서 세션 지문을 만든다. 지문용 해시를 다시 조회하지 않는다.
- 재발급의 사용자·지문 검증 후 마지막 rotation 순서와 지문 없는 세션 거절 정책을 유지한다.
- `confirmPasswordReset()`은 실제 엔티티 비밀번호를 변경하므로 유지한다.
- 가입 직후 저장한 User를 응답으로 변환하는 것은 추가 조회가 아니므로 유지한다.

## 2. book

[book/BookService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/book/BookService.java), [book/dto/BookResponse.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/book/dto/BookResponse.java)

- `getById()`: 엔티티를 바로 BookResponse로 변환한다. 응답 필드 직접 projection 대상이다.
- `upsert()`의 기존 책 반환 및 유니크 충돌 후 재조회: 조회한 Book은 변경하지 않는다. ISBN/sourceKey 조건으로 BookResponse를 조회할 수 있다.
- 신규 생성 경로만 Book을 생성·저장하고, 저장된 엔티티에서 응답을 만든다. 응답을 위해 재조회하지 않는다.
- 충돌 후 재조회와 실패한 INSERT의 트랜잭션 분리는 유지한다.

BookResponse가 대부분의 책 필드를 사용하므로 컬럼 절감 폭은 작다. sourceKey·감사 시각과 불필요한 엔티티 로딩을 줄이는 정도이며, 큰 성능 개선이라고 단정하지 않는다. 검색은 외부 응답 처리라 JPA 엔티티 로딩 대상이 아니다.

## 3. feed — post 공통 목록 조립과 동일한 대상

[feed/FeedController.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/feed/FeedController.java), [post/PostService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/post/PostService.java)

`home()`, `timeline()`이 호출하는 `assemble()`에서 세 종류를 읽는다.

- Post: 커서·연결 ID와 표시 필드만 사용한다.
- User: ID·핸들·표시 이름·이미지만 필요하지만 이메일·해시·권한 등까지 읽는다.
- Book: BookResponse 표시 필드만 사용한다.

게시글·작성자·책·viewer 좋아요 여부를 평탄한 조회 DTO로 받고 중첩 응답을 구성한다. 엔티티 목록, ID 수집, User/Book Map 조립을 없앨 수 있다.

홈의 본인·팔로잉 제외, 팔로잉 피드의 포함 조건, 삭제·숨김·탈퇴 제외, ID 커서·size+1을 유지한다. 좋아요는 viewer로 제한하여 게시글 행이 증식하지 않게 한다.

팔로잉 ID와 좋아요 ID는 이미 스칼라다. 이들을 함께 조회하는 것은 #51의 쿼리 통합 개선이며, 과도한 엔티티 로딩으로 다시 세지 않는다.

## 4. follow

[follow/FollowService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/follow/FollowService.java)

### ID 확인

`idOf()`는 User를 읽고 ID만 꺼낸다. 활성 handle로 ID를 반환하는 스칼라 조회로 바꾼다. 팔로우·언팔로우·목록이 공통으로 사용하며, 404는 보존한다. 쓰기 경로라도 대상 User를 변경하지 않으므로 엔티티가 필요하지 않다.

### 목록

`page()`의 Follow는 관계 ID와 상대 ID, User는 표시 필드만 사용한다. 관계 ID·핸들·이름·이미지·viewer 팔로우 여부를 반환하는 목록 projection으로 통합한다.

커서용 관계 ID는 외부 응답에 없으므로 조회 DTO에만 두면 된다. 탈퇴 제외와 관계 ID 정렬을 보존한다. 부모 사용자 존재 확인은 유지한다.

팔로잉 ID, 관계 여부, COUNT는 이미 스칼라다. 새 Follow 저장과 직접 DELETE도 유지한다.

## 5. notification

[notification/NotificationService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/notification/NotificationService.java), [notification/NotificationRepository.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/notification/NotificationRepository.java)

`list()`의 Notification만 엔티티로 남아 있다. 커서·연결 정보와 응답용 종류·생성 시각·읽음 여부에만 사용한다. `isRead()`도 readAt의 null 여부 계산이므로 엔티티를 필요로 하지 않는다.

알림·행위자·게시글 표시 필드를 통합 projection으로 반환하고 중첩 응답을 구성한다. 수신자 조건은 조회 조건으로 유지한다. 삭제·숨김 게시글이 없어도 알림을 남기고, 행위자 소실/탈퇴도 명시적으로 처리한다.

ActorView와 PostView는 이미 projection이므로 새 지적으로 세지 않는다. 읽음 처리는 직접 UPDATE, 미확인 수와 소유 여부는 COUNT/EXISTS이며 유지한다. Notification 신규 저장도 유지한다.

## 6. post

[post/PostService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/post/PostService.java), [post/CommentService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/post/CommentService.java), [post/PostLikeService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/post/PostLikeService.java)

### 표시용 조회

- 상세 `read()`: Post·User·Book을 응답으로만 변환한다. 좋아요 여부까지 통합 projection으로 조회한다.
- 책별·작성자별 목록: feed에서 설명한 공통 `assemble()`와 같은 문제다.
- `byAuthor()`의 사용자 확인: handle로 User를 읽어 ID만 사용한다. 공통 활성 ID 조회로 대체한다.
- 댓글 `byPost()`: Comment의 ID·본문·생성 시각과 작성자 표시 필드만 필요하다. 댓글·작성자 projection으로 통합하되 부모 글 존재 확인은 유지한다.

### 작성 경로의 조회

- 글 작성의 Book: 존재 확인·ID 사용·응답 생성용이다. 책 표시 projection으로 충분하다.
- 글·댓글 작성의 User: 활성 계정 확인과 작성자 표시용이다. 활성 사용자 표시 projection으로 충분하다.
- 새 Post/Comment는 엔티티로 생성·저장하고 결과에서 응답을 만든다. 저장 후 재조회하지 않는다.
- 댓글·좋아요 생성 후 알림 발행: Post 전체에서 authorId만 꺼낸다. 알림 수신자 ID 스칼라 조회로 줄인다. 쓰기와 이벤트 순서는 유지한다.

### 삭제 권한 확인

- 글 삭제 `remove()`: Post는 작성자 비교용이며 실제 변경은 softDelete 쿼리다. 기존 공개 조건을 만족하는 작성자 ID 조회로 충분하다. 없음 404/다른 작성자 403을 유지한다.
- 댓글 삭제: Comment에서 postId·authorId만 사용하며 실제 변경은 조건부 UPDATE다. 두 필드의 projection으로 충분하다.
- `postAuthorIs()`: 공개 조건과 작성자 조건의 EXISTS로 대체할 수 있다.

### 잠금용 조회

댓글 삭제·좋아요 취소의 Post 잠금 조회는 필드를 사용하지 않는다. 최소 컬럼 잠금 조회가 가능한지 별도로 검증하되, 일반 EXISTS로 바꿔 잠금을 없애면 안 된다. 카운터 제거를 진행한다면 #50에 따라 잠금 필요성을 먼저 재검토한다.

## 7. reading

[reading/LibraryService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/reading/LibraryService.java), [reading/ReadingService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/reading/ReadingService.java), [reading/ReadingLinker.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/reading/ReadingLinker.java)

### 명확한 전환 대상

1. 서재 주인: User에서 ID만 사용한다. 공통 활성 ID 조회로 바꾼다.
2. 서재 목록: Reading·Book은 LibraryItemResponse로만 변환한다. 통합 projection으로 책 ID 수집과 Map 조립까지 제거한다. 필터·커서·서재 포함 조건을 유지한다.
3. `readingOf()`: ReadingResponse로만 변환한다. 응답 필드를 직접 조회한다. 서재 항목 우선, 없으면 최신 과거 기록이라는 정렬과 1건 제한을 유지한다.
4. ReadingLinker 첫 조회: `findShelvedByUserIdAndBookId()`에서 ID만 꺼내 다시 잠근다. 이미 존재하는 `findShelvedId()`를 재사용한다. 첫 조회는 잠금 없는 ID 탐색으로 유지한다.

### 별도 검증 대상

- 서재 제거 `remove()`: 잠금 조회한 엔티티 자체는 변경하지 않고 unshelve 쿼리를 호출한다. 소유자 확인용 값만 반환하는 잠금 조회를 검토한다. update도 사용하는 mine을 통째로 바꾸지는 않는다.
- ReadingLinker의 lock 및 충돌 후 잠금 재조회: ID만 반환한다. 잠금 SQL을 보존하며 최소 컬럼으로 줄일 수 있는지 검증한다.
- 현재 서재 제거는 하드 삭제가 아니므로, 과거 하드 삭제를 근거로 도입한 연결 잠금 자체가 여전히 필요한지 먼저 판단한다.

### 엔티티 유지

update의 apply/updateRating, 다시 담기의 shelveAgain, ReadingLinker의 shelveAgainIfRemoved는 실제 상태를 변경한다. 해당 엔티티 조회와 신규 저장은 유지한다. 목표 저장은 이미 upsert와 COUNT다.

## 8. report

[report/ReportService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/report/ReportService.java), [report/ReportAdminService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/report/ReportAdminService.java)

### 신고 생성

Post·Comment는 작성자 ID만, 사용자 신고의 User는 대상 ID만 사용한다. 각각 기존 공개 조건의 작성자 ID, 활성 사용자 ID 조회로 바꾼다. 대상별 404와 자기 신고 금지는 유지한다.

### 운영자 권한

requireAdmin은 User 전체에서 role만 본다. ID와 ADMIN 역할의 EXISTS로 충분하다. 없음/비관리자 모두 기존처럼 403이다. DB에서 확인하는 정책은 유지하며 JWT나 캐시로 대체하지 않는다. 탈퇴 관리자 처리 정책 변경은 projection 치환과 별개다.

### 운영자 목록

Report는 응답과 커서에만 사용하고, peopleIn의 User는 ID·핸들만 쓴다. 신고·대상·신고자 핸들을 통합 projection으로 반환한다.

관리자 목록의 숨김·삭제 대상 조회와 대상이 없어도 신고를 남기는 동작을 보존한다. 활성 사용자용 projection을 재사용하면서 현재 조회되는 탈퇴 사용자 정보를 의도 없이 제외하지 않는다. 대상 글·댓글의 ContentView는 이미 projection이다.

### 유지·별도 검증

handle은 Report의 판단·처리자·처리 시각을 실제 변경하므로 엔티티를 유지한다. 댓글 처리의 findAnyByIdForUpdate는 반환값을 사용하지 않는 잠금 조회다. 최소 컬럼 전환보다 카운터 설계와 잠금 필요성을 먼저 판단한다.

## 9. user

[user/UserService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/user/UserService.java), [user/UserController.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/user/UserController.java)

- 공개 프로필: User 전체 대신 핸들·표시 이름·소개·이미지와 관계 집계를 통합 projection으로 조회한다. activeHandle과 404를 유지한다. 비밀번호 해시·이메일·권한은 읽지 않는다.
- 탈퇴 검증: 엔티티 자체를 수정하지 않고 별도 조건부 UPDATE를 사용한다. `id = :userId and deletedAt is null` 조건의 passwordHash 스칼라 조회로 충분하다. 이미 userId를 알고 있으므로 새 DTO도 필요 없다.
- 프로필 수정: updateProfile로 실제 엔티티를 변경하므로 유지한다. 수정 결과를 응답으로 변환하는 것은 괜찮으며 재조회하지 않는다.

탈퇴 검증 조회를 축소해도 탈퇴의 동시성·후처리 문제가 해결되는 것은 아니다. 기존 오류와 UPDATE 결과 처리를 보존한다. 다른 패키지에서 사용한 User 조회는 해당 패키지 항목에 포함했으므로 중복 집계하지 않는다.

## 구현 시 과도한 추상화를 피하는 기준

- 같은 목적의 활성 사용자 ID 조회 등은 공유한다.
- 표시용·인증용·관리자용 데이터를 하나의 거대한 User DTO로 합치지 않는다.
- 같은 모양이라도 활성 사용자와 탈퇴 포함 관리자 조회 조건은 구분한다.
- 평탄한 조회 DTO에서 중첩 Response를 만드는 변환은 허용한다. 변환 자체가 문제가 아니다.
- projection의 SQL이 실제 필요한 컬럼만 SELECT하는지 확인한다. 엔티티를 내부에 담은 DTO나 접근자에서 전체 엔티티를 읽는 형태로 이름만 바꾸지 않는다.
- 쓰기용 엔티티 메서드를 일괄 삭제하지 않는다. 읽기 호출을 분리한 뒤 사용처를 확인한다.
- #51의 조회 통합과 함께 처리하여 DTO별 분리 조회·Map 조립을 그대로 남기지 않는다.

## 완료 기준

- [ ] 필요한 컬럼만 조회하는지 실제 SQL로 확인한다.
- [ ] 화면 표시 경로에서 비밀번호 해시 등 불필요한 인증 정보가 조회되지 않는다.
- [ ] 404/403, 활성 계정·삭제·숨김 필터, null 응답 정책을 보존한다.
- [ ] 커서 순서, size+1, 빈 페이지, 중복·누락을 검증한다.
- [ ] 신규 저장·수정 후 응답을 위해 추가 SELECT하지 않는다.
- [ ] 로그인 지문과 재발급 rotation 순서를 유지한다.
- [ ] 잠금 치환은 실제 SQL·범위·트랜잭션·동시성 테스트로 검증한다.
- [ ] 제거 대상 설계가 만든 잠금은 반환 타입만 고치지 말고 필요성부터 판단한다.
- [ ] 실행 계획과 대표 데이터로 효과를 확인한다. 엔티티 제거만으로 성능 향상을 단정하지 않는다.
- [ ] 불필요해진 Map 조립·조회 메서드·의존성을 정리한다.


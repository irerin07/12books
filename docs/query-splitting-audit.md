# 불필요한 쿼리 분할 정리

## 무엇을 고치려는가

하나의 응답에 필요한 데이터를 같은 DB에 두고도, 엔티티·서비스 경계에 따라 여러 번 조회한 뒤 Java에서 조립하는 곳을 정리한다.

예를 들어 게시글 상세에서 게시글, 작성자, 책, 좋아요 여부를 각각 읽으면 SELECT가 4번이다. 필요한 표시 필드를 한 조회에서 반환하면 별도의 ID 수집과 Map 조립 없이 응답을 만들 수 있다.

이 문서는 앞선 [설계 전제 재검토 #50](https://github.com/irerin07/12books/issues/50)과 다르다. 캐시·카운터·새 인프라를 추가하는 제안이 아니라, 현재 데이터를 읽는 경로를 단순화하는 제안이다.

- 범위: auth, book, feed, follow, notification, post, reading, report, user.
- 기준 커밋: `4d44d0515d4b6f894eddcdfeeec284684476042f`.
- 코드 정적 검토 결과다. 실행 SQL 계측·부하 테스트·수정은 하지 않았다.
- 횟수는 별도 표시가 없으면 데이터가 있는 정상 경로의 SELECT 수다. 빈 페이지, 조기 실패에서는 달라진다.
- 쿼리 수 감소가 실행 시간 개선을 보장하지는 않는다. 집계 비용과 실행 계획도 확인해야 한다.
- follow/user의 프로필 지적, feed/post의 응답 조립 지적은 같은 원인을 공유한다. 별개 결함으로 중복 집계하지 않는다.

## 전체 요약

| 패키지·경로 | 현재 → 제안 | 핵심 |
|---|---|---|
| auth 가입 사전 중복 검사 | 2 → 1 | 이메일·핸들 존재 여부 함께 조회 |
| auth 정상 재발급 | Redis 3 → 2왕복 | userId·지문 함께 읽고, 교체는 마지막 |
| auth 단일 세션 폐기 | Redis 최대 3 → 1왕복 | 조회·인덱스 제거·삭제를 스크립트로 묶기 |
| book | 해당 없음 | 추가 조회가 충돌 이후 재조회인지 구별 |
| feed 목록 | 5 → 1 | 팔로우 조건을 DB에서 평가하고 표시 정보 함께 조회 |
| follow 목록 | 4 → 2 | 대상 사용자 확인 유지, 목록·표시 정보·관계 통합 |
| notification 목록 | 2~3 → 1 | 알림·행위자·게시글 표시 정보 통합 |
| post 상세 | 4 → 1 | 게시글·작성자·책·좋아요 통합 |
| post 책별/작성자별 목록 | 5 → 2 | 대상 존재 확인 유지, 목록 통합 |
| post 댓글 목록 | 3 → 2 | 게시글 확인 유지, 댓글·작성자 통합 |
| reading 서재 | 3 → 2 | 서재 주인 확인 유지, 독서·책 통합 |
| reading 추가 후보 판별 | 해당 부분 2 → 1 | 현재 서재 항목과 과거 회차 후보 함께 조회 |
| report 운영자 목록 | 3~5 → 2 | 권한 확인 유지, 신고·대상·신고자 통합 |
| report USER 신고 기각 | 불필요 SELECT 1회 제거 | 결과와 무관하게 아무 복구 작업도 하지 않음 |
| user 공개 프로필 | 4 → 1 | 사용자·팔로워 수·팔로잉 수·관계 통합 |
| user 프로필 수정 | SELECT 4 → 2 | 수정용 조회 유지, 두 집계 통합·자기 팔로우 조회 제거 |

## 1. auth — SQL과 Redis 왕복을 구분한다

[AuthService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/auth/AuthService.java), [RefreshTokenStore.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/auth/RefreshTokenStore.java)

### 가입

`signup()`은 이메일 중복과 핸들 중복을 각각 사용자 엔티티 조회로 확인한다. 필요한 것은 두 개의 존재 여부뿐이다. 한 SQL에서 독립적인 EXISTS 결과 두 개를 받으면 된다.

두 값 모두 중복일 때 기존의 이메일 오류 우선순위를 보존한다. 사전 검사만으로 동시 가입을 막을 수 없으므로 DB 유니크 제약과 충돌 예외 처리는 유지한다.

### 재발급

현재 정상 경로는 Redis에서 userId 조회 → DB 사용자 확인 → Redis에서 자격증명 지문 조회 → Redis 원자 교체다.

같은 세션의 userId와 지문을 HMGET 한 번으로 읽으면 Redis 왕복이 3회에서 2회로 줄어든다. **DB 확인과 지문 검증 이후에 rotation하는 순서는 유지한다.** 교체를 앞으로 당겨 1회로 줄이면 DB 장애 시 클라이언트가 기존 refresh를 잃는 문제가 돌아온다. 지문 없는 세션 거절 정책도 유지한다.

### 단일 세션 폐기

`revoke()`는 userId 조회, 사용자 인덱스 제거, 세션 삭제를 별도로 실행한다. 현재 Redis 구성에서 이를 하나의 Lua 실행으로 묶을 수 있다. SQL 조회 분할은 아니지만 같은 목적의 불필요한 왕복이다. 향후 Redis Cluster를 도입하면 다중 키의 슬롯 제약은 별도로 확인해야 한다.

## 2. book — 이번 기준에서 추가 지적 없음

책 조회 자체는 단건 조회다. 새 책 등록의 INSERT와 유니크 충돌 후 재조회는 동일 응답을 테이블별로 나눈 사례가 아니다. 충돌 이후 다른 요청이 만든 결과를 읽는 동작을 단순 중복 조회로 제거하지 않는다.

## 3. feed / post — 공통 응답 조립부터 통합한다

[FeedController.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/feed/FeedController.java), [PostService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/post/PostService.java), [CommentService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/post/CommentService.java)

### 피드

팔로잉 ID 목록을 먼저 가져와 게시글 조회에 넘기고, 게시글 목록을 읽은 뒤 작성자·책·좋아요를 따로 조회한다. 정상 비어 있지 않은 페이지에서 총 5회다.

팔로우 포함·제외 조건은 게시글 쿼리의 EXISTS/NOT EXISTS로 처리할 수 있다. 작성자·책과 해당 viewer의 좋아요 여부도 함께 반환하면 목록 조회 한 번으로 만들 수 있다. 팔로잉 ID 목록을 애플리케이션으로 운반할 필요도 없어진다.

### 게시글 상세·목록

`read()`는 게시글, 작성자, 책, 좋아요 여부를 각각 읽는다. 표시용 projection으로 4회를 1회로 줄일 수 있다.

`byBook()`, `byAuthor()`는 책/사용자 존재 확인을 유지하고, 게시글 목록과 부가 정보를 통합하면 5회에서 2회로 줄일 수 있다. 존재하지 않는 대상의 404와 존재하지만 글이 없는 빈 목록은 계속 구별한다.

댓글 목록도 부모 게시글 확인은 유지하고 댓글·작성자를 함께 조회하면 3회에서 2회로 줄일 수 있다.

### 지켜야 할 것

- 작성자·책은 단건 연결, 좋아요는 해당 viewer로 제한된 유일 관계로 연결한다. 전체 좋아요 행을 JOIN해 페이지 행 수를 늘리지 않는다.
- 기존 공개 범위, 탈퇴 사용자 제외, 정렬, 커서, size+1 규칙을 보존한다.
- 댓글 삭제의 작성자/게시글 권한 확인과 부모 잠금은 별개다. 조회 수만 보고 잠금 순서를 바꾸지 않는다.
- 좋아요·댓글 작성의 조건부 UPDATE는 조회가 아니다. 이후 필요한 정보 조회와 묶어 “중복 SELECT”라고 세지 않는다.

## 4. follow — 목록 조립과 프로필 집계를 구별한다

[FollowService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/follow/FollowService.java)

팔로워·팔로잉 목록은 대상 사용자 확인 → Follow 페이지 → 사용자 목록 → viewer의 팔로우 여부 조회로 나뉜다.

첫 사용자 확인은 404와 빈 목록 구별을 위해 유지한다. 나머지는 Follow에서 상대 사용자를 JOIN하고 viewer의 관계를 함께 조회하면 된다. 총 4회에서 2회다. 탈퇴 제외 조건과 관계 ID 기반 커서를 유지한다.

프로필에서 호출하는 팔로워 수·팔로잉 수·관계 여부의 개별 조회 문제는 아래 user 항목에서 함께 해결한다.

## 5. notification — 대상이 없어도 알림은 남겨야 한다

[NotificationService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/notification/NotificationService.java)

`list()`는 알림 목록, 행위자 표시 정보, 필요하면 게시글 표시 정보를 별도로 가져온다. 2~3회를 LEFT JOIN 기반 projection 1회로 통합할 수 있다.

삭제되거나 숨겨진 게시글 때문에 알림 행 자체가 빠지면 안 된다. 게시글 공개 조건은 알림을 제거하는 WHERE가 아니라 대상 연결 조건에 둔다. 행위자가 없거나 탈퇴한 경우도 현재 의도한 응답 정책을 먼저 보존하며, INNER JOIN으로 알림을 조용히 없애지 않는다.

미확인 알림 수는 별도 API의 단일 집계다. 목록과 무조건 묶을 필요는 없다. `markRead()`의 UPDATE 결과가 0일 때 수행하는 존재 확인은 “이미 읽음”과 “없거나 다른 사람 소유”를 구분하므로 단순 중복으로 제거하지 않는다.

## 6. reading — 서재 조회와 쓰기 전 후보 탐색

[LibraryService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/reading/LibraryService.java), [ReadingService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/reading/ReadingService.java)

### 서재 목록

서재 주인 확인 → 독서 목록 → 책 목록으로 3회다. 주인 확인을 유지하고 독서·책 표시 정보를 함께 조회하면 2회다.

### 서재 추가

현재 서재 항목을 찾고, 없으면 과거 회차를 찾는다. “현재 서재 항목 우선, 없으면 최신 과거 회차”라는 후보 선택을 한 조회로 표현할 수 있다. `inBookshelf DESC, id DESC` 우선순위로 제한하고 ID와 서재 포함 여부만 가져오는 방향이다.

이후 재등록의 잠금 조회와 유니크 제약은 유지한다. 후보 탐색에서 엔티티를 미리 로딩해 이후 잠금 조회가 낡은 영속성 컨텍스트 상태를 재사용하게 만들지 않는다.

진도 변경의 잠금 조회, 목표 저장 후 집계, 유니크 충돌 후 재조회는 이번 분할 지적에서 제외한다.

## 7. report — 대상 종류별 Map 조립과 무의미한 조회

[ReportAdminService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/report/ReportAdminService.java)

### 운영자 목록

권한 확인 → 신고 목록 → 대상 게시글 → 대상 댓글 → 신고자/대상 사용자 조회로 최대 5회다.

권한 확인은 유지한다. 목록은 대상 종류에 따른 조건부 LEFT JOIN과 신고자 JOIN으로 한 번에 반환한다. 총 3~5회에서 2회로 줄이고 ID 수집, 종류별 Map, `contentsOf()`, `contentOf()`, `peopleIn()` 등의 조립을 제거할 수 있다.

각 대상 PK에 연결하므로 한 신고가 여러 행으로 늘어나지 않게 구성할 수 있다. **관리자 목록은 숨김·삭제된 대상도 읽는 현재 동작을 보존한다.** 대상이 없어도 신고 자체를 없애지 않는다.

### USER 신고 기각

`restore()`는 다른 ACTIONED 신고 존재 여부를 먼저 조회하지만 USER 분기는 아무 작업도 하지 않는다. 결과가 true든 false든 복구 작업이 없으므로 USER는 해당 조회 전에 반환하면 된다. 판단 기록은 그대로 수행한다.

댓글 소속 게시글 조회와 부모 잠금은 현재 댓글 카운터 설계에 묶인 별도 문제다. 카운터 설계 검토는 #50에서 다루며, 여기서는 무리하게 JOIN으로 잠금 범위를 바꾸지 않는다.

## 8. user — 서비스 경계가 쿼리 분리를 강제하지 않는다

[UserController.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/user/UserController.java), [UserService.java](https://github.com/irerin07/12books/blob/4d44d0515d4b6f894eddcdfeeec284684476042f/src/main/java/com/irene/twelvebooks/user/UserService.java)

### 공개 프로필

`getByHandle()` 이후 `withRelation()`에서 팔로워 수, 팔로잉 수, 팔로우 여부를 각각 읽어 총 4회다.

프로필 전용 조회에서 표시 필드와 독립 집계 두 개, EXISTS를 함께 반환하면 1회다. 사용자 자체를 기준으로 조회하므로 결과가 없으면 기존처럼 404다. 이메일·해시는 조회하지 않아도 된다.

컨트롤러 조립의 근거는 user/follow 순환 참조 방지지만, 패키지 참조와 SQL 조회 경계는 별개다. 공개 프로필 조회가 필요한 테이블을 함께 읽는 데 엔티티 양방향 연관관계나 새 프레임워크는 필요하지 않다.

팔로워와 팔로잉을 일반 JOIN 두 개로 펼친 뒤 COUNT하면 행이 곱해질 수 있으므로 독립 집계를 유지한다. 탈퇴한 상대를 제외하는 현재 조건도 보존한다.

### 내 프로필 수정

수정 후에도 관계 조회 3개를 수행한다. 특히 자기 자신을 팔로우하는지 DB에 묻지만 도메인 규칙상 false다.

수정한 사용자 정보는 재사용하고 두 개의 수만 한 집계 쿼리로 가져온다. 사용자 SELECT 1회 + 관계 SELECT 3회가 사용자 SELECT 1회 + 집계 SELECT 1회로 줄어든다. UPDATE는 별도다.

탈퇴 전 비밀번호 검증용 조회와 실제 탈퇴 UPDATE는 서로 다른 목적이다. 탈퇴 후 카운터 정리 문제는 #50의 설계 검토로 남긴다.

## 권장 작업 순서와 완료 기준

1. post의 상세/목록 표시용 조회와 feed의 팔로우 조건 통합을 먼저 진행한다. 공통 조립을 중복 구현하지 않는다.
2. follow/user의 프로필과 목록을 함께 정리한다.
3. notification, reading 서재, report 목록을 정리한다.
4. auth의 왕복 감소와 reading 후보 탐색은 보안·잠금 순서를 보존하는 테스트와 함께 별도로 진행한다.

- [ ] 각 경로의 SQL/Redis 호출 횟수를 실제 테스트로 확인한다.
- [ ] 기존 응답 필드와 404/403, 빈 목록 동작을 보존한다.
- [ ] 커서 페이지의 중복·누락과 size+1 동작을 검증한다.
- [ ] 탈퇴·삭제·숨김·대상 소실 시 응답을 검증한다.
- [ ] 인증 rotation 순서와 쓰기 잠금 순서를 보존한다.
- [ ] 대표 데이터 분포에서 실행 계획을 확인한다. 단순히 “한 쿼리라 빠르다”고 판정하지 않는다.
- [ ] 사라진 Java 조립 코드와 사용하지 않는 조회 메서드를 정리한다.

**목표는 모든 일을 한 SQL로 밀어 넣는 것이 아니다. 응답 하나를 만들기 위해 불필요하게 왕복하고 조립하는 부분을 없애되, 서로 다른 목적의 검증·잠금·쓰기는 구별하는 것이다.**


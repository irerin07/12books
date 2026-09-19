package com.irene.twelvebooks.reading;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

	/**
	 * 지금 서재에 꽂혀 있는 기록의 <b>id만</b> 찾는다. 다시 담기가 "이미 있는가"를 볼 때 쓴다.
	 *
	 * <p>엔티티가 아니라 id를 돌려주는 것이 핵심이다. 여기서 엔티티를 읽어 오면 그것이
	 * 영속성 컨텍스트에 올라가고, 뒤이은 잠금 조회는 <b>잠금만 잡고 이미 들고 있던 인스턴스를
	 * 그대로 돌려준다.</b> 그러면 잠금을 기다리는 동안 남이 바꿔 커밋한 내용을 보지 못한 채
	 * 옛 필드로 판단하게 된다 — 잠갔는데도 직렬화가 되지 않는다.
	 */
	@Query("""
			select r.id from Reading r
			where r.userId = :userId and r.bookId = :bookId and r.inBookshelf = true
			""")
	Optional<Long> findShelvedId(@Param("userId") Long userId, @Param("bookId") Long bookId);

	/**
	 * 이 책의 <b>지난 독서 중 가장 최근 것</b>. 다시 담기가 "이어서 읽을 것이 있는가"를 볼 때 쓴다.
	 *
	 * <p>여러 벌일 수 있다 — 새로 시작을 고를 때마다 한 행이 쌓인다. 이어서 읽는 대상은
	 * 언제나 마지막 것이다.
	 */
	@Query("""
			select r.id from Reading r
			where r.userId = :userId and r.bookId = :bookId and r.inBookshelf = false
			order by r.id desc
			""")
	List<Long> findPastIds(@Param("userId") Long userId, @Param("bookId") Long bookId, Pageable pageable);

	/**
	 * 이 책에 대한 내 기록 하나. <b>서재에 있으면 그것, 없으면 가장 최근 지난 독서</b>다.
	 *
	 * <p>화면이 담기 버튼을 그리기 전에 부른다 — "담김"으로 표시할지, "200쪽까지 읽으셨어요.
	 * 이어서 읽을까요?"를 물을지가 이 한 번의 조회로 정해진다.
	 */
	@Query("""
			select r from Reading r
			where r.userId = :userId and r.bookId = :bookId
			order by r.inBookshelf desc, r.id desc
			""")
	List<Reading> findMine(@Param("userId") Long userId, @Param("bookId") Long bookId, Pageable pageable);

	/** 다시 담기가 확인과 쓰기 사이를 직렬화하려고 잠그는 경로. 뺀 기록도 돌려준다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reading r where r.id = :id")
	Optional<Reading> findAnyByIdForUpdate(@Param("id") Long id);

	/**
	 * 지금 서재에 있는 기록만 찾는다. 뺀 기록은 행이 남아 있어도 "서재에 없는 책"으로
	 * 답해야 한다 — 그러지 않으면 뺀 책이 감상평 쓰기에서 조용히 다시 담긴다.
	 */
	@Query("select r from Reading r where r.userId = :userId and r.bookId = :bookId and r.inBookshelf = true")
	Optional<Reading> findShelvedByUserIdAndBookId(@Param("userId") Long userId,
			@Param("bookId") Long bookId);

	/**
	 * 수정·삭제가 읽어 가는 경로. 행에 쓰기 잠금을 건다.
	 *
	 * <p>{@code status}와 {@code currentPage}는 독립 필드가 아니다 — "완독이면서 총 쪽수를
	 * 알면 진도는 끝"이라는 규칙이 둘을 묶는다. 한쪽이 완독으로 바꾸고 다른 쪽이 진도를
	 * 중간으로 옮기면 각자는 자기 스냅샷에서 옳지만 합쳐진 결과가 규칙을 깬다. 컬럼 단위
	 * 부분 UPDATE도, {@code current_page <= page_count} CHECK도 이것을 막지 못한다(150 ≤ 300).
	 *
	 * <p>그래서 읽기-수정-쓰기를 직렬화한다. 뒤에 온 요청은 앞의 결과를 보고 다시 판단하므로
	 * 규칙이 유지되고, 낙관적 잠금과 달리 사용자에게 409를 돌려주지 않는다 — 진도 자동 저장에
	 * 충돌 오류가 뜨는 것은 고칠 방법이 없는 오류다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reading r where r.id = :id and r.inBookshelf = true")
	Optional<Reading> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 이미 있는 줄 아는 기록을 (user, book)으로 잠그고 읽는다.
	 *
	 * <p>잠금 읽기는 트랜잭션의 스냅샷이 아니라 <b>최신 커밋</b>을 본다. REPEATABLE READ에서
	 * 평범한 재조회는 트랜잭션이 시작할 때의 세상을 계속 보므로, 그 사이 남이 만들어 커밋한
	 * 행을 영원히 찾지 못한다 — 중복 키로 막힌 뒤의 재조회가 바로 그 상황이다.
	 *
	 * <p><b>첫 조회로 쓰지 않는다.</b> 행이 없을 때 잠금 읽기는 갭 잠금을 잡고, 그러면 같은
	 * 요청이 다른 트랜잭션으로 넣으려는 insert가 자기 자신의 갭 잠금에 막힌다.
	 * "이미 누가 만들었다"는 신호를 받은 뒤에만 부른다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reading r where r.userId = :userId and r.bookId = :bookId and r.inBookshelf = true")
	Optional<Reading> findShelvedByUserIdAndBookIdForUpdate(@Param("userId") Long userId,
			@Param("bookId") Long bookId);

	// allow-no-block-filter: 사람이 아니라 책이 나오는 목록이다. 서재 주인에 대한 차단은
	// LibraryController의 blockGuard가 404로 막는다
	/**
	 * 서재 한 페이지. 주어진 필터는 전부 AND로 묶이고, 주지 않은 것은 조건에서 빠진다.
	 *
	 * <p>연도는 {@code year(started_at) = 2026}이 아니라 <b>[그 해 시작, 다음 해 시작)</b> 범위로
	 * 묻는다. 컬럼에 함수를 씌우면 인덱스를 탈 수 없어서다.
	 *
	 * <p>{@code year}는 시작과 완독 중 <b>하나라도</b> 그 해면 통과하는 편의 필터다. 이것을 완독
	 * 연도로만 두면 {@code year=2026&status=READING}이 항상 빈 목록이 된다 — 읽는 중인 책은
	 * 완독일이 없어 연도 조건에 걸릴 수가 없기 때문이다.
	 *
	 * <p>정렬은 {@code id desc}라 커서가 id 하나다. {@code size + 1}건을 가져와 다음 페이지
	 * 존재를 판정한다.
	 */
	@Query("""
			select r from Reading r
			where r.userId = :userId
			  and r.inBookshelf = true
			  and (:status is null or r.status = :status)
			  and (:startedFrom is null or (r.startedAt >= :startedFrom and r.startedAt < :startedTo))
			  and (:finishedFrom is null or (r.finishedAt >= :finishedFrom and r.finishedAt < :finishedTo))
			  and (:yearFrom is null
			       or (r.startedAt >= :yearFrom and r.startedAt < :yearTo)
			       or (r.finishedAt >= :yearFrom and r.finishedAt < :yearTo))
			  and (:cursor is null or r.id < :cursor)
			order by r.id desc
			""")
	List<Reading> findLibraryPage(@Param("userId") Long userId,
			@Param("status") ReadingStatus status,
			@Param("yearFrom") LocalDateTime yearFrom, @Param("yearTo") LocalDateTime yearTo,
			@Param("startedFrom") LocalDateTime startedFrom, @Param("startedTo") LocalDateTime startedTo,
			@Param("finishedFrom") LocalDateTime finishedFrom, @Param("finishedTo") LocalDateTime finishedTo,
			@Param("cursor") Long cursor,
			Pageable pageable);

	/**
	 * 그 해에 다 읽은 <b>책 종수</b>. 달성률의 분자이고, 서재 조회의 연도 필터와 무관하게
	 * <b>완독 기준으로만</b> 센다(plan.md). 여기도 범위로 물어 인덱스를 막지 않는다.
	 *
	 * <p>{@code distinct r.bookId}인 이유는 질문이 "올해 몇 권 읽었나"이기 때문이다. 같은 책을
	 * 두 번 읽으면 회차는 둘이지만 책은 한 권이다. 두 번 읽었다는 사실은
	 * {@link #countFinishedSessionsBetween}이 따로 답한다.
	 *
	 * <p><b>서재 포함 여부를 보지 않는다.</b> 서재는 "지금 무엇을 꽂아 두었나"이고 실적은
	 * "그 해에 무엇을 다 읽었나"라 기준이 다르다 — 다 읽고 책장에서 내렸다고 읽지 않은 것이
	 * 되지는 않는다. 예전에는 {@code in_bookshelf = true} 조건이 있어 뺀 책이 실적에서
	 * 빠졌는데, 그 조건에는 근거가 없었다.
	 */
	@Query("""
			select count(distinct r.bookId) from Reading r
			where r.userId = :userId
			  and r.status = com.irene.twelvebooks.reading.ReadingStatus.FINISHED
			  and r.finishedAt >= :from and r.finishedAt < :to
			""")
	long countFinishedBetween(@Param("userId") Long userId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	/**
	 * 그 해의 <b>완독 회차 수</b>. 같은 책을 두 번 읽으면 둘이다.
	 *
	 * <p>달성률에는 쓰지 않는다 — 얇은 책을 반복해 숫자를 올릴 수 있어서다. 대신 함께
	 * 보여 준다. 한 숫자로 뭉개면 "몇 권 읽었나"와 "몇 번 읽었나" 둘 다 잃는다.
	 */
	@Query("""
			select count(r) from Reading r
			where r.userId = :userId
			  and r.status = com.irene.twelvebooks.reading.ReadingStatus.FINISHED
			  and r.finishedAt >= :from and r.finishedAt < :to
			""")
	long countFinishedSessionsBetween(@Param("userId") Long userId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	/**
	 * 서재에서 뺀다 — 행은 그대로 두고 목록에서만 내린다.
	 *
	 * @return 바뀐 행 수. 0이면 이미 빠져 있다는 뜻이다.
	 */
	@Modifying
	@Query("update Reading r set r.inBookshelf = false where r.id = :id and r.inBookshelf = true")
	int unshelve(@Param("id") Long id);
}

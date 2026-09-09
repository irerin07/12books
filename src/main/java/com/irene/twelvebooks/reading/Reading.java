package com.irene.twelvebooks.reading;

import com.irene.twelvebooks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 한 사람이 한 책을 읽은 기록. (user, book) 당 한 행이고, 재독은 새 행을 만들지 않고
 * 상태를 되돌려 이 행을 재사용한다.
 *
 * <p>상태 전이에 따라오는 부수 효과는 전부 이 안에 있다. 서비스가 필드를 직접 세팅하면
 * "완독으로 바꿀 때 완독일을 채운다" 같은 규칙이 호출부마다 흩어지고, 한 곳에서 빠뜨리면
 * 조용히 어긋난 데이터가 남는다.
 *
 * <p>시간은 인자로 받는다. 엔티티가 시계를 직접 읽으면 테스트가 sleep에 기대게 된다.
 *
 * <p>{@code @DynamicUpdate}는 바뀐 컬럼만 UPDATE한다. 폰에서 진도를, 노트북에서 별점을
 * 동시에 고쳐도 둘 다 살아남는다 — 전체 UPDATE를 날리면 나중 쓰기가 먼저 반영된 값을
 * 되돌린다. 같은 필드를 동시에 고치면 나중 쓰기가 이기는데, 진도라면 나중 위치가 맞는 값이다.
 * 충돌을 오류로 알려야 할 만큼 잦아지면 그때 {@code @Version} + 409로 올린다.
 */
@Entity
@DynamicUpdate
@Table(name = "readings")
public class Reading extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "book_id", nullable = false)
	private Long bookId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReadingStatus status;

	@Column(name = "current_page", nullable = false)
	private int currentPage;

	/** 내 판본의 총 쪽수. 카카오가 주지 않아 사용자가 채운다. 모르면 진도 상한도 없다. */
	@Column(name = "page_count")
	private Integer pageCount;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	private Integer rating;

	protected Reading() {
	}

	private Reading(Long userId, Long bookId, ReadingStatus status) {
		this.userId = userId;
		this.bookId = bookId;
		this.status = status;
		this.currentPage = 0;
	}

	public static Reading of(Long userId, Long bookId, ReadingStatus status, LocalDateTime now) {
		Reading reading = new Reading(userId, bookId, status);
		reading.changeStatus(status, now);
		return reading;
	}

	/**
	 * 상태를 바꾸고 따라오는 날짜를 정리한다.
	 *
	 * <ul>
	 * <li>{@code READING}으로 가면 시작일이 비어 있을 때만 채운다 — 덮었다 다시 펴는 것은
	 * 시작이 아니므로 처음 편 날이 남아야 한다.</li>
	 * <li>{@code FINISHED}로 <b>처음 들어갈 때만</b> 완독일을 남긴다. 이미 완독인데 같은 요청이
	 * 다시 오면(재시도·중복 클릭) 다 읽은 날이 오늘로 바뀌어 버린다.</li>
	 * <li>{@code FINISHED}에서 나오면 완독일을 비운다. 재독을 시작한 것이므로 "다 읽은 날"이
	 * 남아 있으면 안 된다.</li>
	 * </ul>
	 */
	public void changeStatus(ReadingStatus next, LocalDateTime now) {
		if (status == ReadingStatus.FINISHED && next != ReadingStatus.FINISHED) {
			this.finishedAt = null;
		}
		if (next == ReadingStatus.READING && startedAt == null) {
			this.startedAt = now;
		}
		if (next == ReadingStatus.FINISHED && status != ReadingStatus.FINISHED) {
			this.finishedAt = now;
		}
		this.status = next;
		settleProgress();
	}

	/**
	 * 총 쪽수와 진도를 함께 반영한다. null은 "안 보냈다"는 뜻이라 기존 값을 유지한다.
	 *
	 * <p>둘을 한 메서드에서 받는 이유는 <b>최종 조합</b>으로 판단해야 하기 때문이다. 하나씩
	 * 차례로 적용하면 320쪽 300쪽까지 읽은 기록을 "200쪽짜리의 150쪽"으로 고치려 할 때
	 * 중간 상태(200쪽인데 진도 300)에 걸려 거부된다 — 최종 상태는 멀쩡한데도.
	 */
	public void applyProgress(Integer newPageCount, Integer newCurrentPage) {
		Integer nextPageCount = newPageCount == null ? pageCount : newPageCount;
		int nextCurrentPage = newCurrentPage == null ? currentPage : newCurrentPage;

		require(nextPageCount == null || nextPageCount > 0, "총 쪽수는 1 이상이어야 합니다");
		require(nextCurrentPage >= 0, "현재 쪽수는 0 이상이어야 합니다");
		require(nextPageCount == null || nextCurrentPage <= nextPageCount,
				"현재 쪽수는 총 쪽수를 넘을 수 없습니다");

		this.pageCount = nextPageCount;
		this.currentPage = nextCurrentPage;
		settleProgress();
	}

	public void updateRating(Integer rating) {
		require(rating == null || (rating >= 1 && rating <= 5), "별점은 1~5입니다");
		this.rating = rating;
	}

	/**
	 * 완독이면서 총 쪽수를 아는 기록은 진도가 항상 끝이다.
	 *
	 * <p>상태 전이와 진도 수정 양쪽 끝에서 부르는 이유는, 어느 쪽 순서로 들어와도 같은 결론에
	 * 닿아야 하기 때문이다 — 쪽수를 모른 채 완독한 뒤 나중에 쪽수를 넣는 경우와, 완독 상태에서
	 * 진도만 중간으로 되돌리려는 경우가 모두 여기서 정리된다.
	 */
	private void settleProgress() {
		if (status == ReadingStatus.FINISHED && pageCount != null) {
			this.currentPage = pageCount;
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

	public boolean ownedBy(Long candidateUserId) {
		return userId.equals(candidateUserId);
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getBookId() {
		return bookId;
	}

	public ReadingStatus getStatus() {
		return status;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public Integer getPageCount() {
		return pageCount;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public Integer getRating() {
		return rating;
	}
}

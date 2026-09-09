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
 */
@Entity
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
	 * <li>{@code FINISHED}로 가면 완독일을 남기고, 총 쪽수를 알면 진도를 끝까지 옮긴다.
	 * 다 읽었는데 진도가 중간에 멈춰 있으면 서재 통계가 어긋난다.</li>
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
		if (next == ReadingStatus.FINISHED) {
			this.finishedAt = now;
			if (pageCount != null) {
				this.currentPage = pageCount;
			}
		}
		this.status = next;
	}

	/** 진도는 되돌아갈 수도 있다. 앞부분을 다시 읽는 것은 정상이다. */
	public void updateProgress(int page) {
		require(page >= 0, "현재 쪽수는 0 이상이어야 합니다");
		require(pageCount == null || page <= pageCount,
				"현재 쪽수는 총 쪽수(%d)를 넘을 수 없습니다".formatted(pageCount));
		this.currentPage = page;
	}

	/**
	 * 총 쪽수를 채우거나 고친다. 이미 읽은 쪽수보다 작게 줄이는 것은 막는다 — 조용히
	 * 진도를 깎아 내리면 사용자가 잃은 줄도 모르고 잃는다.
	 */
	public void updatePageCount(Integer pageCount) {
		require(pageCount == null || pageCount > 0, "총 쪽수는 1 이상이어야 합니다");
		require(pageCount == null || pageCount >= currentPage,
				"총 쪽수는 이미 읽은 쪽수(%d)보다 작을 수 없습니다".formatted(currentPage));
		this.pageCount = pageCount;
	}

	public void updateRating(Integer rating) {
		require(rating == null || (rating >= 1 && rating <= 5), "별점은 1~5입니다");
		this.rating = rating;
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

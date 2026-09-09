package com.irene.twelvebooks.reading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상태 전이에 따라오는 부수 효과. 컨테이너 없이 도는 순수 로직이다.
 */
class ReadingTest {

	private static final Long USER = 1L;
	private static final Long BOOK = 2L;
	private static final LocalDateTime FIRST = LocalDateTime.of(2026, 1, 10, 9, 0);
	private static final LocalDateTime LATER = LocalDateTime.of(2026, 2, 3, 21, 30);

	private Reading want() {
		return Reading.of(USER, BOOK, ReadingStatus.WANT_TO_READ, FIRST);
	}

	@Test
	@DisplayName("읽고 싶다로 담으면 아직 시작한 것이 아니다")
	void wantToReadHasNotStarted() {
		Reading reading = want();

		assertThat(reading.getStatus()).isEqualTo(ReadingStatus.WANT_TO_READ);
		assertThat(reading.getCurrentPage()).isZero();
		assertThat(reading.getStartedAt()).isNull();
		assertThat(reading.getFinishedAt()).isNull();
	}

	@Test
	@DisplayName("읽는 중으로 바꾸면 시작일이 채워진다")
	void startingFillsStartedAt() {
		Reading reading = want();

		reading.changeStatus(ReadingStatus.READING, FIRST);

		assertThat(reading.getStartedAt()).isEqualTo(FIRST);
	}

	@Test
	@DisplayName("이미 시작한 책을 다시 읽는 중으로 바꿔도 시작일은 그대로다")
	void restartingKeepsTheOriginalStartedAt() {
		Reading reading = want();
		reading.changeStatus(ReadingStatus.READING, FIRST);

		reading.changeStatus(ReadingStatus.PAUSED, LATER);
		reading.changeStatus(ReadingStatus.READING, LATER);

		// 처음 편 날이 시작일이다. 덮었다 다시 펴는 것은 시작이 아니다.
		assertThat(reading.getStartedAt()).isEqualTo(FIRST);
	}

	@Test
	@DisplayName("완독으로 바꾸면 완독일이 남고 진도가 끝까지 간다")
	void finishingRecordsFinishedAtAndCompletesProgress() {
		Reading reading = want();
		reading.changeStatus(ReadingStatus.READING, FIRST);
		reading.updatePageCount(320);
		reading.updateProgress(100);

		reading.changeStatus(ReadingStatus.FINISHED, LATER);

		assertThat(reading.getFinishedAt()).isEqualTo(LATER);
		assertThat(reading.getCurrentPage()).isEqualTo(320);
	}

	@Test
	@DisplayName("총 쪽수를 모르면 완독해도 진도는 건드리지 않는다")
	void finishingWithoutPageCountLeavesProgress() {
		Reading reading = want();
		reading.updateProgress(80);

		reading.changeStatus(ReadingStatus.FINISHED, LATER);

		assertThat(reading.getFinishedAt()).isEqualTo(LATER);
		assertThat(reading.getCurrentPage()).isEqualTo(80);
	}

	@Test
	@DisplayName("완독에서 나오면 완독일이 비워진다 — 재독의 시작")
	void leavingFinishedClearsFinishedAt() {
		Reading reading = want();
		reading.changeStatus(ReadingStatus.FINISHED, LATER);

		reading.changeStatus(ReadingStatus.READING, LATER);

		assertThat(reading.getFinishedAt()).isNull();
	}

	@Test
	@DisplayName("진도는 되돌아갈 수 있다")
	void progressCanGoBackwards() {
		Reading reading = want();
		reading.updateProgress(120);

		reading.updateProgress(80);

		assertThat(reading.getCurrentPage()).isEqualTo(80);
	}

	@Test
	@DisplayName("진도는 0 미만일 수 없고, 총 쪽수를 알면 그것을 넘을 수 없다")
	void progressStaysWithinBounds() {
		Reading reading = want();

		assertThatThrownBy(() -> reading.updateProgress(-1))
				.isInstanceOf(IllegalArgumentException.class);

		reading.updatePageCount(320);
		assertThatThrownBy(() -> reading.updateProgress(321))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("총 쪽수를 이미 읽은 쪽수보다 작게 줄일 수 없다")
	void pageCountCannotDropBelowProgress() {
		Reading reading = want();
		reading.updateProgress(200);

		assertThatThrownBy(() -> reading.updatePageCount(100))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("별점은 1~5다")
	void ratingIsOneToFive() {
		Reading reading = want();

		reading.updateRating(5);
		assertThat(reading.getRating()).isEqualTo(5);

		assertThatThrownBy(() -> reading.updateRating(0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> reading.updateRating(6))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("소유자만 자기 기록으로 인정한다")
	void knowsItsOwner() {
		Reading reading = want();

		assertThat(reading.ownedBy(USER)).isTrue();
		assertThat(reading.ownedBy(99L)).isFalse();
	}
}

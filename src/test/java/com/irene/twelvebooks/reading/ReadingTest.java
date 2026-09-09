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
		reading.applyProgress(320, 100);

		reading.changeStatus(ReadingStatus.FINISHED, LATER);

		assertThat(reading.getFinishedAt()).isEqualTo(LATER);
		assertThat(reading.getCurrentPage()).isEqualTo(320);
	}

	@Test
	@DisplayName("총 쪽수를 모르면 완독해도 진도는 건드리지 않는다")
	void finishingWithoutPageCountLeavesProgress() {
		Reading reading = want();
		reading.applyProgress(null, 80);

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
	@DisplayName("이미 완독인데 같은 상태를 다시 보내도 완독일은 그대로다")
	void refinishingKeepsTheOriginalFinishedAt() {
		Reading reading = want();
		reading.changeStatus(ReadingStatus.FINISHED, FIRST);

		reading.changeStatus(ReadingStatus.FINISHED, LATER);

		// 다 읽은 날은 한 번 정해지면 재시도로 바뀌지 않는다.
		assertThat(reading.getFinishedAt()).isEqualTo(FIRST);
	}

	@Test
	@DisplayName("완독 상태에서는 진도를 중간으로 되돌릴 수 없다")
	void finishedProgressStaysAtTheEnd() {
		Reading reading = want();
		reading.applyProgress(320, null);
		reading.changeStatus(ReadingStatus.FINISHED, LATER);

		reading.applyProgress(null, 100);

		assertThat(reading.getCurrentPage()).isEqualTo(320);
	}

	@Test
	@DisplayName("쪽수를 모른 채 완독한 뒤 쪽수를 넣으면 진도가 끝으로 간다")
	void fillingPageCountAfterFinishingCompletesProgress() {
		Reading reading = want();
		reading.changeStatus(ReadingStatus.FINISHED, LATER);

		reading.applyProgress(300, null);

		assertThat(reading.getCurrentPage()).isEqualTo(300);
	}

	@Test
	@DisplayName("총 쪽수와 진도를 함께 줄이면 최종 조합으로 판단한다")
	void validatesTheFinalCombination() {
		Reading reading = want();
		reading.applyProgress(320, 300);

		// 200쪽짜리로 고치면서 진도도 150으로 내린다 — 중간값(200 < 300)이 아니라 최종 조합을 본다
		reading.applyProgress(200, 150);

		assertThat(reading.getPageCount()).isEqualTo(200);
		assertThat(reading.getCurrentPage()).isEqualTo(150);
	}

	@Test
	@DisplayName("진도는 되돌아갈 수 있다")
	void progressCanGoBackwards() {
		Reading reading = want();
		reading.applyProgress(null, 120);

		reading.applyProgress(null, 80);

		assertThat(reading.getCurrentPage()).isEqualTo(80);
	}

	@Test
	@DisplayName("진도는 0 미만일 수 없고, 총 쪽수를 알면 그것을 넘을 수 없다")
	void progressStaysWithinBounds() {
		Reading reading = want();

		assertThatThrownBy(() -> reading.applyProgress(null, -1))
				.isInstanceOf(IllegalArgumentException.class);

		reading.applyProgress(320, null);
		assertThatThrownBy(() -> reading.applyProgress(null, 321))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("총 쪽수를 이미 읽은 쪽수보다 작게 줄일 수 없다")
	void pageCountCannotDropBelowProgress() {
		Reading reading = want();
		reading.applyProgress(null, 200);

		// 진도를 함께 내리지 않고 총 쪽수만 줄이면 최종 조합이 어긋난다
		assertThatThrownBy(() -> reading.applyProgress(100, null))
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

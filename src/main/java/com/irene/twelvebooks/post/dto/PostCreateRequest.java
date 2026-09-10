package com.irene.twelvebooks.post.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 감상평 작성. {@code bookId}만 필수다 — 읽은 구간도, 서재에 담아 뒀는지도 요구하지 않는다.
 * "기록의 문턱은 최대한 낮게"(spec.md §1.4)가 입력 조건에서 먼저 지켜져야 한다.
 *
 * <p>{@code readingId}는 받지 않는다. 서버가 (user, book)으로 찾아 붙이고 없으면 만든다 —
 * 클라이언트가 보내게 하면 남의 기록 id를 실어 보내는 경로가 열린다.
 */
public record PostCreateRequest(
		@NotNull Long bookId,
		@NotBlank @Size(max = 1000) String content,
		@Min(1) Integer fromPage,
		@Min(1) Integer toPage,
		Boolean spoiler) {

	/**
	 * 읽은 구간의 순서. 필드 하나만 봐서는 알 수 없는 규칙이라 둘을 함께 본다.
	 *
	 * <p>한쪽만 보낸 것은 막지 않는다. "90쪽까지 읽었다"만 아는 기록이 정상이고,
	 * 여기서 짝을 강요하면 문턱이 올라간다.
	 */
	@AssertTrue(message = "시작 쪽은 끝 쪽보다 뒤일 수 없습니다")
	public boolean isPageRangeOrdered() {
		return fromPage == null || toPage == null || fromPage <= toPage;
	}

	public boolean spoilerOrDefault() {
		return spoiler != null && spoiler;
	}
}

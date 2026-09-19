package com.irene.twelvebooks.common.support;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 페이징 응답. PK가 auto-increment이므로 {@code id desc}가 곧 최신순이고, 커서는 마지막 항목의 id다.
 *
 * <p>{@code @JsonInclude(NON_NULL)}인 이유는 마지막 페이지의 {@code nextCursor} 때문이다. 다른
 * 응답 DTO와 같은 규약을 따라 <b>키째 뺀다</b> — 같은 필드가 어떤 목록에서는 {@code null}로 오고
 * 어떤 목록에서는 없으면 클라이언트가 둘 다 다뤄야 한다. 다음 페이지가 있는지는
 * {@code hasNext}가 답하므로 {@code nextCursor}의 부재로 판단할 일도 없다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorPage<T>(List<T> items, Long nextCursor, boolean hasNext) {

	/**
	 * {@code size + 1}건을 조회한 결과를 페이지로 만든다. 초과분 1건은 다음 페이지의 존재를 알리는 용도로만 쓰고 버린다.
	 *
	 * @param size 한 페이지의 크기. 컨트롤러가 clamp하지만, 0 이하가 새어 들어오면 커서 계산이
	 *             조용히 깨지므로(빈 items에서 마지막 항목을 꺼내게 된다) 여기서 막는다.
	 */
	public static <T> CursorPage<T> of(List<T> rows, int size, Function<T, Long> cursorExtractor) {
		if (size <= 0) {
			throw new IllegalArgumentException("페이지 크기는 1 이상이어야 합니다: " + size);
		}
		boolean hasNext = rows.size() > size;
		List<T> items = hasNext ? List.copyOf(rows.subList(0, size)) : List.copyOf(rows);
		Long nextCursor = hasNext ? cursorExtractor.apply(items.get(items.size() - 1)) : null;
		return new CursorPage<>(items, nextCursor, hasNext);
	}
}

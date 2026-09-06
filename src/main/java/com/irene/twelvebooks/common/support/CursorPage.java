package com.irene.twelvebooks.common.support;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 페이징 응답. PK가 auto-increment이므로 {@code id desc}가 곧 최신순이고, 커서는 마지막 항목의 id다.
 */
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

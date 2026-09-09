package com.irene.twelvebooks.reading.dto;

import com.irene.twelvebooks.reading.ReadingStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 진도·상태·별점의 부분 수정. 프로필과 같은 규칙으로, <b>보내지 않은 필드(null)는 바꾸지 않는다</b>.
 *
 * <p>범위 검증을 여기와 엔티티 양쪽에 두는 이유는 역할이 달라서다. 여기서 걸리면 필드 이름이
 * 붙은 400이 되어 클라이언트가 무엇을 고쳐야 하는지 알고, 엔티티의 검증은 어떤 경로로 들어와도
 * 깨지지 않게 하는 마지막 방어선이다.
 */
public record ReadingUpdateRequest(
		ReadingStatus status,
		@Min(0) Integer currentPage,
		@Min(1) Integer pageCount,
		@Min(1) @Max(5) Integer rating) {
}

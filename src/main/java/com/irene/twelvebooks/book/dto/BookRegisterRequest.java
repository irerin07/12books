package com.irene.twelvebooks.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 검색 결과에서 고른 책을 내부에 확정할 때 보내는 것. 검색 응답 한 건을 그대로 되돌려보내면 된다.
 *
 * <p>{@code signature}까지 그대로 보내야 한다. 서버는 나머지 필드로 서명을 다시 계산해
 * 대조하므로, 한 글자라도 고쳐 보내면 등록이 거부된다.
 */
public record BookRegisterRequest(

		// ISBN-13은 숫자 13자리다. X는 ISBN-10의 체크문자라 여기 올 수 없다.
		@Pattern(regexp = "^$|^[0-9]{13}$", message = "ISBN13은 숫자 13자리여야 합니다")
		String isbn13,

		@NotBlank @Size(max = 500)
		String title,

		@NotBlank @Size(max = 500)
		String authors,

		@Size(max = 200)
		String publisher,

		@Size(max = 500)
		String thumbnailUrl,

		LocalDate publishedAt,

		@NotBlank
		String signature) {
}

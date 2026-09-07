package com.irene.twelvebooks.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 검색 결과에서 고른 책을 내부에 확정할 때 보내는 것. 검색 응답을 그대로 되돌려보내면 된다.
 */
public record BookRegisterRequest(

		@Pattern(regexp = "^$|^[0-9Xx]{13}$", message = "ISBN13은 13자리여야 합니다")
		String isbn13,

		@NotBlank @Size(max = 500)
		String title,

		@NotBlank @Size(max = 500)
		String authors,

		@Size(max = 200)
		String publisher,

		@Size(max = 500)
		String thumbnailUrl,

		LocalDate publishedAt) {
}

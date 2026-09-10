package com.irene.twelvebooks.post;

import com.irene.twelvebooks.post.dto.PostCreateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 읽은 구간 검증은 컨테이너 없이 확인할 수 있는 순수 규칙이다.
 *
 * <p>구간은 필드 하나만 봐서는 판단할 수 없어 {@code @AssertTrue}로 둘을 함께 본다.
 */
class PostCreateRequestTest {

	private static final Validator VALIDATOR;

	static {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			VALIDATOR = factory.getValidator();
		}
	}

	private static PostCreateRequest request(Integer fromPage, Integer toPage) {
		return new PostCreateRequest(1L, "읽었다", fromPage, toPage, null);
	}

	@Test
	@DisplayName("시작 쪽이 끝 쪽보다 뒤면 거부한다")
	void rejectsReversedRange() {
		assertThat(VALIDATOR.validate(request(90, 47))).isNotEmpty();
	}

	@Test
	@DisplayName("같은 쪽만 읽은 것도 정상이다")
	void acceptsSinglePage() {
		assertThat(VALIDATOR.validate(request(47, 47))).isEmpty();
	}

	@Test
	@DisplayName("구간은 선택이라 한쪽만 보내거나 아예 안 보내도 통과한다")
	void acceptsPartialRange() {
		assertThat(VALIDATOR.validate(request(null, null))).isEmpty();
		assertThat(VALIDATOR.validate(request(47, null))).isEmpty();
		assertThat(VALIDATOR.validate(request(null, 90))).isEmpty();
	}

	@Test
	@DisplayName("0쪽은 없다 — 읽은 구간은 1쪽부터")
	void rejectsZeroPage() {
		assertThat(VALIDATOR.validate(request(0, 90))).isNotEmpty();
	}

	@Test
	@DisplayName("본문은 비어 있을 수 없고 1000자를 넘을 수 없다")
	void guardsContent() {
		assertThat(VALIDATOR.validate(new PostCreateRequest(1L, "  ", 1, 2, null))).isNotEmpty();
		assertThat(VALIDATOR.validate(new PostCreateRequest(1L, "가".repeat(1001), 1, 2, null))).isNotEmpty();
		assertThat(VALIDATOR.validate(new PostCreateRequest(1L, "가".repeat(1000), 1, 2, null))).isEmpty();
	}
}

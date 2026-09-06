package com.irene.twelvebooks.common.error;

import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	@DisplayName("BusinessException은 ErrorCode가 가진 상태·코드·메시지로 변환된다")
	void businessExceptionUsesErrorCode() throws Exception {
		mockMvc.perform(get("/test/business"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
				.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT.getMessage()))
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	@DisplayName("검증 실패는 fieldErrors에 필드별 사유를 담아 400으로 내려간다")
	void validationFailurePopulatesFieldErrors() throws Exception {
		mockMvc.perform(post("/test/validated")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
	}

	@Test
	@DisplayName("예상 못 한 예외는 500으로 바꾸되 내부 메시지를 응답에 싣지 않는다")
	void unexpectedExceptionHidesInternalMessage() throws Exception {
		mockMvc.perform(get("/test/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_ERROR.getCode()))
				.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_ERROR.getMessage()));
	}

	@RestController
	static class TestController {

		@org.springframework.web.bind.annotation.GetMapping("/test/business")
		void business() {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}

		@org.springframework.web.bind.annotation.GetMapping("/test/boom")
		void boom() {
			throw new IllegalStateException("데이터소스 비밀번호가 틀렸습니다");
		}

		@PostMapping("/test/validated")
		void validated(@jakarta.validation.Valid @RequestBody TestRequest request) {
		}
	}

	record TestRequest(@NotBlank String title) {
	}
}

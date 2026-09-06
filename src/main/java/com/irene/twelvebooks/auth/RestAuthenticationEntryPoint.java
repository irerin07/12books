package com.irene.twelvebooks.auth;

import tools.jackson.databind.ObjectMapper;
import com.irene.twelvebooks.common.error.ErrorCode;
import com.irene.twelvebooks.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 인증 없이 보호된 경로에 닿았을 때의 응답. 기본 동작(빈 본문 또는 로그인 폼)을 쓰지 않고
 * 나머지 에러와 같은 {@code { code, message, fieldErrors }} 형식으로 맞춘다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
			throws IOException {
		ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(),
				new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of()));
	}
}

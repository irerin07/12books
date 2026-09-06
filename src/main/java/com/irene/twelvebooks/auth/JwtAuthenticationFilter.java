package com.irene.twelvebooks.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer 토큰이 유효하면 SecurityContext를 채우고, 아니면 <em>아무것도 하지 않고</em> 넘긴다.
 *
 * <p>여기서 예외를 던지지 않는 것이 핵심이다. 던지면 인증 실패와 서버 오류가 같은 경로로 섞인다.
 * 인증이 필요한 경로였다면 인가 단계에서 걸러져 {@link RestAuthenticationEntryPoint}가 401을 만든다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;

	public JwtAuthenticationFilter(JwtProvider jwtProvider) {
		this.jwtProvider = jwtProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		resolveToken(request)
				.flatMap(jwtProvider::parse)
				.ifPresent(principal -> {
					var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
					SecurityContextHolder.getContext().setAuthentication(authentication);
				});
		chain.doFilter(request, response);
	}

	private java.util.Optional<String> resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return java.util.Optional.empty();
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(token);
	}
}

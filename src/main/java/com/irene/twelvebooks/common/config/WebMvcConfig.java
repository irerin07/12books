package com.irene.twelvebooks.common.config;

import com.irene.twelvebooks.auth.AuthUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import com.irene.twelvebooks.common.ratelimit.RateLimitInterceptor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

	private final AuthUserArgumentResolver authUserArgumentResolver;
	private final RateLimitInterceptor rateLimitInterceptor;

	public WebMvcConfig(AuthUserArgumentResolver authUserArgumentResolver,
			RateLimitInterceptor rateLimitInterceptor) {
		this.authUserArgumentResolver = authUserArgumentResolver;
		this.rateLimitInterceptor = rateLimitInterceptor;
	}

	/**
	 * 요청 제한은 컨트롤러에 닿기 전에 판단한다. 어느 엔드포인트에 걸리는지는 경로 목록이
	 * 아니라 {@code @RateLimit}이 정한다 — 목록을 따로 두면 엔드포인트가 늘 때 빠뜨린다.
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitInterceptor);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(authUserArgumentResolver);
	}
}

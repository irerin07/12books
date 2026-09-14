package com.irene.twelvebooks.common.ratelimit;

import com.irene.twelvebooks.auth.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * {@link RateLimit}이 붙은 엔드포인트에서 세고 막는다.
 *
 * <p>컨트롤러 메서드에 도달하기 전에 판단하므로, 막힌 요청은 DB를 건드리지 않는다 —
 * 부하를 막으려는 장치가 부하를 만들면 뜻이 없다.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private final RateLimiter rateLimiter;

	public RateLimitInterceptor(RateLimiter rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		RateLimit limit = method.getMethodAnnotation(RateLimit.class);
		if (limit == null) {
			return true;
		}

		String bucket = limit.name() + ":" + subjectOf(limit.scope(), request);
		Duration window = Duration.ofSeconds(limit.windowSeconds());

		// 실패만 세는 경로는 여기서 올리지 않는다. 올리면 성공한 요청까지 한도를 깎는다.
		RateLimiter.Decision decision = limit.failuresOnly()
				? rateLimiter.peek(bucket, limit.limit(), window)
				: rateLimiter.check(bucket, limit.limit(), window);
		if (!decision.allowed()) {
			throw new RateLimitExceededException(decision.retryAfter());
		}
		return true;
	}

	/**
	 * 결과가 나온 뒤에 센다. {@code failuresOnly}인 경로만 해당한다.
	 *
	 * <p>4xx·5xx를 실패로 본다. 401(자격증명 불일치)이 주 대상이고, 400처럼 형식이 틀린
	 * 요청도 함께 센다 — 자동화된 시도는 보통 그쪽에서도 실수를 낸다.
	 */
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
			Object handler, Exception ex) {
		if (!(handler instanceof HandlerMethod method)) {
			return;
		}
		RateLimit limit = method.getMethodAnnotation(RateLimit.class);
		if (limit == null || !limit.failuresOnly() || response.getStatus() < 400) {
			return;
		}
		// 이미 막혀서 429가 된 요청은 또 세지 않는다. 세면 두드릴수록 창이 길어져
		// 사실상 영구 차단이 된다.
		if (response.getStatus() == 429) {
			return;
		}
		rateLimiter.record(limit.name() + ":" + subjectOf(limit.scope(), request),
				Duration.ofSeconds(limit.windowSeconds()));
	}

	/**
	 * 누구 몫으로 셀지.
	 *
	 * <p>{@code USER}인데 아직 인증 전이면 IP로 떨어진다. 그런 경로는 어차피 인증이 막지만,
	 * 여기서 사람이 없다고 통과시키면 <b>토큰 없이 두드리는 요청이 제한을 비껴간다</b>.
	 */
	private String subjectOf(RateLimit.Scope scope, HttpServletRequest request) {
		if (scope == RateLimit.Scope.USER) {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
				return "u:" + principal.userId();
			}
		}
		return "ip:" + request.getRemoteAddr();
	}
}

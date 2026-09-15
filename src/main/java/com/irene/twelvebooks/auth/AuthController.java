package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.auth.dto.LoginRequest;
import com.irene.twelvebooks.auth.dto.PasswordResetConfirmRequest;
import com.irene.twelvebooks.auth.dto.PasswordResetRequest;
import com.irene.twelvebooks.common.ratelimit.RateLimit;
import com.irene.twelvebooks.auth.dto.SignupRequest;
import com.irene.twelvebooks.auth.dto.SignupResponse;
import com.irene.twelvebooks.auth.dto.TokenResponse;
import com.irene.twelvebooks.common.error.BusinessException;
import com.irene.twelvebooks.common.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final RefreshCookies refreshCookies;

	public AuthController(AuthService authService, RefreshCookies refreshCookies) {
		this.authService = authService;
		this.refreshCookies = refreshCookies;
	}

	// 아직 누구인지 모르니 IP로 센다. 창을 길게 잡은 것은 가입이 드문 행동이어서다 —
	// 한 사무실에서 여러 명이 같은 날 가입하는 정도는 지나갈 수 있어야 한다.
	@RateLimit(name = "signup", limit = 20, windowSeconds = 600, scope = RateLimit.Scope.CLIENT)
	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.from(authService.signup(request)));
	}

	// 비밀번호 추측을 막는 자리다. 실패만 센다 — 성공까지 세면 막는 것이 없고(무차별
	// 대입은 실패로 이뤄진다) 정상 사용만 걸린다. 이메일 단위로 잠그지 않는 이유는 그것이
	// 곧 계정 열거 통로이기 때문이다 — "이 이메일은 잠겼다"가 "이 계정이 있다"를 알려준다.
	@RateLimit(name = "login", limit = 20, windowSeconds = 300, scope = RateLimit.Scope.CLIENT,
			failuresOnly = true)
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return tokenResponse(authService.login(request));
	}

	/**
	 * 재설정 요청. <b>언제나 204</b>다 — 계정이 있든 없든, 메일이 나갔든 실패했든.
	 *
	 * <p>IP로 센다. 이메일 단위로 막으면 그 자체가 계정 열거 통로가 되고("이 주소는 자주
	 * 요청돼 막혔다"), 남의 주소로 요청을 반복해 그 사람의 재설정을 막을 수도 있다.
	 */
	@RateLimit(name = "password-reset", limit = 10, windowSeconds = 600, scope = RateLimit.Scope.CLIENT)
	@PostMapping("/password-reset")
	public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		authService.requestPasswordReset(request);
		return ResponseEntity.noContent().build();
	}

	/**
	 * 토큰으로 비밀번호를 바꾼다. 토큰을 찍어 맞히는 시도를 막으려고 실패를 센다 —
	 * 성공까지 세면 한 사람이 여러 번 재설정하는 정상 사용이 걸린다.
	 */
	@RateLimit(name = "password-reset-confirm", limit = 20, windowSeconds = 600,
			scope = RateLimit.Scope.CLIENT, failuresOnly = true)
	@PostMapping("/password-reset/confirm")
	public ResponseEntity<Void> confirmPasswordReset(
			@Valid @RequestBody PasswordResetConfirmRequest request) {
		authService.confirmPasswordReset(request);
		return ResponseEntity.noContent().build();
	}

	/** POST 전용이다. GET이면 링크 한 번으로 재발급이 일어난다. */
	@PostMapping("/reissue")
	public ResponseEntity<TokenResponse> reissue(
			@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
		if (refreshToken == null) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
		return tokenResponse(authService.reissue(refreshToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
		authService.logout(refreshToken);

		HttpHeaders headers = new HttpHeaders();
		refreshCookies.clear(headers);
		return ResponseEntity.noContent().headers(headers).build();
	}

	private ResponseEntity<TokenResponse> tokenResponse(AuthService.Tokens tokens) {
		HttpHeaders headers = new HttpHeaders();
		refreshCookies.set(headers, tokens.refreshToken());
		return ResponseEntity.ok().headers(headers).body(new TokenResponse(tokens.accessToken()));
	}
}

package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.auth.dto.LoginRequest;
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

	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.from(authService.signup(request)));
	}

	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return tokenResponse(authService.login(request));
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

package com.irene.twelvebooks.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 불투명 토큰을 만들고 해시한다. refresh 세션과 비밀번호 재설정이 같은 규칙을 쓴다.
 *
 * <p><b>저장하는 것은 언제나 해시다.</b> Redis 덤프가 그대로 세션 탈취나 계정 탈취가 되지
 * 않게 한다. 토큰은 이미 고엔트로피 난수라 솔트·키 스트레칭이 필요 없다 — BCrypt를 쓰면
 * 느리기만 하고 얻는 것이 없다(사전 공격 대상이 아니다).
 *
 * <p>둘이 같은 코드를 나눠 쓰는 이유는 여기가 틀리면 조용히 안전하지 않기 때문이다. 복사본이
 * 둘이면 한쪽만 고쳐지는 날이 온다.
 */
final class TokenSecrets {

	/** 128비트로도 충분하지만, 오가는 값이라 여유를 둔다. */
	private static final int TOKEN_BYTES = 32;

	private static final SecureRandom RANDOM = new SecureRandom();

	private TokenSecrets() {
	}

	/** URL에 그대로 실을 수 있는 난수 토큰. 재설정 링크의 질의 문자열로도 나간다. */
	static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}
}

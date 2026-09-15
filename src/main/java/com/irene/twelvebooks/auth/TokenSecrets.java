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

	/**
	 * 지금 자격증명의 <b>지문.</b> 세션이 어느 비밀번호로 만들어졌는지를 가리킨다.
	 *
	 * <p>따로 버전 컬럼을 두지 않는 이유가 있다. 해시와 번호를 나눠 두면 (1) 둘을 따로 읽게
	 * 되어 그 사이에 재설정이 끝나면 <b>옛 해시에 새 번호가 붙고</b>, (2) 번호를 올리는 시점과
	 * 동시 변경을 따로 관리해야 한다. 지문은 <b>그 행의 현재 해시에 대한 순수 함수</b>라
	 * 둘 다 생기지 않는다 — 검증한 해시에서 뽑으면 언제나 짝이 맞는다.
	 *
	 * <p>BCrypt는 바꿀 때마다 새 솔트를 뽑으므로 같은 비밀번호로 되돌려도 지문이 달라진다.
	 * 자르지 않고 전체를 쓴다 — 길이를 정하는 결정 자체를 만들지 않는다.
	 *
	 * <p>저장되는 것은 해시가 아니라 단방향 요약이다. <b>원문 자격증명을 추가로 노출하지 않는
	 * 비교용 값</b>이라고 보는 것이 정확하다(되짚으려면 솔트를 포함한 BCrypt 해시 전체를
	 * 맞혀야 한다).
	 */
	static String fingerprintOf(String passwordHash) {
		return hash(passwordHash);
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

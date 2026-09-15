package com.irene.twelvebooks.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송자 접속 정보.
 *
 * <p>키가 비어 있어도 앱은 뜬다. 메일이 안 나가는 것과 서비스가 안 도는 것은 다른 사건이고,
 * 글쓰기·읽기는 메일과 무관하게 돌아야 한다. 대신 <b>발송은 조용히 실패한다</b> — 재설정
 * 요청이 언제나 204라서(계정 유무를 흘리지 않으려고) 로그의 경고가 유일한 신호다.
 */
@ConfigurationProperties(prefix = "twelvebooks.mail.resend")
public record ResendProperties(String apiKey) {

	public ResendProperties {
		apiKey = (apiKey == null) ? "" : apiKey;
	}
}

package com.irene.twelvebooks.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 비밀번호 재설정 링크의 조건.
 *
 * <p>셋 다 설정으로 뺀 이유가 있다.
 *
 * <ul>
 *   <li>{@code ttl} — 오래 살려 두면 메일함을 한 번 들여다본 사람이 계정을 가져간다. 짧으면
 *       다시 요청하면 그만이라 기울기가 한쪽으로 분명하다.</li>
 *   <li>{@code linkBase} — 링크는 <b>프런트 화면</b>을 가리켜야 한다. 사용자는 새 비밀번호를
 *       입력할 곳으로 가야지 JSON을 보면 안 된다. 그 주소는 배포마다 다르다.</li>
 *   <li>{@code from} — 아무 주소나 쓸 수 없다. SMTP 서버가 허용한 계정·도메인이어야 하고,
 *       아니면 거부되거나 스팸으로 분류된다. 발송자를 바꾸면 이 값도 함께 바뀐다.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "twelvebooks.auth.password-reset")
public record PasswordResetProperties(Duration ttl, String linkBase, String from) {

	public PasswordResetProperties {
		ttl = (ttl == null) ? Duration.ofMinutes(30) : ttl;
		linkBase = (linkBase == null || linkBase.isBlank())
				? "http://localhost:3000/reset-password" : linkBase;
		from = (from == null || from.isBlank()) ? "no-reply@12books.local" : from;
	}

	/** 메일에 실을 링크. 토큰은 원문 그대로 실린다 — 서버에는 해시만 남는다. */
	public String linkFor(String rawToken) {
		return linkBase + "?token=" + rawToken;
	}
}

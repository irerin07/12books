package com.irene.twelvebooks.auth;

import com.irene.twelvebooks.common.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 재설정 링크를 메일로 보낸다.
 *
 * <p><b>요청 스레드에서 보내지 않는다.</b> SMTP 왕복은 수백 ms에서 몇 초까지 가고, 그것을
 * 기다리면 응답 시간이 메일 서버 상태에 묶인다. 더 중요한 이유가 있다 — 발송 실패가 응답을
 * 바꾸면 <b>"이 주소는 계정이 있어서 보내려다 실패했다"가 드러난다.</b> 요청은 언제나 204여야
 * 하고, 그 규칙은 발송과 응답이 분리돼야 지켜진다.
 *
 * <p>평문으로 보낸다. 링크 하나를 전하는 데 HTML이 필요 없고, 평문이 스팸 판정에도 유리하다.
 */
@Component
public class PasswordResetMailer {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

	private final JavaMailSender mailSender;
	private final PasswordResetProperties properties;

	public PasswordResetMailer(JavaMailSender mailSender, PasswordResetProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Async(AsyncConfig.MAIL_EXECUTOR)
	public void send(String email, String rawToken) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.from());
		message.setTo(email);
		message.setSubject("[12books] 비밀번호 재설정");
		message.setText("""
				아래 링크에서 새 비밀번호를 정해 주세요.

				%s

				이 링크는 %d분 뒤에 만료되고 한 번만 쓸 수 있습니다.
				본인이 요청한 것이 아니라면 이 메일을 무시하세요 — 비밀번호는 그대로입니다.
				"""
				.formatted(properties.linkFor(rawToken), properties.ttl().toMinutes()));

		try {
			mailSender.send(message);
		}
		catch (MailException e) {
			// 여기서 터져도 사용자에게는 이미 204를 보냈다. 다시 요청하면 새 링크가 나가므로
			// 재시도를 붙이지 않는다 — 붙이면 메일 서버가 흔들릴 때 같은 주소로 여러 통이 간다.
			// 주소는 남기지 않는다. 로그에 남기는 순간 "누가 비밀번호를 잊었는지"가 함께 남는다.
			log.warn("비밀번호 재설정 메일을 보내지 못했습니다", e);
		}
	}
}

package com.irene.twelvebooks.auth;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resend로 메일을 보낸다.
 *
 * <p>SMTP 대신 이쪽을 고른 값은 <b>실패했을 때 남는 것</b>이다. SMTP는 거절 코드 한 줄이지만
 * 여기서는 상태 코드·오류 이름·응답 본문이 오고, 성공하면 메시지 id가 남아 나중에 "그 메일이
 * 어떻게 됐나"를 물을 수 있다. 대가는 발송자가 코드에 박히는 것이다.
 *
 * <p>예외를 밖으로 던진다. 무엇을 할지는 부르는 쪽이 정한다 — 비밀번호 재설정에서는
 * 삼켜야 한다(실패를 응답에 실으면 계정 유무가 드러난다).
 */
@Component
public class ResendMailClient implements ResetMailSender {

	private static final Logger log = LoggerFactory.getLogger(ResendMailClient.class);

	private final Resend resend;
	private final String from;

	public ResendMailClient(ResendProperties properties, PasswordResetProperties mailProperties) {
		this.resend = new Resend(properties.apiKey());
		this.from = mailProperties.from();
	}

	@Override
	public void send(String to, String subject, String text) {
		CreateEmailOptions options = CreateEmailOptions.builder()
				.from(from)
				.to(to)
				.subject(subject)
				.text(text)
				.build();
		try {
			CreateEmailResponse sent = resend.emails().send(options);
			// 받는 사람은 남기지 않는다. 남기는 순간 "누가 비밀번호를 잊었는지"가 로그에 쌓인다.
			log.info("메일을 보냈습니다: id={}", sent.getId());
		}
		catch (ResendException e) {
			throw new MailSendFailed(e);
		}
	}

	/** 발송 실패. 원인(상태 코드·오류 이름)은 감싼 예외에 들어 있다. */
	public static class MailSendFailed extends RuntimeException {

		MailSendFailed(ResendException cause) {
			super("메일 발송에 실패했습니다: status=%s, error=%s"
					.formatted(cause.getStatusCode(), cause.getErrorName()), cause);
		}
	}
}

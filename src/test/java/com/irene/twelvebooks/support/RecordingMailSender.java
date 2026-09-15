package com.irene.twelvebooks.support;

import com.irene.twelvebooks.auth.ResetMailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 나간 메일을 받아 두는 대역.
 *
 * <p>SDK는 자기 HTTP 클라이언트를 직접 들고 있어 바깥에서 갈아끼울 수 없다. 그래서 검증이
 * <b>우리 경계</b>에서 끝난다 — 무엇을 보내려 했는지는 보지만, 그것이 실제로 도착하는지는
 * 여기서 알 수 없다. SMTP였다면 테스트 안에 진짜 서버를 띄워 그것까지 봤다.
 * 그 확인은 {@code external-apis.md}의 실측이 맡는다.
 *
 * <p>발송이 비동기라 큐로 받는다. 테스트는 {@link #next()}로 기다린다.
 */
public class RecordingMailSender implements ResetMailSender {

	/** 한 통. 받는 사람·제목·본문. */
	public record Sent(String to, String subject, String text) {
	}

	private final BlockingQueue<Sent> sent = new LinkedBlockingQueue<>();

	@Override
	public void send(String to, String subject, String text) {
		sent.add(new Sent(to, subject, text));
	}

	/** 다음 한 통을 기다린다. 5초 안에 안 오면 빈 값이다 — 안 나간 것을 확인할 때도 쓴다. */
	public Sent next() throws InterruptedException {
		return sent.poll(5, TimeUnit.SECONDS);
	}

	/** 짧게만 기다린다. "나가지 않았다"를 확인하는 자리에서 5초를 버리지 않으려고. */
	public Sent nothingSoon() throws InterruptedException {
		return sent.poll(500, TimeUnit.MILLISECONDS);
	}

	public void clear() {
		sent.clear();
	}

	@TestConfiguration
	public static class Config {

		@Bean
		@Primary
		public RecordingMailSender recordingMailSender() {
			return new RecordingMailSender();
		}
	}
}

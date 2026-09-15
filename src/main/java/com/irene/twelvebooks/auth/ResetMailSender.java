package com.irene.twelvebooks.auth;

/**
 * 메일 한 통을 바깥으로 내보낸다.
 *
 * <p>인터페이스를 두는 이유는 <b>테스트가 발송자를 붙잡을 자리</b>가 필요해서다. SDK는 자기
 * HTTP 클라이언트를 직접 들고 있어 바깥에서 갈아끼울 수 없다(생성자가 공개돼 있지 않다).
 * SMTP였다면 테스트 안에 진짜 서버를 띄워 "실제로 나갔다"까지 봤겠지만, SDK를 쓰기로 한
 * 이상 검증은 <b>여기까지</b>다 — 무엇을 보내려 했는지는 확인하고, 실제 전달은 실측으로 본다.
 */
public interface ResetMailSender {

	void send(String to, String subject, String text);
}

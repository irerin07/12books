package com.irene.twelvebooks.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 엔드포인트를 한 창 안에 몇 번까지 부를 수 있는지.
 *
 * <p>설정 파일이 아니라 엔드포인트 위에 붙인다. 한도는 그 동작이 무엇인지와 붙어 있는
 * 값이라, 따로 두면 엔드포인트를 읽는 사람이 제한이 걸려 있다는 사실조차 모른다.
 *
 * <p>세는 단위는 {@link Scope}가 정한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

	/**
	 * 무엇을 세는 버킷인지. 키에 들어가므로 <b>엔드포인트마다 달라야</b> 한다 — 같은 이름을
	 * 쓰면 두 동작이 서로의 몫을 깎아먹는다.
	 */
	String name();

	/** 한 창 안에 허용할 횟수. */
	int limit();

	/** 창의 길이(초). 이 시간이 지나면 처음부터 다시 센다. */
	int windowSeconds();

	/** 누구를 기준으로 셀지. */
	Scope scope();

	/**
	 * 실패한 요청만 셀지.
	 *
	 * <p>로그인이 그렇다. <b>성공까지 세면 막는 것이 없다</b> — 무차별 대입은 실패로
	 * 이뤄지고, 공격자에게 성공은 이미 끝난 뒤다. 반대로 성공을 세면 정상 사용이 걸린다:
	 * 한 곳에서 여러 계정으로 로그인하는 도구가 곧장 막힌다.
	 *
	 * <p>쓰기에는 켜지 않는다. 거기서 막으려는 것은 "틀린 요청"이 아니라 "너무 많은 글"이라
	 * 성공한 요청도 세야 한다.
	 */
	boolean failuresOnly() default false;

	enum Scope {

		/**
		 * 부르는 쪽의 IP. 아직 누구인지 모르는 인증 전 경로에 쓴다.
		 *
		 * <p>프록시 뒤에서는 원래 IP를 볼 수 있어야 한다 — 안 그러면 모든 요청이 프록시 한
		 * 주소로 보여 <b>전체 사용자가 한 버킷을 나눠 쓴다</b>. 그 설정은 application.yaml의
		 * {@code server.forward-headers-strategy}에 있다.
		 */
		CLIENT,

		/**
		 * 로그인한 사람. 인증 뒤의 쓰기에 쓴다.
		 *
		 * <p>이쪽을 IP로 세면 같은 사무실·같은 통신사 뒤에 있는 사람들이 서로의 몫을
		 * 깎아먹는다. 한 명이 몰아 쓰면 나머지가 글을 못 쓴다.
		 */
		USER
	}
}

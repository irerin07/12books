package com.irene.twelvebooks.user;

/**
 * 사람의 권한.
 *
 * <p>둘뿐이다. 신고 처리 말고 운영자만 할 수 있는 일이 아직 없어서, 지금 역할을 잘게 나누면
 * 쓰이지 않는 구분만 생긴다.
 */
public enum UserRole {

	USER,
	ADMIN
}

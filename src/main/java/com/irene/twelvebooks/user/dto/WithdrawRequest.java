package com.irene.twelvebooks.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 탈퇴. 비밀번호를 다시 받는다.
 *
 * <p>access 토큰만으로 받지 않는 이유는 <b>되돌릴 수 없는 일</b>이기 때문이다. 자리를 비운
 * 사이 남이 브라우저를 만지거나 토큰이 새면, 확인 한 번 없이 계정이 사라진다.
 */
public record WithdrawRequest(@NotBlank String password) {
}

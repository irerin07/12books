package com.irene.twelvebooks.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자 수가 아니라 UTF-8 바이트 길이를 제한한다. 한글은 글자당 3바이트라
 * {@code @Size}로는 바이트 상한이 있는 값(BCrypt 입력 등)을 지킬 수 없다.
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ByteSizeValidator.class)
public @interface ByteSize {

	String message() default "허용된 바이트 길이를 넘었습니다";

	int max();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}

package com.irene.twelvebooks.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class ByteSizeValidator implements ConstraintValidator<ByteSize, String> {

	private int max;

	@Override
	public void initialize(ByteSize constraint) {
		this.max = constraint.max();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		// null은 @NotBlank가 볼 몫이다.
		return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
	}
}

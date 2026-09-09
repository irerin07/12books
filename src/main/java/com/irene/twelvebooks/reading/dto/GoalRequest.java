package com.irene.twelvebooks.reading.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GoalRequest(@NotNull @Min(1) @Max(1000) Integer targetCount) {
}

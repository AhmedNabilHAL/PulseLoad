package com.pulseload.feature.tests.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record StageDto(

        @NotNull
        @Positive
        Integer durationSeconds,

        @NotNull
        @PositiveOrZero
        Integer target
) {
}

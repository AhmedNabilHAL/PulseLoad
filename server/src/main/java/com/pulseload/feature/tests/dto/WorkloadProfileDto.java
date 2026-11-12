package com.pulseload.feature.tests.dto;

import com.pulseload.model.LoadModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record WorkloadProfileDto(

        @NotNull
        LoadModel loadModel,

        @NotNull
        @PositiveOrZero
        Integer startTarget,

        @NotNull
        @Positive
        Integer maxConcurrency,

        @NotEmpty
        List<@Valid StageDto> stages
) {
}

package com.pulseload.service.tests.dto;

import com.pulseload.model.HttpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateTestDto(

        @NotBlank
        @Size(max = 150)
        String testName,

        @NotBlank
        @URL
        String targetUrl,

        @NotNull
        HttpMethod httpMethod,

        @NotNull
        @Positive
        Integer durationInSeconds,

        @NotNull
        @Positive
        Integer requestsPerSec
) {
}

package com.pulseload.feature.tests.dto;

import com.pulseload.model.HttpMethod;

import java.util.UUID;

public record TestDto(
        UUID id,
        String testName,
        String targetUrl,
        HttpMethod httpMethod,
        WorkloadProfileDto workloadProfile
) {
}

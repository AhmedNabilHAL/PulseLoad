package com.pulseload.service.tests.dto;

import com.pulseload.model.HttpMethod;

import java.util.UUID;

public record TestDto(
        UUID id,
        String testName,
        String targetUrl,
        HttpMethod httpMethod,
        Integer durationInSeconds,
        Integer requestsPerSec
) {
}

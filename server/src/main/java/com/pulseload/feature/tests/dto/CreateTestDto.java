package com.pulseload.feature.tests.dto;

import com.pulseload.model.HttpMethod;

// EXERCISE (request validation): this request body is accepted with no constraints, so blank names,
// malformed URLs, and invalid workload values reach the service. Add Jakarta Bean Validation
// annotations (and cascade into the workload profile) so bad requests are rejected with 400.
public record CreateTestDto(

        String testName,

        String targetUrl,

        HttpMethod httpMethod,

        WorkloadProfileDto workloadProfile
) {
}

package com.pulseload.feature.tests.service;

import com.pulseload.feature.tests.dto.CreateTestDto;
import com.pulseload.feature.tests.dto.TestDto;

import java.util.List;
import java.util.UUID;

public interface TestService {

    List<TestDto> getAllTests();
    TestDto getTestById(UUID id);
    TestDto createTest(CreateTestDto createTestDto);
    void updateTest(UUID id, CreateTestDto createTestDto);
    void deleteTest(UUID id);

}

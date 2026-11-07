package com.pulseload.service.tests.service;

import com.pulseload.service.tests.dto.CreateTestDto;
import com.pulseload.service.tests.dto.TestDto;

import java.util.List;
import java.util.UUID;

public interface TestService {

    List<TestDto> getAllTests();
    TestDto getTestById(UUID id);
    TestDto createTest(CreateTestDto createTestDto);
    void updateTest(UUID id, CreateTestDto createTestDto);
    void deleteTest(UUID id);

}

package com.pulseload.feature.tests.service;

import com.pulseload.exception.TestNotFoundException;
import com.pulseload.model.Test;
import com.pulseload.feature.tests.data.TestRepository;
import com.pulseload.feature.tests.dto.CreateTestDto;
import com.pulseload.feature.tests.dto.TestDto;
import com.pulseload.feature.tests.mapper.TestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestServiceImpl implements TestService {

    private final TestRepository testRepository;
    private final TestMapper testMapper;

    @Override
    public List<TestDto> getAllTests() {
        return testMapper.toDtos(testRepository.findAll());
    }

    @Override
    public TestDto getTestById(UUID id) {
        return testMapper.toDto(findTestOrThrow(id));
    }

    @Override
    @Transactional
    public TestDto createTest(CreateTestDto createTestDto) {
        Test test = testMapper.toEntity(createTestDto);
        return testMapper.toDto(testRepository.save(test));
    }

    @Override
    @Transactional
    public void updateTest(UUID id, CreateTestDto createTestDto) {
        Test test = findTestOrThrow(id);
        testMapper.updateEntity(createTestDto, test);
    }

    @Override
    @Transactional
    public void deleteTest(UUID id) {
        if (!testRepository.existsById(id)) {
            throw new TestNotFoundException(id);
        }
        testRepository.deleteById(id);
    }

    private Test findTestOrThrow(UUID id) {
        return testRepository.findById(id)
                .orElseThrow(() -> new TestNotFoundException(id));
    }
}

package com.pulseload.feature.runs.mapper;

import com.pulseload.model.TestRun;
import com.pulseload.model.TestRunMetric;
import com.pulseload.feature.runs.dto.TestRunDto;
import com.pulseload.feature.runs.dto.TestRunMetricDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TestRunMapper {

    @Mapping(target = "testId", source = "test.id")
    TestRunDto toDto(TestRun testRun);

    List<TestRunDto> toDtos(List<TestRun> testRuns);

    TestRunMetricDto toDto(TestRunMetric metric);

    List<TestRunMetricDto> toMetricDtos(List<TestRunMetric> metrics);
}

package com.pulseload.service.tests.mapper;

import com.pulseload.model.Test;
import com.pulseload.service.tests.dto.CreateTestDto;
import com.pulseload.service.tests.dto.TestDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TestMapper {

    TestDto toDto(Test test);

    List<TestDto> toDtos(List<Test> tests);

    @Mapping(target = "id", ignore = true)
    Test toEntity(CreateTestDto createTestDto);

    @Mapping(target = "id", ignore = true)
    void updateEntity(CreateTestDto createTestDto, @MappingTarget Test test);
}

package com.pulseload.workshop;

import com.pulseload.exception.InvalidTestConfigurationException;
import com.pulseload.feature.tests.data.TestRepository;
import com.pulseload.feature.tests.dto.CreateTestDto;
import com.pulseload.feature.tests.dto.StageDto;
import com.pulseload.feature.tests.dto.WorkloadProfileDto;
import com.pulseload.feature.tests.mapper.TestMapper;
import com.pulseload.feature.tests.service.TestServiceImpl;
import com.pulseload.model.HttpMethod;
import com.pulseload.model.LoadModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Acceptance test for exercise 1B (business validation). A closed-model
 * workload can never drive
 * more concurrent virtual users than its own concurrency cap, so creating a
 * test whose peak stage
 * target exceeds {@code maxConcurrency} must be rejected in the service layer —
 * a rule that request
 * (annotation) validation cannot express. Fails until the business rule is
 * implemented.
 */
@Tag("acceptance")
class ValidationBusinessAcceptanceTest {

  @Test
  void rejectsWorkloadWhosePeakTargetExceedsMaxConcurrency() {
    TestServiceImpl service = new TestServiceImpl(mock(TestRepository.class), mock(TestMapper.class));

    CreateTestDto overProvisioned = new CreateTestDto(
        "Over-provisioned",
        "https://example.com",
        HttpMethod.GET,
        new WorkloadProfileDto(LoadModel.CLOSED, 5, 5, List.of(new StageDto(10, 20))));

    assertThatThrownBy(() -> service.createTest(overProvisioned))
        .isInstanceOf(InvalidTestConfigurationException.class);
  }
}

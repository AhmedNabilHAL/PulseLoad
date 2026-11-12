package com.pulseload.feature.runs.controller;

import com.pulseload.exception.RunConflictException;
import com.pulseload.exception.RunNotFoundException;
import com.pulseload.model.LoadModel;
import com.pulseload.model.TestRunStatus;
import com.pulseload.feature.runs.dto.RunSummaryDto;
import com.pulseload.feature.runs.dto.TestRunDto;
import com.pulseload.feature.runs.service.TestRunService;
import com.pulseload.feature.tests.dto.StageDto;
import com.pulseload.feature.tests.dto.WorkloadProfileDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RunController.class)
class RunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestRunService testRunService;

    @Test
    void startRunReturns202WithLocationHeader() throws Exception {
        UUID testId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        TestRunDto dto = runDto(runId, TestRunStatus.PENDING);
        when(testRunService.startRun(testId)).thenReturn(dto);

        mockMvc.perform(post("/tests/{testId}/runs", testId))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/runs/" + runId)))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void startRunReturns409WhenTooManyRunsAreActive() throws Exception {
        UUID testId = UUID.randomUUID();
        when(testRunService.startRun(testId)).thenThrow(new RunConflictException("Too many runs in progress"));

        mockMvc.perform(post("/tests/{testId}/runs", testId))
                .andExpect(status().isConflict());
    }

    @Test
    void getRunByIdReturns404WhenMissing() throws Exception {
        UUID runId = UUID.randomUUID();
        when(testRunService.getRunById(runId)).thenThrow(new RunNotFoundException(runId));

        mockMvc.perform(get("/runs/{runId}", runId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRunByIdReturnsTheRun() throws Exception {
        UUID runId = UUID.randomUUID();
        when(testRunService.getRunById(runId)).thenReturn(runDto(runId, TestRunStatus.RUNNING));

        mockMvc.perform(get("/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void stopRunReturns204() throws Exception {
        UUID runId = UUID.randomUUID();

        mockMvc.perform(post("/runs/{runId}/stop", runId))
                .andExpect(status().isNoContent());

        verify(testRunService).stopRun(runId);
    }

    @Test
    void getRunsForTestReturnsTheList() throws Exception {
        UUID testId = UUID.randomUUID();
        when(testRunService.getRunsForTest(testId)).thenReturn(List.of(runDto(UUID.randomUUID(), TestRunStatus.COMPLETED)));

        mockMvc.perform(get("/tests/{testId}/runs", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private static TestRunDto runDto(UUID runId, TestRunStatus status) {
        WorkloadProfileDto config = new WorkloadProfileDto(LoadModel.OPEN, 10, 100, List.of(new StageDto(60, 100)));
        RunSummaryDto summary = status == TestRunStatus.COMPLETED
                ? new RunSummaryDto(100L, 98L, 2L, null, null, null, 100.0, 98.0, 0.3, 1_000_000L, false, List.of())
                : null;
        return new TestRunDto(runId, UUID.randomUUID(), status, config, Instant.now(), null, null, summary);
    }
}

package com.pulseload.feature.tests.controller;

import com.pulseload.feature.tests.dto.CreateTestDto;
import com.pulseload.feature.tests.dto.StageDto;
import com.pulseload.feature.tests.dto.WorkloadProfileDto;
import com.pulseload.feature.tests.service.TestService;
import com.pulseload.model.HttpMethod;
import com.pulseload.model.LoadModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TestController.class)
class TestControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private TestService testService;

    @Test
    void rejectsTestWithNoWorkloadStages() throws Exception {
        CreateTestDto invalid = new CreateTestDto(
                "Checkout API",
                "https://example.com/checkout",
                HttpMethod.GET,
                new WorkloadProfileDto(LoadModel.OPEN, 10, 100, List.of(new StageDto(-5, -1)))
        );

        mockMvc.perform(post("/tests")
                        .contentType("application/json")
                        .content(jsonMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }
}

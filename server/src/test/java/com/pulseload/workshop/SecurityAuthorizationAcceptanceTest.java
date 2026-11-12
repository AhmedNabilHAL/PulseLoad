package com.pulseload.workshop;

import com.pulseload.config.SecurityConfig;
import com.pulseload.feature.tests.controller.TestController;
import com.pulseload.feature.tests.service.TestService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for the security topic.
 *
 * <p>
 * 2A (endpoint authorization): a VIEWER may read but must not create tests —
 * the create
 * endpoint requires an elevated role via the security filter chain.
 *
 * <p>
 * 2B (method-level authorization): deleting a test is destructive and must
 * require ADMIN,
 * enforced with method security ({@code @PreAuthorize}) even though an OPERATOR
 * may perform other
 * mutations. Each fails until its authorization rule is in place.
 */
@WebMvcTest(TestController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
@Tag("acceptance")
class SecurityAuthorizationAcceptanceTest {

  private static final String VALID_BODY = """
      {"testName":"Checkout","targetUrl":"https://example.com","httpMethod":"GET",
       "workloadProfile":{"loadModel":"CLOSED","startTarget":5,"maxConcurrency":5,
       "stages":[{"durationSeconds":10,"target":5}]}}""";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TestService testService;

  @Test
  @WithMockUser(roles = "VIEWER")
  void viewerCannotCreateTests() throws Exception {
    mockMvc.perform(post("/tests").with(csrf()).contentType("application/json").content(VALID_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "OPERATOR")
  void operatorCannotDeleteTests() throws Exception {
    mockMvc.perform(delete("/tests/{id}", UUID.randomUUID()).with(csrf()))
        .andExpect(status().isForbidden());
  }
}

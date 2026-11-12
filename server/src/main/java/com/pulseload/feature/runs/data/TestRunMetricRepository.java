package com.pulseload.feature.runs.data;

import com.pulseload.model.TestRun;
import com.pulseload.model.TestRunMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestRunMetricRepository extends JpaRepository<TestRunMetric, UUID> {

    List<TestRunMetric> findByTestRunOrderByElapsedSecondsAsc(TestRun testRun);
}

package com.pulseload.feature.runs.data;

import com.pulseload.model.Test;
import com.pulseload.model.TestRun;
import com.pulseload.model.TestRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

    List<TestRun> findByTest(Test test);

    List<TestRun> findByStatusIn(List<TestRunStatus> statuses);
}

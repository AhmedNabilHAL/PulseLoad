package com.pulseload.feature.runs.service;

import com.pulseload.engine.api.RunSpec;
import com.pulseload.exception.RunConflictException;
import com.pulseload.exception.RunNotFoundException;
import com.pulseload.exception.TestNotFoundException;
import com.pulseload.model.Test;
import com.pulseload.model.TestRun;
import com.pulseload.model.TestRunStatus;
import com.pulseload.feature.runs.data.TestRunMetricRepository;
import com.pulseload.feature.runs.data.TestRunRepository;
import com.pulseload.feature.runs.dto.TestRunDto;
import com.pulseload.feature.runs.dto.TestRunMetricDto;
import com.pulseload.feature.runs.execution.LoadGenerator;
import com.pulseload.feature.runs.mapper.TestRunMapper;
import com.pulseload.feature.tests.data.TestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class TestRunServiceImpl implements TestRunService {

    private final TestRepository testRepository;
    private final TestRunRepository testRunRepository;
    private final TestRunMetricRepository testRunMetricRepository;
    private final TestRunMapper testRunMapper;
    private final LoadGenerator loadGenerator;
    private final RunCoordinator runCoordinator;
    private final RunPersistence runPersistence;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.runs.request-timeout:30s}")
    private final Duration requestTimeout;

    @Override
    @Transactional
    public TestRunDto startRun(UUID testId) {
        if (!runCoordinator.tryReserve()) {
            throw new RunConflictException("Too many runs in progress; try again once an active run finishes");
        }

        try {
            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new TestNotFoundException(testId));

            TestRun run = new TestRun();
            run.setTest(test);
            run.setStatus(TestRunStatus.PENDING);
            run.setConfigSnapshot(test.getWorkloadProfile());
            run.setCreatedAt(Instant.now());

            TestRun saved = testRunRepository.save(run);
            try (var ignored = MDC.putCloseable("runId", saved.getId().toString())) {
                log.info("Run created, awaiting engine start: testId={}", testId);
            }
            eventPublisher.publishEvent(new RunCreatedEvent(saved.getId()));

            return testRunMapper.toDto(saved);
        } catch (RuntimeException e) {
            runCoordinator.release();
            throw e;
        }
    }

    @Override
    public List<TestRunDto> getRunsForTest(UUID testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new TestNotFoundException(testId));
        return testRunMapper.toDtos(testRunRepository.findByTest(test));
    }

    @Override
    public TestRunDto getRunById(UUID runId) {
        return testRunMapper.toDto(findRunOrThrow(runId));
    }

    @Override
    public List<TestRunMetricDto> getMetrics(UUID runId) {
        TestRun run = findRunOrThrow(runId);
        return testRunMapper.toMetricDtos(testRunMetricRepository.findByTestRunOrderByElapsedSecondsAsc(run));
    }

    @Override
    public void stopRun(UUID runId) {
        findRunOrThrow(runId);
        loadGenerator.stop(runId);
    }

    private TestRun findRunOrThrow(UUID runId) {
        return testRunRepository.findById(runId)
                .orElseThrow(() -> new RunNotFoundException(runId));
    }

    /**
     * Starts the engine only once the transaction that created the {@code TestRun} row has
     * actually committed — if the transaction rolled back, this listener never fires, so the
     * engine can never run against a row that doesn't durably exist.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onRunCreated(RunCreatedEvent event) {
        TestRun run = testRunRepository.findById(event.runId()).orElseThrow();
        Test test = run.getTest();

        RunSpec spec = new RunSpec(
                run.getId(),
                test.getTargetUrl(),
                test.getHttpMethod(),
                run.getConfigSnapshot().getLoadModel(),
                run.getConfigSnapshot().getStartTarget(),
                run.getConfigSnapshot().getMaxConcurrency(),
                run.getConfigSnapshot().getStages(),
                requestTimeout
        );

        ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
        EngineRunListener listener = new EngineRunListener(run.getId(), runPersistence, persistenceExecutor, runCoordinator);
        loadGenerator.start(spec, listener);
    }
}

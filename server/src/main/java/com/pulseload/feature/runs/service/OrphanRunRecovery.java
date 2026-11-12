package com.pulseload.feature.runs.service;

import com.pulseload.model.TestRun;
import com.pulseload.model.TestRunStatus;
import com.pulseload.feature.runs.data.TestRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Marks any run left in {@code PENDING}/{@code RUNNING} from a previous process as
 * {@code FAILED} on startup. Those rows can only exist if the JVM died mid-run (a graceful
 * shutdown drains active runs via {@code EngineShutdownHandler} before exiting) — there is no
 * live {@code EngineRun} for them anymore in this process, so they would otherwise sit
 * "running" forever.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OrphanRunRecovery {

    private final TestRunRepository testRunRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void recoverOrphanedRuns() {
        List<TestRun> orphans = testRunRepository.findByStatusIn(List.of(TestRunStatus.PENDING, TestRunStatus.RUNNING));
        for (TestRun orphan : orphans) {
            orphan.setStatus(TestRunStatus.FAILED);
            orphan.setFinishedAt(Instant.now());
        }
        if (!orphans.isEmpty()) {
            log.warn("Marked {} orphaned run(s) as FAILED on startup", orphans.size());
        }
    }
}

package com.pulseload.model;

import com.pulseload.engine.executor.ExecutionOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * One row per second of a run's execution, mirroring {@code engine.api.MetricsSnapshot} plus the
 * generator's own resource usage at that instant. This is what the time-series/metrics endpoint
 * returns so a user can watch a run's actual-vs-requested RPS and latency evolve, and later
 * correlate a dip in actual RPS with a CPU spike (US 2.4).
 */
@Entity
@Table(name = "test_run_metrics")
@Getter
@Setter
@NoArgsConstructor
public class TestRunMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_run_id", nullable = false, updatable = false)
    private TestRun testRun;

    @Column(name = "elapsed_seconds", nullable = false)
    private Double elapsedSeconds;

    @Column(name = "requested_target", nullable = false)
    private Double requestedTarget;

    @Column(name = "completed_rps", nullable = false)
    private Double completedRps;

    @Column(name = "completed_count", nullable = false)
    private Long completedCount;

    @Column(name = "dropped_count", nullable = false)
    private Long droppedCount;

    @Column(name = "active_requests", nullable = false)
    private Integer activeRequests;

    @Column(name = "backlog", nullable = false)
    private Long backlog;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outcome_counts", nullable = false, columnDefinition = "jsonb")
    private Map<ExecutionOutcome, Long> outcomeCounts;

    @Column(name = "cpu_load")
    private Double cpuLoad;

    @Column(name = "heap_used_bytes")
    private Long heapUsedBytes;
}

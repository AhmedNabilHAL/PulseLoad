package com.pulseload.model;

import com.pulseload.engine.api.LatencyStats;
import com.pulseload.engine.executor.ExecutionOutcome;
import com.pulseload.engine.saturation.SaturationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Final metrics for a completed (or failed/cancelled) run, populated from the engine's
 * {@code RunReport} once {@code EngineRun} finishes. Counts and RPS get flat columns since
 * they're the fields most likely to be filtered/sorted on later (e.g. "show failed runs").
 * The latency breakdowns and outcome counts are stored as JSONB, matching the precedent set by
 * {@code WorkloadProfile.stages}, since they are only ever displayed whole, never queried into.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class RunSummary {

    @Column(name = "total_dispatched")
    private Long totalDispatched;

    @Column(name = "total_completed")
    private Long totalCompleted;

    @Column(name = "total_dropped")
    private Long totalDropped;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outcome_counts", columnDefinition = "jsonb")
    private Map<ExecutionOutcome, Long> outcomeCounts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_time_stats", columnDefinition = "jsonb")
    private LatencyStats serviceTimeStats;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_time_stats", columnDefinition = "jsonb")
    private LatencyStats responseTimeStats;

    @Column(name = "requested_rps")
    private Double requestedRps;

    @Column(name = "actual_rps")
    private Double actualRps;

    @Column(name = "peak_cpu_load")
    private Double peakCpuLoad;

    @Column(name = "peak_heap_used_bytes")
    private Long peakHeapUsedBytes;

    @Column(name = "saturated")
    private Boolean saturated;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saturation_reasons", columnDefinition = "jsonb")
    private java.util.List<SaturationReason> saturationReasons;
}

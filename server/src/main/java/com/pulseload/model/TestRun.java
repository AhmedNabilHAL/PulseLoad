package com.pulseload.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_runs")
@Getter
@Setter
@NoArgsConstructor
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false, updatable = false)
    private Test test;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private TestRunStatus status;

    /**
     * Snapshot of the workload profile the test had at the moment this run was created, so a
     * later edit to the {@code Test} doesn't retroactively change what a past run says it ran.
     * Copied by value when the run is created; never updated afterwards.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "loadModel", column = @Column(name = "config_load_model", nullable = false, length = 10)),
            @AttributeOverride(name = "startTarget", column = @Column(name = "config_start_target", nullable = false)),
            @AttributeOverride(name = "maxConcurrency", column = @Column(name = "config_max_concurrency", nullable = false)),
            @AttributeOverride(name = "stages", column = @Column(name = "config_stages", nullable = false, columnDefinition = "jsonb"))
    })
    private WorkloadProfile configSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Embedded
    private RunSummary summary;
}

package com.pulseload.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class WorkloadProfile {

    @Enumerated(EnumType.STRING)
    @Column(name = "load_model", length = 10, nullable = false)
    private LoadModel loadModel;

    /** Target value (RPS or VUs, depending on loadModel) at the start of the first stage. */
    @Column(name = "start_target", nullable = false)
    private Integer startTarget;

    /** Upper bound on in-flight requests (OPEN model) or concurrent VU loops (CLOSED model). */
    @Column(name = "max_concurrency", nullable = false)
    private Integer maxConcurrency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Stage> stages;
}

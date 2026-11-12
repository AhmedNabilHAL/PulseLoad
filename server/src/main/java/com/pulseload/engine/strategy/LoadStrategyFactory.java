package com.pulseload.engine.strategy;

import com.pulseload.engine.api.RunSpec;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.model.LoadModel;

/**
 * Builds the {@link LoadStrategy} for one load model. Spring collects every implementation into
 * a {@code List<LoadStrategyFactory>}, and whoever is starting a run (today,
 * {@code InProcessEngine}) picks the one whose {@link #loadModel()} matches the run's spec —
 * replacing what used to be an if/else in {@code EngineRun.buildStrategy()}.
 */
public interface LoadStrategyFactory {

    LoadModel loadModel();

    LoadStrategy create(RunSpec spec, WorkloadSchedule schedule, ProtocolExecutor executor, MetricsRecorder metricsRecorder);
}

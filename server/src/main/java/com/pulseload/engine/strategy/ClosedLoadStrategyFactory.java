package com.pulseload.engine.strategy;

import com.pulseload.engine.api.RunSpec;
import com.pulseload.engine.concurrent.TaskDispatcher;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;
import com.pulseload.model.LoadModel;
import org.springframework.stereotype.Component;

@Component
public class ClosedLoadStrategyFactory implements LoadStrategyFactory {

    private static final TaskDispatcher DISPATCHER = TaskDispatcher.withMdcPropagation(TaskDispatcher.VIRTUAL_THREADS);

    @Override
    public LoadModel loadModel() {
        return LoadModel.CLOSED;
    }

    @Override
    public LoadStrategy create(RunSpec spec, WorkloadSchedule schedule, ProtocolExecutor executor, MetricsRecorder metricsRecorder) {
        return new ClosedModelStrategy(schedule, executor, metricsRecorder, DISPATCHER, NanoClock.SYSTEM);
    }
}

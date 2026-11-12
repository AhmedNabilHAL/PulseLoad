package com.pulseload.engine.strategy;

import com.pulseload.engine.api.RunSpec;
import com.pulseload.engine.concurrent.TaskDispatcher;
import com.pulseload.engine.executor.ProtocolExecutor;
import com.pulseload.engine.metrics.MetricsRecorder;
import com.pulseload.engine.schedule.WorkloadSchedule;
import com.pulseload.engine.time.NanoClock;
import com.pulseload.engine.vu.VirtualUserRegistry;
import com.pulseload.model.LoadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class ClosedLoadStrategyFactory implements LoadStrategyFactory {

    // EXERCISE (threads): virtual users are dispatched onto a small fixed pool of platform threads,
    // so only two ever run at once no matter how many are configured. Use an execution mechanism
    // that lets every virtual user run concurrently.
    private static final ExecutorService VU_POOL = Executors.newFixedThreadPool(2);
    private static final TaskDispatcher DISPATCHER = TaskDispatcher.withMdcPropagation(VU_POOL::execute);

    private final VirtualUserRegistry vuRegistry;

    @Override
    public LoadModel loadModel() {
        return LoadModel.CLOSED;
    }

    @Override
    public LoadStrategy create(RunSpec spec, WorkloadSchedule schedule, ProtocolExecutor executor,
            MetricsRecorder metricsRecorder) {
        return new ClosedModelStrategy(spec.runId(), schedule, executor, metricsRecorder, DISPATCHER, vuRegistry,
                NanoClock.SYSTEM);
    }
}

package com.pulseload.engine.metrics;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Samples the generator process's own CPU and heap usage. Used by {@link
 * com.pulseload.engine.saturation.SaturationDetector} to tell "the generator itself is the
 * bottleneck" apart from "the target or the concurrency cap is the bottleneck" (US 2.4).
 *
 * <p>Not thread-safe by convention rather than by design: {@link OperatingSystemMXBean#getProcessCpuLoad()}
 * reports load over the window since the previous call to any CPU-load method on the JVM-wide
 * singleton bean, so polling it from more than one thread would make each caller's readings
 * reflect an unpredictable, shared window. One {@code ResourceSampler} per run, polled from a
 * single thread (the engine's reporting tick), avoids that. The very first reading after JVM
 * startup is unreliable (it is often {@code -1} or an average since process start rather than a
 * real instantaneous value) and should be discarded by the caller.
 */
public final class ResourceSampler {

    private final OperatingSystemMXBean operatingSystemBean =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    /** Process CPU load normalized 0..1 across all available CPUs, or -1 if not yet available. */
    public double sampleCpuLoad() {
        return operatingSystemBean.getProcessCpuLoad();
    }

    public long sampleHeapUsedBytes() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }

    /** The JVM's maximum heap size in bytes, or -1 if undefined, for computing a usage ratio. */
    public long maxHeapBytes() {
        return memoryBean.getHeapMemoryUsage().getMax();
    }
}

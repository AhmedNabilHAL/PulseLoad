package com.pulseload.engine.logging;

import java.util.UUID;

/** Implemented by beans that {@link RunMdcAspect} should tag with a {@code runId} in MDC. */
public interface RunIdentifiable {

    UUID runId();
}

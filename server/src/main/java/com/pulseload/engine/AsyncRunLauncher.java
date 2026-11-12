package com.pulseload.engine;

import com.pulseload.config.AsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs an {@link EngineRun} to completion on a background thread so the HTTP
 * request that started
 * the run returns immediately (202 Accepted) instead of blocking for the run's
 * whole duration.
 *
 * <p>
 * This is a separate Spring bean from {@link InProcessEngine} on purpose:
 * {@code @Async} works
 * through a proxy, and a proxy is only consulted on calls that cross a bean
 * boundary. If this
 * method lived on {@code InProcessEngine} and were called internally, the
 * self-invocation would
 * bypass the proxy and silently run synchronously.
 */
@Component
class AsyncRunLauncher {

  @Async(AsyncConfig.RUN_LAUNCHER_EXECUTOR)
  void launch(EngineRun run, Runnable onFinished) {
    try {
      run.runToCompletion();
    } finally {
      onFinished.run();
    }
  }
}

package com.pulseload.engine.concurrent;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Seam over how a dispatched request's work actually runs. Production dispatches onto a fresh
 * virtual thread per request; tests inject a synchronous dispatcher so that the effects of a
 * tick (dispatch, completion, metrics) are observable immediately, without racing a background
 * thread.
 */
@FunctionalInterface
public interface TaskDispatcher {

    TaskDispatcher VIRTUAL_THREADS = task -> Thread.ofVirtual().start(task);

    void dispatch(Runnable task);

    /**
     * Wraps a dispatcher so every task it runs sees the calling thread's current MDC context map
     * copied onto whatever thread actually executes it — necessary because MDC is a plain
     * {@link ThreadLocal} that a freshly spawned virtual thread does not inherit.
     */
    static TaskDispatcher withMdcPropagation(TaskDispatcher delegate) {
        return task -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            delegate.dispatch(() -> {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                try {
                    task.run();
                } finally {
                    MDC.clear();
                }
            });
        };
    }
}

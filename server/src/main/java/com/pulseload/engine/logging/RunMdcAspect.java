package com.pulseload.engine.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Wraps every {@link RunMdcScope @RunMdcScope} method on a {@link RunIdentifiable} bean with its
 * {@code runId} set in MDC for the method's whole execution, restoring (not blindly clearing)
 * whatever value MDC held before — the same save/restore contract the old {@code RunMdc} helper
 * had, needed so a method that's already inside its own {@code @RunMdcScope} call on the same
 * thread (e.g. an inline call from one advised method to another) doesn't wipe out the outer
 * scope's runId when it finishes. {@code ProceedingJoinPoint.proceed()} is a plain per-invocation
 * method call, so nested/reentrant calls on the same thread unwind correctly with this pattern.
 */
@Aspect
@Component
public class RunMdcAspect {

    private static final String RUN_ID_KEY = "runId";

    @Around("@annotation(RunMdcScope) && target(run)")
    public Object withRunId(ProceedingJoinPoint joinPoint, RunIdentifiable run) throws Throwable {
        String previous = MDC.get(RUN_ID_KEY);
        MDC.put(RUN_ID_KEY, run.runId().toString());
        try {
            return joinPoint.proceed();
        } finally {
            if (previous != null) {
                MDC.put(RUN_ID_KEY, previous);
            } else {
                MDC.remove(RUN_ID_KEY);
            }
        }
    }
}

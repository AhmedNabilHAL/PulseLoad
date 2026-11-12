package com.pulseload.engine.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose entire execution should run with the owning bean's {@code runId} set in
 * MDC. Only takes effect on a bean that implements {@link RunIdentifiable} — see
 * {@link RunMdcAspect}. The bean must be Spring-managed and the annotated method called through
 * the bean's proxy (not a plain self-invocation) for the advice to apply.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface RunMdcScope {
}

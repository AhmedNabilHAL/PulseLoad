package com.pulseload.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Centralizes operation logging so individual methods don't each repeat the same enter/exit/timing
 * boilerplate.
 *
 * <p>EXERCISE (aspect scope): the pointcut below matches every method across the feature packages —
 * controllers, services, mappers, and repositories alike — so a single request fans out into many
 * log lines. Narrow it so it logs meaningful service operations without flooding the logs.
 */
@Aspect
@Component
public class ServiceLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceLoggingAspect.class);

    @Around("execution(* com.pulseload.feature..*.*(..))")
    public Object logServiceOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String operation = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        long startNanos = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            log.info("{} completed in {} ms", operation, (System.nanoTime() - startNanos) / 1_000_000);
            return result;
        } catch (Throwable t) {
            log.warn("{} failed: {}", operation, t.toString());
            throw t;
        }
    }
}

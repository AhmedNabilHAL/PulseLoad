package com.pulseload.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Centralizes service-operation logging so individual service methods don't each repeat the same
 * enter/exit/timing boilerplate. The pointcut is deliberately narrow — only the control-plane
 * service implementations — so it never advises the hot per-request engine path or low-level
 * collaborators (repositories, mappers), which would flood the logs.
 */
@Aspect
@Component
public class ServiceLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceLoggingAspect.class);

    @Around("execution(* com.pulseload.feature..service.*Impl.*(..))")
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

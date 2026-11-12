package com.pulseload.engine.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.framework.AopContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link RunMdcAspect} through a real AspectJ proxy (no Spring context needed) — proves
 * the aspect reproduces the exact save/restore MDC contract the old {@code RunMdc} helper had,
 * including under the same "one advised method calls another on the same thread" nesting
 * {@code EngineRun.runToCompletion()}/{@code emitSnapshot()} rely on.
 */
class RunMdcAspectTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static Target newProxy() {
        Target target = new Target();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.setExposeProxy(true);
        factory.addAspect(new RunMdcAspect());
        return factory.getProxy();
    }

    @Test
    void setsRunIdForTheDurationOfTheCallAndRestoresItAfterward() {
        Target proxy = newProxy();

        proxy.outer(() -> assertThat(MDC.get("runId")).isEqualTo(proxy.runId().toString()));

        assertThat(MDC.get("runId")).isNull();
    }

    @Test
    void restoresMdcEvenIfTheAdvisedMethodThrows() {
        Target proxy = newProxy();

        assertThatThrownBy(() -> proxy.outer(() -> {
            throw new RuntimeException("boom");
        })).isInstanceOf(RuntimeException.class);

        assertThat(MDC.get("runId")).isNull();
    }

    @Test
    void nestedAdvisedCallOnTheSameThreadDoesNotWipeTheOuterScopesRunIdOnExit() {
        // Regression test for the same bug RunMdc's save/restore fix addressed: EngineRun's
        // runToCompletion() calls emitSnapshot() inline on the same thread, and both are
        // @RunMdcScope methods for the same runId. If the inner call's cleanup unconditionally
        // cleared MDC (rather than restoring the previous value), the outer call's subsequent
        // log lines would see a blank runId even though it never actually ended.
        Target proxy = newProxy();

        proxy.outer(() -> {
            assertThat(MDC.get("runId")).isEqualTo(proxy.runId().toString());
            // Route through the proxy, exactly as EngineRun.runToCompletion() must for its
            // ticker-scheduled call — a plain `proxy.inner()`-style self-call inside Target
            // itself would bypass advice, which is precisely the self-invocation problem this
            // whole design works around via AopContext.currentProxy().
            ((Target) AopContext.currentProxy()).inner();
            assertThat(MDC.get("runId")).isEqualTo(proxy.runId().toString());
        });

        assertThat(MDC.get("runId")).isNull();
    }

    static class Target implements RunIdentifiable {
        private final UUID id = UUID.randomUUID();

        @Override
        public UUID runId() {
            return id;
        }

        @RunMdcScope
        public void outer(Runnable body) {
            body.run();
        }

        @RunMdcScope
        public void inner() {
            assertThat(MDC.get("runId")).isEqualTo(id.toString());
        }
    }
}

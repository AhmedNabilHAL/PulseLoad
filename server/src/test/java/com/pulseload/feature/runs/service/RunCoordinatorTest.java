package com.pulseload.feature.runs.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RunCoordinatorTest {

    @Test
    void allowsUpToTheConfiguredLimitThenRejects() {
        RunCoordinator coordinator = new RunCoordinator(2);

        assertThat(coordinator.tryReserve()).isTrue();
        assertThat(coordinator.tryReserve()).isTrue();
        assertThat(coordinator.tryReserve()).isFalse();
    }

    @Test
    void releasingFreesUpASlotForTheNextReservation() {
        RunCoordinator coordinator = new RunCoordinator(1);

        assertThat(coordinator.tryReserve()).isTrue();
        assertThat(coordinator.tryReserve()).isFalse();

        coordinator.release();

        assertThat(coordinator.tryReserve()).isTrue();
    }

    @Test
    void releaseIsANoOpWhenNothingIsReserved() {
        RunCoordinator coordinator = new RunCoordinator(1);

        coordinator.release();
        coordinator.release();

        assertThat(coordinator.tryReserve()).isTrue();
        assertThat(coordinator.tryReserve()).isFalse();
    }

    @Test
    void underConcurrentContentionExactlyTheLimitSucceeds() throws InterruptedException {
        int limit = 5;
        int contenders = 50;
        RunCoordinator coordinator = new RunCoordinator(limit);
        AtomicInteger successCount = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(contenders);

        for (int i = 0; i < contenders; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startGate.await();
                    if (coordinator.tryReserve()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        doneLatch.await();

        assertThat(successCount.get()).isEqualTo(limit);
    }
}

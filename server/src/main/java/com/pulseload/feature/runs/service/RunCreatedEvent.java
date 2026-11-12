package com.pulseload.feature.runs.service;

import java.util.UUID;

/**
 * Published after a {@code TestRun} row is saved, and handled only once that save is durably
 * committed ({@code @TransactionalEventListener(phase = AFTER_COMMIT)}) — so the engine never
 * starts a run whose row might vanish from a rolled-back transaction.
 */
record RunCreatedEvent(UUID runId) {
}

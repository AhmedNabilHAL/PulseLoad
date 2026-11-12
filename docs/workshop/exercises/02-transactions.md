# Exercise 2 — Transactions

**Branch:** `session/02-transactions` · **~13 min** · guided

**Learning objective:** Understand transaction boundaries and why a side effect that must happen only
after a commit is bound to the transaction — remove the transaction and the side effect silently
disappears.

---

## Guided — the stranded run

**Existing behaviour:** You start a run, get a `202`, and the run row appears as `QUEUED` — then
never changes. The virtual users never execute.

**Problem:** Starting a run saves the row and publishes a `RunCreatedEvent`. The engine is launched
by a listener annotated `@TransactionalEventListener(AFTER_COMMIT)` — it fires only after the
surrounding transaction commits, so the engine never runs against an uncommitted (or rolled-back)
row. But if the publishing method isn't transactional, there is no commit to bind to, and the event
is dropped.

**Relevant code:**
- `server/src/main/java/com/pulseload/feature/runs/service/TestRunServiceImpl.java`
  (`startRun`, and the `onRunCreated` listener)
- `server/src/main/java/com/pulseload/feature/runs/service/RunCreatedEvent.java`

**Conceptual guidance:** Look at what makes an `AFTER_COMMIT` listener fire. What has to be true about
the method that publishes the event?

**Expected behaviour:** A started run transitions `QUEUED → RUNNING → COMPLETED` and virtual users
execute.

**Acceptance:** `TransactionAcceptanceTest` passes. (`RunLifecycleIntegrationTest` is `@Disabled` on
this branch for the same reason — it comes back to life once the fix is in.)

**Verification:** `./mvnw test -Dtest.excludedGroups= -Dgroups=acceptance -Dtest=TransactionAcceptanceTest`

## Independent — discussion

Look at `RunPersistence` — every write method is `@Transactional(propagation = REQUIRES_NEW)`. Why?
What would happen to pacing accuracy or to a completed snapshot if one slow metric write shared, and
rolled back, a single long transaction? Be ready to explain the trade-off.

**Debrief:** Transactions aren't only about rollback; they define *when* committed work becomes
visible and when transaction-bound side effects run. `AFTER_COMMIT` is the correct place to trigger
external action (starting the engine) precisely because it guarantees the data is durable first.

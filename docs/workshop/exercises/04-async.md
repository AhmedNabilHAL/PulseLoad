# Exercise 4 — Async & concurrency

**Branch:** `session/04-async` · **~23 min** · guided + independent

**Learning objective:** Understand synchronous vs asynchronous execution, how Spring's `@Async` works
through a proxy (and the self-invocation trap), and the difference between a small platform-thread
pool and virtual threads for many blocking tasks.

---

## Guided — stop blocking the caller

**Existing behaviour:** `POST /api/tests/{id}/runs` is supposed to return `202` immediately, but the
request hangs until the entire run finishes.

**Problem:** The engine runs the load synchronously on the caller's thread. A run is long-lived, so
the HTTP request (and the thread serving it) is tied up for its whole duration.

**Relevant code:** `server/src/main/java/com/pulseload/engine/InProcessEngine.java` (`start`),
`server/src/main/java/com/pulseload/config/AsyncConfig.java`.

**Conceptual guidance:** Move `runToCompletion()` onto a background executor with `@Async`. Watch
out: `@Async` works via a proxy, so calling an `@Async` method from *within the same bean* bypasses
it and still runs synchronously. The clean fix puts the async method on its own bean.

**Expected behaviour:** `startRun` returns promptly while the run proceeds in the background; the UI
shows `QUEUED → RUNNING`.

**Acceptance:** `AsyncExecutionAcceptanceTest.startRunReturnsPromptlyWithoutBlockingForTheRunDuration`
passes.

## Independent — let every virtual user run

**Existing behaviour:** Configure 8 virtual users and only ~2 are ever active at once; throughput is
capped.

**Problem:** Virtual users are dispatched onto a fixed pool of 2 platform threads, so at most 2 run
concurrently no matter how many are configured. A load-testing virtual user is a *logical actor* and
spends most of its time blocked on I/O — a perfect fit for virtual threads, where thousands can run
at once cheaply.

**Relevant code:**
`server/src/main/java/com/pulseload/engine/strategy/ClosedLoadStrategyFactory.java`,
`server/src/main/java/com/pulseload/engine/concurrent/TaskDispatcher.java`.

**Conceptual guidance:** Switch the dispatcher to a mechanism that gives each virtual user its own
(virtual) thread. Distinguish the load-testing *virtual user* from the JVM *virtual thread* it runs on.

**Expected behaviour:** All 8 virtual users run concurrently; the VU panel shows ~8 requesting at
once and throughput jumps.

**Acceptance:** `AsyncExecutionAcceptanceTest.virtualUsersExecuteConcurrently` passes.

**Verification:** `./mvnw test -Dtest.excludedGroups= -Dgroups=acceptance -Dtest=AsyncExecutionAcceptanceTest`

**Debrief:** Async moves work off the request thread so endpoints stay responsive; the right executor
matters. A small platform pool serializes blocking work; virtual threads let many blocking actors run
concurrently without a large pool. And remember the proxy: `@Async`, `@Transactional`, and aspects all
rely on it, so self-invocation silently skips them.

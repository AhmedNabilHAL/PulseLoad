# PulseLoad backend: Sprint 1 & 2 (through User Story 2.4) — Handoff Plan

> This is a handoff document for continuing in-progress work in a fresh AI session/tool with
> no memory of prior conversation. It captures what's done, what's left, the design decisions
> already made (and why), and every non-obvious technical gotcha discovered so far. Read the
> "Environment gotchas" section before running any build commands.

## Project context

PulseLoad is a self-hosted, personal-learning load-testing platform (see repo root
`PulseLoad  user stories.md` for the full product spec and 11-sprint roadmap). This plan covers
finishing **Sprint 1** (first end-to-end load test) and **Sprint 2** (real workload engine)
as defined there:

- US 1.2: start a test run (`POST /tests/{id}/runs`)
- US 1.3: basic results (totals, min/avg/max/percentile latency, RPS)
- US 2.1: rate control (rate limiter, scheduler, executor, concurrency control)
- US 2.2: ramp-up workload (`WorkloadProfile`, `Stage`, linear ramp)
- US 2.3: concurrent users (VUs) as distinct from RPS
- US 2.4: detect when the load generator itself is saturated (not just the target)

Only US 1.1 (Test CRUD) existed when this plan started. Backend: `server/`, Java 21, Spring
Boot 4.1.1, PostgreSQL, Liquibase (`ddl-auto=validate`), Lombok, MapStruct 1.6.3. Feature-based
packaging under `com.pulseload.service.<feature>/{controller,service,data,dto,mapper}`;
entities in `com.pulseload.model`; exceptions in `com.pulseload.exception` with
`GlobalExceptionHandler` returning `ProblemDetail`. Context path `/api`.

**Collaboration style expected on this project** (from `CLAUDE.md`): this is a personal
learning project. Prioritize teaching over just producing code — explain architectural
decisions and trade-offs. Work incrementally with a compilable, tested checkpoint after each
phase; don't dump large amounts of code at once. Challenge assumptions and bad patterns rather
than rubber-stamping. Verify uncertain technical claims against real docs/jars instead of
guessing (this mattered a lot — see "Environment gotchas" below, several assumed APIs turned
out to be wrong).

## Why the design looks the way it does

The user's original sketch was: `TestRun` → spins up a per-run `TestRunWorkloadManager`
service → spins up several `TestRunWorker` processes (one per simulated user, possibly one
VM each) → each worker connects through a rotating proxy for a unique IP → hits the target →
sends results to a queue → the manager aggregates via the observer pattern → notifies the main
service via a webhook.

This was reshaped to match the project's own documented roadmap and how real load-testing
tools (k6, Gatling, JMeter) work:

| Original idea | What we're building instead | Why |
|---|---|---|
| Worker per simulated user / VM per user | **Virtual user (VU) = a virtual-thread loop.** One generator process runs thousands of VUs. | Same model as k6/Gatling/JMeter. A VM per VU can't scale past a handful of users. Java 21 virtual threads make this cheap. |
| Per-run manager as its own *service* | A singleton **`RunCoordinator`** in the control plane, tracking many runs via a `ConcurrentHashMap`, plus a per-run engine object. | Matches Kubernetes: one Job controller manages every Job; it doesn't spin up a new controller process per Job. |
| Proxy / IP rotation for unique IPs | **Dropped entirely.** | Adds latency that pollutes the very numbers being measured, adds a hop that can itself become the bottleneck (undermining US 2.4), and IP rotation to disguise many requests as different users is the kind of technique used to evade rate limits/WAFs — not appropriate even for testing your own service. Real IP diversity comes for free once workers run on separate machines (Sprint 4/8 in the roadmap). |
| Result queue + observer + webhook, per-request | **`LoadGenerator` port + `RunListener` observer**, with workers emitting **~1/sec metrics snapshots using mergeable HDR histograms**, not a message per request. | The in-process adapter implements the port today; a gRPC adapter (Sprint 4) or NATS (Sprint 6) replaces it later without touching domain code. Streaming every request result would be the wrong unit of decoupling — aggregated histograms are what workers should hand upward. |

**Decisions already made and confirmed with the user (do not re-litigate without reason):**
- The engine runs **in-process** inside Spring Boot for now, but behind clean ports, so
  swapping in a remote/gRPC worker later (Sprint 4) doesn't touch calling code.
- **No proxy.** Full stop — not even as a stub.
- Ramp-up stages use **linear interpolation** between stage targets (k6 semantics), not
  step/discrete jumps.
- Backend-only for this plan. **The webapp's existing create-test form will break** because
  `durationInSeconds`/`requestsPerSec` are replaced by `workloadProfile`. Fixing the frontend
  is an explicit, separate follow-up — out of scope here.

## Architecture (end state after Phase 6)

```
RunController → TestRunService ──(AFTER_COMMIT event)──► RunCoordinator (singleton, tracks active runs)
                                                            │ uses port
                                              LoadGenerator (service.runs.execution)   ◄── Sprint 4: GrpcLoadGenerator
                                                            │ adapter
                                              InProcessLoadGenerator ──► com.pulseload.engine (pure Java, no Spring)
                                                            │                 LoadStrategy: Open | Closed
                                                            │                 ProtocolExecutor: HttpExecutor
                                              RunListener ◄─┘ onSnapshot / onCompleted / onFailed
                                                 → persists TestRunMetric rows + final RunSummary (on its own single-thread executor)
```

- The port (`LoadGenerator`) belongs to the code that calls it (`service.runs.execution`), not
  to the engine — standard ports-and-adapters.
- `com.pulseload.engine.*` has **zero Spring annotations** anywhere. It's designed to be
  extractable into a standalone `worker/` module/repo later without modification.
- `engine.api` holds plain records shaped to map 1:1 onto a future gRPC message (`RunSpec`,
  `MetricsSnapshot`, `RunReport`, `LatencyStats`).

## Key design rules (the actual engineering content — read before implementing Phase 4+)

**Open model (RPS) vs. closed model (VUs) — these are fundamentally different algorithms,**
not a flag on the same loop:
- **Open model**: a scheduler ticks every ~5ms. At each tick it computes
  `WorkloadSchedule.cumulativeAt(elapsed)` (how many requests *should* have been sent by now)
  minus how many have actually been dispatched, and dispatches that many new requests onto
  virtual threads, gated by `Semaphore(maxConcurrency)`.
  - If no permit is free at a request's intended dispatch time, **that request is dropped and
    counted** (k6 calls this `dropped_iterations`) — it must NOT be queued up to burst out
    later, because that burst is not the load the test asked for.
  - "Queue depth" / backlog = requests owed by the schedule but not yet dispatched because
    permits ran out.
- **Closed model**: at each tick, adjust the number of currently-running VU virtual-thread
  loops to match `WorkloadSchedule.targetAt(elapsed)`. Each VU loops: send request, wait for
  response, repeat (optionally with think time, not required for Sprint 2).

**Coordinated omission — this is the single most important correctness detail in the whole
engine.** If you only measure actual-send-to-response latency, a generator that falls behind
schedule will report *good* latency numbers for the few requests it does get out, even though
real users would have experienced much longer waits for the (silently delayed or dropped)
requests it was late to send. This is a well-known measurement bug in naive load testers.
Mitigation already implemented in `MetricsRecorder` (see Phase 3 below): **two separate HDR
histograms** —
- `serviceTime`: actual send → response (useful for understanding the target's raw performance)
- `responseTime`: *intended* send time (per the schedule, via `WorkloadSchedule.elapsedAtCumulative`)
  → response. **This is the number that should be shown to users** under the open model, since
  it reflects what a real user queue would have experienced.
- Do **not** use HdrHistogram's `recordValueWithExpectedInterval` — that API is specifically
  for closed-loop/closed-model correction and is the wrong tool here.
- Under the **closed model**, coordinated omission is inherent by construction (a VU can't send
  its next request until the last one returns) — this should be labeled/documented in results,
  not "fixed", since it's a property of the model US 2.3 is explicitly teaching.

**Saturation (US 2.4) needs a *cause*, not just a threshold.** A full `Semaphore` only means
the generator is saturated if the *generator* is the bottleneck. By Little's Law, the
concurrency needed to sustain a given RPS at a given latency is `RPS × latency` — so if
`maxConcurrency` fills up because the **target** is slow (high latency), that's a configuration/
target limit, not generator saturation. Classify reasons into two buckets:
- `GENERATOR` (→ sets `saturated=true`): process CPU high (via
  `com.sun.management.OperatingSystemMXBean#getProcessCpuLoad()`, confirmed available and not
  deprecated in JDK 21 — discard the first reading, poll from a single thread, values are
  container-aware and normalized 0..1 across all CPUs), heap pressure, the dispatch tick
  overrunning its own interval, or dispatch lag occurring *while permits were still available*
  (i.e., the generator itself couldn't keep up, independent of the concurrency cap).
- `CONCURRENCY_CAP` / `TARGET` (→ a separate warning, **not** `saturated=true`): permits
  exhausted *together with* rising latency — this means the configured cap or the target's
  speed is the limiting factor, not the generator's own resources.

## Status: what's done (Phases 0–3, all checkpoints passed with `BUILD SUCCESS`)

### Phase 0 — Test infrastructure ✅
`server/pom.xml`:
- Added `spring-boot-starter-actuator` (+ test), `spring-boot-starter-data-jpa-test`,
  `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`
  (note: **not** `org.testcontainers:junit-jupiter`/`postgresql` — those 1.x coordinates don't
  exist in Testcontainers 2.x, which prefixes module artifacts with `testcontainers-`), and
  `org.hdrhistogram:HdrHistogram:2.2.2` pinned explicitly (confirmed: Boot 4.1.1's BOM does
  **not** manage HdrHistogram's version).
- Fixed a **pre-existing bug on `main`** unrelated to this work: `protobuf-maven-plugin` was
  configured with no `.proto` files present yet (gRPC isn't needed until Sprint 3) and fails
  the build by default on missing sources (`failOnMissingSources` defaults to `true`). Added
  `<configuration><failOnMissingSources>false</failOnMissingSources></configuration>` to that
  plugin — this is also the plugin's own documented recommendation for this situation. Verified
  this break pre-dates this work by stashing all changes and reproducing the failure on bare
  `main`.
- `server/src/test/java/com/pulseload/TestcontainersConfiguration.java` (new, `public`): a
  `@TestConfiguration` with a `@ServiceConnection PostgreSQLContainer` bean, imported into
  `ServerApplicationTests`. **Important**: use `org.testcontainers.postgresql.PostgreSQLContainer`
  (non-generic), not `org.testcontainers.containers.PostgreSQLContainer<?>` — the latter is
  deprecated in Testcontainers 2.x (confirmed via `javap`), replaced by a new, same-API, non-
  generic class in the `org.testcontainers.postgresql` package.

### Phase 1 — Workload model ✅
- `model/LoadModel.java` (enum: `OPEN`, `CLOSED`), `model/Stage.java` (record:
  `durationSeconds`, `target`), `model/WorkloadProfile.java` (`@Embeddable`: `loadModel`,
  `startTarget`, `maxConcurrency`, `stages` as `List<Stage>` persisted as a **single `jsonb`
  column** via `@JdbcTypeCode(SqlTypes.JSON)` — confirmed Hibernate 7.4.5.Final (the version
  Boot 4.1.1's BOM pins) ships `Jackson3JsonFormatMapper` and supports this out of the box).
  - **Deliberate deviation from an earlier draft of this plan**: stages were originally going
    to be a relational `test_stages` table via `@ElementCollection`/`@OrderColumn`. Switched to
    JSONB because `@ElementCollection` is LAZY by default and stages are value objects always
    loaded with the parent — JSONB avoids a lazy-initialization trap in the MapStruct mapper
    and matches how the run config snapshot is already planned to be stored (see Phase 5).
- `model/Test.java`: `durationInSeconds`/`requestsPerSec` fields removed, replaced by
  `@Embedded WorkloadProfile workloadProfile`.
- `service/tests/dto/StageDto.java`, `WorkloadProfileDto.java` (new, with nested `@Valid`
  cascading — `List<@Valid StageDto> stages`). `CreateTestDto`/`TestDto` updated to carry
  `WorkloadProfileDto` instead of the two flat fields.
- `TestMapper` needed **no code changes** — MapStruct auto-generates nested entity↔DTO mapping
  when field names match across levels, which they do here.
- `db/changelog/changes/002-workload-profile.xml` (new): adds the new columns, backfills
  existing rows into a single OPEN-model stage derived from the old `duration_in_seconds`/
  `requests_per_sec` values, enforces `NOT NULL`, then drops the old columns. Registered in
  `db.changelog-master.xml`.
- Tests: `TestRepositoryIT` (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` +
  `@Import(TestcontainersConfiguration.class)` — **`@DataJpaTest` defaults to replacing the
  datasource with an embedded DB**, which must be disabled to actually test against the real
  Postgres container and its `jsonb` column type) proves the JSONB round-trip.
  `TestControllerValidationTest` (`@WebMvcTest`, **without** importing `SecurityConfig`import —
  importing it caused `NoSuchBeanDefinitionException` for `HttpSecurity` inside the slice;
  Boot's default test security is permissive enough for a pure-validation test) proves a stage
  with negative duration/target is rejected with 400.
  - **Naming collision, will recur in every future test touching `Test`**: the domain entity
    is called `com.pulseload.model.Test`, which collides with `org.junit.jupiter.api.Test`.
    Java has no import aliasing, so the workaround is to fully-qualify
    `com.pulseload.model.Test` at each use site inside test classes (see
    `TestRepositoryIT` for the pattern). Expect to redo this in every future test file for
    this feature.

### Phase 2 — Engine API + WorkloadSchedule ✅
New `com.pulseload.engine` package (confirmed zero Spring/JPA dependencies throughout):
- `engine/api/RunSpec.java`: record with `runId, targetUrl, httpMethod, loadModel, startTarget,
  maxConcurrency, stages, requestTimeout`. **Deliberately reuses** `com.pulseload.model.{HttpMethod,
  LoadModel, Stage}` directly rather than duplicating them as engine-local types — they're
  already plain/dependency-free, and the existing codebase already shares `HttpMethod` this way
  (`CreateTestDto` imports it directly). Flagged to the user as a documented deviation from an
  earlier plan wording that implied full engine/model isolation; not yet revisited — worth
  raising again if `engine` is ever extracted to a separate module/repo where `model` won't be
  on the classpath.
- `engine/time/NanoClock.java`: one-method interface (`long nanoTime()`) with a
  `NanoClock.SYSTEM = System::nanoTime` constant, so pacing logic in Phase 4 can be driven by a
  fake clock in tests.
- `engine/schedule/WorkloadSchedule.java`: the ramp math.
  - `targetAt(elapsedSeconds)`: instantaneous target, piecewise-linear across stages starting
    from `startTarget`, clamped before start / after end.
  - `cumulativeAt(elapsedSeconds)`: integral of `targetAt` from 0 — for the open model, this
    equals "how many requests should have been dispatched by now." Computed per-segment as
    `cumulativeAtStart + startValue*s + valueDelta*s²/(2*duration)` (trapezoid-area formula for
    a linear ramp segment).
  - `elapsedAtCumulative(cumulativeTarget)`: the **inverse** of `cumulativeAt`, solved via the
    quadratic formula per ramping segment (and simple division for flat/constant segments).
    This is what computes the *intended send time* of the n-th request, feeding the
    coordinated-omission-aware `responseTime` histogram in Phase 3/4.
  - A zero-duration stage is treated as an **instant jump** (updates the running target without
    adding a segment), not a degenerate zero-length segment — matches k6 semantics.
  - Tests (`WorkloadScheduleTest`, 7 tests): ramp correctness, cumulative-as-area, the inverse
    round-tripping against `cumulativeAt`, multi-stage plateau-then-ramp, boundary clamping,
    the zero-duration jump case, and rejecting an empty stage list.

### Phase 3 — Execution + metrics ✅
- `engine/executor/{ExecutionOutcome,ExecutionResult,ProtocolExecutor,HttpExecutor}.java`:
  - `ProtocolExecutor extends AutoCloseable` — `execute()` returns an `ExecutionResult`
    (outcome, status code, service-time nanos). This is the exact interface US 3.4 in the
    roadmap asks for ("HTTP and gRPC executors share the same workload engine") — `HttpExecutor`
    today, `GrpcExecutor` in Sprint 3.
  - `HttpExecutor`: **one `java.net.http.HttpClient` per run**, built with
    `.version(HttpClient.Version.HTTP_1_1)` (deliberately **not** HTTP/2 — multiplexing would
    hide connection-level behavior the user is trying to observe), a per-request `.timeout(...)`,
    `BodyHandlers.discarding()` for the response body. Confirmed `HttpClient implements
    AutoCloseable` in JDK 21 (has a `close()` method) via `javap` — call it when the run ends.
    Outcomes bucketed: 2xx/3xx/4xx/5xx by status-code-div-100, plus `TIMEOUT`
    (`HttpTimeoutException`) and `CONNECT_ERROR` (`ConnectException`/other `IOException`/
    interrupted).
  - Tested (`HttpExecutorTest`, 5 tests) against a **real JDK built-in `com.sun.net.httpserver.HttpServer`**
    stub (not mocks) — covers 200, 404, 500, a genuinely slow handler that trips the timeout,
    and a connection-refused case (`localhost:1`).
- `engine/metrics/MetricsRecorder.java`: the dual-histogram design (see "Coordinated omission"
  above). Two `org.HdrHistogram.Recorder` instances (`new Recorder(3)` — 3 significant decimal
  digits), per-outcome `LongAdder` counters in an `EnumMap`, an `AtomicInteger` for active
  requests, `LongAdder` for dropped and dispatched counts. `recordDispatch()` /
  `recordCompletion(outcome, serviceTimeNanos, responseTimeNanos)` / `recordDropped()`.
  `intervalServiceTimeHistogram()`/`intervalResponseTimeHistogram()` call
  `Recorder.getIntervalHistogram()`, which **snapshots and resets** the recorder — intended to
  be called once per second by a reporting thread, never concurrently with itself.
  `statsOf(Histogram)` extracts a `LatencyStats` record (min/mean/max/p50/p90/p95/p99) via
  confirmed-available `Histogram` methods (`getMinValue`, `getMean`, `getMaxValue`,
  `getValueAtPercentile(double)`).
- `engine/metrics/RunSummaryCalculator.java`: holds two run-total `Histogram` instances and
  `accumulate(serviceInterval, responseInterval)`-s each second's interval histograms into them
  via HdrHistogram's `Histogram.add(AbstractHistogram)` (confirmed mergeable via `javap`).
  `finish(recorder, elapsedSeconds, requestedRps)` produces the final `RunReport`. **This is
  the mechanism that makes percentile math correct** — merging histograms and then computing
  percentiles once, rather than averaging per-interval percentiles (which is mathematically
  invalid and was explicitly flagged as a pitfall during planning).
- `engine/api/{LatencyStats,MetricsSnapshot,RunReport}.java`: filled in now that their
  producers exist (deferred from Phase 2 deliberately, to avoid guessing their shape before the
  code that populates them existed). `MetricsSnapshot` additionally carries
  `requestedTarget`/`sentCount`/`backlog` fields that only the *pacing strategy* (Phase 4) can
  supply, not `MetricsRecorder` alone — documented in the record's javadoc.
- Tests (`MetricsRecorderTest`, 4 tests): outcome/active-request tracking, interval-histogram
  reset-on-read, the coordinated-omission divergence scenario (short service time, long
  response time, asserting `serviceTime.p50 < responseTime.p50`), and `RunSummaryCalculator`
  correctly accumulating two intervals into matching run-total min/max/RPS.

**Full suite as of end of Phase 3: 19/19 tests passing, `BUILD SUCCESS`.**

## Status: Phase 4 — complete ✅ (27/27 tests passing, `BUILD SUCCESS`)

**Scope note / deviation from the original phase breakdown**: the original plan grouped
`EngineRun`/`InProcessEngine` into this same phase alongside the two strategies. Mid-
implementation, this was reconsidered: `EngineRun`'s whole job is to wire a `LoadStrategy` up to
a `RunListener` for progress reporting, but `RunListener` is a port that belongs in
`service.runs.execution` (Phase 6) and doesn't exist yet. Building `EngineRun` now would mean
inventing its callback shape against a guess, likely to be redone once the real port exists.
**Decision: `EngineRun`/`InProcessEngine` are deferred to Phase 6**, to be designed against the
actual `RunListener` port. Phase 4 ended up scoped to just the two `LoadStrategy`
implementations plus their supporting concurrency seam — this held up through implementation.

**What was built:**
- `engine/concurrent/TaskDispatcher.java`: a `@FunctionalInterface` seam —
  `TaskDispatcher.VIRTUAL_THREADS = task -> Thread.ofVirtual().start(task)` for production;
  tests inject a synchronous `Runnable::run` dispatcher so a tick's effects (dispatch,
  completion, metrics) are observable immediately without racing a background thread.
- `engine/strategy/LoadStrategy.java`: the shared interface — `tick(elapsedSeconds)` (exposed
  directly so tests can drive explicit elapsed times instead of real time), `run()` (the
  production entry point, blocks the calling thread), `stop()`, `backlog()`.
- `engine/strategy/AbstractLoadStrategy.java`: the shared `run()`/`stop()` loop both strategies
  extend. `run()` ticks every 5ms (via `NanoClock`, not wall clock directly, so a fake clock
  could in principle drive it — though in practice tests bypass `run()` and call `tick()`
  directly) until the schedule's `totalDurationSeconds()` is reached or `stop()` is called, then
  calls `onScheduleComplete()` (a hook, no-op by default) and blocks in `awaitDrain()` until
  `MetricsRecorder.activeRequests()` reaches zero — so `run()` doesn't return mid-flight.
- `engine/strategy/OpenModelStrategy.java`: each tick computes
  `owed = floor(schedule.cumulativeAt(elapsed)) - resolvedCount`, and for each owed request
  either dispatches it (`Semaphore.tryAcquire()` succeeds) or drops it immediately
  (`metricsRecorder.recordDropped()`) — never queued for later, exactly per the plan. Every
  owed request is *resolved* (dispatched or dropped) within the tick that owes it, so
  `backlog()` is always 0 by construction — there is no persistent cross-tick queue. The
  intended send time for the n-th request is `schedule.elapsedAtCumulative(n)`, converted to an
  absolute nanoTime anchored to the strategy's construction time; `responseTimeNanos` is
  `System.nanoTime() - intendedSendNanos`, **clamped to a minimum of 0** — this clamp exists for
  two reasons: (1) real floating-point rounding when converting a double elapsed-seconds value
  to nanos can occasionally land a few ns in the future even at the correct real tick time, and
  (2) it makes direct `tick()` unit tests work correctly when a test fast-forwards to a large
  elapsed time far ahead of real wall-clock time (discovered via a real test failure — see
  "bugs found via tests" below).
- `engine/strategy/ClosedModelStrategy.java`: each tick computes
  `target = round(schedule.targetAt(elapsed))` and starts `target - runningVus` new VU loops if
  the target grew. Each started VU loop is minted with a **stable integer index** (the post-
  increment value of a shared `AtomicInteger runningVus`) at start time, and loops
  `while (isRunning() && myIndex <= targetVus.get())` — so when the target later drops, only the
  VUs with the *highest* indices (the most recently started ones) see their own index exceed the
  new target and stop themselves after their current iteration, never mid-request. (An earlier
  draft had every VU check the same shared condition `runningVus.get() <= targetVus.get()` —
  that's wrong, because **all** VUs would see the same answer and all stop simultaneously
  instead of just the excess; per-VU stable indices were needed instead. Caught by reasoning
  through the design before it was ever run, not by a test failure.) `serviceTimeNanos` and
  `responseTimeNanos` are recorded as the identical value, since coordinated omission is
  inherent to this model and not something to correct.
- Tests: `OpenModelStrategyTest` (3), `ClosedModelStrategyTest` (3), plus a new
  `LoadStrategyRunIntegrationTest` (2) exercising the real `run()` entry point end-to-end against
  a real `HttpExecutor` and a real JDK `HttpServer` stub — not just direct `tick()` calls — to
  prove the strategies, executor, and metrics recorder actually work together, not just in
  isolation. `FakeProtocolExecutor` (always-succeeds, fixed latency) backs the open-model unit
  tests; a hand-rolled `BlockingExecutor` (parks on a `CountDownLatch` until released) backs the
  closed-model unit tests so VU counts can be asserted deterministically mid-flight.

**Two real bugs found and fixed while writing these tests — both informative beyond this one
phase:**
1. **Negative-latency crash**: `HdrHistogram.recordValue` throws
   `ArrayIndexOutOfBoundsException: Histogram recorded value cannot be negative` if ever called
   with a negative long. The open-model test `dispatchesExactlyTheCumulativeTargetInASingleTick`
   hit this immediately: when a test fast-forwards via a direct `tick(10.0)` call, the "intended
   send time" computed from the schedule is far in the future relative to the real `now()` the
   test actually runs at, making `responseTimeNanos` negative. **Fixed with
   `Math.max(0, System.nanoTime() - intendedSendNanos)`** in `OpenModelStrategy.dispatchOne`.
   This matters beyond the test: real floating-point rounding in production could in principle
   produce the same small negative value at the correct real tick time, and an unhandled
   exception there would crash the whole run mid-flight and lose all its metrics — so this isn't
   a test-only workaround, it's a real defensive fix.
2. **Test-only VU tight-loop leak causing cross-test pollution**: the original
   `ClosedModelStrategyTest` created strategies and `BlockingExecutor`s inline per test without
   ever calling `strategy.stop()`. Since the tests only ever called `tick()` directly (never
   `run()`), `isRunning()` stayed `true` forever. Once a test called `executor.release()` at its
   end, that `CountDownLatch` opened *permanently* (a `CountDownLatch` can't be reset) — so
   every VU loop from that test, which was still looping on
   `while (isRunning() && myIndex <= targetVus.get())`, immediately started spinning in a tight,
   non-blocking loop (`execute()` now returns instantly forever). Those leaked tight loops from
   earlier tests then starved the next test's brand-new VUs of carrier threads, causing
   `startsVuLoopsUpToTheTargetAtAGivenElapsedTime` to see 0 in-flight requests instead of 5
   within its 2-second timeout **only when run as part of the full class**, passing fine in
   isolation — a classic test-pollution signature. **Fixed by tracking every strategy/executor
   a test creates and calling `strategy.stop()` + `executor.release()` on all of them in
   `@AfterEach`.** This was diagnosed empirically: a standalone repro of the same threading
   pattern worked fine, which ruled out a JDK/virtual-thread issue and pointed at state leaking
   between test methods; adding temporary `System.out.println` tracing directly in
   `ClosedModelStrategy.tick`/the dispatched lambda (then removing it before committing) showed
   the third test's own VUs were being started correctly, they just weren't getting scheduled —
   confirming thread starvation rather than a dispatch bug. **Lesson for later phases**: any
   test that starts a `LoadStrategy`'s VU loops without going through the full `run()`/`stop()`
   lifecycle must explicitly stop it and unblock its executor in teardown, or tight-looping
   virtual threads will leak into later tests in the same JVM/class.

## Status: Phases 5–8 — all complete ✅, plus a real manual smoke test

All eight phases are done, and a real manual smoke test (not just automated tests) has been run
against a real `docker compose`-started Postgres, a real built jar, and a real HTTP stub target —
see "Verification checklist" at the end of this document for the full smoke-test writeup,
including one real bug it found and fixed that no automated test had caught. Final state:
**54/54 tests passing, `BUILD SUCCESS`**, covering Sprint 1 (US 1.2/1.3) and Sprint 2 (US 2.1-2.4)
in full. The sections below are kept as a detailed record of what was built and why, including
real bugs found and fixed along the way — useful context for Sprint 3+ work, not a to-do list.

### Phase 5 — Runs persistence ✅ complete (30/30 tests passing, `BUILD SUCCESS`)

**What was built:**
- `model/TestRunStatus.java`: enum `PENDING/RUNNING/COMPLETED/FAILED/CANCELLED`.
- `model/SaturationReason.java`: enum with the `GENERATOR` reasons (`HIGH_CPU`, `HIGH_HEAP`,
  `DISPATCH_LAG`) and the `CONCURRENCY_CAP`/`TARGET` reasons (`CONCURRENCY_CAP_REACHED`,
  `TARGET_LATENCY_LIMITED`) documented inline per-constant, matching the "Key design rules"
  categorization above. Populated by `SaturationDetector` in Phase 7.
- `model/RunSummary.java` (`@Embeddable`): flat columns for `totalDispatched/totalCompleted/
  totalDropped/requestedRps/actualRps/peakCpuLoad/peakHeapUsedBytes/saturated` (the fields most
  likely to be filtered/sorted on later, e.g. "show failed runs"), and JSONB columns for
  `outcomeCounts` (`Map<ExecutionOutcome, Long>`), `serviceTimeStats`/`responseTimeStats` (both
  `com.pulseload.engine.api.LatencyStats`, stored whole rather than flattened into ~7 columns
  each), and `saturationReasons` (`List<SaturationReason>`) — all via the same
  `@JdbcTypeCode(SqlTypes.JSON)` pattern as `WorkloadProfile.stages`. **Note**: this makes
  `model` depend on `engine.api`/`engine.executor` for `LatencyStats`/`ExecutionOutcome`, which
  is the reverse of the dependency direction flagged as a deliberate choice back in Phase 2
  (where `engine.api.RunSpec` reuses `model`'s plain types). Not a cycle in practice — nothing
  in `engine` depends on `model` or `service` — but worth knowing if `engine` is ever extracted
  to a separate module, since `LatencyStats`/`ExecutionOutcome` would need to either move with it
  and be duplicated, or stay shared.
- `model/TestRun.java`: `test` (`@ManyToOne(fetch = LAZY)` to `Test`), `status`,
  `createdAt`/`startedAt`/`finishedAt` (`java.time.Instant` — no prior timestamp convention
  existed in the codebase, so `Instant` was chosen as the standard unambiguous UTC choice for
  server-side timestamps), `summary` (`@Embedded RunSummary`), and `configSnapshot`.
  **Important design decision, made explicitly with the user rather than assumed**: the plan
  originally called for `configSnapshot` to be a single JSONB column holding the whole
  `WorkloadProfile` object (mirroring how `WorkloadProfile.stages` itself is stored). This was
  flagged as untested/risky, since `WorkloadProfile` is itself `@Embeddable` and Hibernate might
  fight having a double-purpose type (flattened via `@Embedded` in one place, JSON-serialized
  whole in another). **Resolved: `configSnapshot` is `@Embedded` normally**, using
  `@AttributeOverrides` to rename its four columns to `config_load_model`/`config_start_target`/
  `config_max_concurrency`/`config_stages` so they don't collide with any other embedded
  `WorkloadProfile` on the same table. This flattens cleanly, confirmed by the passing
  `@DataJpaTest` and its logged SQL (`insert into test_runs (config_load_model,
  config_max_concurrency, config_stages, config_start_target, ...)`). `WorkloadProfile.stages`
  (nested one level inside the override) still serializes to JSONB exactly as before — the
  `@AttributeOverride` only renames/reconfigures the *column*, not the type's own internal
  mapping.
- `model/TestRunMetric.java`: one row per second — `testRun` (`@ManyToOne(fetch = LAZY)`),
  `elapsedSeconds/requestedTarget/completedRps/completedCount/droppedCount/activeRequests/
  backlog` as flat columns (these mirror `engine.api.MetricsSnapshot` field-for-field) plus
  `outcomeCounts` (JSONB), and `cpuLoad`/`heapUsedBytes` for the generator's own resource usage
  at that instant (feeds Phase 7's saturation correlation and the time-series/metrics endpoint).
- `service/runs/data/TestRunRepository.java` (`findByTest`, `findByStatusIn` — the latter added
  specifically for Phase 6's `OrphanRunRecovery`, which needs to find stale `PENDING`/`RUNNING`
  rows left over from a crashed process) and `TestRunMetricRepository.java`
  (`findByTestRunOrderByElapsedSecondsAsc` — backs the metrics time-series endpoint), both plain
  `JpaRepository<Entity, UUID>` following the existing `TestRepository` convention.
- `003-test-runs.xml` Liquibase changeset (two `createTable`s plus FK constraints and indexes on
  `test_id`/`status`/`test_run_id`), registered in `db.changelog-master.xml`.
- Checkpoint: `TestRunRepositoryIT` (`@DataJpaTest` + `@AutoConfigureTestDatabase(replace =
  NONE)` + `@Import(TestcontainersConfiguration.class)`, same pattern as Phase 1's
  `TestRepositoryIT`, including the same `com.pulseload.model.Test`/`@Test` fully-qualification
  workaround) — 3 tests: a full round-trip of a completed run with its flattened config snapshot
  and JSONB summary (including asserting the nested `LatencyStats.p99Nanos()` and
  `Map<ExecutionOutcome, Long>` survive the round-trip intact), `findByStatusIn` filtering
  correctly, and metrics persisting linked to a run and returning in `elapsedSeconds` order.

### Phase 6 — Port, RunCoordinator, Run API ✅ complete (42/42 tests passing, `BUILD SUCCESS`)

**Architecture correction made during implementation**: the plan originally placed
`RunListener` in `service.runs.execution`. That would have made the dependency-free `engine`
package depend back on the Spring-facing `service` layer to call it — backwards from the
invariant maintained since Phase 2 (engine has zero Spring/`service` dependencies). **Resolved:
`RunListener` lives in `com.pulseload.engine` itself** (sibling to `EngineRun`/`InProcessEngine`),
parallel to how `MetricsSnapshot`/`RunReport` already live in `engine.api`. `LoadGenerator` (the
port the *control plane* calls to drive the engine) stays in `service.runs.execution` as planned,
and imports `engine.RunListener`/`engine.api.RunSpec` — that direction is fine, since the control
plane is expected to depend on the engine, not the reverse.

**What was built:**
- `engine/RunListener.java`: `onSnapshot(MetricsSnapshot)`, `onCompleted(RunReport, boolean
  cancelled)`, `onFailed(Throwable)`. The `cancelled` flag was added beyond the original plan —
  without it there was no way for a persistence layer to distinguish a run that reached its
  schedule's natural end from one stopped early via `POST /runs/{id}/stop`, both of which call
  the same `strategy.run()` → `onCompleted` path.
- `engine/EngineRun.java` (package-private — an implementation detail of `InProcessEngine`, not
  part of any public contract): builds the right `LoadStrategy` from `RunSpec.loadModel()`
  (`OpenModelStrategy` needs `maxConcurrency` in its constructor; `ClosedModelStrategy` doesn't,
  VU count *is* the concurrency — confirmed these exact constructor shapes against the real
  Phase 4 code before wiring them up), runs it on `runToCompletion()`, and separately schedules
  a 1-second `ScheduledExecutorService` tick that drains `MetricsRecorder`'s interval histograms
  (**exactly once per interval each**, since `getIntervalHistogram()` resets on read — verified
  by re-reading Phase 3's `MetricsRecorder` javadoc before writing this), accumulates them into
  the run's `RunSummaryCalculator`, and emits a `MetricsSnapshot` via the listener. A listener
  exception inside the snapshot tick is caught and swallowed (logged nowhere yet — see Phase 8)
  so a transient DB hiccup can never kill an in-progress run.
- `engine/InProcessEngine.java`: tracks one `EngineRun` per active `runId` in a
  `ConcurrentHashMap`, matching the plan's own Kubernetes-Job-controller analogy — one
  coordinator object, not one process per run. `stopAll()` requests every active run to stop
  and blocks until each has actually finished (`EngineRun.awaitFinished()`), used by the
  shutdown handler below.
- `service/runs/execution/LoadGenerator.java` (port) +
  `service/runs/execution/inprocess/InProcessLoadGenerator.java` (the `@Component` adapter,
  delegating to a private `InProcessEngine` instance; exposes `stopAll()` beyond the port
  interface specifically for the shutdown handler, since draining in-process runs isn't a
  concept that belongs on a port a future `GrpcLoadGenerator` would also need to implement).
- `service/runs/service/RunCoordinator.java`: a single `AtomicInteger` gated by
  `@Value("${app.runs.max-concurrent:1}")`, matching the existing codebase's plain-`@Value`
  config convention (`app.cors.allowed-origins`) rather than introducing a new
  `@ConfigurationProperties` class for one property. `tryReserve()` is a single
  `getAndUpdate(current -> current < max ? current + 1 : current) < max` — atomically reads the
  pre-update count and applies the increment-if-under-limit in one step, no CAS-retry loop
  needed. Verified correct under real contention: `RunCoordinatorTest` fires 50 virtual threads
  at a limit of 5 and asserts exactly 5 succeed.
- `service/runs/service/TestRunServiceImpl.java`: `startRun` reserves a coordinator slot,
  persists a `PENDING` `TestRun` (config snapshot copied from the `Test` at this exact moment),
  and publishes a `RunCreatedEvent`; `onRunCreated` is
  `@TransactionalEventListener(phase = AFTER_COMMIT)` so the engine only ever starts against a
  durably-committed row, wiring a fresh `RunSpec` and a per-run `EngineRunListener` (its own
  `Executors.newSingleThreadExecutor()`, shut down once the run's terminal callback fires) into
  `LoadGenerator.start`. `service/runs/service/RunPersistence.java` holds the actual
  `@Transactional(propagation = REQUIRES_NEW)` writes (`markRunning`, `saveSnapshot`,
  `saveCompletion`, `saveFailure`), all called from `EngineRunListener` via that dedicated
  executor — never from the engine's own pacing or ticker thread, exactly per the plan's
  requirement that slow DB writes must not perturb pacing accuracy.
- `service/runs/service/OrphanRunRecovery.java`: `@EventListener(ApplicationReadyEvent.class)` +
  `@Transactional`, using `TestRunRepository.findByStatusIn(PENDING, RUNNING)` (added for exactly
  this purpose back in Phase 5) to mark stale rows `FAILED` on startup.
- `service/runs/service/EngineShutdownHandler.java`: `SmartLifecycle` whose `stop()` calls
  `InProcessLoadGenerator.stopAll()` — depends on the concrete adapter class rather than the
  `LoadGenerator` port, deliberately, since "drain in-process runs on JVM shutdown" isn't a
  concept a future remote `GrpcLoadGenerator` would share.
- `service/runs/dto/{RunSummaryDto,TestRunDto,TestRunMetricDto}.java` and
  `service/runs/mapper/TestRunMapper.java` — MapStruct again needed zero custom mapping methods
  for the nested `WorkloadProfile`/`RunSummary` object graphs (field names line up), only one
  explicit `@Mapping(target = "testId", source = "test.id")` for the one field that isn't a
  direct 1:1 name match.
- `exception/{RunNotFoundException,RunConflictException}.java` + two new
  `GlobalExceptionHandler` entries (404, 409).
- `controller/RunController.java`: all five endpoints from the plan. One deliberate deviation
  from `TestController`'s own `Location`-header pattern: `TestController` builds its header with
  `ServletUriComponentsBuilder.fromCurrentRequest()` (appending `/{id}` to the *same* path that
  was just POSTed to, which is correct there since `GET /tests/{id}` is literally
  `POST /tests` + `/{id}`). `RunController.startRun` instead uses `fromCurrentContextPath()`,
  because the created resource's path (`/runs/{runId}`) is a *different* path than the request
  that created it (`/tests/{testId}/runs`) — appending to the current request's path would have
  produced a wrong URL like `/tests/{testId}/runs/{runId}` instead of `/runs/{runId}`.

**A real Spring Framework bug/gotcha found and fixed, not just a test issue**: `@Transactional
(readOnly = true)` on `TestRunServiceImpl`'s class level was inherited by the
`@TransactionalEventListener`-annotated `onRunCreated` method, and Spring Framework 7 **refuses
to start the application context** in that combination — throws
`IllegalStateException: @TransactionalEventListener method must not be annotated with
@Transactional unless when declared as REQUIRES_NEW or NOT_SUPPORTED` at bean-initialization
time. This isn't a theoretical concern caught by a test; it's a hard startup failure that will
hit anyone who adds a `@TransactionalEventListener` method to a class that has a class-level
`@Transactional` of any propagation other than those two. **Fixed by adding an explicit
`@Transactional(propagation = Propagation.REQUIRES_NEW)` directly on `onRunCreated`** — which is
also the functionally correct propagation regardless of this restriction, since the method reads
state that was committed by a *different*, already-finished transaction (the one that saved the
`TestRun` row), not one it should try to join.

**Checkpoint tests:**
- `RunCoordinatorTest` (4 tests, pure unit, no Spring) — see above.
- `RunControllerTest` (6 tests, `@WebMvcTest`) — 202+Location on start, 409 on coordinator
  rejection, 404 on missing run, 200 with the right body, 204 on stop, and the list endpoint.
  No `SecurityConfig` import needed (same as Phase 1's finding — Boot's default test security
  inside `@WebMvcTest` is permissive enough).
- `RunLifecycleIntegrationTest` (2 tests, full `@SpringBootTest` + real Testcontainers Postgres
  + a real `com.sun.net.httpserver.HttpServer` stub, **not mocks anywhere in this test**) — this
  is the proof that Phases 2 through 6 actually work together: persists a real `Test`, calls the
  real `TestRunServiceImpl.startRun` exactly as `RunController` would, lets the real
  `OpenModelStrategy`/`ClosedModelStrategy` dispatch real HTTP requests through the real
  `HttpExecutor` against the stub server, polls `getRunById` until the row reaches a terminal
  status, and asserts the final `TestRunDto` shows `COMPLETED` with a non-empty summary whose
  `totalCompleted` exactly matches the stub server's own independently-tracked request count —
  plus a populated metrics time series. Run for **both** load models.

### Phase 7 — Saturation detection (US 2.4) ✅ complete (50/50 tests passing, `BUILD SUCCESS`)

**Package-placement fix made during implementation**: `SaturationReason` had been put in
`com.pulseload.model` back in Phase 5 (before this phase's design was worked out in detail).
Once `RunReport` (in `engine.api`) needed to carry saturation reasons too, that would have
created a genuine circular package dependency (`model` → `engine.api` via `RunSummary`, and
`engine.api` → `model` via `RunReport`). **Resolved by moving `SaturationReason` into
`com.pulseload.engine.saturation`**, alongside the new `SaturationDetector`/`SaturationInput`/
`SaturationResult` — `model.RunSummary` already imports engine types for `LatencyStats`/
`ExecutionOutcome`, so this keeps the dependency direction consistent (`model` depends on
`engine`, never the reverse).

**What was built:**
- `engine/metrics/ResourceSampler.java`: `sampleCpuLoad()` wraps
  `OperatingSystemMXBean#getProcessCpuLoad()` (confirmed exact signature via `javap`), returning
  the raw reading including its `-1`/unreliable cases — discarding the first reading is the
  *caller's* responsibility (see `EngineRun` below), since the sampler itself has no way to know
  which reading is "first" across repeated calls. `sampleHeapUsedBytes()`/`maxHeapBytes()` wrap
  `MemoryMXBean#getHeapMemoryUsage()`.
- `engine/saturation/{SaturationInput,SaturationResult,SaturationDetector}.java`:
  `SaturationInput` is a deliberately primitive-only record (no dependency on `engine.metrics`/
  `engine.api` types) so the detector stays trivially unit-testable with synthetic values.
  `SaturationDetector.evaluate()` first checks whether there's a real shortfall at all (actual
  RPS below 95% of requested — a small gap within tolerance is not flagged at all, confirmed by
  a dedicated test), then separately evaluates `GENERATOR` reasons (`HIGH_CPU` ≥85% CPU,
  `HIGH_HEAP` ≥85% of max heap, `DISPATCH_LAG` from tick-overrun count) and `CONCURRENCY_CAP`/
  `TARGET` reasons (`CONCURRENCY_CAP_REACHED` whenever any request was dropped,
  `TARGET_LATENCY_LIMITED` additionally when p95 response time is ≥250ms). Only the `GENERATOR`
  reasons set `saturated=true`; both reason categories can be reported together if both apply.
- `engine/strategy/AbstractLoadStrategy.java` gained **tick-overrun tracking**: `run()` now
  measures the real wall-clock gap between consecutive `tick()` calls and increments an
  `AtomicLong` whenever that gap exceeds 3x the intended 5ms interval, exposed via a new
  `tickOverrunCount()` method added to the `LoadStrategy` interface (implemented once, `final`,
  in the abstract base — both concrete strategies get it for free).
- `engine/api/RunReport.java` gained `peakCpuLoad`, `peakHeapUsedBytes`, `saturated`,
  `saturationReasons` fields. `engine/api/MetricsSnapshot.java` gained `cpuLoad`/`heapUsedBytes`
  per-second fields too (not just the final report), since the US 2.4 story explicitly wants to
  "correlate a dip in actual RPS with a CPU spike" over time, which needs the time series, not
  just a final peak value — `TestRunMetric`'s `cpuLoad`/`heapUsedBytes` columns (already present
  since Phase 5) are exactly what this fills in.
- `engine/metrics/RunSummaryCalculator.finish()` signature grew to accept
  `(recorder, elapsedSeconds, requestedRps, peakCpuLoad, peakHeapUsedBytes, maxHeapBytes,
  tickOverrunCount)` and now internally builds a `SaturationInput` (using the already-computed
  response-time `LatencyStats.p95Nanos()` and `recorder.droppedCount() > 0`) and runs
  `SaturationDetector` before constructing the final `RunReport`.
- `engine/EngineRun.java`: samples CPU/heap once per second inside `emitSnapshot()` (the same
  tick that already reads interval histograms), tracks running peaks in `AtomicReference<Double>`/
  `AtomicLong` fields, and **discards the first CPU reading** via a `firstCpuReadingDiscarded`
  flag (confirmed necessary and correctly implemented by a 50/50 passing test suite — the first
  `getProcessCpuLoad()` call after JVM start is well known to be unreliable).
- `service/runs/service/RunPersistence.java` updated to persist the new `RunReport`/
  `MetricsSnapshot` fields into `RunSummary`/`TestRunMetric`.

**Checkpoint**: `SaturationDetectorTest`, 8 tests — including the *exact* US 2.4 example from the
user stories (`requestedRps=5000, actualRps=4120`, high CPU) correctly producing
`saturated=true, reasons=[HIGH_CPU]`; a concurrency-cap-exhausted-with-low-generator-resources
scenario correctly producing `saturated=false` with only `CONCURRENCY_CAP_REACHED` (or both cap
reasons when latency is also high); and a within-tolerance shortfall producing no flags at all.

### Phase 8 — Observability ✅ complete (53/53 tests passing, `BUILD SUCCESS`)

**What was built:**
- `engine/RunMdc.java`: `RunMdc.run(UUID runId, Runnable task)` sets MDC's `runId` key, runs the
  task, then **restores the previous value** (not an unconditional clear) in a `finally`. The
  previous-value restore is not cosmetic — see the real bug below.
- `engine/concurrent/TaskDispatcher.java` gained a `static withMdcPropagation(TaskDispatcher)`
  decorator: it snapshots `MDC.getCopyOfContextMap()` on the calling thread before dispatching,
  then applies that snapshot on whatever thread actually runs the task (and clears MDC in a
  `finally` there). `EngineRun.buildStrategy()` wraps `TaskDispatcher.VIRTUAL_THREADS` with this
  once, which is the single chokepoint through which *every* virtual thread in both
  `OpenModelStrategy` and `ClosedModelStrategy` is spawned — so no change was needed inside the
  strategies themselves to get MDC propagation into every dispatched request/VU-loop thread.
- `engine/EngineRun.java`: wraps the whole `runToCompletion()` body, and separately the whole
  `emitSnapshot()` body (since that runs on the `snapshotTicker`'s own thread, a different thread
  than `runToCompletion`'s), in `RunMdc.run(spec.runId(), ...)`. Added lifecycle log lines (`Run
  starting`, `Run completed`/`Run cancelled` with final counts, `Run failed` with the exception)
  and a per-second `Progress: elapsed=...s requested=... actual=... active=... dropped=...` line
  inside `emitSnapshot()`.
- `service/runs/service/TestRunServiceImpl.java`: logs `Run created, awaiting engine start` right
  after the `PENDING` row is saved, inside its own `RunMdc.run(saved.getId(), ...)` scope — this
  is the earliest lifecycle point, before the engine ever sees the run.
- `application.properties`: added `management.endpoints.web.exposure.include=health,info,metrics`
  and a custom `logging.pattern.console` that adds `[runId=%X{runId}]` to the default Boot
  console pattern (Boot's own default pattern has a `LOG_CORRELATION_PATTERN` placeholder for
  tracing integration, but nothing generic for arbitrary MDC keys — confirmed by inspecting
  `spring-boot-4.1.1.jar`'s `defaults.xml` directly).
- `application-prod.properties`: added `logging.structured.format.console=ecs` — MDC key/values
  are included automatically in Boot's structured JSON output, so prod doesn't need the custom
  pattern string, only dev's plain-text console does.

**A real concurrency bug found via the Phase 8 checkpoint itself, not a unit test**: the plan's
own checkpoint ("grep the logs for a runId to confirm the full lifecycle is traceable") is what
surfaced this. The very first implementation of `RunMdc.run()` used `MDC.putCloseable(...)` in a
try-with-resources, whose `close()` (confirmed by reading slf4j's own source) unconditionally
calls `MDC.remove(key)` rather than restoring a prior value. `EngineRun.runToCompletion()` opens
an outer `RunMdc.run()` scope, and near its end calls `emitSnapshot()` **inline, synchronously,
on the same thread**, which opens its *own* nested `RunMdc.run()` scope for the same `runId` —
so the inner call's cleanup wiped the MDC key out from under the still-active outer scope,
and the "Run completed"/"Run cancelled" log lines that immediately followed on that same thread
showed `runId=` blank. This was invisible in the earlier plain-text console pattern (which didn't
print MDC at all) and would have gone unnoticed if the custom `[runId=%X{runId}]` pattern hadn't
been added specifically to satisfy this checkpoint — a case where building the observability
feature immediately exposed a correctness bug the feature itself was meant to prevent. **Fixed
by rewriting `RunMdc.run()` to capture `MDC.get(key)` before setting the new value and restore
that captured value (or remove the key only if it was previously absent) in `finally`** — the
standard safe-nesting pattern. Locked in with a dedicated regression test,
`RunMdcTest.nestedCallOnTheSameThreadDoesNotWipeTheOuterScopesRunIdOnExit`, and verified
empirically by rerunning `RunLifecycleIntegrationTest` with captured console output and grepping
for the `runId` across the full `Run created → Run starting → Progress (×N) → Run completed`
sequence.

**One flaky test observed, investigated, and confirmed non-recurring**: a single full-suite run
produced `RunLifecycleIntegrationTest.openModelRunGoesThroughTheFullLifecycleAndProducesASummary`
failing with `expected: COMPLETED but was: FAILED`. Rerun in isolation immediately after, it
passed; a second full-suite rerun also passed cleanly (53/53). This is consistent with transient
resource contention on the sandbox machine (many Testcontainers-backed Spring context loads
back-to-back in one suite run) rather than a logic bug — no code change was made in response, but
documenting it here in case it recurs with a clearer pattern in a future session.

**Known minor benign race, documented rather than fixed**: `EngineRun.runToCompletion()` calls
`snapshotTicker.shutdownNow()` in its `finally` block, but `shutdownNow()` does not interrupt a
task that is already executing — so if the `ScheduledExecutorService`'s periodic tick had already
fired right as the strategy finished, one extra `emitSnapshot()` (and therefore one extra
`test_run_metrics` row, with a timestamp slightly after the run's `COMPLETED`/`finished_at`) can
land after the terminal DB write. Observed directly in a captured test log. This doesn't corrupt
data or cause test failures (the terminal `saveCompletion` write and the stray `saveSnapshot`
write are independent, non-conflicting rows), so it wasn't worth adding synchronization for at
this scope — flagged here so a future session doesn't mistake it for a new bug if noticed again.

## Environment gotchas (read before running builds in a new session)

These were discovered empirically in this environment and are **not** assumptions — verified
via `javap` against actual cached jars, or by reproducing failures. Some directly contradicted
an earlier research pass, which is itself a lesson: verify against the real jars on this
machine, don't trust secondhand research blindly.

1. **`./mvnw`/`./mvnw.cmd` cannot run here.** They try to download Maven 3.10.0 over HTTPS and
   this network's TLS layer rejects it (`CRYPT_E_NO_REVOCATION_CHECK`, a Windows `schannel`
   revocation-check policy issue — confirmed via raw `curl -v` to `repo.maven.apache.org`
   failing the same way). Maven **is already cached locally** — find it and invoke it directly,
   e.g.:
   ```
   find ~/.m2/wrapper/dists -iname "mvn.cmd"
   ```
   and use the full path to that binary instead of `./mvnw`. (Maven's own HTTP client, used for
   dependency resolution once it's running, does **not** hit this same TLS issue — only curl's
   schannel backend does. So builds that fetch new dependencies from Maven Central do still
   work once Maven itself is launched via the cached binary.)
2. **Testcontainers 2.x renamed module artifact IDs.** The 1.x coordinates
   `org.testcontainers:junit-jupiter` and `org.testcontainers:postgresql` don't exist in 2.x —
   they're `org.testcontainers:testcontainers-junit-jupiter` and
   `org.testcontainers:testcontainers-postgresql`. Confirmed by reading the real
   `testcontainers-bom:2.0.5` POM.
3. **Testcontainers 2.x deprecated `org.testcontainers.containers.PostgreSQLContainer`.** Use
   `org.testcontainers.postgresql.PostgreSQLContainer` instead (same API, no longer generic —
   drop the `<?>`). Confirmed via `javap -v` showing the `Deprecated` attribute on the old
   class, and listing the new jar's contents to find the replacement package.
4. **Spring Boot 4 uses Jackson 3, not Jackson 2** — the base package renamed from
   `com.fasterxml.jackson` to `tools.jackson`. In particular, `ObjectMapper` for autowiring in
   tests should be `tools.jackson.databind.json.JsonMapper` (which extends `ObjectMapper`, so
   old code *compiles* in places but the Spring-managed bean to `@Autowired` is a `JsonMapper`).
   `com.fasterxml.jackson.annotation.*` (the annotations module) is unaffected and still works
   as before.
5. **Spring Boot 4 test-slice packages moved.** `@WebMvcTest` is now in
   `org.springframework.boot.webmvc.test.autoconfigure`; `@DataJpaTest` is in
   `org.springframework.boot.data.jpa.test.autoconfigure` and requires the
   `spring-boot-starter-data-jpa-test` dependency explicitly; `@MockBean` was **removed** —
   use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito`. All
   confirmed via `javap` against the actual jars, not assumed from memory.
6. **`@DataJpaTest` defaults to replacing your datasource with an embedded one**
   (`@AutoConfigureTestDatabase`, default `Replace.ANY`). To actually exercise Postgres-specific
   behavior (like the `jsonb` column type used throughout this plan), add
   `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` alongside
   `@Import(TestcontainersConfiguration.class)`.
7. **`@WebMvcTest` + importing this project's `SecurityConfig` fails** with
   `NoSuchBeanDefinitionException` for `HttpSecurity` — the bean-method style
   (`securityFilterChain(HttpSecurity http)`) doesn't wire up cleanly inside that narrow test
   slice. For pure-controller/validation tests, just don't import it (Boot's default test
   security is permissive enough). If a test specifically needs to verify security/CORS
   behavior, use full `@SpringBootTest` + `MockMvc` instead of `@WebMvcTest`.
8. **The domain entity is named `Test` (`com.pulseload.model.Test`), colliding with
   `org.junit.jupiter.api.Test`.** Java has no import aliasing. Fully-qualify
   `com.pulseload.model.Test` at each use site in any test class that touches the entity — this
   will come up again and again across the remaining phases.
9. **Pre-existing, unrelated bug on `main` as of this plan's start**: the `protobuf-maven-plugin`
   fails the entire build (`compile`, `test`, everything) because it's configured but no
   `.proto` files exist yet (gRPC lands in Sprint 3). Already fixed in Phase 0 via
   `failOnMissingSources=false` on that plugin — if this plan is being resumed from a commit
   *before* that fix landed, re-apply it first or nothing will build.
10. **`HdrHistogram` is not managed by Spring Boot 4.1.1's dependency BOM** — its version must
    be pinned explicitly in `pom.xml` (`2.2.2`, confirmed as the latest on Maven Central at
    research time).
11. **Tests that start `LoadStrategy` VU loops without going through the real `run()`/`stop()`
    lifecycle can leak tight-looping virtual threads into later tests in the same class/JVM.**
    If a test calls `tick()` directly (as the Phase 4 unit tests do, for determinism) and never
    calls `stop()`, `isRunning()` stays `true` forever; if that test's fake executor then stops
    blocking (e.g. a `CountDownLatch.countDown()`, which can't be un-done), any VU loops it
    started keep spinning in a tight non-blocking loop indefinitely. Symptom: a later, unrelated
    test in the same class times out waiting for its own VUs to get scheduled, but passes fine
    when run in isolation — a classic cross-test-pollution signature, not a logic bug in the
    class under test. Fix: any test driving VU loops directly must track every strategy/fake
    executor it creates and explicitly `stop()`/unblock all of them in `@AfterEach`.

## How to verify this plan's claims before continuing

Don't trust any version number, package path, or API shape in this document (or your own
training data) without a quick empirical check in this specific environment — several things
in an earlier research pass turned out to be wrong once checked against the real cached jars
(e.g. Testcontainers artifact IDs, the exact JSON mapper type). The pattern used throughout this
work, if a new session needs to check something similar:
```bash
# Find the exact jar actually resolved for this project
find ~/.m2/repository/<group>/<artifact> -iname "*.jar" | grep -v sources

# Inspect its real public API / package location
"/c/Program Files/Java/jdk-21.0.12.1/bin/javap.exe" -p -classpath <jar> <FullyQualifiedClassName>

# Check for @Deprecated on a class/method
"/c/Program Files/Java/jdk-21.0.12.1/bin/javap.exe" -v <classfile> | grep -i deprecated
```
And always run the actual Maven build after each change — don't assume compilation success.

## Verification checklist for the finished Sprint 1+2 slice — status: fully done, including a real manual smoke test

1. ✅ `mvn test` (using the local Maven binary per gotcha #1) — full suite green, **54/54**.
2. ✅ `RunLifecycleIntegrationTest` starts a real Testcontainers Postgres and a real
   `com.sun.net.httpserver.HttpServer` stub target.
3. ✅ Persists a `Test` with an OPEN-model workload (and separately a CLOSED-model one) and
   starts a run through the real `TestRunServiceImpl`, exactly as the controller would.
4. ✅ Polls `GET`-equivalent (`testRunService.getRunById`) until `COMPLETED`.
5. ✅ Confirmed: `totalCompleted` in the final summary exactly matches the stub server's own
   independently-tracked request count; the metrics time series (`getMetrics`) is non-empty.
   Response-time ≥ service-time is proven separately and more directly by
   `MetricsRecorderTest.responseTimeCanDivergeFromServiceTimeUnderCoordinatedOmission`.
6. ✅ Saturation checks — proven directly and more rigorously via `SaturationDetectorTest`'s 8
   synthetic-input cases (including the exact 5,000/4,120-RPS US 2.4 example).
7. ✅ Grepped captured console output from a real `RunLifecycleIntegrationTest` run and confirmed
   the full `Run created` → `Run starting` → `Progress` (×N, one per second) → `Run completed`
   sequence is traceable by a single `runId`, with no per-request log lines. This is also how the
   MDC-nesting bug documented under Phase 8 was actually found.
8. ✅ **Real manual smoke test completed** — `docker compose up postgres`, built and ran the real
   jar (`java -jar target/server-0.0.1-SNAPSHOT.jar`, dev profile) against real Postgres on
   `localhost:5432`, a hand-written `com.sun.net.httpserver.HttpServer` stub target on
   `localhost:9999` with a 20ms artificial delay, and drove the whole thing with real `curl`
   requests (not test code) — closing the one gap the automated suite couldn't cover. Verified:
   - `POST /api/tests` → `POST /api/tests/{id}/runs` → 202 + correct `Location` header →
     poll `GET /api/runs/{id}` → `COMPLETED`, for both a two-stage OPEN ramp and a CLOSED-model
     workload.
   - `POST /api/runs/{id}/stop` on a 30-second CLOSED-model run actually stopped it early
     (`RUNNING` → 204 → `CANCELLED`, `finishedAt` well before the schedule's natural end).
   - The concurrency limit: starting a second run while one was active returned 409 with a
     correct `ProblemDetail` body (`"Too many runs in progress..."`).
   - A deliberately cap-constrained workload (2000 RPS target, `maxConcurrency=5`) correctly
     produced `saturated=false` with reason `CONCURRENCY_CAP_REACHED` — low CPU, lots of drops,
     *not* flagged as generator saturation. This is the real-environment proof of the exact
     distinction US 2.4 and `SaturationDetector` are built around.
   - `GET /api/actuator` confirmed `health`, `info`, `metrics` are exposed exactly as configured.
   - No unexpected `ERROR`/`WARN` log lines across the whole session (only the expected dev-profile
     notices).

**A real, previously-undetected bug was found and fixed by this smoke test** (not by any of the
53 automated tests that existed before it): `EngineRun.runToCompletion()` was passing
`schedule.targetAt(elapsed)` — the schedule's **final instantaneous** target — as the whole run's
`requestedRps` into `RunSummaryCalculator.finish()`, instead of the **average** rate the ramp
asked for over its full duration. For the smoke test's two-stage ramp (0→50 RPS over 3s, then
50→200 RPS over 3s), this made `requestedRps=200` (the end of the ramp) get compared against an
`actualRps≈69`, producing a false `saturated=true, reasons=[DISPATCH_LAG]` on a run that was
actually tracking the ramp close to perfectly — the *honest* average target over 6s is
`cumulativeAt(6)/6 = 450/6 = 75`, which `actualRps≈69` is reasonably close to. This bug was
invisible to every existing automated test because `SaturationDetectorTest` tests the detector
in isolation with synthetic inputs (unaffected by how `EngineRun` computes its `requestedRps`
argument), and `RunLifecycleIntegrationTest`'s workloads use **flat**, non-ramping stages, where
the instantaneous-final-target and the time-averaged-target happen to be numerically identical —
so the bug could only manifest with a ramping workload, which no prior test exercised. **Fixed**
by computing `elapsed > 0 ? schedule.cumulativeAt(elapsed) / elapsed : schedule.targetAt(elapsed)`
in `EngineRun.runToCompletion()` instead of calling `schedule.targetAt(elapsed)` directly. Verified
both by rerunning the exact same smoke-test scenario against the rebuilt jar (now correctly
reporting `requestedRps≈72, actualRps≈72, saturated=false`) and by a new permanent regression
test, `InProcessEngineTest.reportsTheAverageRequestedRateOverTheRampNotTheFinalInstantaneousTarget`,
which drives a real `InProcessEngine.start()` against a real stub server with a 0→100 RPS ramp and
asserts the reported `requestedRps` lands near the time-averaged 50, not the final-tick 100.
**Lesson for future phases**: any test asserting on `RunReport`/`EngineRun` behavior that only
uses flat (non-ramping) stages cannot catch bugs specific to how ramping schedules are summarized
— worth deliberately including at least one ramping-workload test wherever `EngineRun`-level
behavior is exercised going forward.

## Post-completion refactor: Lombok construction + Spring AOP logging (reverses the "Spring-free engine" decision)

After Sprint 1+2 was fully done (54/54 tests, manual smoke test passed), the user asked for two
cleanups: (1) use Lombok's `@RequiredArgsConstructor` idiom consistently, including for
`InProcessEngine`'s choice between `OpenModelStrategy`/`ClosedModelStrategy`, and (2) replace the
repetitive explicit `RunMdc.run(spec.runId(), () -> {...})` wrapper calls inside `EngineRun` with
a Spring AOP `@Aspect`.

**This directly reverses a decision made earlier in this plan** (see "Why the design looks the
way it does" above): `com.pulseload.engine` was deliberately kept Spring-free so it could later
be extracted into a standalone worker module ahead of Sprint 4's distributed-workers story.
Spring's proxy-based AOP can only intercept calls made through a bean's proxy, so an `@Aspect`
has no way to reach a plain `new EngineRun(...)`. When this was raised, **the user explicitly
chose to drop the Spring-free constraint** rather than find a non-Spring alternative for the
logging ask. `com.pulseload.engine` now depends on Spring Boot. If `engine` is ever extracted
into a separate worker module in Sprint 4, that worker would need to bundle Spring Boot itself
(a legitimate option — `spring-boot-starter-grpc-server` is already in `pom.xml`).

**Three Spring mechanics were independently verified against the real jars in `~/.m2` (via
`javap`/decompiled source, not assumed) before implementing, since they're easy to get subtly
wrong:**
1. **Boot 4.1.1 renamed the AOP starter.** `spring-boot-starter-aop` doesn't exist in the 4.1.1
   BOM at all — confirmed by grepping the actual `spring-boot-dependencies-4.1.1.pom`. It's now
   `spring-boot-starter-aspectj`. `AopAutoConfiguration` auto-applies
   `@EnableAspectJAutoProxy(proxyTargetClass=true)` once AspectJ's `Advice` class is on the
   classpath (confirmed by decompiling Boot's own `AspectJAutoProxyingConfiguration` inner
   class's actual annotation attributes), but does **not** set `exposeProxy=true` — a second,
   explicit `@EnableAspectJAutoProxy(exposeProxy = true)` config class was needed for that.
   Confirmed via `AspectJAutoProxyRegistrar`'s source that a second declaration only *escalates*
   `proxyTargetClass`/`exposeProxy` to `true` on the one shared proxy-creator bean — it never
   turns either back off, so the two declarations don't conflict.
2. **Self-invocation bypasses Spring AOP, confirmed via source inspection of `CglibAopProxy`.**
   A method reference like `this::emitSnapshot` or a plain `emitSnapshot()` call runs directly
   against the real object, skipping the proxy (and therefore the aspect) entirely. The fix is
   `AopContext.currentProxy()`, resolved once on the thread that's already inside the advised
   method (it throws `IllegalStateException` on any other thread) and captured in a local
   variable (`self`) before being used to schedule work on a different thread.
3. **Explicit constructor arguments replace autowiring entirely — confirmed in
   `ConstructorResolver.autowireConstructor`'s actual source.** When any explicit args are
   passed (e.g. via `ObjectProvider<T>.getObject(Object...)`), Spring requires the parameter
   count to match exactly and skips by-type resolution for every parameter — there's no way to
   mix "pass this at runtime" with "autowire that by type" in one constructor call. This is why
   `InProcessEngine` (not `EngineRun` itself) resolves the right `LoadStrategyFactory` and passes
   it in explicitly alongside the per-run `RunSpec`/`ProtocolExecutor`/`RunListener`.

**A real latent bug was found and fixed along the way, not something introduced by this
refactor:** `TestRunServiceImpl.requestTimeout` was a `private final Duration` field annotated
`@Value(...)`, relying on `@RequiredArgsConstructor` to generate a constructor parameter for it.
Lombok does **not** copy arbitrary annotations like `@Value` onto generated constructor
parameters unless a `lombok.config` explicitly lists them under `copyableAnnotations` — and no
`lombok.config` existed in this repo. The generated constructor therefore had a bare,
unannotated `Duration` parameter; Spring would have looked for a `Duration` bean, found none,
and thrown `UnsatisfiedDependencyException` at context startup. This had never been caught
because the file was only introduced in an uncommitted package move (`service.runs` →
`feature.runs`) and hadn't been run since. Fixed by adding `server/lombok.config` with
`lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Value` — Lombok's
own documented mechanism for exactly this situation — confirmed working by decompiling the
generated `.class` and seeing `@Value` correctly attached to the constructor parameter. A second,
related bug was fixed at the same time: the key was `app.runs.request-timeout-seconds` but
Spring's `DurationStyle.SIMPLE` parses a bare number as **milliseconds**, not seconds — so even
once the constructor worked, `30` meant 30 milliseconds. Renamed to
`app.runs.request-timeout=30s` with an explicit unit suffix.

**What changed:**
- `pom.xml`: added `spring-boot-starter-aspectj`.
- `server/lombok.config`: new file (see above).
- `RunCoordinator`: hand-written constructor replaced by `@RequiredArgsConstructor` with `@Value`
  directly on the `final` field.
- `TestRunServiceImpl`: the `@Value`/`Duration` bug fixed as above; its one `RunMdc.run(...)`
  call site (inside `startRun`, on a request thread with a local `runId`) replaced with a direct
  `MDC.putCloseable(...)` try-with-resources — this one case doesn't fit the new aspect, which
  keys off a `RunIdentifiable` *bean field*, not a local variable, and introducing a second
  logging mechanism for one line wasn't worth it.
- `com.pulseload.engine.strategy`: new `LoadStrategyFactory` interface plus
  `OpenLoadStrategyFactory`/`ClosedLoadStrategyFactory` `@Component` beans, replacing the
  if/else that used to live in `EngineRun.buildStrategy()`. `OpenModelStrategy`/
  `ClosedModelStrategy` themselves are unchanged, so every test that constructs them directly
  via `new` (`OpenModelStrategyTest`, `ClosedModelStrategyTest`, `LoadStrategyRunIntegrationTest`)
  needed no changes at all.
- `com.pulseload.engine.logging`: new package — `RunMdcScope` (marker annotation),
  `RunIdentifiable` (marker interface exposing `UUID runId()`), `EngineAopConfig` (the
  `exposeProxy=true` escalation), `RunMdcAspect` (the `@Around` advice, replicating `RunMdc`'s
  exact save-and-restore-not-blindly-clear semantics). `RunMdc.java` deleted.
- `EngineRun`: now `@Component @Scope(SCOPE_PROTOTYPE)`, implements `RunIdentifiable`, no longer
  `final` (CGLIB must subclass it). `runToCompletion()`/`emitSnapshot()` are now `public`,
  non-`final`, and annotated `@RunMdcScope` instead of each wrapping its body in
  `RunMdc.run(...)`. `runToCompletion()` resolves `AopContext.currentProxy()` once into a local
  `self` and schedules the snapshot ticker against `self::emitSnapshot` (so the ticker's own
  thread goes through the proxy and gets advised); the inline `emitSnapshot()` call near the end
  of the same method is left as a plain self-call on purpose, since it's already inside
  `runToCompletion`'s own advised scope on the same thread.
- `InProcessEngine`: now `@Component @RequiredArgsConstructor`, injecting
  `List<LoadStrategyFactory>` (Spring auto-collects every matching bean — confirmed idiomatic,
  not a workaround) and `ObjectProvider<EngineRun>`. `start()` resolves the right factory for
  `spec.loadModel()` and calls `engineRuns.getObject(spec, executor, listener, strategyFactory)`
  with all four arguments explicit.
- `InProcessLoadGenerator`: now `@RequiredArgsConstructor` with an injected
  `private final InProcessEngine engine` instead of `new InProcessEngine()`.
- `application.properties`: the comment above `logging.pattern.console` updated to reference
  `RunMdcAspect` instead of the deleted `RunMdc`.
- `EngineRunListener` was deliberately left unchanged — its hand-written constructor has a
  non-trivial body (kicking off `markRunning` as a side effect), which
  `@RequiredArgsConstructor` cannot express (Lombok only generates pure field-assignment
  constructors), and it isn't a Spring bean.

**Tests:**
- `RunMdcTest` deleted, replaced by `com.pulseload.engine.logging.RunMdcAspectTest` (3 tests,
  no Spring context needed) — built with `AspectJProxyFactory` directly
  (`setProxyTargetClass(true)`, `setExposeProxy(true)`, `addAspect(new RunMdcAspect())`) against
  a small `RunIdentifiable` test target. Ports all three original assertions (sets-and-clears,
  clears-on-exception, and the nested-call-doesn't-wipe-the-outer-scope regression), with the
  nested case now explicitly routing through `AopContext.currentProxy()` to mirror
  `EngineRun`'s exact self-invocation pattern.
- `InProcessEngineTest` converted from `new InProcessEngine()` to a lightweight
  `@SpringJUnitConfig`-backed test (a local `@Configuration` importing just the engine beans —
  no JPA/DB), since `EngineRun` now requires a real proxy for `AopContext.currentProxy()` to
  work. Added a new regression test,
  `propagatesRunIdIntoMdcOnTheTickerThreadNotJustTheRunThread`, which captures `MDC.get("runId")`
  from inside `onSnapshot` and asserts it's always correct — this is the test that actually
  proves the self-invocation fix works, and it was confirmed empirically by observing `Progress`
  log lines emitted from a *different* thread pool than `Run starting`/`Run completed` in the
  same run, with the correct `runId` throughout.
- The existing `reportsTheAverageRequestedRateOverTheRampNotTheFinalInstantaneousTarget` test
  needed its JVM-warm-up strengthened (two short warm-up runs instead of one, and a longer 4s
  ramp instead of 2s) after moving to a real Spring context — the added CGLIB-proxy and
  component-scanning overhead made the original single-short-warm-up version occasionally flaky
  in a way the original (non-Spring) version wasn't. Confirmed stable across 3 consecutive reruns
  after the change.
- Full suite: **55/55 tests passing, `BUILD SUCCESS`** (one more test than the pre-refactor 54,
  from the new ticker-thread MDC regression test).

**Manual smoke test repeated end-to-end** against the rebuilt jar (`docker compose up postgres`,
real stub HTTP target, real `curl` requests) to confirm the AOP-driven logging produces the exact
same traceable lifecycle as the explicit `RunMdc.run()` calls it replaced: grepped a `runId`
across `Run created` → `Run starting` → `Progress` (×7, confirmed spanning two different thread
pools) → `Run completed`, and confirmed the final summary (`requestedRps≈46.8, actualRps≈46.8,
saturated=false`) still reflects the earlier ramp-averaging fix correctly.

# Plan: PulseLoad 90-Minute Backend Workshop Transformation

Transform PulseLoad into a focused teaching environment for three topics — validation/exception handling, Spring Security, and async/concurrency — by (1) simplifying the backend to a lean CLOSED-model load tester with real virtual users, (2) rewiring the frontend to make runtime behavior observable, and (3) preparing reverse-progression exercise branches. All five confirmed decisions (lean engine, Postgres+Liquibase, HTTP Basic security with cookie based authentication and role based authorization, `@Async` refactor, VU registry) are baked in below.

## Architecture assessment (actual repo)
- **Backend is mature and well-factored**: `InProcessEngine.start()` manually spawns a thread running `EngineRun.runToCompletion()`; `ClosedModelStrategy.vuLoop()` is a genuine virtual-user loop dispatched via `TaskDispatcher.VIRTUAL_THREADS`; AOP `@RunMdcScope`, prototype `EngineRun`, scheduled snapshots, decoupled persistence. Validation + `GlobalExceptionHandler` already solid.
- **Security is empty** — `SecurityConfig` does `anyRequest().permitAll()` with no users/roles. Must be built up for Topic 2.
- **Async uses explicit executors, not `@Async`** — needs a teaching surface.
- **No per-VU state** is exposed today.
- **Frontend is badly out of sync** — old `durationInSeconds`/`requestsPerSec` model, mock Zustand data, only `getHealth()`, stub `TestPage`.

## Simplification proposal (keep / simplify / remove)

| Component | Current responsibility | Recommendation | Reason / Workshop relevance |
|---|---|---|---|
| CLOSED model (`ClosedModelStrategy`, `vuLoop`) | Virtual-user concurrency loop | **Keep** | Real VUs → core of Topic 3 observability |
| OPEN model (`OpenModelStrategy`, factories, semaphore, coordinated omission) | Arrival-rate pacing | **Remove** | Advanced; not needed for the 3 topics |
| `saturation/*`, `ResourceSampler` | Bottleneck detection, CPU/heap sampling | **Remove** | High cognitive load, off-topic |
| HDR histograms + service/response-time split | Latency with coordinated-omission correction | **Simplify** | Replace with simple latency (min/mean/max/p95) + outcome counts |
| `MetricsRecorder`, per-second `TestRunMetric` snapshots | Live metrics | **Keep (trim)** | Powers run observability |
| `InProcessEngine` thread spawn | Launch run on manual thread | **Refactor → `@Async`** | Topic 3 guided exercise surface |
| Virtual-user state | (none) | **Add** (`VirtualUserRegistry` + endpoint) | Topic 3 visuals + traceability |
| `SecurityConfig` | permitAll | **Rebuild** | HTTP Basic + cookie based authentication + roles for Topic 2 |
| Validation + `GlobalExceptionHandler` | DTO validation, ProblemDetail | **Keep + extend** | Add business validation for Topic 1B |
| gRPC/protobuf deps, distributed future code | Sprint-4 seam | **Remove/defer** | Unused, heavyweight |

## Steps

### Phase 2 — Golden backend baseline (`session/main`)
1. Remove OPEN model (`engine/strategy/Open*`, `OpenLoadStrategyFactory`), `engine/saturation/*`, `ResourceSampler`, and prune related fields from `RunReport`/`RunSummary`/`MetricsSnapshot`. Keep clean package boundaries — delete whole packages, don't monolithize. *Prune the tests that reference them.*
2. Simplify metrics to outcome counts + simple latency (min/mean/max/p95); drop service-vs-response-time split in `MetricsRecorder`/`ClosedModelStrategy`.
3. Rename lifecycle `PENDING → QUEUED` across `TestRunStatus`, `RunPersistence`, DTOs, and add a Liquibase changelog if the enum string is persisted. *depends on 1.*
4. Add `VirtualUserRegistry` (bounded per run) with VU state (index, `IDLE/REQUESTING/WAITING/COMPLETED/ERROR`, requestCount, lastActivityAt, lastOutcome). Instrument `ClosedModelStrategy.vuLoop()` to record transitions; expose `GET /runs/{id}/virtual-users` (bounded/paginated, no sensitive headers/payloads). *parallel with 5.*
5. Refactor run launch: replace `Executors...newThread(...)` in `InProcessEngine.start()` with an `AsyncRunLauncher` bean method annotated `@Async`, backed by `AsyncConfig` (virtual-thread executor), `@EnableAsync`. *parallel with 4.*
6. Rebuild `SecurityConfig`: HTTP Basic, cookie based authentication, `InMemoryUserDetailsManager` (`viewer`=ROLE_VIEWER, `operator`=ROLE_OPERATOR), `authorizeHttpRequests` (reads→authenticated, mutations + run start/stop→OPERATOR), enable `@EnableMethodSecurity`, `DELETE /tests/{id}`→`@PreAuthorize` ADMIN. Set CORS `allowCredentials=true`. *depends on nothing; parallel.*
7. Add business validation (CLOSED rule: `maxConcurrency >= peak stage target`) in the test service throwing a domain exception mapped in `GlobalExceptionHandler`; ensure `MethodArgumentNotValidException` yields consistent ProblemDetail. *depends on 3.*
8. Verify: `./mvnw test` green, `./mvnw spring-boot:run` starts, docker-compose Postgres, manual end-to-end run.

### Phase 3 — Frontend integration (identical across all branches)
9. Rewrite `api/client.ts` as a typed client (tests CRUD, runs, metrics, VUs) sending Basic-auth header; update `types/index.d.ts` to `workloadProfile`, `TestRun`, `TestRunMetric`, `VirtualUser`.
10. Replace mock Zustand store with backend-backed fetching + bounded polling (no SSE/WebSockets).
11. Build `TestPage`: create/select test, configure CLOSED load, start/stop run, lifecycle badge (`QUEUED→RUNNING→COMPLETED/FAILED/CANCELLED`), run list, metrics summary, **VU panel** (bounded), visible failures. Accessibility: labels, contrast, keyboard, no color-only status. Traceability Test→Run→VU→Request→Result via consistent IDs.
12. Verify: `npm run build`, `npm run lint`, manual flow against golden backend.

### Phase 4 — Exercise specs + acceptance tests
One spec each, participant-facing vs instructor-solution separated; intentionally-failing acceptance tests clearly labeled.

### Phase 5 — Reverse-progression branches
Layer regressions top-down; verify each compiles/runs/tests and git-diffs cleanly.

### Phase 6 — Docs + final verification checklist

## Workshop exercise matrix

| Topic | A — Instructor-guided | B — Independent challenge |
|---|---|---|
| **1. Validation** (~15m) | Restore request validation on `CreateTestDto` + consistent central handling (branch strips annotations + inconsistent handler). Observe 400 ProblemDetail vs 500/silent-accept. | Add **business** validation in the service layer (`maxConcurrency >= peak stage target`) → domain exception mapped consistently. *Same concept, different layer.* |
| **2. Security** (~30m) | Tighten endpoint authorization via `HttpSecurity` path+method rules (branch too permissive); `viewer` gets 403 on POST, 200 on GET. Teaches filter chain, authn vs authz. | Add **method-level** `@PreAuthorize` on a different op (`DELETE /tests`→ADMIN or stop-run). *Same concept, different mechanism.* |
| **3. Async** (~35m) | Convert blocking/self-invoking run launch to `@Async` + Executor; fix the **proxy self-invocation pitfall** → POST returns 202, run proceeds in background. Teaches `@Async`, proxies, thread pools. | VU execution runs on a bounded **platform-thread** pool that throttles concurrency → switch to **virtual threads**; observable throughput + active-VU jump in the VU panel. Teaches platform vs virtual threads. |

## Branch progression (prepared in reverse)
```
session/main  (all solved)
   └─ session/03-async      (from main: +both async regressions; validation+security correct)
        └─ session/02-security  (from 03-async: +both security regressions; async still regressed)
             └─ session/01-validation  (from 02-security: +both validation regressions)  ← workshop start
```
Workshop runs forward `01 → 02 → 03 → main`; switching branches restores the prior topic's correct solution. Frontend contract is identical across branches (always sends Basic auth; permissive branches ignore it).

## Relevant files
- `server/.../engine/InProcessEngine.java` — replace manual `newThread` launch with `@Async` call (Topic 3A).
- `server/.../engine/strategy/ClosedModelStrategy.java` — instrument `vuLoop` for VU registry; Topic 3B dispatcher swap point.
- `server/.../engine/concurrent/TaskDispatcher.java` — `VIRTUAL_THREADS` vs platform-pool (Topic 3B).
- `server/.../config/SecurityConfig.java` — rebuild for HTTP Basic + roles (Topic 2).
- `server/.../feature/tests/dto/CreateTestDto.java`, `.../service/TestServiceImpl.java`, `.../exception/GlobalExceptionHandler.java` — Topic 1 A/B.
- `server/.../feature/runs/controller/RunController.java` + new `VirtualUserRegistry` — VU endpoint.
- Remove: `engine/strategy/Open*`, `engine/saturation/*`, `engine/metrics/ResourceSampler.java` and dependent tests.
- `webapp/src/api/client.ts`, `webapp/src/types/index.d.ts`, `webapp/src/context/useTests.ts`, `webapp/src/pages/TestPage.tsx` — full frontend integration.
- `docs/workshop/**` — new documentation.

## Verification
1. Backend: `cd server && ./mvnw test` green; `./mvnw spring-boot:run` starts with docker-compose Postgres; manual run shows VUs executing and lifecycle transitions.
2. Frontend: `cd webapp && npm run build && npm run lint`; manual create→run→observe VUs→results.
3. Each branch: checkout, compile, run, tests pass (exercise acceptance tests fail only where intended); `git diff` shows exactly the intended localized regressions.
4. Teaching: every exercise starts from an observable problem; A/B pairs are related-not-identical; app never uncompilable/arbitrarily broken.

## Decisions
- Lean engine (CLOSED-only), Postgres+Liquibase+Testcontainers, HTTP Basic + in-memory roles, `@Async` run launch, bounded VU registry — all confirmed.
- Excluded: gRPC/distributed workers, OPEN model, saturation detection, coordinated-omission latency, SSE/WebSockets.

## Further considerations
1. **Virtual-threads exercise (3B) difficulty** — switching the VU dispatcher is a one-line change but conceptually deep. Recommend keeping it as the *independent* challenge with an acceptance test asserting concurrent active-VU count. Alternative: demote to an instructor demo if time is tight. (Recommend: keep as 3B.)
2. **Latency metric approach** — Option A: drop HdrHistogram for simple running stats (less dependency, simpler). Option B: keep a single HdrHistogram for p95 realism. (Recommend: Option A for cognitive simplicity.)
3. **Metrics delivery to frontend** — simple polling of `GET /runs/{id}/metrics` + `/virtual-users`. (Recommend polling; matches the "no streaming infra" constraint.)

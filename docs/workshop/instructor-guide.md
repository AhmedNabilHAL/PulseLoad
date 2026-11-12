# Instructor guide

Keep each topic to its time box. The two security and async topics are the deep ones; validation,
transactions, and aspects are shorter. Every exercise has an observable before/after you can demo.

## Suggested timing (90 min)

| Time | Topic | Branch |
|------|-------|--------|
| 0:00–0:10 | Intro, run the app, trace Test → Run → Virtual User → Request → Result | `session/01-validation` |
| 0:10–0:22 | 1 · Validation & exception handling | `session/01-validation` |
| 0:22–0:35 | 2 · Transactions | `session/02-transactions` |
| 0:35–0:58 | 3 · Spring Security | `session/03-security` |
| 0:58–1:21 | 4 · Async & concurrency | `session/04-async` |
| 1:21–1:30 | 5 · Aspects (demo + discussion) | `session/05-aspect` |

## Per topic

Each exercise doc has a **guided** part (you drive) and an **independent** part (participants try).
Verify with the topic's acceptance test; the fix is visible as the same code on the next branch up.

| Topic | Guided | Independent |
|-------|--------|-------------|
| 1 Validation | Restore request validation on `CreateTestDto` (annotations + cascade) → 400 | Add the service-layer business rule (peak target ≤ maxConcurrency) → 422 |
| 2 Transactions | Make `startRun` transactional so the `AFTER_COMMIT` event fires and the run leaves QUEUED | Explain/why `REQUIRES_NEW` on `RunPersistence` keeps snapshot writes isolated |
| 3 Security | Tighten `authorizeHttpRequests` so only OPERATOR/ADMIN may mutate (viewer → 403) | Re-add `@PreAuthorize` so only ADMIN may delete (operator → 403) |
| 4 Async | Move the run launch off the request thread with `@Async` on a separate bean (202 returns immediately) | Switch the VU dispatcher back to virtual threads so all VUs run concurrently |
| 5 Aspect | Narrow the logging pointcut to the service layer so one call logs once, not per collaborator | Discuss trade-offs: annotation-driven pointcuts, log levels, hot paths |

## Verifying a solution

```bash
cd server
./mvnw test -Dtest.excludedGroups= -Dgroups=acceptance
```

Run the whole group, or target one class, e.g.
`-Dtest=SecurityAuthorizationAcceptanceTest`. The acceptance tests live in
`server/src/test/java/com/pulseload/workshop` (plus `TestControllerValidationTest`).

## Common pitfalls to highlight

- **Transactions / events:** a `@TransactionalEventListener(AFTER_COMMIT)` silently does nothing if
  the publisher isn't in a transaction. Great "why is my run stuck?" moment.
- **Async / proxies:** annotating a method `@Async` and calling it from the *same* bean does nothing —
  self-invocation bypasses the proxy. The reference solution uses a separate bean on purpose.
- **Security:** filter-chain rules (`authorizeHttpRequests`) run before the controller; method
  security (`@PreAuthorize`) runs at invocation. Show both and when each fits.
- **Aspects:** a broad pointcut is the cheapest way to flood logs. Show the request's log volume
  before/after narrowing it.

# Exercise 5 — Aspect-oriented programming

**Branch:** `session/05-aspect` · **~9 min** · guided demo + discussion

**Learning objective:** See how an aspect removes cross-cutting boilerplate (logging) from many
methods at once — and how the same power, with a careless pointcut, floods the logs.

---

## Guided — scope the logging aspect

**Existing behaviour:** `ServiceLoggingAspect` logs an enter/exit/timing line for its join points, so
no service method needs its own logging boilerplate. But its pointcut matches *every* method in the
feature packages, so a single request produces a cascade of log lines — controllers, services,
mappers, and repositories all logged.

**Problem:** A pointcut that's too broad turns a helpful cross-cutting concern into log noise (and
overhead on hot paths).

**Relevant code:** `server/src/main/java/com/pulseload/observability/ServiceLoggingAspect.java`.

**Conceptual guidance:** Narrow the `@Around` pointcut so it advises only the control-plane service
implementations, not their collaborators. An `execution(...)` expression scoped to the service layer
is enough; an annotation-driven pointcut (a custom `@Loggable`) is another option worth discussing.

**Expected behaviour:** One service call produces exactly one aspect log line, not one per
collaborator it calls.

**Acceptance:** `AspectLoggingAcceptanceTest` passes.

**Verification:** `./mvnw test -Dtest.excludedGroups= -Dgroups=acceptance -Dtest=AspectLoggingAcceptanceTest`

**Debrief:** AOP is leverage: one aspect replaces boilerplate across the codebase, which is exactly
why it's used for logging, metrics, transactions, and security. That leverage cuts both ways — pointcut
scope, log level, and keeping aspects off hot paths are what separate a useful aspect from a noisy,
costly one. (See also `RunMdcAspect`, which uses AOP to tag every run's logs with its `runId`.)

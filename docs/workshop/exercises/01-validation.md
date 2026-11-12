# Exercise 1 — Validation & exception handling

**Branch:** `session/01-validation` · **~12 min** · guided + independent

**Learning objective:** Know where validation belongs in a layered app — request (shape) validation
at the boundary vs business (rule) validation in the service — and how both surface as consistent
HTTP error responses.

---

## Guided — request validation

**Existing behaviour:** `POST /api/tests` accepts anything. A blank name, a non-URL target, or
negative workload numbers are persisted or blow up deeper in the stack.

**Problem:** Invalid input isn't rejected at the boundary with a clear `400`.

**Relevant code:**
- `server/src/main/java/com/pulseload/feature/tests/dto/CreateTestDto.java`
- `server/src/main/java/com/pulseload/feature/tests/dto/WorkloadProfileDto.java`, `StageDto.java`
- `server/src/main/java/com/pulseload/feature/tests/controller/TestController.java` (`@Valid`)
- `server/src/main/java/com/pulseload/exception/GlobalExceptionHandler.java`

**Conceptual guidance:** Jakarta Bean Validation annotations describe the *shape* of a valid request.
Remember to cascade (`@Valid`) into nested objects. The controller already triggers validation; the
global handler already turns failures into a problem response.

**Expected behaviour:** An invalid create request returns `400` with a problem detail; a valid one
still succeeds.

**Acceptance:** `TestControllerValidationTest` passes.

## Independent — business validation

**Existing behaviour:** You can create a test whose peak stage target exceeds `maxConcurrency` — a
closed-model run can never drive more virtual users than its own cap, so the config is nonsensical.

**Problem:** This rule can't be expressed with field annotations; it compares fields against each
other.

**Relevant code:** `server/src/main/java/com/pulseload/feature/tests/service/TestServiceImpl.java`,
`server/src/main/java/com/pulseload/exception/InvalidTestConfigurationException.java`.

**Conceptual guidance:** Enforce the rule in the service layer on both create and update. Throw a
domain exception and map it to a 4xx in the global handler.

**Expected behaviour:** Creating an over-provisioned test returns `422`.

**Acceptance:** `ValidationBusinessAcceptanceTest` passes.

**Verification:** `./mvnw test -Dtest.excludedGroups= -Dgroups=acceptance -Dtest=TestControllerValidationTest,ValidationBusinessAcceptanceTest`

**Debrief:** Boundary validation protects the whole app from malformed input cheaply; business
validation lives where the invariant does (the service/domain). Returning consistent problem
responses is what makes an API predictable to clients.

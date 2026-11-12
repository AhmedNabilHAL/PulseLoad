# Exercise 3 — Spring Security

**Branch:** `session/03-security` · **~23 min** · guided + independent

**Learning objective:** See how the Spring Security filter chain authorizes requests before they
reach a controller, the difference between authentication and authorization, and when to use
filter-chain rules vs method-level security.

---

## Guided — authorization that's too coarse

**Existing behaviour:** Any authenticated user can do anything. Sign in as `viewer` and you can still
create tests and start runs.

**Problem:** Authorization is `anyRequest().authenticated()` — it checks *who you are* (authn) but
not *what you're allowed to do* (authz). Mutations should require an elevated role.

**Relevant code:** `server/src/main/java/com/pulseload/config/SecurityConfig.java`
(`authorizeHttpRequests`).

**Conceptual guidance:** Add method- and path-scoped rules so creating/updating tests and
controlling runs require `OPERATOR` or `ADMIN`, while reads stay open to any authenticated user. These
rules run in the filter chain, before the controller.

**Expected behaviour:** `viewer` gets `403` on `POST /api/tests`; `operator` still succeeds; everyone
can `GET`.

**Acceptance:** `SecurityAuthorizationAcceptanceTest.viewerCannotCreateTests` passes.

## Independent — method-level security for delete

**Existing behaviour:** An `operator` can delete tests.

**Problem:** Delete is destructive and should require `ADMIN`, even though `operator` may perform
other mutations. Expressing this purely with path rules is awkward; it belongs next to the method.

**Relevant code:** `server/src/main/java/com/pulseload/feature/tests/controller/TestController.java`
(`deleteTest`). Method security is already enabled on `SecurityConfig` (`@EnableMethodSecurity`).

**Conceptual guidance:** Annotate the delete handler so only `ADMIN` passes.

**Expected behaviour:** `operator` gets `403` on `DELETE /api/tests/{id}`; `admin` succeeds.

**Acceptance:** `SecurityAuthorizationAcceptanceTest.operatorCannotDeleteTests` passes.

**Verification:** `./mvnw test -Dtest.excludedGroups= -Dgroups=acceptance -Dtest=SecurityAuthorizationAcceptanceTest`

**Debrief:** Filter-chain rules are coarse and centralized (great for "all writes need a role");
method security is fine-grained and lives with the code it guards (great for "this one operation
needs more"). Both compose — a request must satisfy every applicable rule. Note how CSRF and the
session cookie are also part of the same chain.

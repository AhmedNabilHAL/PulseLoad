# PulseLoad Backend Workshop

A 90-minute, hands-on tour of five backend engineering concepts, built on PulseLoad — a small
closed-model HTTP load tester. You create a load test, start a run, and watch virtual users execute
against a target in real time.

Each concept is a Git branch containing a deliberately incomplete implementation. You fix it, observe
the behaviour change, then move to the next branch — which already contains the correct version of
everything you just solved.

## Topics

| # | Topic | Focus |
|---|-------|-------|
| 1 | Validation & exception handling | Request vs business validation, consistent error responses |
| 2 | Transactions | Transaction boundaries and transaction-bound events |
| 3 | Spring Security | Filter-chain authorization, authn vs authz, method security |
| 4 | Async & concurrency | `@Async`, thread pools, platform vs virtual threads |
| 5 | Aspect-oriented programming | Cutting log boilerplate without flooding the logs |

## Prerequisites

- Java 21, Docker Desktop, Node 20+.
- An IDE and a terminal.

## Running the app

```bash
# 1. Start PostgreSQL
docker compose up -d postgres

# 2. Backend (from server/)
cd server
./mvnw spring-boot:run        # http://localhost:8080/api

# 3. Frontend (from webapp/, in another terminal)
cd webapp
npm install
npm run dev                   # http://localhost:5173
```

Sign in with one of the workshop accounts (password = username):

| User | Role | Can |
|------|------|-----|
| `viewer` | VIEWER | read tests and runs |
| `operator` | OPERATOR | create tests, start/stop runs |
| `admin` | ADMIN | everything, including delete |

## Running the tests

```bash
cd server
./mvnw test                                           # normal suite (always green)
./mvnw test -Dtest.excludedGroups= -Dgroups=acceptance # exercise acceptance tests
```

The **acceptance tests** encode each exercise's target behaviour. On an exercise branch, the current
topic's acceptance test is red; it turns green when you solve the exercise. The normal suite stays
green on every branch so a red build always means "my exercise isn't done yet", never "the branch is
broken".

## Workshop flow

You move through the branches in order, solving one topic per branch:

```
session/01-validation → session/02-transactions → session/03-security
      → session/04-async → session/05-aspect → session/main (reference solution)
```

See [branch-map.md](branch-map.md) for the Git commands and [exercises/](exercises/) for each spec.
Each topic has a **guided** exercise (walked through together) and an **independent** challenge.

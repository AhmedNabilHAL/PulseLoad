# Branch map

The branches were prepared top-down from the complete reference (`session/main`), each one adding a
topic's regressions. You run the workshop bottom-up, removing those regressions one topic at a time.

```
session/main            complete reference implementation (all exercises solved)
   ▲
   │  solve 05 · aspect scope
session/05-aspect       validation, transactions, security, async solved · aspect over-logs
   ▲
   │  solve 04 · async & concurrency
session/04-async        validation, transactions, security solved · run launch blocks, VU pool throttled
   ▲
   │  solve 03 · security
session/03-security     validation, transactions solved · authorization too coarse
   ▲
   │  solve 02 · transactions
session/02-transactions validation solved · started runs never leave QUEUED
   ▲
   │  solve 01 · validation
session/01-validation   ◀── workshop starts here (every topic unsolved)
```

Each branch is exactly the branch above it plus one topic's intentional regressions — verify with
`git diff session/02-transactions session/01-validation` etc. The frontend is identical on every
branch.

## Moving between stages

Commit or stash your work before switching branches.

```bash
git switch session/01-validation   # start
# ...solve validation...
git stash            # or: git commit -m "my validation solution"

git switch session/02-transactions # validation is already correct here
# ...solve transactions...

git switch session/03-security
git switch session/04-async
git switch session/05-aspect
git switch session/main            # the full reference solution
```

## Seeing a solution

The solution to any exercise is simply the same code on the **next** branch up. For example, to see
the intended security fix while on `session/03-security`:

```bash
git diff session/03-security session/04-async -- server/src/main/java/com/pulseload/config
```

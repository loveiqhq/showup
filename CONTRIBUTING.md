# How we work

Decided 2 September 2026. This is the workflow, not a suggestion — everything goes the same way
round, every time, including small fixes.

## Three branches

```
   feature branch  ──▶  development  ──▶  staging  ──▶  main
                        (work)           (QA)          (production)
```

| Branch | What it means | Who promotes into it |
|---|---|---|
| **`development`** | Where the work happens. This is the default branch: a fresh clone lands here. Green means "it builds and the automated checks pass" — **not** that it is finished. | Anyone, from a feature branch |
| **`staging`** | Ready for a person to test. Promoted when a batch of work is worth QA'ing, not per commit. | Whoever is promoting, from `development` |
| **`main`** | Production. Only ever holds work that has passed QA on staging. | Only after QA passes, from `staging` |

**Work travels one way and skips nothing.** Nothing is committed straight to `staging` or `main`,
and a fix that is urgent still starts on `development` — an exception made once becomes the way it
is done.

## The everyday case

```bash
git switch development
git pull
git switch -c fix/whatever-it-is
# ... work ...
git push -u origin fix/whatever-it-is
```

Open the pull request **against `development`**. GitHub offers this by default because
`development` is the default branch. Merge once CI is green.

## Promoting to staging, when something is ready to test

```bash
git switch staging
git pull
git merge development
git push
```

Then tell whoever is doing QA what to look at. A promotion is worth an explanation — "these three
screens changed, here is what to try" — because QA without a scope is a hunt.

## Promoting to main, once QA has passed

```bash
git switch main
git pull
git merge staging
git push
git tag -a qa-2026-09-05 -m "QA passed: <what was tested>"
git push --tags
```

The tag is the point. It is a permanent bookmark on the exact version a person approved, so when
something breaks later there is a known-good state to compare against.

## What CI does at each step

The same two jobs run on every pull request and on every push to all three branches:

- **Lint, build, unit & e2e tests** — the backend
- **Android build, unit tests & design conformance** — both apps built, 45 tests, 9 design checkers

Both must pass before anything merges into `staging` or `main`. They are advisory on
`development`, because that is where work in progress lives.

**CI cannot tell you something is finished.** It tells you nothing is obviously broken. Whether a
screen is done is a question about the product, and it is answered in Jira.

## Why this shape

Written down because it was a real decision, not the default.

`main` is protected because it is what will be deployed. `staging` exists so that a person tests a
stable batch rather than a moving target — and, crucially, so that "it passed QA" refers to a
specific version somebody actually looked at, rather than to whatever happened to be merged that
afternoon.

The alternative — a single branch, with tags marking approved builds — is lighter and was
considered. Three branches were chosen deliberately: the team is about to grow, and a workflow is
much easier to establish before there are deployments than after.

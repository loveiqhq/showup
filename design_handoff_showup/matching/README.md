# Matching — server ranking for Search

Rules: `matching-plan.md` (this folder, copied from the design project). Server-only flow: no reference file, no spec sheets — each ticket's worked examples are its spec and its unit tests.

## Flow rules

1. Every number is config, versioned as `ranker_version`; a weight change never needs a release.
2. Never inputs: ethnicity, religion, politics, health, photo attractiveness.
3. Gender is always two-way and hard.
4. The client never computes or logs a score.

## Status

| # | Ticket | Status |
|---|---|---|
| 01 | Search ranking — filters, score, ordering rules, soft range | built 31 Oct 2026 (SHOWUP-190) |

## Backlog

- Legal page "how we rank" (`matching-plan.md` §6) — before public launch.
- Instant Mode ranking — later.

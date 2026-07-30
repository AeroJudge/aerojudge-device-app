# Migration record

This repository was assembled on 2026-07-30 from **two** source repositories, because neither
one alone was complete:

| Source | Contributed |
|---|---|
| `IMAC-ORG/imac-judge-app` | full history, 58 tags, 2 abandoned 2023 branches, all issue/PR/release metadata |
| `dpgarceau/TEMP_aj-device-app_migration` | `main` (96 commits ahead, 0 behind), 26 tags |

A native GitHub transfer was not possible — the only admin on `IMAC-ORG` is unreachable — so
this was a mirror-based migration of the **union** of both repos. Commit SHAs, authorship,
dates and signatures are preserved exactly; no history was rewritten at any point.

## Start here

- **[MIGRATION-SPEC.md](MIGRATION-SPEC.md)** — the full plan. **Section 12 is the execution
  record**: what actually happened, which assumptions turned out wrong, and the bugs found
  along the way. Read that section first.
- **[MIGRATION-FINDINGS.md](MIGRATION-FINDINGS.md)** — the measured facts, with the commands to
  re-verify them.
- **[PREFLIGHT.md](PREFLIGHT.md)** — Phase 0 report (read-only probe).
- **[VERIFICATION.md](VERIFICATION.md)** — Phase 3 verification of this repo, 17/17 pass.

## Final state

488 commits · 4 branches · 84 tags · 1 root · `main` = `f3492464e6044ec9f26e3f957927d3fb048876d6`
29 releases with 64 assets (1.61 GB) · 11 labels · 1 milestone

## `archive/` — what was *not* recreated

Issues and pull requests were **deliberately not recreated** as GitHub issues. The open work was
already re-written by hand as issues #1–#13, and recreating 122 historical items would have
buried those while notifying 7 real people for no benefit.

The complete history is preserved here instead:

| File | Contents |
|---|---|
| `archive/issues.json` | all 45 issues (14 open, 31 closed) with bodies, comments, labels, milestones, authors, timestamps |
| `archive/pulls.json` | all 77 pull requests (1 open, 69 merged, 7 closed-unmerged) |
| `archive/releases.json` | all 29 releases including every asset URL |
| `archive/rendered/` | 122 human-readable markdown renderings of the above |

`@mentions` and `#refs` throughout `archive/rendered/` are escaped as `@<!---->name`, which
displays normally but never notifies anyone or cross-links an unrelated issue.

## One asset was not migrated

`v1.1.0/2025-03-29_Clean_8GB_bootfs.zip` (1.9 GB, 2 downloads) remains on the legacy repo. Its
URL is recorded in that release's notes and in `archive/releases.json`.

## Scripts

`phase0-preflight.sh`, `phase1-union-push.sh`, `phase3-verify.sh`, `phase2d-releases.sh`,
`phase2-metadata.sh` — the actual scripts used, all idempotent and re-runnable.
`phase0` and `phase3` are read-only. Timestamped run logs are in `logs/`.

## Caveat

Everything under `migration/` describes a one-time event and is **not** maintained. Treat the
numbers as accurate for 2026-07-30 and re-measure rather than trusting them later.

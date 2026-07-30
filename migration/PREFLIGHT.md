# Phase 0 — Preflight Report

Generated 20260730T023801Z by `migration/phase0-preflight.sh`.
**Read-only run — nothing was created, pushed, or modified.** Idempotent; re-run any time.

## Verdict: **PASS** — safe to proceed to Phase 1

## Access

| Check | Value |
|---|---|
| Token scopes | 'delete_repo', 'gist', 'read:org', 'repo', 'workflow' |
| AeroJudge role | `admin` |
| Effective permission on source | `pull, push, triage` (no admin — expected) |
| Org push protection on new repos | `false` |

## Source — IMAC-ORG/imac-judge-app

| Field | Value |
|---|---|
| Visibility / default branch | public / `main` |
| Description | Design and src code for IMAC Judge software |
| Homepage | _(none)_ |
| Topics | _(none)_ |
| Flags | issues=true projects=true wiki=false discussions=false archived=false |
| **Releases** | **29 — 65 assets, 3.38 GB** |
| → to migrate (spec 6d) | 64 assets, 1.61 GB |
| → excluded by decision | v1.1.0/2025-03-29_Clean_8GB_bootfs.zip (1899491097 bytes)  |
| → releases left with no assets | 0 (must be 0) |
| Branches / tags | 4 / 58 |

## Issues, PRs, metadata

| Item | Count |
|---|---|
| Issues open / closed | 14 / 31 |
| PRs open / merged / closed-unmerged | 1 / 69 / 7 |
| Comments issues / PRs | 61 / 13 |
| Labels / milestones | 11 / 1 |
| Cross-repository (fork) PRs | none |
| Orphaned closed-unmerged PR heads | none — nothing orphaned |

Estimated Phase 2 create calls: **196** (~10–20 min at 3 s cadence).

## Two-repo relationship

| Check | Value | Required |
|---|---|---|
| source/main tip | `f4a9e00c24d9a57dfa6b495a842c6e2fa4a96b4a` | — |
| snapshot/main tip | `f3492464e6044ec9f26e3f957927d3fb048876d6` | — |
| merge-base | `f4a9e00c24d9a57dfa6b495a842c6e2fa4a96b4a` | = source tip |
| snapshot ahead | **96** | — |
| **source ahead (would be lost)** | **0** | **must be 0** |
| Tag collisions | **0** | must be 0 |
| Tags only in source | 58 | expected — namespaces are disjoint |
| Tags only in snapshot | 26 | expected |
| Shared tag names | 0 | 0 — the two schemes never overlap |
| Source / snapshot tag counts | 58 / 26 | union must be 84 |

Commits unique to each source branch vs snapshot/main:

| Branch | Unique commits |
|---|---|
| `feat_round_numbering` | 2 |
| `feature/eureka` | 1 |
| `admin-pages-overhaul` | 0 |

## Union projection (built locally, not pushed)

| Stage | Commits | Branches | Tags | Roots |
|---|---|---|---|---|
| Source, refs/pull dropped | 406 | 4 | 58 | — |
| **Union** | **488** | **4** | **84** | **1** |

- Union `main` = `f3492464e6044ec9f26e3f957927d3fb048876d6`
- Source refs unreachable in union: **0** (must be 0)
- Refs outside heads/tags: **0** (must be 0)
- Files with old-name references: **5** — MIGRATION.md README.md scripts/archive/config3-50.txt scripts/archive/config3-60.txt scripts/config.txt 
  (all intentional prose — see MIGRATION-FINDINGS.md §2.7)

## Integrity

| Check | Result |
|---|---|
| Git LFS | none |
| Submodules | none |

## Destination

| Repo | State |
|---|---|
| AeroJudge/aerojudge-device-app | AeroJudge/aerojudge-device-app — `private`, **0 refs** (must be 0) |
| AeroJudge/migration-dryrun | does not exist |

---

**Halt here.** Metadata (Phase 2) is deferred until after the real push, per the operator
scope decision. Phase 1 covers the git side only.

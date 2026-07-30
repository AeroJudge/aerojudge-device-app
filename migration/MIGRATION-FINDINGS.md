# Migration Findings & Status

Companion to `MIGRATION-SPEC.md`. Records what has been **verified by measurement** versus
what was assumed, so nobody re-derives it or acts on a stale premise.

- **Last updated:** 2026-07-29
- **Phase reached:** Section 0 quickstart, steps 0.1–0.4 complete. **Phase 0 not yet run.**
- **Anything created, pushed, or modified on GitHub:** **No.** All work so far is read-only.

---

## 1. Current status

| Step | State |
|---|---|
| 0.1–0.3 Folder created, spec saved, opened in VS Code | ✅ `d:\GitDEV_AeroJudge\aerojudge-migration` |
| 0.4 Terminal available | ✅ Git Bash and PowerShell both usable |
| 0.5 Verify tooling | ✅ `git` 2.51.2, `gh` 2.96.0 |
| 0.6 Authenticate | ✅ `dpgarceau`, all required scopes |
| 0.7 Decide public vs private | ✅ Resolved — repo already exists and is private |
| 0.8–0.10 Run Phase 0 | ✅ **PASS** — `migration/PREFLIGHT.md` |
| Phase 1 — dry run push | ✅ pushed to `migration-dryrun` |
| Phase 3 — verify dry run | ✅ **17/17 PASS** (caught the default-branch bug) |
| Phase 4 — **real push** | ✅ **union pushed to `AeroJudge/aerojudge-device-app`** |
| Phase 3 — verify real repo | ✅ **17/17 PASS** — `migration/VERIFICATION.md` |
| Dry-run repo deleted | ✅ |
| Releases (§6d) | **← next** |
| Metadata (§6a/6b) | pending |
| Re-enable Actions | pending — do last |

### The git migration is complete and verified

`AeroJudge/aerojudge-device-app` now holds **488 commits, 4 branches, 84 tags, 1 root**,
`main` = `f3492464e6044ec9f26e3f957927d3fb048876d6`. Branch tips and tag targets are
byte-identical to the union; 78 annotated tags kept their `tag` object type; all 3
orphan-risk commits present; zero source refs unreachable. **Actions remain disabled.**

### Bug the dry run caught

The first push left the default branch as `admin-pages-overhaul` — GitHub assigns it from the
first ref processed in a mirror push, which is alphabetical, and `main` loses. The `gh repo
edit` issued right after the push returned exit 0 and **silently lost the race**. Fixed with
set → verify → retry ×5 → hard fail. This is precisely what the dry run existed to find, and
it would otherwise have surfaced weeks later as clones checking out the wrong branch.

### Workflow triggers — why Actions had to be off

| Workflow | Name | Trigger |
|---|---|---|
| `maven.yml` | Create Tag | `push` to `main` touching `pom.xml`, `judge/**`, `figures/**`, `volume_service/**`, `scripts/**`, `assembly/**` |
| `build.yml` | Create Release | `workflow_run` on *Create Tag* completing, or manual dispatch |

That is an automated release pipeline. With Actions enabled, the mirror push would have created
a spurious tag **and published a release**, corrupting the verified tag set. Neither workflow
has a `schedule` trigger, so re-enabling after the push is safe — nothing fires until the next
qualifying push to `main`.

**This also settles the version-scheme question:** the pipeline generates `v26.1.x`
(`YY.MAJOR.MINOR`, year 26). The snapshot's `v26.1.0`–`v26.1.4` came from it; the source's
`v1.0.x` / `v2.1.2` scheme is legacy.

**No blockers remain.** Phase 0 passed 2026-07-30 via `migration/phase0-preflight.sh`
(read-only, idempotent, re-runnable).

### Releases — a Section 1 assumption that was wrong

The spec originally stated "no releases". **False:** the source has **29 releases, 65 assets,
3.38 GB** — actively used, 1,267 total downloads. A mirror push carries tags but never release
notes or binaries, so these would have been silently lost.

Artifacts are the compiled app (`judge.jar`, ~46 MB × 29), supporting bundles
(`volume_service.zip`, `figures.zip`), and one 1.8 GB device boot image. Span 2023-07-13 →
2026-05-01.

**Operator decision 2026-07-30:** recreate **all 29** releases with notes and assets, excluding
only `v1.1.0/2025-03-29_Clean_8GB_bootfs.zip` (1,899,491,097 bytes, **2 downloads**). That
leaves **64 assets / 1.61 GB** to transfer, and **no release ends up empty**. The excluded
asset's original URL is preserved in that release's body and in the JSON archive. Spec §6d.

### Verified access

| Check | Result |
|---|---|
| Token scopes | `delete_repo`, `gist`, `read:org`, `repo`, `workflow` — all required present |
| AeroJudge role | **admin**, active — can create, delete, and configure repos |
| IMAC-ORG role | member; source repo perms `push`/`pull`/`triage`, **no admin** (matches spec assumption) |
| Source repo | public, not archived, issues on, projects on, **no wiki, no discussions**, 15 open issues+PRs |
| Org policy | members may create public and private repos; 2FA not enforced |
| Org push protection on new repos | **disabled** — lowers the risk of a secret-scanning push rejection |

### Destination repo already exists — and that is fine

`AeroJudge/aerojudge-device-app` was created **2026-05-20** and is:

- **completely empty** — `size: 0`, **0 refs** (verified by `git ls-remote`)
- **private**, default branch `main`, not a fork, not archived
- `dpgarceau` has **admin**

So it must **not** be created, and pushing into it destroys nothing. **Re-confirm it is still
empty immediately before the real push** — if `git ls-remote … | wc -l` is not `0`, stop,
because `--mirror` would delete whatever is there.

`AeroJudge/migration-dryrun` does **not** exist (correct — Phase 1 creates it).

---

## 2. Verified facts

Measured 2026-07-29 by mirror-cloning both repos read-only and inspecting them. **These are
measurements, not assumptions.** Re-verify before executing — the snapshot repo may have moved.

### 2.1 There are two source repos, and neither alone is complete

| Repo | Role | `main` tip |
|---|---|---|
| `IMAC-ORG/imac-judge-app` | history, all metadata, 2 unique branches | `f4a9e00c24d9a57dfa6b495a842c6e2fa4a96b4a` (2026-04-30) |
| `dpgarceau/TEMP_aj-device-app_migration` | newer code (only branch: `main`) | `f3492464e6044ec9f26e3f957927d3fb048876d6` (2026-07-25) |

### 2.2 The snapshot is a full clone, not a copy — so there is no cherry-pick

- `snapshot/main` is a **strict fast-forward** of `source/main`: **96 ahead, 0 behind**.
- Merge-base is `f4a9e00` — **exactly the source's `main` tip**.
- Integration is therefore a plain `git fetch`. **No cherry-pick, no graft, no `filter-repo`,
  no conflict resolution, no post-migration integration phase.**
- The 96 new commits span 2026-05-02 → 2026-07-25: `dpgarceau` (89), `David Garceau` (7).

### 2.3 Tags: two disjoint sets, zero collisions

> **Corrected 2026-07-30.** An earlier version of this document claimed all 58 source tags
> existed in the snapshot at identical SHAs. **That was false** — an artifact of a tag-pollution
> bug in the first preflight script (a cross-repo `git fetch` without `--no-tags` auto-followed
> tags into `refs/tags/*`, making each mirror appear to contain the other's tags). Both mirrors
> have since been re-cloned pristine and re-measured.

| | Count |
|---|---|
| Source tags | **58** (`v0.0.1`, `v1.0.100`–`v1.0.116`, `v2.1.2`, …) |
| Snapshot tags | **26** (`v1.0.1`–`v1.0.3`, `v5.1.0`–`v5.1.17`, `v26.1.0`–`v26.1.4`) |
| **Shared names** | **0 — the two schemes are completely disjoint** |
| Union | **84** = 58 + 26 |

Collisions are therefore zero, but *because the namespaces never overlap*, not because the
snapshot mirrors the source's tags. **The 58 source tags exist only in the source**, which is a
second independent reason the union needs both repos.

No code is affected: the source's tagged commits are all ancestors of union `main` (verified for
`v2.1.2` → `5b6743e7`). Only the tag refs were absent from the snapshot, never the commits.

The union tag count of **84** was correct throughout — that arithmetic never depended on the
mistaken overlap claim.

### 2.4 What the snapshot is missing — 3 commits

These exist **only** in the source, on two abandoned 2023 branches. Mirroring just the snapshot
would silently lose them; this is why the destination must be the union.

| Branch | Unique commits |
|---|---|
| `feat_round_numbering` | `a05856c` (2023-07-24, Dan Carroll), `0bd668e` (2023-07-23, Dan Carroll) |
| `feature/eureka` | `7318be6` (2023-08-21, Tej Pillay) |
| `admin-pages-overhaul` | none — tip already contained in `snapshot/main` |

### 2.5 `8afe8c6` is not an integration base

`8afe8c603a5140964fed9d7897ab8b53aea4d9b5` — described as `v2.1.2-rc3-38-g8afe8c6` — is
**exactly 38 commits after `v2.1.2-rc3`**, so that describe string was accurate. But:

- It exists **only in the snapshot**, where it is tagged `v1.0.1`.
- It does **not** exist in the source at all.
- The describe string came from a view with both tag sets visible, since `v2.1.2-rc3` is a
  source tag and `8afe8c6` is a snapshot commit.
- It is an ordinary mid-range commit in the fast-forward. **It needs no special handling.**

### 2.6 Expected union shape

| Stage | Commits (all refs) | Branches | Tags | Roots |
|---|---|---|---|---|
| Source mirror, raw | 754 | 4 | 58 | 2 |
| Source mirror, after dropping `refs/pull/*` | 406 | 4 | 58 | 1 |
| **Union after fetching snapshot** | **488** | **4** | **84** | **1** |

The raw source's excess commits and second root are all inside `refs/pull/*` — including one
fork PR with unrelated history. Both disappear at Phase 1 step 3.

Final branch tips in the union:

| Branch | Tip | From |
|---|---|---|
| `main` | `f349246` | snapshot |
| `admin-pages-overhaul` | `8abce3d` | source |
| `feat_round_numbering` | `a05856c` | source |
| `feature/eureka` | `7318be6` | source |

### 2.7 The rename is already almost entirely done

The 96 snapshot commits did the work. Counts on `main`:

| Term | Source (old) | Union (current) |
|---|---|---|
| `imacjudgeapp` | 2 in 1 file | **0** |
| `imac-judge-app` | 18 in 9 files | **2** |
| `IMAC-ORG` | 13 in 7 files | **9** |

Every mechanical hotspot is clean — `pom.xml`, `Dockerfile`, `dockerbuild/`, `.devcontainer/`,
`.github/workflows/*` all report **0** — and `imac-judge-app.iml` no longer exists in the tree.

All 11 survivors are **intentional prose about the project's own history**. Leave them:

| File | Line(s) | Nature |
|---|---|---|
| `MIGRATION.md` | 9 | URL of the old repo in migration docs |
| `README.md` | 16, 20, 22, 24, 26 | "continuation of the former IMAC Judge App", legacy-archive plan |
| `scripts/config.txt` | 7 | `# NOT TO BE USED ON OLDER LEGACY IMAC-ORG DERIVED SYSTEMS` |
| `scripts/archive/config3-50.txt` | 7 | same warning comment |
| `scripts/archive/config3-60.txt` | 7 | same warning comment |

**There is no rename PR to write.** Generate the report for the record, from the **union** —
sweeping the source alone reports the stale 2026-04-30 picture and sends someone re-doing
finished work.

---

## 3. Premises that turned out wrong

Recorded so they are not reintroduced. Each would have caused significant re-work.

| Original premise | Reality |
|---|---|
| The other repo is a download/copy with no shared ancestry | Full clone, complete shared ancestry |
| ~100 commits to cherry-pick, or graft via matching root tree | 96-commit fast-forward; a plain `git fetch` |
| `8afe8c6` is the integration base | Ordinary mid-range commit, snapshot-only, no special handling |
| The rename PR is a substantial job across many hotspots | Already done; 11 intentional prose survivors |
| Mirroring one repo is sufficient | Neither repo alone is complete — 3 commits live only in the source |
| "No releases, no packages" | **29 releases, 65 assets, 3.38 GB**, actively downloaded |
| All 58 source tags exist in the snapshot | **False** — the tag sets are disjoint (58 vs 26, 0 shared); an artifact of a tag-pollution bug in the first preflight script |

---

## 4. How to re-verify (read-only, ~2 min)

```bash
git clone --mirror https://github.com/IMAC-ORG/imac-judge-app.git src.git
git clone --mirror https://github.com/dpgarceau/TEMP_aj-device-app_migration.git snap.git
cd src.git
git remote add snap ../snap.git && git fetch snap 'refs/heads/*:refs/snap/heads/*' 'refs/tags/*:refs/snap/tags/*'

git rev-list --count refs/heads/main..refs/snap/heads/main   # expect 96 (snapshot ahead)
git rev-list --count refs/snap/heads/main..refs/heads/main   # MUST be 0 — else STOP
git merge-base refs/heads/main refs/snap/heads/main          # expect f4a9e00…
```

**If the second number is not 0, stop.** The fast-forward assumption is void and the union
procedure in Phase 1 is unsafe.

Rename counts, run against the built union:

```bash
git grep -I -i -n -e IMAC-ORG -e imac-judge-app -e imacjudgeapp refs/heads/main
```

---

## 4b. Measured metadata volume & scope decision

| Item | Count |
|---|---|
| Issues | 14 open, 31 closed |
| Pull requests | 1 open, 76 closed (69 merged, 7 closed-unmerged) |
| Comments | 61 on issues, 13 on PRs |
| Labels / milestones | 11 / 1 |
| **Cross-repository (fork) PRs** | **0** |
| **Closed-unmerged PR heads unreachable in the union** | **0 of 7** |

**No PR code is orphaned.** Deleting `refs/pull/*` loses nothing; Phase 1's promotion step is a
verified no-op. Open PR #121's head is `admin-pages-overhaul` @ `8abce3d4`, already one of the
4 migrated branches.

**Operator scope decision (2026-07-29):** metadata is **not dry-run**. The dry run covers the
git side only. Phase 2 runs once against the real repo after git is verified, and issue
recreation is **best-effort** — the accepted fallback is hand-recreating the 14 open issues
from the JSON archive. The JSON archive itself is not best-effort and must be complete.

Total create calls ≈ **196**, roughly 10–20 minutes at the 3-second cadence — well inside the
500/hour ceiling. (An earlier estimate of "several hours" was wrong.)

---

## 5. Open decisions

1. **Public or private** for `AeroJudge/aerojudge-device-app`. The source is public. This also
   determines secret-scanning exposure — push protection is free on public repos but needs GHAS
   on private ones, so a private dry run will **not** surface a block the public real run hits.
2. **Version scheme.** The snapshot re-tags from `v1.0.1` while the source reached `v1.0.116`
   and `v2.1.2`. Both sets land in the destination without collision, but the result has two
   overlapping schemes. Decide what the next release tag is.
3. **Fate of the two abandoned 2023 branches** (`feat_round_numbering`, `feature/eureka`).
   They are migrated for completeness; deleting them later is a normal repo decision.
4. **Fate of `dpgarceau/TEMP_aj-device-app_migration`** once verified — its content is fully
   contained in the union. Do not delete it until Phase 3 passes.

---

## 6. Spec changes driven by these findings

`MIGRATION-SPEC.md` was updated throughout. The substantive ones:

- **§1** — rewritten around two source repos and the verified fast-forward relationship.
- **§4 Phase 0** — re-verifies the relationship, tag collisions, and the 3 stale-branch commits;
  also checks releases/packages/Discussions and org push-protection settings.
- **§5 Phase 1** — now builds the union (new step 3b) with expected shapes asserted before and
  after; `--force` explicitly forbidden on that fetch.
- **§5 Phase 1** — Actions disabled on the destination **before** any push, and fork-PR heads
  promoted **before** `refs/pull/*` is deleted. Both are irreversible if skipped.
- **§6b** — `@<!---->user` escaping instead of backticks, code blocks skipped, incremental
  `number-map.json`, rolling hourly rate-limit counter, mandatory render-and-review gate.
- **§7** — verification asserts the exact union shape and that no source commit was lost.
- **§8** — rename section replaced with the measured residual; cherry-pick/graft marked obsolete.
- **§2** — ground rule 7 (never rewrite history) and rule 8 (never fabricate data) added.

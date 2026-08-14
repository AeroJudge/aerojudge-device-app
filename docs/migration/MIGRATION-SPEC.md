# Repository Migration Spec — imac-judge-app → aerojudge-device-app

**Audience:** the engineer or agent executing this migration.
**Read this entire file before running anything.**

> **Read `MIGRATION-FINDINGS.md` first.** It records current status, the blocker, open
> decisions, and — importantly — which facts in this spec were *measured* versus assumed,
> along with five original premises that turned out wrong. Acting on the superseded ones
> causes avoidable re-work.

---

## 0. Operator quickstart — do these yourself, in order

**A new empty folder, not a clone of the repo.** Claude Code works in a scratch folder; the
mirror clone is created *inside* it by Phase 1. Do not open an existing working copy of
imac-judge-app.

1. Create an empty folder, e.g. `C:\dev\aerojudge-migration`.
2. Save this `MIGRATION-SPEC.md` into that folder.
3. VS Code → **File → Open Folder** → select that folder.
4. Open a terminal: **Ctrl+`**. On Windows, use a **Git Bash** or **WSL** terminal, not
   PowerShell — the scripts in this spec are bash. (Pick from the dropdown next to the `+`
   in the terminal panel.) If you'd rather stay in PowerShell, tell Claude Code to write
   PowerShell scripts instead of bash and to translate the commands in this spec accordingly.
5. Verify the tooling:
   ```bash
   git --version
   gh --version        # if missing: winget install GitHub.cli, then RESTART the terminal
   ```
6. Authenticate. **`delete_repo` is required** — Phase 4 tears down the dry-run repo and
   will fail at the last step without it:
   ```bash
   gh auth login                                    # skip if already logged in
   gh auth refresh -h github.com -s workflow -s read:org -s delete_repo
   gh auth status                                   # confirm the account and scopes
   ```
7. ~~Decide public or private.~~ **Resolved 2026-07-29:** the destination repo already exists,
   created 2026-05-20, and is **private and completely empty (0 refs, size 0)**. It does not
   need creating. Keep it private through the migration and flip to public afterwards if
   desired — see Phase 4.
8. Start Claude Code in that terminal:
   ```bash
   claude
   ```
9. Paste the kickoff message from Section 11.
10. Review the preflight report it produces, then tell it to proceed phase by phase. Approve
    each phase rather than letting it run straight through.

Note on `jq`: this spec shows `jq` in a couple of places, but `gh` has jq built in via the
`--jq` flag, so a separate jq install is not required. Claude Code should prefer `gh --jq`.

---

## 1. Objective

Move `IMAC-ORG/imac-judge-app` to `AeroJudge/aerojudge-device-app`, preserving as much as
possible, and **fold in the newer code that currently lives in a second repo** so the result is
a single complete working base. A native GitHub repository transfer is **not** available — the
only admin on the source org is unreachable. This is a mirror-based migration of the union of
two repos, plus scripted recreation of metadata.

| Item | Value |
|---|---|
| Source (history + all metadata) | `https://github.com/IMAC-ORG/imac-judge-app` (public) |
| Source (newer code, see below) | `https://github.com/dpgarceau/TEMP_aj-device-app_migration` |
| Final destination | `https://github.com/AeroJudge/aerojudge-device-app` |
| Dry-run destination | `https://github.com/AeroJudge/migration-dryrun` (private, deleted afterward) |
| Operator access on source | **Write / push** — not admin |
| Default branch | `main` |

### Two source repositories, not one

The newer code lives in a second repo. **It is a full clone sharing complete ancestry with
`imac-judge-app` — not a detached copy.** This was verified directly, and it removes the need
for any cherry-pick, graft or conflict resolution.

| Item | Value |
|---|---|
| Snapshot repo | `https://github.com/dpgarceau/TEMP_aj-device-app_migration` |
| Its only branch | `main`, tip `f3492464e6044ec9f26e3f957927d3fb048876d6` (2026-07-25) |
| Source `main` tip | `f4a9e00c24d9a57dfa6b495a842c6e2fa4a96b4a` (2026-04-30) |
| Merge-base | **`f4a9e00` — identical to source `main`'s tip** |

#### Verified relationship (measured 2026-07-29, re-verify in Phase 0)

- **`snapshot/main` is a strict fast-forward of `source/main`.** 96 commits ahead, **0 behind**.
  Every source `main` commit is present, unmodified, at its original SHA.
- **The two tag sets are disjoint: source 58, snapshot 26, shared names 0.** Collisions are
  therefore zero — but *because the schemes never overlap*, not because the snapshot carries
  the source's tags. **The 58 source tags exist only in the source.** Union = 84.
  (Corrected 2026-07-30; an earlier claim that the snapshot held all 58 was a measurement
  artifact. No code is affected — the source's tagged commits are all ancestors of union
  `main`; only the tag refs were absent.)
- The 96 new commits run 2026-05-02 → 2026-07-25, authored by `dpgarceau` (89) and
  `David Garceau` (7).
- `8afe8c6` (`v2.1.2-rc3-38-g8afe8c6`, tagged `v1.0.1` in the snapshot) exists **only in the
  snapshot**, 38 commits past `v2.1.2-rc3`. It is *not* an integration base — it is an ordinary
  commit in the middle of the fast-forward range, and needs no special handling.
- **The snapshot is missing 3 commits** that exist only on two abandoned 2023 source branches:
  `feat_round_numbering` (+2, Dan Carroll) and `feature/eureka` (+1, Tej Pillay).
  `admin-pages-overhaul`'s tip is already contained in `snapshot/main`.

**Therefore neither repo alone is complete.** The destination must be the **union**: the source
supplies the two stale branches and full provenance, the snapshot supplies `main` and 26 tags.
Phase 1 builds that union in one local repo and pushes once.

#### Why history fidelity still matters

Both repos already agree on every shared SHA. Preserving that exactly is what makes the
fast-forward work — a single rewritten commit re-hashes every descendant, breaks the
fast-forward relationship, and turns a clean push into an unrelated-histories mess. Hence
ground rules 7 and 8. No fabricated data, no rewriting, ever.

This outranks every other goal in this spec, including metadata completeness. Concretely:

- **No fabricated data anywhere.** Nothing is invented, padded, stubbed or placeholder-filled
  — not commits, not tags, not branches, not issues. Where something cannot be preserved it is
  recorded as a loss in Section 10 and in `migration/VERIFICATION.md`. An accurate gap always
  beats a plausible-looking filler.
- **No history rewrite for any reason** — not to strip a secret, not to fix a bad author
  email, not to drop a large file. See ground rule 7.
- **Annotation is confined to the metadata plane.** Provenance headers and escaping in
  Section 6b apply only to recreated *issue* text. They never touch commit messages, and no
  phase in this spec rewrites a commit message.
- Verification (Section 7) proves this by comparing tip SHAs per branch and target SHAs per
  tag. Because Git SHAs are content-addressed over full ancestry, matching tips prove the
  entire reachable graph is identical. **Those checks are what prove the union was built
  correctly — never weaken or skip them.**

### Known facts about the source (verified 2026-07-29)

- 380 commits on `main`; 406 across all refs once `refs/pull/*` is dropped; 4 branches,
  58 tags, 1 root commit. (A raw mirror reports 754 commits and 2 roots — the excess is
  `refs/pull/*`, including one fork PR with unrelated history. Both vanish at Phase 1 step 3.)
- 14 open issues, 1 open pull request (closed counts unknown until the API is queried)
- GitHub Actions workflows present under `.github/workflows/` → **the `workflow` token scope is mandatory**
- `.gitattributes` contains only `*.sh text eol=lf` → **no Git LFS**
- No releases, no packages, no wiki (wiki tab absent), 0 stars, 0 forks, 5 watchers
- Java / Maven project (`pom.xml`), Docker + devcontainer, plus `scripts/`, `docs/`, `figures/en/`

Do not treat these as gospel — Phase 0 re-verifies all of them.

---

## 2. Ground rules for the executing agent

1. **Script everything.** Every phase is a committed shell script under `./migration/`, not
   ad-hoc terminal commands. A failed step must be re-runnable without redoing earlier steps.
2. **Idempotency.** Scripts must detect already-completed work and skip it (e.g. don't create a
   label that exists, don't re-create an issue whose backlink URL is already present in the
   destination).
3. **Never force-push** to the destination. Never run destructive git commands against the
   source. The source repo is read-mostly for this exercise.
4. **Stop and report on any *permission* 403/404.** Do not retry them in a loop and do not
   attempt to work around them. Log them to `migration/BLOCKED.md` and continue with the
   next independent step.
   **Distinguish this from a rate-limit 403**, which is retryable and expected in Phase 2b.
   A rate-limit 403 carries a `Retry-After` header, or `x-ratelimit-remaining: 0`, or a body
   containing "secondary rate limit". Anything else is a permission failure — halt it.
   Never treat an unclassified 403 as retryable.
5. **Log everything** to `migration/logs/<phase>-<timestamp>.log`.
6. **Ask before deviating.** If the spec is ambiguous or reality doesn't match Section 1,
   stop and ask rather than improvising.
7. **Never rewrite history. No exceptions, no judgement calls.** `filter-branch`,
   `filter-repo`, `rebase`, `commit --amend`, `squash`, grafts, replace-refs and shallow
   clones are all forbidden in every phase. If a situation seems to require one — a secret in
   history, a malformed author, an oversized blob — that is a **hard stop and an escalation to
   the operator**, never an improvisation. Rewriting breaks the fast-forward relationship
   between the two source repos described in Section 1, and there is no way to detect the
   damage after the fact except by full SHA comparison against sources that may no longer be
   reachable.
8. **Never invent data to fill a gap.** If a value can't be read, record it as unknown and
   list it as a loss. Do not substitute a default, an empty placeholder, or a synthesised
   stand-in for anything — commits, refs, timestamps, authors, issue numbers.

---

## 3. Prerequisites (operator does these first, before Claude Code runs)

```bash
# GitHub CLI must be authenticated with the workflow scope, or the push will be rejected
# the moment it hits .github/workflows/. delete_repo is needed by Phase 4.
gh auth refresh -h github.com -s workflow -s read:org -s delete_repo
gh auth status
```

If the AeroJudge org enforces SAML SSO, the token must be explicitly authorized for that org
under GitHub → Settings → Developer settings → Personal access tokens → Configure SSO.

Working directory: a fresh empty folder outside any existing repo. The mirror clone creates a
bare repo; do not run this inside another working tree.

---

## 4. Phase 0 — Preflight probe (read-only, no writes)

Produce `migration/PREFLIGHT.md` containing:

- Effective permission on the source: `gh api repos/IMAC-ORG/imac-judge-app --jq '.permissions'`
- Full ref inventory: `git ls-remote --heads --tags https://github.com/IMAC-ORG/imac-judge-app.git`
  → record branch count, tag count, and the full list
- Counts of issues and PRs, **including closed**:
  `gh issue list -R IMAC-ORG/imac-judge-app --state all --limit 1000 --json number | jq length`
  and the same for `gh pr list`
- Label list, milestone list
- Repo metadata: description, homepage, topics, default branch, whether issues/projects/wiki
  are enabled
- Confirm no LFS: `git lfs ls-files` after clone, or grep `.gitattributes` for `filter=lfs`
- Confirm no submodules: check for `.gitmodules` on every branch
- Whether the destination repos already exist
- **Releases, packages, and Discussions.** Section 1 asserts these are absent — verify, do not
  assume: `gh release list`, `gh api repos/IMAC-ORG/imac-judge-app --jq '.has_discussions'`,
  `gh api repos/IMAC-ORG/imac-judge-app/packages 2>&1 || true`. If any exist, stop and ask —
  this spec does not cover migrating them.
- **Fork/non-fork split for every PR, open and closed** — this determines whether Phase 1 can
  safely drop `refs/pull/*`. See Phase 1's fork-PR rule:
  ```bash
  gh pr list -R IMAC-ORG/imac-judge-app --state all --limit 1000 \
    --json number,state,isCrossRepository,headRefName,headRepositoryOwner
  ```
  Record the list of PR numbers where `isCrossRepository` is true. **If that list is
  non-empty, say so prominently in the preflight report** — those PRs need ref promotion
  before the `refs/pull` deletion, and the code is unrecoverable if the step is skipped.
- **Re-verify the two-repo relationship from Section 1.** These were measured on 2026-07-29;
  the snapshot may have moved since. Mirror both, then:
  ```bash
  # in the source mirror, with the snapshot fetched as remote "snap"
  git rev-list --count refs/heads/main..snap/main    # expect 96  (snapshot ahead)
  git rev-list --count snap/main..refs/heads/main    # MUST be 0  (nothing lost)
  git merge-base refs/heads/main snap/main           # expect f4a9e00…, = source main tip
  ```
  **If the second number is not 0, STOP.** The fast-forward assumption is void, the union
  procedure in Phase 1 is unsafe, and the whole integration approach needs re-deciding.
- **Re-verify zero tag collisions.** For every tag name in both repos, targets must be
  identical. Any conflict is a hard stop — a mirror push would silently pick one.
- Confirm the 3 stale-branch commits (`feat_round_numbering` +2, `feature/eureka` +1) are
  still present and still absent from `snapshot/main`.
- **Destination org push-protection posture.** Secret-scanning push protection is free on
  public repos but requires GHAS on private ones, so a private dry run will NOT surface a
  block that the public real run then hits. Record:
  ```bash
  gh api orgs/AeroJudge --jq \
    '{pp: .secret_scanning_push_protection_enabled_for_new_repositories,
      ss: .secret_scanning_enabled_for_new_repositories}'
  ```
  Note the planned visibility of the final repo alongside it. See Phase 4's pre-push scan.

Then confirm the destination org: `gh api user/orgs --jq '.[].login'` must include `AeroJudge`,
and `gh api orgs/AeroJudge/memberships/{your-login} --jq '.role'` should report your role.

**Halt here and show the preflight report before proceeding.**

---

## 5. Phase 1 — Code, branches, tags (mirror push)

> **Dry-run scope, 2026-07-29 (operator): the dry run covers the git side only.** Build the
> union, push it to `AeroJudge/migration-dryrun`, run Phase 3 verification, delete the dry run.
> **Do not recreate labels, milestones or issues against the dry run** — Phase 2 runs once,
> against the real repo, after the git side is verified there too.
>
> Revised phase order:
>
> 1. Phase 1 → dry run repo, git only
> 2. Phase 3 → verify the dry run
> 3. Phase 4 → delete dry run, push the union to the real repo, verify again
> 4. **Then** Phase 2 → metadata, once, against the real repo

Order matters in this phase. Two steps here cause irreversible damage if skipped or reordered:
promoting fork-PR refs (step 2, which must precede step 3) and disabling Actions (step 5,
which must precede step 6). Do not reorder them.

The destination is the **union** of both repos (Section 1). Build it locally, verify it, then
push once. Do not push the two repos separately.

```bash
# 1. Mirror the source, then build the union in it.
git clone --mirror https://github.com/IMAC-ORG/imac-judge-app.git
cd imac-judge-app.git
```

**2. Confirm no PR code is orphaned before deleting `refs/pull`.** For a PR whose head branch
no longer exists, `refs/pull/<N>/head` can be the only copy of that code, and step 3 deletes it
permanently. Phase 2b recreates PRs as issue **text only**, so a lost diff is unrecoverable.

**Verified 2026-07-29 — nothing is orphaned, so this step is a no-op:**

| Finding | Result |
|---|---|
| Cross-repository (fork) PRs | **0** |
| PR outcomes | 69 merged, 7 closed-unmerged, 1 open |
| Closed-unmerged heads unreachable in the union | **0 of 7** |
| Open PR #121 head | `admin-pages-overhaul` @ `8abce3d4` — already one of the 4 migrated branches |

Re-verify rather than assume, then proceed:

```bash
gh pr list -R IMAC-ORG/imac-judge-app --state all --limit 1000 \
  --jq '.[]|select(.mergedAt==null and .state=="CLOSED")|.headRefOid' \
  --json number,state,mergedAt,headRefOid |
while read oid; do git cat-file -e "$oid" 2>/dev/null || echo "ORPHANED: $oid"; done
```

If that prints nothing, delete `refs/pull` freely. **If it prints any SHA**, promote that ref
(`git branch migrated-pr/<N> refs/pull/<N>/head`) before step 3 and record it in
`migration/archive/promoted-refs.txt`.

```bash
# 3. GitHub exposes read-only refs/pull/* on the source; pushing them fails with
#    "deny updating a hidden ref". Delete them locally, AFTER step 2.
git for-each-ref --format='delete %(refname)' refs/pull | git update-ref --stdin
```

**3b. Fold in the snapshot repo.** This is a fast-forward of `main` plus 26 new tags; no
force, no merge commit, no conflicts. Verify the expected shape before and after.

```bash
# expected here: 406 commits, 4 branches, 58 tags, 1 root
git rev-list --all --count; git for-each-ref refs/heads | wc -l
git for-each-ref refs/tags | wc -l; git rev-list --max-parents=0 --all | wc -l

git remote add snap https://github.com/dpgarceau/TEMP_aj-device-app_migration.git
# NOTE: refs/pull/* is deliberately excluded from this refspec — the snapshot has 7 of them
git fetch snap 'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*'

# expected now: 488 commits, 4 branches, 84 tags, 1 root
git rev-list --all --count; git for-each-ref refs/heads | wc -l
git for-each-ref refs/tags | wc -l; git rev-list --max-parents=0 --all | wc -l
git rev-parse refs/heads/main   # expect f3492464e6044ec9f26e3f957927d3fb048876d6
```

**The `git fetch` must not report a forced update, and must not be given `--force`.** A
non-fast-forward here means the Section 1 relationship no longer holds — stop, do not
override. Expected final branch tips:

| Branch | Tip | Source |
|---|---|---|
| `main` | `f349246` | snapshot |
| `admin-pages-overhaul` | `8abce3d` | source |
| `feat_round_numbering` | `a05856c` | source (2 commits found nowhere else) |
| `feature/eureka` | `7318be6` | source (1 commit found nowhere else) |

```bash
# 4. Create the destination as an EMPTY repo — no README, no .gitignore, no license.
#    Run this from OUTSIDE the bare repo so gh does not try to adopt it.
cd ..
gh repo create AeroJudge/migration-dryrun --private \
  --description "Temporary dry run — safe to delete"
```

**5. Disable Actions on the destination NOW, before any push.** The source has
`on: push` workflows and the rename report in Section 8 flags deploy targets and image
registries among them. `git push --mirror` lands every branch at once, and every matching
workflow fires immediately against real infrastructure. This is not undoable.

```bash
gh api -X PUT repos/AeroJudge/migration-dryrun/actions/permissions -F enabled=false
# verify it actually took before pushing — do not proceed on a non-false result
gh api repos/AeroJudge/migration-dryrun/actions/permissions --jq '.enabled'
```

Actions stay disabled until Phase 3 verification passes. Re-enabling is a Phase 4 step.
**This applies to the real repo in Phase 4 exactly as it does to the dry run.**

```bash
# 6. Push.
cd imac-judge-app.git
git remote set-url --push origin https://github.com/AeroJudge/migration-dryrun.git
git push --mirror
```

Then set the default branch to `main` on the destination if it didn't land that way:
`gh repo edit AeroJudge/migration-dryrun --default-branch main`

Also carry over description, homepage and topics captured in Phase 0 via `gh repo edit`.

If the push is **rejected by secret-scanning push protection**, stop. Do not bypass it and do
not rewrite history to get around it — record the finding in `migration/BLOCKED.md` and bring
it to the operator. See Phase 4 for why this may appear on the real run despite a clean dry run.

---

## 6. Phase 2 — Metadata recreation

> **FINAL OUTCOME (2026-07-30): issues and PRs were NOT recreated. This was the right call.**
>
> Labels (11) and the milestone (1) *were* created — they are useful for live work.
> Issue/PR history is preserved **only** as the JSON archive, committed to the repo.
>
> Why recreation was dropped: the operator had already hand-written 13 curated open issues
> (#1–#13) directly in the destination. Recreating 122 historical items would have buried
> those under stale content and fired **200 notifications at 7 real people** — including
> former IMAC contributors — for no benefit. See the execution record in Section 12.
>
> The render gate (below) did its job: it produced 122 rendered bodies for review and halted
> *before* creating anything, which is what made this decision possible while it was still free.
>
> The sub-sections below are retained as the design of record — they describe what a full
> recreation *would* require, and the escaping rules in 6b still apply to release bodies (6d).

Run in this order. Each is its own script.

### 6a. Labels and milestones

Export from source, create on destination preserving name, colour and description. Milestones
keep title, description, due date and state.

### 6b. Issues and pull requests → issues + JSON archive

**Both**: a full JSON export committed to the repo, *and* recreation as issues.

Export first: `gh issue list -R IMAC-ORG/imac-judge-app --state all --limit 1000 --json
number,title,body,state,labels,milestone,assignees,author,createdAt,closedAt,comments,url`
and the equivalent for PRs. Commit these to `migration/archive/issues.json` and
`migration/archive/pulls.json` in the destination repo.

Recreation rules — **these matter, read carefully**:

- Process in **ascending original number order**. Issues and PRs share a number space on
  GitHub; recreate them interleaved by original number, not issues-then-PRs.
- **Numbering will drift, and that is accepted.** Any gap in the source numbering (deleted or
  transferred items) shifts every subsequent number permanently, and no ordering trick
  recovers it. Do **not** create placeholder issues to fill gaps. Instead, correctness rests
  on the number map below, not on the numbers matching.
- **Maintain `migration/archive/number-map.json` incrementally** — append
  `{"old": <n>, "kind": "issue"|"pr", "new": <m>, "url": "<new url>"}` immediately after each
  successful create, flushing to disk every time, before the next create. This file is:
  (a) the idempotency key — check it before creating, in preference to searching the
  destination by URL, which is slow and misses items whose body was truncated; and
  (b) the resume point when the hourly rate limit stops the run mid-way. Treat a create that
  succeeded but whose map entry was not written as the one failure mode worth guarding
  against: write the entry before doing anything else with the new issue.
- Prepend a provenance header to every body:
  `> Migrated from IMAC-ORG/imac-judge-app#<n> — originally opened by <author> on <date>`
  …applying the escaping rule below to the author name and the `#<n>`.
- **Neutralise `@mentions` and `#123` references in all migrated body and comment text.** An
  unescaped `@dave` sends a real notification to a real person, and `#123` autolinks to an
  unrelated issue in the new repo. This applies to the provenance header too. Getting it
  wrong spams people and **cannot be undone by deleting the repo afterwards.**

  Use the HTML-comment form, **not** backticks:

  | Source text | Write this | Renders as |
  |---|---|---|
  | `@dave` | `@<!---->dave` | @dave, no notification |
  | `#123` | `#<!---->123` | #123, no autolink |

  Backticks were the earlier instruction and are wrong for two reasons: they change the
  rendering of ordinary prose, and applying them *inside* an existing fenced block emits
  literal backticks into the code sample.

- **Do not transform text inside fenced or indented code blocks, or inside inline code
  spans.** GitHub neither notifies nor autolinks inside those, so there is nothing to fix,
  and rewriting them corrupts the content. Parse the markdown well enough to skip
  ``` fences, `~~~` fences, 4-space-indented blocks, and backtick spans before substituting.
- Match `@` mentions only where GitHub would: a `@` at a word boundary followed by a valid
  login (alphanumerics and single hyphens). Do not match inside email addresses.
- Match `#123` only as a `#` at a word boundary followed by digits **and not more hex
  digits** — `#123456` in a CSS colour must be left alone, as must `#!/bin/sh`.
- **Render-and-review gate — this runs before the first create call of the whole phase.**
  Write every transformed body and comment to `migration/archive/rendered/<kind>-<n>.md` and
  **halt**. The operator reviews them, greps for any surviving bare `@`, and gives explicit
  approval to proceed. Deleting the dry-run repo does not un-send notifications, so the dry
  run is not a safety net for this — the gate is.
- Preserve original comments as comments on the new issue, each prefixed with
  `@<!---->author commented on <date>:`.
- Apply labels and milestones. For migrated PRs, additionally apply a `migrated-pr` label
  (create it) so they're distinguishable.
- Assignees will fail for anyone not a member of the AeroJudge org. Attempt it; on failure,
  record the intended assignee in the body instead and continue. Do not abort.
- After creating an issue whose original state was closed, close it
  (`gh issue close --reason completed`, or `not planned` where the original was
  closed-as-not-planned; for PRs that were closed unmerged, use `not planned`).
- **Rate limits — two separate ceilings, both must be handled.** GitHub's secondary limits cap
  content creation at roughly **80/minute** and **500/hour**. Every issue create *and* every
  comment create counts.
  - Per-minute: sleep 3 seconds between create calls (≈20/min, comfortably under 80).
  - Per-hour: **the 3-second sleep does nothing for this one.** Issues + PRs + all their
    comments will very likely exceed 500 calls. Keep a rolling count of creates in the last
    60 minutes; at **450**, pause until the oldest call ages out of the window. Without this
    the run walks into a wall of 403s partway through.
  - Handle a rate-limit 403 (identified per Section 2, rule 4) by honouring `Retry-After`,
    not by hammering. A 403 that is *not* a rate-limit 403 halts the phase.
  - This run may span multiple hours. That is expected — it must be resumable, which is what
    `number-map.json` is for.
- Idempotency: before creating, consult `migration/archive/number-map.json`. If the original
  number is present, skip. Fall back to searching the destination for the original URL only
  if the map is missing or suspected incomplete.

### 6d. Releases and assets

**BOTH source repos publish releases. Probe both.** Phase 0 originally probed only
`IMAC-ORG/imac-judge-app` and reported 29 releases — it hardcoded the source repo and never
looked at the snapshot, which has **22 more**, including `v26.1.1`–`v26.1.4`, the newest
shipping versions. The preflight reported PASS with those missing. Corrected 2026-07-30.

| Source | Releases | Assets migrated |
|---|---|---|
| `IMAC-ORG/imac-judge-app` | 29 | 64 (1.61 GB) — one excluded, see below |
| `dpgarceau/TEMP_aj-device-app_migration` | 22 | 66 (0.77 GB) — none excluded |
| **Destination total** | **51** | **130 (2.38 GB)** |

Section 1 originally assumed there were no releases at all. A mirror push carries tags but
**never** release notes or binary assets, so these must be recreated deliberately.
`phase2d-releases.sh` takes `SRC_REPO` and is run once per source repo.

**Operator decision (2026-07-30): recreate all 29 releases with notes and assets, with exactly
one exclusion.**

| | |
|---|---|
| Releases to recreate | **29** (all) |
| Assets to upload | **64** — 1.61 GB |
| **Excluded asset** | `2025-03-29_Clean_8GB_bootfs.zip` on tag `v1.1.0`, 1,899,491,097 bytes, **2 downloads** |

The exclusion is the single 1.8 GB device boot image. It is the largest artifact by far and the
least used. `v1.1.0` still receives its other assets, so **no release ends up empty**.

Rules:

- **Run after the union push and Phase 3 verification.** A release cannot be created before its
  tag exists on the destination. All 84 tags land in Phase 1.
- **Archive first, recreate second.** Commit the complete API record — every release, body,
  timestamp, author, and every asset name/size/`browser_download_url` including the excluded
  one — to `migration/archive/releases.json`. This is **not** best-effort; it is the record.
- For the excluded asset, append a line to that release's body pointing at the original URL:
  `https://github.com/IMAC-ORG/imac-judge-app/releases/download/v1.1.0/2025-03-29_Clean_8GB_bootfs.zip`
  Mark it clearly as hosted on the legacy repo.
- Preserve `tag_name`, `name`, `body`, `prerelease` and `draft` exactly. **Original publish
  dates cannot be set via the API** — record them in the body and in the JSON archive, and list
  the loss in Section 10.
- Apply the `@<!---->` escaping from 6b to release bodies too — they can contain `@mentions`
  and `#refs` just like issues.
- Idempotent: skip any release whose tag already exists on the destination. Verify each upload's
  byte size against the source before moving on; re-upload on mismatch rather than continuing.
- Budget roughly 1.61 GB down and 1.61 GB up, plus scratch disk. Assets download to
  `migration/.work/assets/` which must be **git-ignored and never committed**.

### 6c. Repo settings that can be read with write-level access

Issues/projects toggles, topics, description, homepage. Branch protection, webhooks,
environments and Actions secrets **cannot be read without admin on the source** — they go on
the manual checklist in Section 9, not into a script.

---

## 7. Phase 3 — Verification

Produce `migration/VERIFICATION.md` comparing source and destination:

Compare against the **post-`refs/pull`-deletion** source mirror, not the pristine source. The
raw source carries `refs/pull/*` that were deliberately dropped in Phase 1, so a comparison
against it will never match. Fetch the destination into a fresh mirror clone for these checks.

| Check | Method | Pass condition |
|---|---|---|
| Commit count, all refs | `git rev-list --all --count` in each mirror | identical |
| Promoted fork-PR branches | `migration/archive/promoted-refs.txt` vs `git ls-remote --heads` on destination | every `migrated-pr/<N>` present, or file records "none found" |
| No unescaped mentions | grep all destination issue + comment bodies for `@` at a word boundary not followed by `<!---->` | zero hits outside code blocks |
| Number map complete | `number-map.json` entry count | equals source issues + PRs |
| Actions still disabled | `gh api repos/<dst>/actions/permissions --jq '.enabled'` | `false` until Phase 4 re-enables |
| **Union shape exact** | fresh destination mirror | **488 commits (all refs), 4 branches, 84 tags, 1 root** |
| **`main` is the snapshot tip** | `git rev-parse refs/heads/main` | `f3492464e6044ec9f26e3f957927d3fb048876d6` |
| **Every source ref survived** | for each source branch+tag SHA, `git cat-file -e` on destination | all present — zero misses |
| **Stale-branch commits survived** | `git cat-file -e` on `a05856c9…`, `0bd668e`, `7318be6b…` | all three present |
| **No source commit lost** | `git rev-list --count snap/main..source/main` | `0` |

Because the counts above were measured on 2026-07-29, treat a mismatch as "re-verify Section 1
first", not automatically as a failed push — the snapshot may legitimately have gained commits.
A count that is *lower* than expected, or a missing source ref, is always a hard stop.

Record all of these verbatim in `VERIFICATION.md`. Catching a mismatch here is far cheaper
than discovering it after the rename PR has landed on top.
| Branch list | `git ls-remote --heads` both sides | identical sets |
| Tag list **and tag SHAs** | `git ls-remote --tags` both sides | identical sets and targets |
| Tip SHA per branch | compare | identical |
| Annotated tags intact | `git for-each-ref refs/tags --format='%(objecttype)'` | annotated tags remain `tag`, not `commit` |
| Default branch | `gh repo view --json defaultBranchRef` | `main` |
| Working tree equality | `git diff <src>/main <dst>/main` after fetching both | empty |
| Issue + PR count | destination issue count | equals source issues + PRs |
| Closed states | sample 10 | states match originals |
| Labels / milestones | set comparison | identical |
| Workflows present | `ls .github/workflows` on destination | files present, push not rejected |

Any mismatch is a hard stop.

---

## 8. Phase 4 — The real run

Only after the dry run passes verification:

1. Delete the dry-run repo: `gh repo delete AeroJudge/migration-dryrun --yes`
   (requires the `delete_repo` scope from Section 0.6)
2. **Do not create `AeroJudge/aerojudge-device-app` — it already exists**, created 2026-05-20,
   private, and verified **completely empty (0 refs, size 0)** on 2026-07-29 with admin
   permission for `dpgarceau`. Re-confirm it is still empty immediately before pushing:
   ```bash
   git ls-remote https://github.com/AeroJudge/aerojudge-device-app.git | wc -l   # MUST be 0
   ```
   **If that is not 0, STOP.** Something was pushed in the interim; `--mirror` would delete it.
3. **Disable Actions immediately, before any push** — Phase 1 step 5, applied to the real
   repo. The dry run having been safe does not make this optional.
4. Re-run Phases 1–3 against it, from a **freshly mirrored clone** (the dry-run mirror is
   stale by then). The fresh clone re-fetches `refs/pull/*`, so the fork-PR promotion in
   Phase 1 step 2 **must be redone** — it is not carried over from the dry run.
5. Only after Phase 3 passes, re-enable Actions:
   `gh api -X PUT repos/AeroJudge/aerojudge-device-app/actions/permissions -F enabled=true`
   Watch the first runs. Expect failures from stale repo-scoped references — that is what the
   rename PR below fixes.

### If the final repo will be public, scan history before pushing

Secret-scanning push protection is free on public repos but needs GHAS on private ones. The
dry run was private. **A secret anywhere in the ~380 commits of history will sail through the
dry run and then reject the real push** — the exact failure the dry run is supposed to catch.

Before step 4's push, scan the mirrored history (`gitleaks detect --source . --no-git=false`,
or equivalent) and report findings to the operator **before** attempting the push.

If push protection rejects the push, **rewriting history to strip the secret is forbidden**
(ground rule 7) — it would re-hash every descendant commit and break the union's integrity.
The legitimate resolutions, in order of preference, are all operator decisions:

1. **Rotate the exposed credential**, then have an org owner allow the specific detected
   secret through push protection. The secret stays in history — it is already public in the
   source repo, so migrating it exposes nothing new — but it is dead.
2. Create the destination **private**, push, then flip to public after rotation.
3. Temporarily relax org-level push protection for the migration window.

Log the finding and the chosen route in `migration/BLOCKED.md`. Note that the secret is
**already public** in `IMAC-ORG/imac-judge-app` — rotation is required regardless of which
route is taken, and is urgent independent of this migration.

### Rename fallout — largely already done; report only, change nothing

**The 96 snapshot commits already completed the rename.** Verified against the union's `main`
on 2026-07-29:

| Term | Source `main` (old) | Union `main` (current) |
|---|---|---|
| `imacjudgeapp` | 2 in 1 file | **0** |
| `imac-judge-app` | 18 in 9 files | **2** |
| `IMAC-ORG` | 13 in 7 files | **9** |

Every mechanical hotspot the earlier draft of this spec listed is now clean: `pom.xml`,
`Dockerfile`, `dockerbuild/`, `.devcontainer/`, `.github/workflows/*` all report **0**, and
`imac-judge-app.iml` **no longer exists** in the tree.

All 11 remaining occurrences are **intentional prose about the project's own history**, not
stale references, and should be left exactly as they are:

| File | Line | Nature |
|---|---|---|
| `MIGRATION.md` | 9 | URL of the old repo, in migration documentation |
| `README.md` | 16, 20, 22, 24, 26 | "continuation of the former IMAC Judge App", legacy-archive plan |
| `scripts/config.txt` | 7 | `# NOT TO BE USED ON OLDER LEGACY IMAC-ORG DERIVED SYSTEMS` |
| `scripts/archive/config3-50.txt` | 7 | same warning comment |
| `scripts/archive/config3-60.txt` | 7 | same warning comment |

**There is therefore no rename PR to write.** Produce `migration/RENAME-REPORT.md` recording
the above for the record, confirm the counts still match at Phase 3, and stop. If a future
count is *higher* than this table, something regressed — investigate before acting.

**Change nothing as part of the migration push regardless.** The migration must be
byte-identical to its sources.

### Snapshot integration is already done

An earlier draft of this spec planned a post-migration cherry-pick or graft of ~100 commits.
**That is obsolete.** The snapshot shares full ancestry and fast-forwards the source, so the
integration happens inside Phase 1 step 3b as an ordinary fetch. There is no cherry-pick, no
graft, no `filter-repo`, no conflict resolution, and no post-migration integration phase.

If anything in this section's history tempts a rewrite of the snapshot's commits: don't.
Ground rules 7 and 8 apply, and nothing here requires it.

### Generate the rename report from the union, never the source

`RENAME-REPORT.md` must be produced against the **union's** `main`, not the source's. A sweep
of the source alone reports the stale 2026-04-30 picture — 33 occurrences across hotspots that
have since been fixed — and would send someone re-doing work the snapshot commits already did.

```bash
git grep -I -i -n -e IMAC-ORG -e imac-judge-app -e imacjudgeapp refs/heads/main
```

---

## 9. Manual checklist for the operator (cannot be automated)

- [ ] Re-enter **Actions secrets and variables** — values are write-only in the API and
      unreadable by anyone, including the original admin. Names can be listed only with admin.
- [ ] Recreate **branch protection / rulesets** on `main`
- [ ] Recreate **webhooks** (secrets are not retrievable)
- [ ] Recreate **deploy keys** (private halves are not retrievable)
- [ ] Recreate **environments** and their protection rules
- [ ] Re-enable **Dependabot** / security alerts / code scanning
- [ ] Confirm **Actions were re-enabled** (Phase 4 step 5 — they are deliberately disabled for
      the whole migration) and that workflows run green
- [ ] Update any **external CI, deployment targets, or device provisioning scripts** pointing
      at the old repo URL
- [ ] Have every contributor run `git remote set-url origin https://github.com/AeroJudge/aerojudge-device-app.git`
- [ ] Add a pointer to the new repo in the **old repo's README** — you have write access, so
      this is possible even though archiving it is not
- [ ] Ask GitHub Support about archiving or redirecting `IMAC-ORG/imac-judge-app`, and about
      recovering org ownership if that's still worth pursuing
- [ ] Re-add repository **collaborators / teams** in the new org
- [ ] Decide the fate of `dpgarceau/TEMP_aj-device-app_migration` once the destination is
      verified — its content is fully contained in the union, so it can be archived or deleted.
      Do not delete it until Phase 3 passes.
- [ ] Decide whether the two abandoned 2023 branches (`feat_round_numbering`,
      `feature/eureka`) should survive long-term. They are migrated for completeness; deleting
      them later is a normal repo decision, not a migration one.

---

## 10. Accepted losses

These cannot be preserved by any method short of a native transfer, and the plan accepts them:

- Original issue and PR numbers, timestamps, and authorship attribution (recreated issues are
  authored by the migrating account, with original author noted in the body)
- PR diffs, review threads, review approvals, and merge metadata as *native* PRs — the code is
  preserved in history, but the review conversation becomes issue text
- Cross-reference links between commits and their original issue numbers
- **Issue and PR numbering alignment** — gaps in the source shift everything after them.
  `migration/archive/number-map.json` is the authoritative old→new mapping
- **Reactions, pinned issues, and Projects (classic or v2) linkage**
- Stars (0), forks (0), watchers (5) — watchers will need to re-watch
- Traffic and insights history
- The `IMAC-ORG` URL itself; no automatic redirect exists for a mirror migration

Commit SHAs, authorship, commit dates and GPG signatures **are** preserved exactly. Anyone
with the old clone can add the new remote and push/pull without conflict.

---

## 11. Suggested first message to Claude Code

> Read MIGRATION-SPEC.md in full. Execute Phase 0 only, then stop and show me the preflight
> report. Do not create or push anything yet.
>
> In the report, call out these explicitly, since later phases branch on them:
> the re-verified two-repo relationship from Section 1 (snapshot ahead 96 / behind 0, zero tag
> collisions); the list of cross-repository (fork) PRs; whether releases, packages or
> Discussions exist; and the AeroJudge org's secret-scanning push-protection settings.

---

## 12. Execution record — what actually happened

Executed 2026-07-29 → 2026-07-30 by `dpgarceau`. This section is written **after** the fact and
describes the real run, including where the plan above turned out to be wrong. Where this
section and the phases above disagree, **this section is what happened.**

### Result

`AeroJudge/aerojudge-device-app` (private) now contains:

| | |
|---|---|
| Commits (all refs) | **488** |
| Branches | **4** — `main`, `admin-pages-overhaul`, `feat_round_numbering`, `feature/eureka` |
| Tags | **84** (58 from source + 26 from snapshot, disjoint sets) |
| Root commits | **1** |
| `main` | `f3492464e6044ec9f26e3f957927d3fb048876d6` |
| Releases | **51**, with **130 assets / 2.38 GB** (29 from source + 22 from snapshot) |
| Labels / milestones | 11 / 1 |
| Issues | 13 hand-written by the operator; history archived as JSON, not recreated |

### Phases as run

| Phase | Script | Outcome |
|---|---|---|
| 0 — preflight | `phase0-preflight.sh` | PASS (after two script bugs were fixed — see below) |
| 1 — union push, dry run | `phase1-union-push.sh AeroJudge/migration-dryrun --create` | pushed |
| 3 — verify dry run | `phase3-verify.sh` | 16/17, caught the default-branch bug |
| 3 — re-verify | `phase3-verify.sh` | **17/17 PASS** |
| 4 — union push, real | `phase1-union-push.sh AeroJudge/aerojudge-device-app` | pushed |
| 3 — verify real | `phase3-verify.sh` | **17/17 PASS** |
| — | `gh repo delete AeroJudge/migration-dryrun` | dry run removed |
| 2d — releases (source) | `phase2d-releases.sh` | 29/29 releases, 64/64 assets, byte-identical |
| 2d — releases (snapshot) | `SRC_REPO=… phase2d-releases.sh` | 22/22 releases, 66/66 assets, byte-identical |
| 2a/2b — metadata | `phase2-metadata.sh` | labels + milestone + archive + render gate |
| 2b — issue creation | *not run* | dropped by decision |

### Premises in this spec that were wrong

Every one of these was caught by measuring rather than trusting the document. This is the
single most useful thing about the process and the reason Phase 0 exists.

| Section 1 claimed | Reality |
|---|---|
| The second repo is a download/copy with no shared ancestry | Full clone, complete shared ancestry; `snapshot/main` fast-forwards `source/main` by 96 commits, 0 behind |
| ~100 commits need cherry-picking or grafting | Neither. A plain `git fetch` — no conflicts, no rewriting |
| `8afe8c6` is the integration base | An ordinary mid-range commit that exists only in the snapshot; needs no special handling |
| The rename PR is a substantial job | Already done by the newer commits; the 9 surviving old-name lines across 5 files are intentional prose about project history |
| Mirroring one repo suffices | **Neither repo alone is complete.** 3 commits live only on two abandoned 2023 source branches, and all 58 source tags exist only in the source |
| No releases, no packages | **51 releases across both repos**, 130 assets migrated (2.38 GB), actively downloaded |
| Destination must be created | It already existed — created 2026-05-20, empty, private |
| Fork PRs need ref promotion before dropping `refs/pull` | 0 fork PRs, and all 7 closed-unmerged heads already reachable in the union. No-op |

### Bugs found during execution

1. **Tag pollution via `git fetch`.** Fetching between the two mirrors without `--no-tags`
   auto-followed tags into `refs/tags/*`, making each mirror appear to contain the other's tags.
   This produced a *false* finding that all 58 source tags existed in the snapshot, and inflated
   commit counts. Fixed with `--no-tags`, purity guards that abort on a polluted mirror, and
   direct mirror-to-mirror comparison instead of fetching.
2. **`git remote add` leaves `refs/remotes/*`.** These kept foreign commits reachable and
   inflated `rev-list --all` from 406 to 479. Fixed by fetching from a path with an explicit
   refspec and never adding a remote.
3. **Default branch race.** After a mirror push GitHub sets the default branch from the first
   ref it processes — alphabetically, so `admin-pages-overhaul` beat `main`. A `gh repo edit`
   issued immediately after the push returned exit 0 and *silently lost the race*. Fixed with
   set → verify → retry ×5 → hard fail. **Caught only because the dry run was verified.**

4. **Phase 0 probed releases on one repo only.** The preflight hardcoded
   `IMAC-ORG/imac-judge-app` for the releases check, so it missed the snapshot's **22
   releases** — including `v26.1.1`–`v26.1.4`, the current shipping versions — and still
   reported **PASS**. Everywhere else in this migration both repos were treated as co-equal
   sources; releases were the one place that assumption was not applied. Found only after the
   first release run, while checking an unrelated question about workflow permissions on the
   snapshot repo. Fixed by parameterising `SRC_REPO` and running the script once per source.
   **Lesson: when a migration has two sources, every per-repo probe must loop over both.**

### Deliberate deviations from the plan above

- **Metadata was not dry-run.** The dry run covered the git side only. Rationale: the git push
  is deterministic and fully verifiable, while the expensive and irreversible part of metadata
  is notifications, which the render gate handles better than a dry run would.
- **The real repo was pushed and verified *before* the dry run was deleted**, rather than
  after. The spec ordered deletion first only because the destination needed creating; it
  already existed, so keeping the dry run until the real push verified was strictly safer.
- **Issues and PRs were not recreated** — see Section 6.
- **One release asset excluded**: `v1.1.0/2025-03-29_Clean_8GB_bootfs.zip`, 1,899,491,097 bytes,
  2 downloads. Its original URL is recorded in that release's body and in `releases.json`.

### What made the difference

- **Disabling Actions before the push.** `maven.yml` ("Create Tag") fires on push to `main`
  touching `pom.xml`, `judge/**`, `figures/**`, `volume_service/**`, `scripts/**`, `assembly/**`
  and chains into `build.yml` ("Create Release"), which publishes a release. With Actions
  enabled, the mirror push would have created a spurious tag **and published a release**,
  corrupting the tag set that had just been verified.
- **The render gate.** It surfaced 200 `@mentions` across 7 real people before anything was
  created, which is what allowed the "don't recreate" decision to be made for free.
- **Verifying the dry run rather than assuming it worked.** The default-branch bug produced a
  completely clean-looking push.

### Version scheme going forward

The release pipeline generates `v26.1.x` (`YY.MAJOR.MINOR`, year 26); `pom.xml` selects the
active `YY.MAJOR.0` line. The source's `v1.0.x` / `v2.1.2` tags are legacy. **The next
qualifying push to `main` will auto-create a tag and publish a release** once Actions are on.

### Still outstanding

See Section 9. In particular: re-enable Actions, branch protection, secrets, collaborators,
and adding a pointer to this repo in the old repo's README.

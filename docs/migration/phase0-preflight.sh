#!/usr/bin/env bash
# Phase 0 — Preflight probe. READ-ONLY: creates nothing on GitHub, pushes nothing.
# Re-runnable and idempotent. Writes migration/PREFLIGHT.md plus a timestamped log.
#
# Mirror hygiene rules learned the hard way — do not "simplify" these away:
#   * src.git and snap.git are PRISTINE. Never fetch into them; a stray fetch auto-follows
#     tags into refs/tags/* and silently corrupts every tag and commit count downstream.
#   * Never use `git remote add` for the cross-repo math: it leaves refs/remotes/<name>/*
#     which keeps foreign commits reachable and inflates rev-list --all.
#   * Compare tags between the two mirrors directly. Fetching them into a namespace does
#     not reliably create refs for tags whose objects are already present.
set -uo pipefail

SRC=IMAC-ORG/imac-judge-app
SNAP=dpgarceau/TEMP_aj-device-app_migration
DST=AeroJudge/aerojudge-device-app
DRY=AeroJudge/migration-dryrun
ME=dpgarceau

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$HERE/PREFLIGHT.md"
WORK="${PHASE0_WORK:-$HERE/.work}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$HERE/logs" "$WORK"
LOG="$HERE/logs/phase0-$TS.log"
exec > >(tee -a "$LOG") 2>&1

echo "Phase 0 preflight — $TS"
fail=0
stops=""
stop() { echo "HARD STOP: $*"; stops="$stops- $*"$'\n'; fail=1; }

# ---------- pristine mirrors ----------
for pair in "src:$SRC" "snap:$SNAP"; do
  name="${pair%%:*}"; repo="${pair##*:}"
  if [ -d "$WORK/$name.git" ]; then
    echo "refreshing $name.git"
    git -C "$WORK/$name.git" remote update --prune >/dev/null 2>&1 || echo "WARN: refresh failed for $name"
  else
    echo "cloning $repo"
    git clone --mirror -q "https://github.com/$repo.git" "$WORK/$name.git" || { echo "FATAL: clone $repo failed"; exit 1; }
  fi
done
SRCG="git -C $WORK/src.git"
SNAPG="git -C $WORK/snap.git"

# purity guard
for n in src snap; do
  strays=$(git -C "$WORK/$n.git" for-each-ref --format='%(refname)' \
           | grep -v -E '^refs/(heads|tags|pull)/' | head -3)
  [ -n "$strays" ] && { echo "FATAL: $n.git polluted with: $strays"; echo "Delete $WORK and re-run."; exit 1; }
done

# ---------- combined repo for cross-repo math (src.git stays pristine) ----------
rm -rf "$WORK/comb.git"; cp -r "$WORK/src.git" "$WORK/comb.git"
COMB="git -C $WORK/comb.git"
$COMB fetch -q --no-tags "$WORK/snap.git" 'refs/heads/*:refs/snapx/heads/*'

AHEAD=$($COMB rev-list --count refs/heads/main..refs/snapx/heads/main)
BEHIND=$($COMB rev-list --count refs/snapx/heads/main..refs/heads/main)
MB=$($COMB merge-base refs/heads/main refs/snapx/heads/main)
SRCTIP=$($SRCG rev-parse refs/heads/main)
SNAPTIP=$($SNAPG rev-parse refs/heads/main)
[ "$BEHIND" = "0" ] || stop "source/main has $BEHIND commits absent from snapshot — fast-forward assumption void"
[ "$MB" = "$SRCTIP" ] || stop "merge-base $MB != source tip $SRCTIP"

STALE=""
for b in feat_round_numbering feature/eureka admin-pages-overhaul; do
  n=$($COMB rev-list --count "refs/snapx/heads/main..refs/heads/$b" 2>/dev/null || echo "?")
  STALE="$STALE| \`$b\` | $n |"$'\n'
done

# ---------- tag comparison, done directly between the two mirrors ----------
$SRCG  for-each-ref --format='%(refname:strip=2)' refs/tags | sort -u > "$WORK/srctags.txt"
$SNAPG for-each-ref --format='%(refname:strip=2)' refs/tags | sort -u > "$WORK/snaptags.txt"
SRC_TAGS=$(wc -l < "$WORK/srctags.txt" | tr -d ' ')
SNAP_TAGS=$(wc -l < "$WORK/snaptags.txt" | tr -d ' ')
ONLY_SRC=$(comm -23 "$WORK/srctags.txt" "$WORK/snaptags.txt" | wc -l | tr -d ' ')
ONLY_SNAP=$(comm -13 "$WORK/srctags.txt" "$WORK/snaptags.txt" | wc -l | tr -d ' ')
COLL=0; COLLIST=""
while read -r t; do
  a=$($SRCG  rev-parse -q --verify "refs/tags/$t^{commit}" 2>/dev/null)
  b=$($SNAPG rev-parse -q --verify "refs/tags/$t^{commit}" 2>/dev/null)
  [ -n "$a" ] && [ -n "$b" ] && [ "$a" != "$b" ] && { COLL=$((COLL+1)); COLLIST="$COLLIST $t"; }
done < <(comm -12 "$WORK/srctags.txt" "$WORK/snaptags.txt")
[ "$COLL" = "0" ] || stop "$COLL tag collisions (same name, different commit):$COLLIST"
# NOTE: tags existing only in the source is EXPECTED — the two tag namespaces are disjoint
# (source 58, snapshot 26, overlap 0). That is precisely why the union needs both repos.
# The real guarantee is that the union preserves every source ref; see $MISSING below.
EXPECT_UNION_TAGS=$((ONLY_SRC + ONLY_SNAP + COLL))

# ---------- union projection ----------
rm -rf "$WORK/union.git"; cp -r "$WORK/src.git" "$WORK/union.git"
U="git -C $WORK/union.git"
$U for-each-ref --format='delete %(refname)' refs/pull | $U update-ref --stdin
U_PRE_C=$($U rev-list --all --count); U_PRE_T=$($U for-each-ref refs/tags|wc -l|tr -d ' ')
U_PRE_B=$($U for-each-ref refs/heads|wc -l|tr -d ' ')
$U fetch -q "$WORK/snap.git" 'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*'
U_C=$($U rev-list --all --count); U_B=$($U for-each-ref refs/heads|wc -l|tr -d ' ')
U_T=$($U for-each-ref refs/tags|wc -l|tr -d ' '); U_R=$($U rev-list --max-parents=0 --all|wc -l|tr -d ' ')
U_MAIN=$($U rev-parse refs/heads/main)
U_STRAY=$($U for-each-ref --format='%(refname)' | grep -v -E '^refs/(heads|tags)/' | wc -l | tr -d ' ')
[ "$U_STRAY" = "0" ] || stop "union has $U_STRAY refs outside refs/heads and refs/tags"
[ "$U_MAIN" = "$SNAPTIP" ] || stop "union main $U_MAIN != snapshot tip $SNAPTIP"
[ "$U_T" = "$EXPECT_UNION_TAGS" ] || stop "union has $U_T tags, expected $EXPECT_UNION_TAGS (source $SRC_TAGS + snapshot $SNAP_TAGS, disjoint)"
RENAME_LINES=$($U grep -I -i -l -e IMAC-ORG -e imac-judge-app -e imacjudgeapp refs/heads/main 2>/dev/null | wc -l | tr -d ' ')
RENAME_FILES=$($U grep -I -i -l -e IMAC-ORG -e imac-judge-app -e imacjudgeapp refs/heads/main 2>/dev/null | sed 's|^refs/heads/main:||' | tr '\n' ' ')

# every source ref must survive
MISSING=0
while read -r oid; do $U cat-file -e "$oid" 2>/dev/null || MISSING=$((MISSING+1)); done \
  < <($SRCG for-each-ref --format='%(objectname)' refs/heads refs/tags)
[ "$MISSING" = "0" ] || stop "$MISSING source refs unreachable in the union"

# ---------- GitHub metadata ----------
PERMS=$(gh api "repos/$SRC" --jq '.permissions|to_entries|map(select(.value))|map(.key)|join(", ")')
S_VIS=$(gh api "repos/$SRC" --jq '.visibility'); S_DEF=$(gh api "repos/$SRC" --jq '.default_branch')
S_DESC=$(gh api "repos/$SRC" --jq '.description // ""'); S_HOME=$(gh api "repos/$SRC" --jq '.homepage // ""')
S_TOPICS=$(gh api "repos/$SRC" --jq '(.topics // [])|join(", ")')
S_FLAGS=$(gh api "repos/$SRC" --jq '"issues=\(.has_issues) projects=\(.has_projects) wiki=\(.has_wiki) discussions=\(.has_discussions) archived=\(.archived)"')

I_OPEN=$(gh issue list -R "$SRC" --state open --limit 1000 --json number --jq 'length')
I_CLOSED=$(gh issue list -R "$SRC" --state closed --limit 1000 --json number --jq 'length')
P_OPEN=$(gh pr list -R "$SRC" --state open --limit 1000 --json number --jq 'length')
P_ALL=$(gh pr list -R "$SRC" --state all --limit 1000 --json number --jq 'length')
P_MERGED=$(gh pr list -R "$SRC" --state all --limit 1000 --json mergedAt --jq '[.[]|select(.mergedAt)]|length')
P_UNMERGED=$((P_ALL - P_MERGED - P_OPEN))
C_ISS=$(gh issue list -R "$SRC" --state all --limit 1000 --json comments --jq '[.[].comments|length]|add // 0')
C_PR=$(gh pr list -R "$SRC" --state all --limit 1000 --json comments --jq '[.[].comments|length]|add // 0')
N_LABELS=$(gh api "repos/$SRC/labels" --paginate --jq 'length')
N_MS=$(gh api "repos/$SRC/milestones?state=all" --jq 'length')
FORKPRS=$(gh pr list -R "$SRC" --state all --limit 1000 --json number,isCrossRepository --jq '[.[]|select(.isCrossRepository)|.number]|join(", ")')
[ -z "$FORKPRS" ] && FORKPRS="none"

# Releases: resolved by operator decision 2026-07-30 — recreate all 29 with notes and assets,
# excluding only the 1.8 GB bootfs image. See spec 6d. No longer a hard stop; still reported,
# and still fails if the shape changes from what that decision was based on.
N_REL=$(gh api "repos/$SRC/releases" --paginate --jq '.[].id' 2>/dev/null | wc -l | tr -d ' ')
REL_ASSETS=$(gh api "repos/$SRC/releases" --paginate --jq '.[].assets[].size' 2>/dev/null | awk '{n++; s+=$1} END{printf "%d assets, %.2f GB", n, s/1073741824}')
REL_KEEP=$(gh api "repos/$SRC/releases" --paginate --jq '.[].assets[]|select(.size<=1000000000).size' 2>/dev/null | awk '{n++; s+=$1} END{printf "%d assets, %.2f GB", n, s/1073741824}')
REL_SKIP=$(gh api "repos/$SRC/releases" --paginate --jq '.[]|.tag_name as $t|.assets[]|select(.size>1000000000)|"\($t)/\(.name) (\(.size) bytes)"' 2>/dev/null | tr '\n' ' ')
REL_EMPTY=$(gh api "repos/$SRC/releases" --paginate --jq '[.[]|select([.assets[]|select(.size<=1000000000)]|length==0)]|length' 2>/dev/null || echo 0)
[ "${N_REL:-0}" = "29" ] || stop "release count is $N_REL, decision in spec 6d was based on 29 — re-confirm scope"
[ "${REL_EMPTY:-0}" = "0" ] || stop "$REL_EMPTY releases would have every asset excluded — re-confirm the exclusion rule"

ORPHANS=""
while read -r num oid; do
  [ -z "${oid:-}" ] && continue
  $U cat-file -e "$oid" 2>/dev/null || ORPHANS="$ORPHANS #$num"
done < <(gh pr list -R "$SRC" --state all --limit 1000 --json number,state,mergedAt,headRefOid \
         --jq '.[]|select(.mergedAt==null and .state=="CLOSED")|"\(.number) \(.headRefOid)"')
[ -z "$ORPHANS" ] && ORPHANS="none — nothing orphaned" || stop "orphaned PR heads:$ORPHANS"

LFSRES="none"; $U grep -I -l 'filter=lfs' refs/heads/main -- .gitattributes >/dev/null 2>&1 && { LFSRES="PRESENT"; stop "Git LFS filter found"; }
SUBM="none"
for b in $($U for-each-ref --format='%(refname)' refs/heads); do
  $U cat-file -e "$b:.gitmodules" 2>/dev/null && { SUBM="found on $b"; stop "submodules found on $b"; }
done

DST_EXISTS=$(gh api "repos/$DST" --jq '.full_name' 2>/dev/null | grep -v '^{' || true); DST_EXISTS="${DST_EXISTS:-does not exist}"
DST_VIS=$(gh api "repos/$DST" --jq '.visibility' 2>/dev/null | grep -v '^{' || true); DST_VIS="${DST_VIS:-n/a}"
DST_REFS=$(git ls-remote "https://github.com/$DST.git" 2>/dev/null | wc -l | tr -d ' ')
DRY_EXISTS=$(gh api "repos/$DRY" --jq '.full_name' 2>/dev/null | grep -v '^{' || true); DRY_EXISTS="${DRY_EXISTS:-does not exist}"
ORGROLE=$(gh api "orgs/AeroJudge/memberships/$ME" --jq '.role' 2>/dev/null || echo "unknown")
ORGPP=$(gh api orgs/AeroJudge --jq '.secret_scanning_push_protection_enabled_for_new_repositories' 2>/dev/null || echo "unknown")
SCOPES=$(gh auth status 2>&1 | grep -o "'.*'" | head -1)
[ "$DST_REFS" = "0" ] || stop "destination has $DST_REFS refs — not empty; --mirror would delete them"

# ---------- report ----------
{
cat <<EOF
# Phase 0 — Preflight Report

Generated $TS by \`migration/phase0-preflight.sh\`.
**Read-only run — nothing was created, pushed, or modified.** Idempotent; re-run any time.

## Verdict: $([ "$fail" = "0" ] && echo "**PASS** — safe to proceed to Phase 1" || echo "**HALT** — blocking items below")

EOF
[ "$fail" != "0" ] && printf '### Blocking\n\n%s\n' "$stops"
cat <<EOF
## Access

| Check | Value |
|---|---|
| Token scopes | $SCOPES |
| AeroJudge role | \`$ORGROLE\` |
| Effective permission on source | \`$PERMS\` (no admin — expected) |
| Org push protection on new repos | \`$ORGPP\` |

## Source — $SRC

| Field | Value |
|---|---|
| Visibility / default branch | $S_VIS / \`$S_DEF\` |
| Description | ${S_DESC:-_(none)_} |
| Homepage | ${S_HOME:-_(none)_} |
| Topics | ${S_TOPICS:-_(none)_} |
| Flags | $S_FLAGS |
| **Releases** | **$N_REL — $REL_ASSETS** |
| → to migrate (spec 6d) | $REL_KEEP |
| → excluded by decision | $REL_SKIP |
| → releases left with no assets | $REL_EMPTY (must be 0) |
| Branches / tags | $($SRCG for-each-ref refs/heads|wc -l|tr -d ' ') / $SRC_TAGS |

## Issues, PRs, metadata

| Item | Count |
|---|---|
| Issues open / closed | $I_OPEN / $I_CLOSED |
| PRs open / merged / closed-unmerged | $P_OPEN / $P_MERGED / $P_UNMERGED |
| Comments issues / PRs | $C_ISS / $C_PR |
| Labels / milestones | $N_LABELS / $N_MS |
| Cross-repository (fork) PRs | $FORKPRS |
| Orphaned closed-unmerged PR heads | $ORPHANS |

Estimated Phase 2 create calls: **$((I_OPEN + I_CLOSED + P_ALL + C_ISS + C_PR))** (~10–20 min at 3 s cadence).

## Two-repo relationship

| Check | Value | Required |
|---|---|---|
| source/main tip | \`$SRCTIP\` | — |
| snapshot/main tip | \`$SNAPTIP\` | — |
| merge-base | \`$MB\` | = source tip |
| snapshot ahead | **$AHEAD** | — |
| **source ahead (would be lost)** | **$BEHIND** | **must be 0** |
| Tag collisions | **$COLL** | must be 0 |
| Tags only in source | $ONLY_SRC | expected — namespaces are disjoint |
| Tags only in snapshot | $ONLY_SNAP | expected |
| Shared tag names | $(comm -12 "$WORK/srctags.txt" "$WORK/snaptags.txt" | wc -l | tr -d ' ') | 0 — the two schemes never overlap |
| Source / snapshot tag counts | $SRC_TAGS / $SNAP_TAGS | union must be $EXPECT_UNION_TAGS |

Commits unique to each source branch vs snapshot/main:

| Branch | Unique commits |
|---|---|
$STALE
## Union projection (built locally, not pushed)

| Stage | Commits | Branches | Tags | Roots |
|---|---|---|---|---|
| Source, refs/pull dropped | $U_PRE_C | $U_PRE_B | $U_PRE_T | — |
| **Union** | **$U_C** | **$U_B** | **$U_T** | **$U_R** |

- Union \`main\` = \`$U_MAIN\`
- Source refs unreachable in union: **$MISSING** (must be 0)
- Refs outside heads/tags: **$U_STRAY** (must be 0)
- Files with old-name references: **$RENAME_LINES** — $RENAME_FILES
  (all intentional prose — see MIGRATION-FINDINGS.md §2.7)

## Integrity

| Check | Result |
|---|---|
| Git LFS | $LFSRES |
| Submodules | $SUBM |

## Destination

| Repo | State |
|---|---|
| $DST | $DST_EXISTS — \`$DST_VIS\`, **$DST_REFS refs** (must be 0) |
| $DRY | $DRY_EXISTS |

---

**Halt here.** Metadata (Phase 2) is deferred until after the real push, per the operator
scope decision. Phase 1 covers the git side only.
EOF
} > "$OUT"

echo; echo "wrote $OUT"; echo "log: $LOG"
echo "verdict: $([ "$fail" = "0" ] && echo PASS || echo HALT)"
exit $fail

#!/usr/bin/env bash
# Phase 1 — build the union of both source repos and push it to a destination.
#
#   bash migration/phase1-union-push.sh AeroJudge/migration-dryrun --create
#   bash migration/phase1-union-push.sh AeroJudge/aerojudge-device-app
#
# Safety properties (do not weaken):
#   * Refuses to push to a destination that already has refs — --mirror would delete them.
#   * Disables GitHub Actions on the destination BEFORE pushing, and verifies it took.
#     Source workflows trigger on push; --mirror lands every branch at once.
#   * Never force-pushes. Never rewrites history. Aborts on any shape mismatch.
#   * Mirrors stay pristine; all cross-repo work happens in a disposable union.git.
set -uo pipefail

SRC=IMAC-ORG/imac-judge-app
SNAP=dpgarceau/TEMP_aj-device-app_migration

TARGET="${1:-}"
CREATE="${2:-}"
[ -z "$TARGET" ] && { echo "usage: $0 <owner/repo> [--create]"; exit 2; }

# Expected union shape, established by Phase 0. A mismatch means reality moved.
EXP_COMMITS=488; EXP_BRANCHES=4; EXP_TAGS=84; EXP_ROOTS=1
EXP_MAIN=f3492464e6044ec9f26e3f957927d3fb048876d6

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${PHASE1_WORK:-$HERE/.work}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$HERE/logs" "$WORK"
LOG="$HERE/logs/phase1-$TS.log"
exec > >(tee -a "$LOG") 2>&1

echo "=== Phase 1 — union push -> $TARGET  ($TS) ==="
die() { echo; echo "ABORT: $*"; exit 1; }

# ---------- 1. pristine mirrors ----------
for pair in "src:$SRC" "snap:$SNAP"; do
  n="${pair%%:*}"; r="${pair##*:}"
  if [ -d "$WORK/$n.git" ]; then
    echo "refreshing $n.git"; git -C "$WORK/$n.git" remote update --prune >/dev/null 2>&1 \
      || die "could not refresh $n.git"
  else
    echo "cloning $r"; git clone --mirror -q "https://github.com/$r.git" "$WORK/$n.git" \
      || die "clone failed: $r"
  fi
  strays=$(git -C "$WORK/$n.git" for-each-ref --format='%(refname)' | grep -v -E '^refs/(heads|tags|pull)/' | head -3)
  [ -n "$strays" ] && die "$n.git polluted with: $strays — delete $WORK and re-run"
done

# ---------- 2. build the union ----------
echo "building union"
rm -rf "$WORK/union.git"; cp -r "$WORK/src.git" "$WORK/union.git"
U="git -C $WORK/union.git"
$U for-each-ref --format='delete %(refname)' refs/pull | $U update-ref --stdin
# no --force anywhere: main must fast-forward, tag namespaces are disjoint
$U fetch "$WORK/snap.git" 'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*' 2>&1 | sed 's/^/  /'
[ "${PIPESTATUS[0]}" = "0" ] || die "union fetch failed — if it reported a forced update, the fast-forward assumption is void"

# ---------- 3. assert shape ----------
C=$($U rev-list --all --count); B=$($U for-each-ref refs/heads|wc -l|tr -d ' ')
T=$($U for-each-ref refs/tags|wc -l|tr -d ' '); R=$($U rev-list --max-parents=0 --all|wc -l|tr -d ' ')
M=$($U rev-parse refs/heads/main)
STRAY=$($U for-each-ref --format='%(refname)' | grep -v -E '^refs/(heads|tags)/' | wc -l | tr -d ' ')
echo "  commits=$C branches=$B tags=$T roots=$R stray=$STRAY"
echo "  main=$M"
[ "$C" = "$EXP_COMMITS" ]  || die "commits $C != expected $EXP_COMMITS"
[ "$B" = "$EXP_BRANCHES" ] || die "branches $B != expected $EXP_BRANCHES"
[ "$T" = "$EXP_TAGS" ]     || die "tags $T != expected $EXP_TAGS"
[ "$R" = "$EXP_ROOTS" ]    || die "roots $R != expected $EXP_ROOTS"
[ "$M" = "$EXP_MAIN" ]     || die "main $M != expected $EXP_MAIN"
[ "$STRAY" = "0" ]         || die "$STRAY refs outside heads/tags"

MISSING=0
while read -r oid; do $U cat-file -e "$oid" 2>/dev/null || MISSING=$((MISSING+1)); done \
  < <(git -C "$WORK/src.git" for-each-ref --format='%(objectname)' refs/heads refs/tags)
[ "$MISSING" = "0" ] || die "$MISSING source refs unreachable in union"
echo "  shape OK, all source refs preserved"

# ---------- 4. destination ----------
if ! gh api "repos/$TARGET" --jq .full_name >/dev/null 2>&1; then
  [ "$CREATE" = "--create" ] || die "$TARGET does not exist (pass --create to create it)"
  echo "creating $TARGET (private, empty)"
  gh repo create "$TARGET" --private --description "Temporary dry run — safe to delete" \
    || die "could not create $TARGET"
  sleep 3
else
  echo "$TARGET already exists"
fi

REFS=$(git ls-remote "https://github.com/$TARGET.git" 2>/dev/null | wc -l | tr -d ' ')
echo "  destination refs: $REFS"
[ "$REFS" = "0" ] || die "$TARGET has $REFS refs — --mirror would DELETE them. Refusing."

# ---------- 5. Actions OFF before any push ----------
echo "disabling Actions on $TARGET"
gh api -X PUT "repos/$TARGET/actions/permissions" -F enabled=false >/dev/null 2>&1 \
  || die "could not disable Actions"
ENABLED=$(gh api "repos/$TARGET/actions/permissions" --jq '.enabled' 2>/dev/null)
[ "$ENABLED" = "false" ] || die "Actions still enabled ($ENABLED) — refusing to push"
echo "  Actions disabled (verified)"

# ---------- 6. push ----------
echo "pushing union (mirror)"
$U push --mirror "https://github.com/$TARGET.git" 2>&1 | sed 's/^/  /'
[ "${PIPESTATUS[0]}" = "0" ] || die "push failed — if rejected by secret-scanning push protection, see spec Phase 4; do NOT rewrite history"

# ---------- 7. settings ----------
# Default branch: GitHub assigns this from the first ref it processes during a mirror push,
# which is alphabetical — 'admin-pages-overhaul' wins over 'main'. An edit issued immediately
# after the push races that assignment and silently loses. Set, verify, retry.
echo "setting default branch to main"
for attempt in 1 2 3 4 5; do
  gh repo edit "$TARGET" --default-branch main >/dev/null 2>&1
  CUR=$(gh api "repos/$TARGET" --jq '.default_branch' 2>/dev/null)
  [ "$CUR" = "main" ] && { echo "  default branch = main (attempt $attempt)"; break; }
  echo "  attempt $attempt: still '$CUR', retrying"
  sleep 3
done
[ "$(gh api "repos/$TARGET" --jq '.default_branch' 2>/dev/null)" = "main" ] \
  || die "default branch would not stick on main"

DESC=$(gh api "repos/$SRC" --jq '.description // ""')
if [ -n "$DESC" ] && [ "$CREATE" != "--create" ]; then
  gh repo edit "$TARGET" --description "$DESC" >/dev/null 2>&1 || echo "  WARN: description not set"
fi

echo
echo "=== pushed. verify with: bash migration/phase3-verify.sh $TARGET ==="
echo "log: $LOG"

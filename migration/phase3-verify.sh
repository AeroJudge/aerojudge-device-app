#!/usr/bin/env bash
# Phase 3 — verify a pushed destination against the union of both sources.
#
#   bash migration/phase3-verify.sh AeroJudge/migration-dryrun
#
# Read-only. Compares a FRESH mirror of the destination against a locally rebuilt union.
# Any mismatch is a hard failure — this is the gate the whole migration rests on.
set -uo pipefail

SRC=IMAC-ORG/imac-judge-app
SNAP=dpgarceau/TEMP_aj-device-app_migration
TARGET="${1:-}"
[ -z "$TARGET" ] && { echo "usage: $0 <owner/repo>"; exit 2; }

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${PHASE3_WORK:-$HERE/.work}"
OUT="$HERE/VERIFICATION.md"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$HERE/logs" "$WORK"
LOG="$HERE/logs/phase3-$TS.log"
exec > >(tee -a "$LOG") 2>&1

echo "=== Phase 3 — verify $TARGET ($TS) ==="
fails=""
rows=""
chk() { # name  actual  expected
  if [ "$2" = "$3" ]; then rows="$rows| $1 | \`$2\` | \`$3\` | PASS |"$'\n'; echo "  PASS  $1: $2"
  else rows="$rows| $1 | \`$2\` | \`$3\` | **FAIL** |"$'\n'; fails="$fails- $1: got '$2', expected '$3'"$'\n'; echo "  FAIL  $1: got $2 expected $3"; fi
}

# union must already exist from Phase 1; rebuild if absent
if [ ! -d "$WORK/union.git" ]; then
  echo "union.git missing — rebuilding"
  for pair in "src:$SRC" "snap:$SNAP"; do
    n="${pair%%:*}"; r="${pair##*:}"
    [ -d "$WORK/$n.git" ] || git clone --mirror -q "https://github.com/$r.git" "$WORK/$n.git"
  done
  rm -rf "$WORK/union.git"; cp -r "$WORK/src.git" "$WORK/union.git"
  git -C "$WORK/union.git" for-each-ref --format='delete %(refname)' refs/pull | git -C "$WORK/union.git" update-ref --stdin
  git -C "$WORK/union.git" fetch -q "$WORK/snap.git" 'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*'
fi

echo "cloning destination fresh"
rm -rf "$WORK/dst.git"
git clone --mirror -q "https://github.com/$TARGET.git" "$WORK/dst.git" || { echo "FATAL: clone failed"; exit 1; }

U="git -C $WORK/union.git"; D="git -C $WORK/dst.git"

chk "commit count (all refs)" "$($D rev-list --all --count)" "$($U rev-list --all --count)"
chk "branch count"            "$($D for-each-ref refs/heads|wc -l|tr -d ' ')" "$($U for-each-ref refs/heads|wc -l|tr -d ' ')"
chk "tag count"               "$($D for-each-ref refs/tags|wc -l|tr -d ' ')"  "$($U for-each-ref refs/tags|wc -l|tr -d ' ')"
chk "root commits"            "$($D rev-list --max-parents=0 --all|wc -l|tr -d ' ')" "$($U rev-list --max-parents=0 --all|wc -l|tr -d ' ')"
chk "main tip"                "$($D rev-parse refs/heads/main)" "$($U rev-parse refs/heads/main)"
chk "refs outside heads/tags" "$($D for-each-ref --format='%(refname)'|grep -v -E '^refs/(heads|tags)/'|wc -l|tr -d ' ')" "0"

# per-branch tips
BR_DIFF=$(diff <($U for-each-ref --format='%(refname) %(objectname)' refs/heads|sort) \
               <($D for-each-ref --format='%(refname) %(objectname)' refs/heads|sort) | wc -l | tr -d ' ')
chk "branch tips identical (diff lines)" "$BR_DIFF" "0"

# per-tag targets, including annotated-tag object type
TAG_DIFF=$(diff <($U for-each-ref --format='%(refname) %(objectname) %(objecttype)' refs/tags|sort) \
                <($D for-each-ref --format='%(refname) %(objectname) %(objecttype)' refs/tags|sort) | wc -l | tr -d ' ')
chk "tag targets+types identical (diff lines)" "$TAG_DIFF" "0"
chk "annotated tags preserved" "$($D for-each-ref --format='%(objecttype)' refs/tags|grep -c '^tag$')" "$($U for-each-ref --format='%(objecttype)' refs/tags|grep -c '^tag$')"

# every source ref object present on destination
MISS=0
while read -r oid; do $D cat-file -e "$oid" 2>/dev/null || MISS=$((MISS+1)); done \
  < <(git -C "$WORK/src.git" for-each-ref --format='%(objectname)' refs/heads refs/tags)
chk "source refs unreachable on destination" "$MISS" "0"

# the three orphan-risk commits
for c in a05856c90978a3181d1d110696ab733306148112 0bd668e 7318be6b0c40850bb7ca4f6350775c56aaed3b1a; do
  $D cat-file -e "$c" 2>/dev/null && s=present || s=ABSENT
  chk "stale-branch commit ${c:0:8}" "$s" "present"
done

# workflows landed
chk "workflows present on destination" "$($D ls-tree --name-only refs/heads/main -- .github/workflows/ 2>/dev/null | wc -l | tr -d ' ' | awk '{print ($1>0)?"yes":"no"}')" "yes"

# Actions must still be off at this stage
chk "Actions still disabled" "$(gh api "repos/$TARGET/actions/permissions" --jq '.enabled' 2>/dev/null)" "false"
chk "default branch" "$(gh api "repos/$TARGET" --jq '.default_branch' 2>/dev/null)" "main"

VERDICT=$([ -z "$fails" ] && echo "**PASS**" || echo "**FAIL**")
cat > "$OUT" <<EOF
# Phase 3 — Verification: $TARGET

Generated $TS by \`migration/phase3-verify.sh\`. Read-only.
Destination cloned fresh and compared against a locally rebuilt union of both sources.

## Verdict: $VERDICT

| Check | Destination | Expected | Result |
|---|---|---|---|
$rows
EOF
[ -n "$fails" ] && printf '\n## Failures\n\n%s\n' "$fails" >> "$OUT"

echo; echo "verdict: $([ -z "$fails" ] && echo PASS || echo FAIL)"
echo "wrote $OUT"; echo "log: $LOG"
[ -z "$fails" ]

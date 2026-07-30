#!/usr/bin/env bash
# Phase 2a/2b(export+render) — labels, milestones, JSON archive, and the render gate.
#
#   bash migration/phase2-metadata.sh AeroJudge/aerojudge-device-app
#
# This script deliberately STOPS before creating any issue. Issue bodies are rendered to
# migration/archive/rendered/ for human review first: an unescaped @mention notifies a real
# person and CANNOT be undone by deleting the repo afterwards (spec 6b).
#
# Creation is a separate script, run only after that review.
set -uo pipefail

SRC=IMAC-ORG/imac-judge-app
TARGET="${1:-}"
[ -z "$TARGET" ] && { echo "usage: $0 <owner/repo>"; exit 2; }

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARCHIVE="$HERE/archive"; REND="$ARCHIVE/rendered"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$HERE/logs" "$ARCHIVE" "$REND"
LOG="$HERE/logs/phase2-$TS.log"
exec > >(tee -a "$LOG") 2>&1
echo "=== Phase 2 metadata -> $TARGET ($TS) ==="

# spec 6b escaping: @<!---->user and #<!---->123, never backticks (they corrupt code fences)
esc() { sed -E 's/@([A-Za-z0-9][A-Za-z0-9-]*)/@<!---->\1/g; s/(^|[^A-Za-z0-9&#])#([0-9]+)/\1#<!---->\2/g'; }

# ---------- 2a. labels ----------
echo "--- labels ---"
lc=0
while IFS=$'\t' read -r name color desc; do
  [ -z "${name:-}" ] && continue
  if gh label create "$name" -R "$TARGET" --color "$color" --description "${desc:-}" --force >/dev/null 2>&1; then
    echo "  OK $name"; lc=$((lc+1))
  else echo "  FAIL $name"; fi
done < <(gh api "repos/$SRC/labels" --paginate --jq '.[]|[.name,.color,(.description // "")]|@tsv')
echo "  labels created/updated: $lc"

# ---------- 2a. milestones ----------
echo "--- milestones ---"
mc=0
while IFS=$'\t' read -r title state desc due; do
  [ -z "${title:-}" ] && continue
  if gh api "repos/$TARGET/milestones" --jq '.[].title' 2>/dev/null | grep -Fxq "$title"; then
    echo "  SKIP $title (exists)"; continue
  fi
  ARGS=(-f "title=$title" -f "state=$state" -f "description=${desc:-}")
  [ -n "${due:-}" ] && [ "$due" != "null" ] && ARGS+=(-f "due_on=$due")
  if gh api -X POST "repos/$TARGET/milestones" "${ARGS[@]}" >/dev/null 2>&1; then
    echo "  OK $title"; mc=$((mc+1))
  else echo "  FAIL $title"; fi
done < <(gh api "repos/$SRC/milestones?state=all" --jq '.[]|[.title,.state,(.description // ""),(.due_on // "")]|@tsv')
echo "  milestones created: $mc"

# ---------- 2b. JSON archive — mandatory, complete, not best-effort ----------
echo "--- JSON archive ---"
gh issue list -R "$SRC" --state all --limit 1000 \
  --json number,title,body,state,stateReason,labels,milestone,assignees,author,createdAt,closedAt,updatedAt,comments,url \
  > "$ARCHIVE/issues.json" || { echo "FATAL: issues export failed"; exit 1; }
gh pr list -R "$SRC" --state all --limit 1000 \
  --json number,title,body,state,labels,milestone,assignees,author,createdAt,closedAt,updatedAt,mergedAt,comments,url,headRefName,baseRefName,isCrossRepository \
  > "$ARCHIVE/pulls.json" || { echo "FATAL: pulls export failed"; exit 1; }
NI=$(gh api "repos/$SRC/issues?state=all&per_page=1" --jq 'length' >/dev/null 2>&1; grep -o '"number"' "$ARCHIVE/issues.json" | wc -l | tr -d ' ')
NP=$(grep -o '"url"' "$ARCHIVE/pulls.json" | wc -l | tr -d ' ')
echo "  issues.json + pulls.json written"

# ---------- 2b. render for review — NOTHING IS CREATED ----------
echo "--- rendering bodies for review ---"
rm -f "$REND"/*.md 2>/dev/null
render() { # kind  jsonfile
  local kind="$1" f="$2" i n
  n=$(gh api "repos/$SRC" --jq '.id' >/dev/null 2>&1; echo)
  while IFS=$'\t' read -r num title author created state url; do
    [ -z "${num:-}" ] && continue
    {
      printf '> Migrated from `%s`%s — originally opened by %s on %s\n> Original: %s\n\n' \
        "$SRC" "$(printf '#<!---->%s' "$num")" "$(printf '@<!---->%s' "$author")" "$created" "$url"
      if [ "$kind" = "pr" ]; then printf '_Original was a pull request (%s)._\n\n' "$state"; fi
      gh api "repos/$SRC/issues/$num" --jq '.body // ""' 2>/dev/null | esc
      printf '\n'
      gh api "repos/$SRC/issues/$num/comments" --paginate \
        --jq '.[]|"\n---\n\n@<!---->\(.user.login) commented on \(.created_at):\n\n\(.body)"' 2>/dev/null | esc
    } > "$REND/$kind-$num.md"
  done < <(gh "$([ "$kind" = pr ] && echo pr || echo issue)" list -R "$SRC" --state all --limit 1000 \
             --json number,title,author,createdAt,state,url \
             --jq '.[]|[(.number|tostring),.title,.author.login,.createdAt,.state,.url]|@tsv')
}
render issue "$ARCHIVE/issues.json"
render pr    "$ARCHIVE/pulls.json"
RN=$(ls -1 "$REND"/*.md 2>/dev/null | wc -l | tr -d ' ')
echo "  rendered $RN files -> archive/rendered/"

# ---------- the gate ----------
echo
echo "--- escaping audit (must be zero) ---"
BARE_AT=$(grep -rhoE '(^|[^A-Za-z0-9`<!-])@[A-Za-z0-9][A-Za-z0-9-]*' "$REND" 2>/dev/null | grep -v '@<!---->' | wc -l | tr -d ' ')
BARE_HASH=$(grep -rhoE '(^|[^A-Za-z0-9&#<-])#[0-9]+' "$REND" 2>/dev/null | grep -v '#<!---->' | wc -l | tr -d ' ')
echo "  unescaped @mentions: $BARE_AT"
echo "  unescaped #refs    : $BARE_HASH"
[ "$BARE_AT" != "0" ] && { echo; echo "  offending @ lines:"; grep -rnE '(^|[^A-Za-z0-9`<!-])@[A-Za-z0-9][A-Za-z0-9-]*' "$REND" | grep -v '@<!---->' | head -10; }
[ "$BARE_HASH" != "0" ] && { echo; echo "  offending # lines:"; grep -rnE '(^|[^A-Za-z0-9&#<-])#[0-9]+' "$REND" | grep -v '#<!---->' | head -10; }

echo
echo "=============================================================="
echo " HALT — render gate. NO issues were created."
echo " Review archive/rendered/*.md, confirm the audit above is 0/0,"
echo " then run the creation step. Notifications cannot be undone."
echo "=============================================================="
echo "log: $LOG"

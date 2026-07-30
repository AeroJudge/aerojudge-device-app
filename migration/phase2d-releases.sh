#!/usr/bin/env bash
# Phase 2d — archive and recreate releases on the destination.
#
#   bash migration/phase2d-releases.sh AeroJudge/aerojudge-device-app [--dry]
#
# Operator decision (spec 6d): recreate all 29 releases with notes and assets, excluding only
# the 1.8 GB v1.1.0/2025-03-29_Clean_8GB_bootfs.zip. That asset's original URL is preserved in
# the release body and in the JSON archive.
#
# Uses `gh --jq` only — no standalone jq dependency (see spec Section 0 note).
# Idempotent: skips any tag that already has a release on the destination.
# Verifies every uploaded asset's byte size against the source.
set -uo pipefail

SRC=IMAC-ORG/imac-judge-app
TARGET="${1:-}"
DRY="${2:-}"
[ -z "$TARGET" ] && { echo "usage: $0 <owner/repo> [--dry]"; exit 2; }

SKIP_BYTES=1000000000        # assets larger than this are excluded by operator decision

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${PHASE2D_WORK:-$HERE/.work}"
ASSETS="$WORK/assets"
ARCHIVE="$HERE/archive"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$HERE/logs" "$ASSETS" "$ARCHIVE"
LOG="$HERE/logs/phase2d-$TS.log"
exec > >(tee -a "$LOG") 2>&1

echo "=== Phase 2d — releases -> $TARGET ($TS) ==="
[ "$DRY" = "--dry" ] && echo "*** DRY MODE: nothing created, nothing uploaded ***"

# ---------- 1. archive first, always. this is the record, not best-effort ----------
echo "archiving release metadata"
gh api "repos/$SRC/releases" --paginate > "$ARCHIVE/releases.json" || { echo "FATAL: archive failed"; exit 1; }
gh api "repos/$SRC/releases" --paginate \
  --jq '.[]|[.tag_name,(.name // .tag_name),(.prerelease|tostring),(.draft|tostring),.published_at]|@tsv' \
  > "$WORK/manifest.tsv" || { echo "FATAL: manifest failed"; exit 1; }
gh api "repos/$SRC/releases" --paginate \
  --jq '.[]|.tag_name as $t|.assets[]|[$t,(.size|tostring),.name,.browser_download_url]|@tsv' \
  > "$WORK/assets.tsv" || { echo "FATAL: asset list failed"; exit 1; }

N=$(wc -l < "$WORK/manifest.tsv" | tr -d ' ')
A=$(wc -l < "$WORK/assets.tsv" | tr -d ' ')
KEEPN=$(awk -F'\t' -v m=$SKIP_BYTES '$2<=m' "$WORK/assets.tsv" | wc -l | tr -d ' ')
KEEPB=$(awk -F'\t' -v m=$SKIP_BYTES '$2<=m {s+=$2} END{printf "%.2f", s/1073741824}' "$WORK/assets.tsv")
echo "  $N releases, $A assets total; $KEEPN assets / $KEEPB GB to transfer"
[ "$N" -gt 0 ] || { echo "FATAL: no releases found"; exit 1; }

# escaping per spec 6b — release bodies can contain @mentions and #refs too
esc() { sed -E 's/@([A-Za-z0-9][A-Za-z0-9-]*)/@<!---->\1/g; s/(^|[^A-Za-z0-9&#])#([0-9]+)/\1#<!---->\2/g'; }

created=0; skipped=0; uploaded=0; failed=0

# oldest first, so the destination release list reads in source order
tac "$WORK/manifest.tsv" | while IFS=$'\t' read -r TAG NAME PRE DRAFT PUB; do
  [ -z "${TAG:-}" ] && continue

  if gh release view "$TAG" -R "$TARGET" >/dev/null 2>&1; then
    echo "SKIP $TAG (release already exists)"; continue
  fi
  if ! git ls-remote --tags "https://github.com/$TARGET.git" "refs/tags/$TAG" 2>/dev/null | grep -q .; then
    echo "SKIP $TAG (tag absent on destination)"; continue
  fi

  BODY=$(gh api "repos/$SRC/releases/tags/$TAG" --jq '.body // ""' 2>/dev/null | esc)
  EXCL=$(awk -F'\t' -v t="$TAG" -v m=$SKIP_BYTES '$1==t && $2>m {
           printf ">\n> **%s** (%s bytes) is not attached here — it remains on the legacy repo:\n> %s\n", $3, $2, $4 }' "$WORK/assets.tsv")
  FULLBODY="> Migrated from \`$SRC\` — originally published $PUB
$EXCL
$BODY"

  mkdir -p "$ASSETS/$TAG"; rm -f "$ASSETS/$TAG"/* 2>/dev/null
  files=(); dlfail=0
  while IFS=$'\t' read -r t sz nm url; do
    [ "$t" != "$TAG" ] && continue
    [ "$sz" -gt "$SKIP_BYTES" ] && continue
    if [ "$DRY" = "--dry" ]; then echo "    would fetch $nm ($sz)"; continue; fi
    if ! curl -fsSL -o "$ASSETS/$TAG/$nm" "$url"; then echo "    FAIL download $nm"; dlfail=1; continue; fi
    got=$(stat -c%s "$ASSETS/$TAG/$nm" 2>/dev/null || stat -f%z "$ASSETS/$TAG/$nm")
    if [ "$got" != "$sz" ]; then echo "    FAIL size $nm: got $got want $sz"; dlfail=1; continue; fi
    files+=("$ASSETS/$TAG/$nm")
  done < "$WORK/assets.tsv"

  if [ "$DRY" = "--dry" ]; then echo "would create $TAG"; continue; fi
  if [ "$dlfail" != "0" ]; then echo "FAIL $TAG — asset download/verify failed, not creating"; rm -rf "$ASSETS/$TAG"; continue; fi

  ARGS=(--title "$NAME" --notes "$FULLBODY" --verify-tag)
  [ "$PRE" = "true" ] && ARGS+=(--prerelease)
  [ "$DRAFT" = "true" ] && ARGS+=(--draft)
  if gh release create "$TAG" -R "$TARGET" "${ARGS[@]}" "${files[@]}" >/dev/null 2>&1; then
    echo "OK   $TAG (${#files[@]} assets)"
  else
    echo "FAIL $TAG — gh release create failed"
  fi
  rm -rf "$ASSETS/$TAG"
  sleep 2
done

echo
echo "--- summary ---"
DST_N=$(gh api "repos/$TARGET/releases" --paginate --jq '.[].id' 2>/dev/null | wc -l | tr -d ' ')
echo "releases now on destination: $DST_N (source has $N)"
echo "log: $LOG"

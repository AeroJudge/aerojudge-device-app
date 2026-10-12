#!/bin/sh
# =============================================================================
# fetch_update.sh - AeroJudge Update Fetcher
# =============================================================================
# This script is installed on the device and fetches/executes the latest
# update script from GitHub. This allows the main update logic to be
# updated remotely without needing to modify files on individual devices.
#
# Usage:
#   /home/judge/fetch_update.sh                  # Legacy: full update (download + install)
#   /home/judge/fetch_update.sh --download-only  # Phase 1: check + download assets
#   /home/judge/fetch_update.sh --install        # Phase 2: backup, install, health check
#
# Exit Codes (passed through from judge_update.sh):
#   0 = No update needed (already running latest version)
#   1 = Error occurred during update
#   2 = Update successfully applied / assets downloaded and ready
# =============================================================================

RAW_BASE_URL="${AEROJUDGE_RAW_BASE_URL:-https://raw.githubusercontent.com/AeroJudge/aerojudge-device-app/main}"
UPDATE_URL="$RAW_BASE_URL/scripts/judge_update.sh"

# Recovery is installed before provisioning disables normal internet time.
# Its exit 2 is internal: it permits HTTPS, not an installed-update result.
CLOCK_HELPER=/usr/local/sbin/aerojudge-clock
if [ -x "$CLOCK_HELPER" ] && [ -r /sys/class/rtc/rtc0/name ] &&
    grep -Eq '^rtc-rv3028([[:space:]]|$)' /sys/class/rtc/rtc0/name; then
    CLOCK_RESULT=$(sudo -n "$CLOCK_HELPER" sync-internet)
    CLOCK_EXIT=$?
    if ! printf '%s' "$CLOCK_RESULT" | python3 -c '
import json, sys
try:
    result = json.load(sys.stdin)
    code = int(sys.argv[1])
    assert type(result.get("success")) is bool
    assert isinstance(result.get("message"), str) and result["message"].strip()
    print(result["message"], file=sys.stderr)
    assert (code == 0 and result["success"]) or (code == 2 and not result["success"])
except Exception:
    print("Clock recovery did not confirm safe completion; update stopped.", file=sys.stderr)
    sys.exit(1)
' "$CLOCK_EXIT"; then
        exit 1
    fi
fi

# Download the update script to a temp file so we can pass arguments to it.
# The previous `curl | bash` pattern could not forward arguments.
SCRIPT_FILE=$(mktemp /tmp/judge_update_XXXXXX.sh)
curl -sfS "$UPDATE_URL" -o "$SCRIPT_FILE"
CURL_EXIT=$?

if [ $CURL_EXIT -ne 0 ]; then
    echo "Error fetching update script (no internet?)" >&2
    rm -f "$SCRIPT_FILE"
    exit 1
fi

chmod +x "$SCRIPT_FILE"
bash "$SCRIPT_FILE" "$@"
EXIT_CODE=$?

rm -f "$SCRIPT_FILE"
exit $EXIT_CODE

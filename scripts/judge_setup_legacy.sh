#!/bin/sh
# Compatibility entry point for existing devices and base images.
# Keep this old URL available; the implementation lives in device-scripts.
SCRIPTS_RAW_BASE_URL="${AEROJUDGE_SCRIPTS_RAW_BASE_URL:-https://raw.githubusercontent.com/AeroJudge/device-scripts/main}"
while [ "${SCRIPTS_RAW_BASE_URL%/}" != "$SCRIPTS_RAW_BASE_URL" ]; do SCRIPTS_RAW_BASE_URL=${SCRIPTS_RAW_BASE_URL%/}; done
export AEROJUDGE_SCRIPTS_RAW_BASE_URL="$SCRIPTS_RAW_BASE_URL"

# The caller may save this file under any name; never derive the target from $0.
TARGET_URL="$SCRIPTS_RAW_BASE_URL/judge_setup_legacy.sh"
SCRIPT_FILE=$(mktemp /tmp/aerojudge-forward-XXXXXX.sh) || {
    echo "Could not create temporary file for judge_setup_legacy.sh" >&2
    exit 1
}
trap 'rm -f -- "$SCRIPT_FILE" || echo "Could not remove $SCRIPT_FILE" >&2' 0
trap 'exit 130' INT
trap 'exit 143' TERM

if ! curl -fsSL "$TARGET_URL" -o "$SCRIPT_FILE"; then
    echo "Could not download judge_setup_legacy.sh from $TARGET_URL" >&2
    exit 1
fi
if [ ! -s "$SCRIPT_FILE" ] || ! bash -n "$SCRIPT_FILE"; then
    echo "Downloaded judge_setup_legacy.sh is empty or has invalid shell syntax" >&2
    exit 1
fi

# Foreground execution preserves prompts and the existing systemd scope.
# Do not use errexit: updater and identity callers depend on nonzero statuses.
bash "$SCRIPT_FILE" "$@"
EXIT_CODE=$?
exit "$EXIT_CODE"

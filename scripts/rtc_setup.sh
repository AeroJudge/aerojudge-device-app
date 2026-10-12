#!/bin/bash
# Provision PCB 3.60+ RTC policy on a parked device. Future fresh setup calls
# this same script with already-downloaded files, after release installation.
# Keep the printed backup directory until hardware acceptance is complete.
set -Eeuo pipefail

RAW_BASE_URL="${AEROJUDGE_RAW_BASE_URL:-https://raw.githubusercontent.com/AeroJudge/aerojudge-device-app/main}"
HELPER=/usr/local/sbin/aerojudge-clock
SUDO_RULE=/etc/sudoers.d/aerojudge-clock
LAUNCHER=/home/judge/fetch_update.sh
BACKUP=""
BACKUP_READY=false
CHANGED=false
STEP="preflight"

fail() { echo "RTC setup failed at $STEP: $*" >&2; exit 1; }

save_file() {
    local source="$1" name="$2"
    if [ -e "$source" ] || [ -L "$source" ]; then
        cp -a "$source" "$BACKUP/$name"
    else
        touch "$BACKUP/$name.absent"
    fi
}

restore_file() {
    local destination="$1" name="$2"
    if [ -e "$BACKUP/$name.absent" ]; then
        rm -f -- "$destination"
    else
        cp -a --remove-destination "$BACKUP/$name" "$destination"
    fi
}

save_service() {
    local service="$1" enabled active
    enabled=$(systemctl is-enabled "$service" 2>/dev/null) || true
    active=$(systemctl show "$service" --property=ActiveState --value)
    case "$enabled" in enabled|disabled|static|indirect|masked|enabled-runtime|masked-runtime) ;;
        *) fail "Cannot preserve $service enablement: $enabled";; esac
    case "$active" in active|inactive|failed) ;;
        *) fail "$service is in transitional/unknown state: $active";; esac
    printf '%s\n' "$enabled" > "$BACKUP/$service.enabled"
    printf '%s\n' "$active" > "$BACKUP/$service.active"
}

restore_service() {
    local service="$1" enabled active
    enabled=$(cat "$BACKUP/$service.enabled") || return 1
    active=$(cat "$BACKUP/$service.active") || return 1
    systemctl unmask "$service" || return 1
    case "$enabled" in
        enabled) systemctl enable "$service" || return 1;;
        disabled) systemctl disable "$service" || return 1;;
        enabled-runtime) systemctl enable --runtime "$service" || return 1;;
        masked|masked-runtime|static|indirect) :;;
        *) return 1;;
    esac
    if [ "$active" = active ]; then
        systemctl start "$service" || return 1
    else
        systemctl stop "$service" || return 1
    fi
    # A service can be running while masked. Restore activity before reapplying
    # its original mask, otherwise systemctl refuses the required start.
    case "$enabled" in
        masked) systemctl mask "$service" || return 1;;
        masked-runtime) systemctl mask --runtime "$service" || return 1;;
    esac
}

restore_clock_policy() {
    local failed=0
    # Restore time support first, so package recovery can use normal HTTPS.
    timedatectl set-timezone "$(cat "$BACKUP/timezone-name")" || failed=1
    restore_file /etc/localtime localtime || failed=1
    restore_file /etc/timezone timezone || failed=1
    restore_file /boot/config.txt config.txt || failed=1
    restore_service systemd-timesyncd.service || failed=1
    if [ "$(cat "$BACKUP/fake-hwclock.installed")" = yes ]; then
        if ! dpkg-query -W -f='${Status}' fake-hwclock 2>/dev/null | grep -qx 'install ok installed'; then
            apt-get install -y fake-hwclock || failed=1
        fi
        restore_file /etc/fake-hwclock.data fake-hwclock.data || failed=1
        systemctl daemon-reload || failed=1
        restore_service fake-hwclock.service || failed=1
    fi
    restore_file "$LAUNCHER" fetch_update.sh || failed=1
    restore_file "$SUDO_RULE" sudoers || failed=1
    restore_file "$HELPER" helper || failed=1
    return "$failed"
}

on_exit() {
    local exit_code=$?
    trap - EXIT ERR INT TERM
    if [ "$exit_code" -ne 0 ] && "$BACKUP_READY" && "$CHANGED"; then
        echo "Restoring previous clock policy after failure at $STEP." >&2
        if restore_clock_policy; then
            if [ "$(cat "$BACKUP/judge.service.active")" = active ]; then
                systemctl start judge.service || echo "App restart failed; inspect judge.service." >&2
            fi
        else
            echo "Clock-policy restoration is incomplete. App remains stopped; use $BACKUP for recovery." >&2
        fi
    fi
    [ -z "$BACKUP" ] || echo "Retained RTC backup: $BACKUP"
    exit "$exit_code"
}
trap on_exit EXIT
trap 'echo "RTC setup failed at $STEP (line $LINENO)." >&2' ERR
trap 'exit 1' INT TERM

[ "$(id -u)" = 0 ] || fail "Run with sudo on the parked device."
STAGED_SOURCE=""
if [ "$#" -eq 2 ] && [ "$1" = --staged ]; then
    STAGED_SOURCE="$2"
elif [ "$#" -ne 0 ]; then
    fail "Usage: sudo rtc_setup.sh [--staged <directory>]"
fi

[ -f /boot/config.txt ] || fail "/boot/config.txt is missing. Investigate and fix before rerunning."
for tool in python3 systemctl timedatectl visudo flock install dpkg-query apt-get; do
    command -v "$tool" >/dev/null || fail "Required tool is missing: $tool"
done
getent passwd judge >/dev/null || fail "User judge is missing."
[ -x /sbin/hwclock ] || fail "/sbin/hwclock is missing."
[ -e /lib/systemd/system/systemd-timesyncd.service ] || fail "Keep systemd-timesyncd installed for recovery."

# Hold the same nonblocking lock as app/SSH clock writes throughout policy changes.
exec 9>/run/lock/aerojudge-clock.lock
flock -n 9 || fail "Clock operation busy. Retry when it has finished."

STEP="checking the installed app"
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('/var/opt/judge/bin/judge.jar') as jar:
    for name in ('BOOT-INF/classes/com/aerojudge/judge/service/DeviceClockService.class',
                 'BOOT-INF/classes/com/aerojudge/judge/utils/DeviceClockWarningInterceptor.class',
                 'BOOT-INF/classes/templates/device-time-warning.html'):
        if name not in jar.namelist():
            raise SystemExit('Install the RTC-compatible app before provisioning clock policy.')
PY

STEP="staging recovery support"
install -d -o root -g root -m 0700 /var/lib/aerojudge-clock
STAGE=$(mktemp -d /var/lib/aerojudge-clock/staged.XXXXXX)
for name in device_clock.py fetch_update.sh; do
    if [ -n "$STAGED_SOURCE" ]; then
        install -o root -g root -m 0644 "$STAGED_SOURCE/$name" "$STAGE/$name"
    else
        curl --fail --silent --show-error --location "$RAW_BASE_URL/scripts/$name" -o "$STAGE/$name"
        chmod 0644 "$STAGE/$name"
    fi
done
python3 - "$STAGE/device_clock.py" <<'PY'
import ast, pathlib, sys
ast.parse(pathlib.Path(sys.argv[1]).read_text())
PY
/bin/sh -n "$STAGE/fetch_update.sh"
grep -q 'sync-internet' "$STAGE/fetch_update.sh" || fail "Staged launcher lacks clock recovery support."

STEP="validating boot configuration"
# Only replace the known basic overlay, or retain the exact provisioned overlay.
# Missing/conflicting/unexpected configurations belong to the owner to repair.
python3 - /boot/config.txt "$STAGE/config.txt" <<'PY'
import pathlib, sys
source = pathlib.Path(sys.argv[1]).read_bytes()
basic = 'dtoverlay=i2c-rtc,rv3028'
target = basic + ',trickle-resistor-ohms=3000,backup-switchover-mode=1'
section = ''
rtc_lines = []
i2c_on = False
lines = source.decode('utf-8').splitlines(keepends=True)
for index, line in enumerate(lines):
    code = line.split('#', 1)[0].strip()
    if code.startswith('['):
        section = code
    if code == 'dtparam=i2c_arm=on' and section in ('', '[all]', '[pi4]'):
        i2c_on = True
    if code.startswith('dtparam=i2c_arm=') and code != 'dtparam=i2c_arm=on':
        raise SystemExit('Unexpected I2C configuration. Investigate/fix and rerun.')
    if any(token in code for token in ('i2c-rtc', 'rv3028', 'trickle-resistor-ohms', 'backup-switchover-mode')):
        if code not in (basic, target) or section not in ('', '[all]', '[pi4]'):
            raise SystemExit('Unexpected RTC configuration. Investigate/fix and rerun: ' + code)
        rtc_lines.append(index)
if len(rtc_lines) != 1:
    raise SystemExit('Expected exactly one active RV-3028 overlay; found %s. Investigate/fix and rerun.' % len(rtc_lines))
if not i2c_on:
    raise SystemExit('Expected active dtparam=i2c_arm=on. Investigate/fix and rerun.')
index = rtc_lines[0]
code = lines[index].split('#', 1)[0].strip()
if code == basic:
    lines[index] = lines[index].replace(basic, target, 1)
pathlib.Path(sys.argv[2]).write_bytes(''.join(lines).encode('utf-8'))
PY

STEP="backing up original clock policy"
install -d -o root -g root -m 0700 /var/backups/aerojudge-clock
BACKUP=$(mktemp -d /var/backups/aerojudge-clock/before.XXXXXX)
save_file /boot/config.txt config.txt
save_file /etc/timezone timezone
save_file /etc/localtime localtime
save_file "$LAUNCHER" fetch_update.sh
save_file "$HELPER" helper
save_file "$SUDO_RULE" sudoers
timedatectl show --property=Timezone --value > "$BACKUP/timezone-name"
[ -s "$BACKUP/timezone-name" ] || fail "Could not read the current timezone."
save_service systemd-timesyncd.service
save_service judge.service
if dpkg-query -W -f='${Status}' fake-hwclock 2>/dev/null | grep -qx 'install ok installed'; then
    echo yes > "$BACKUP/fake-hwclock.installed"
    save_service fake-hwclock.service
    save_file /etc/fake-hwclock.data fake-hwclock.data
else
    echo no > "$BACKUP/fake-hwclock.installed"
fi
printf '%s\n' "$STAGE" > "$BACKUP/staged-directory"
BACKUP_READY=true
echo "Clock-policy backup: $BACKUP"

STEP="stopping the app"
CHANGED=true
systemctl stop judge.service
# Deactivate an earlier helper during a rerun; it is restored if provisioning fails.
[ ! -e "$HELPER" ] || chmod 0644 "$HELPER"

STEP="installing recovery launcher and permissions"
install -o judge -g judge -m 0755 "$STAGE/fetch_update.sh" "$LAUNCHER"
cat > "$STAGE/sudoers" <<'SUDOERS'
# The helper validates its command/numeric arguments; no shell is invoked.
judge ALL=(root) NOPASSWD: /usr/local/sbin/aerojudge-clock status, /usr/local/sbin/aerojudge-clock sync-score *, /usr/local/sbin/aerojudge-clock sync-internet
SUDOERS
visudo -cf "$STAGE/sudoers"
install -o root -g root -m 0440 "$STAGE/sudoers" "$SUDO_RULE"
visudo -cf /etc/sudoers

STEP="applying RTC boot configuration and UTC"
install -o root -g root -m 0644 "$STAGE/config.txt" /boot/config.txt
timedatectl set-timezone UTC

STEP="removing fake-hwclock"
if [ "$(cat "$BACKUP/fake-hwclock.installed")" = yes ]; then
    systemctl stop fake-hwclock.service
    apt-get remove -y fake-hwclock
fi

STEP="disabling normal internet time"
systemctl unmask systemd-timesyncd.service
systemctl disable --now systemd-timesyncd.service
# Remain installed and unmasked for bounded, explicit update recovery.
state=$(systemctl show systemd-timesyncd.service --property=ActiveState --value)
[ "$state" = inactive ] || [ "$state" = failed ] || fail "timesyncd is not stopped: $state"
[ "$(systemctl show systemd-timesyncd.service --property=MainPID --value)" = 0 ] || fail "timesyncd still has a process."
enabled=$(systemctl is-enabled systemd-timesyncd.service 2>/dev/null) || true
[ "$enabled" = disabled ] || fail "timesyncd is not disabled: $enabled"
[ "$(timedatectl show --property=Timezone --value)" = UTC ] || fail "UTC timezone did not verify."
if dpkg-query -W -f='${Status}' fake-hwclock 2>/dev/null | grep -qx 'install ok installed'; then
    fail "fake-hwclock is still installed."
fi

STEP="activating the helper"
# This executable is the activation marker. Install it only after policy and
# recovery support are in place. No device identity or RTC EEPROM writes occur.
install -o root -g root -m 0755 "$STAGE/device_clock.py" "$HELPER"
[ "$(stat -c '%U:%G:%a' "$HELPER")" = root:root:755 ] || fail "Helper permissions did not verify."
[ "$(stat -c '%U:%G:%a' "$SUDO_RULE")" = root:root:440 ] || fail "Sudo rule permissions did not verify."

STEP="restarting the app"
if [ "$(cat "$BACKUP/judge.service.active")" = active ]; then
    systemctl start judge.service
fi
CHANGED=false
echo "RTC provisioning completed. Reboot to apply the overlay and verify RTC boot restoration."
echo "Keep $BACKUP and $STAGE until bench acceptance. Load/refresh from Score before event use."

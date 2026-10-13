# AeroJudge Device SD Card Setup

These instructions are for current AeroJudge Device hardware:

- serial numbers above `DPG-110`
- PCB revision `3.61`
- Raspberry Pi 4B

The supported installation path uses the prepared AeroJudge base image.

## Install a Device

1. Download the current AeroJudge Device image from:

   <https://aero-judge.com/device-images.php>

2. Use Raspberry Pi Imager to write the downloaded image to the SD card.

3. Insert the card into the AeroJudge Device and boot it.

4. Connect to the device as user `judge`, then run:

   ```sh
   cd /home/judge
   ./build_aerojudge.sh
   ```

The launcher downloads and runs the current production `judge_setup.sh`. The
setup asks for device-specific settings, installs the application and services,
validates the result, and offers to reboot.

## Test an Unreleased Setup Script

The launcher normally downloads setup code from the production `main` branch of
`AeroJudge/aerojudge-device-app`. To test setup code from an unreleased branch
or fork, override both source endpoints before running the launcher:

```sh
cd /home/judge
export AEROJUDGE_RAW_BASE_URL="https://raw.githubusercontent.com/<owner>/<repo>/refs/heads/<branch>"
export AEROJUDGE_RELEASE_API_URL="https://api.github.com/repos/<owner>/<repo>/releases/latest"
./build_aerojudge.sh
```

The launcher exports those values before running `judge_setup.sh`, so the setup
script, update fetcher, and release installer use the same test endpoints.
Changing only the raw branch URL changes scripts, not release assets. If the
branch requires matching binaries or service archives, publish a test release
from the same repository and point `AEROJUDGE_RELEASE_API_URL` at that
repository's releases API. The release endpoint must contain `judge.jar`,
`figures.zip`, and `volume_service.zip` built from compatible source. Test
release tags must use the stable `vYY.MAJOR.MINOR` format, for example
`v26.1.0`.

## What Setup Installs

The setup script:

- verifies Bullseye, 32-bit, and user `judge`
- refuses to run if existing AeroJudge install state is found
- asks for the board identity and the season year up front, writes identity
  to the PCB EEPROM, then runs without input until the reboot prompt
- expands the root filesystem to use the available SD card space
- installs required packages
- installs the AeroJudge boot configuration
- creates `/var/opt/judge/settings.json`
- creates `/var/opt/judge/pilots/scores`
- installs `judge.service`, `kiosk.service`, and `fetch_update.sh`
- installs and enables `audio-hardware.service` and `volume.service`
- installs the AeroJudge desktop image
- hides the desktop Wastebasket
- disables PackageKit desktop update prompts
- fetches and installs release assets
- provisions the RTC clock policy after downloads, using the shared RTC setup
  tool described below, then activates the clock helper
- validates services, release assets, audio assets, runtime directories, and
  the PCB 3.61+ boot configuration
- asks whether to reboot

## Settings

Fresh setup writes settings directly to:

```text
/var/opt/judge/settings.json
```

The old flow used `/boot/settings.json`; do not use that location for current
fresh-device setup.

Setup currently prompts for `seasonYear` even though release tags now encode the
season year as `vYY.MAJOR.MINOR`. A future approved setup/asset change should
decide how to derive it from the release tag and validate it against
figure-package metadata.

## Version Checks

The running application version comes from the packaged `judge.jar` manifest:

```sh
curl -s http://localhost:8080/api/version
```

The installed-release marker remains separate update/install state:

```sh
cat /home/judge/.judge_last_release
```

Use the marker only to inspect what setup/update last installed successfully.
If needed, verify the packaged artifact directly:

```sh
unzip -p /var/opt/judge/bin/judge.jar META-INF/MANIFEST.MF | grep Implementation-Version
```

Default setup values:

- `judge_id`: `1`
- `line_number`: `1`
- `score_host`: `192.168.8.100`
- `score_http_port`: `80`
- `language`: `en`
- `score_poll_timeout`: `2`
- `score_timeout`: `10`

## Post-Reboot Validation

Run:

```sh
systemctl is-active judge.service kiosk.service audio-hardware.service volume.service
systemctl is-enabled judge.service kiosk.service audio-hardware.service volume.service
curl -s http://localhost:8080/actuator/health
amixer -c sndrpihifiberry get Digital
raspi-gpio get 8
pactl info | grep -E 'Server Name|Default Sink'
journalctl -u audio-hardware.service -u volume.service -n 80 --no-pager
ls -ld /var/opt/judge/pilots /var/opt/judge/pilots/scores
cat /var/opt/judge/settings.json
```

Then validate on assembled hardware:

- kiosk opens AeroJudge at `newcomp.html`
- another machine can load `http://<pi-ip>:8080`
- Score connectivity and test sync work
- GPIO scoring buttons perform the expected actions
- volume thumbwheel changes volume
- audio is audible through the device amplifier
- shutdown/poweroff behavior works

## Board Identity

The PCB stores its identity in the RV-3028 user EEPROM. It survives SD
reimage, device reset, Pi replacement, and power loss. `device_identity.sh` is
the only tool that writes it. It changes nothing else on the device and does
not need a reboot or service restart. Fresh setup runs this same tool before
installing anything; a board with a valid record is shown and not asked for.

Obtain and run over SSH as `judge`:

```sh
cd /home/judge
curl -fsSL https://raw.githubusercontent.com/AeroJudge/aerojudge-device-app/main/scripts/device_identity.sh -o device_identity.sh
chmod +x device_identity.sh
./device_identity.sh read
sudo ./device_identity.sh write
```

`write` shows the current EEPROM contents, asks for build mark, board revision,
and serial, shows the record, asks for confirmation, writes, and reads back.
Success prints `Identity written and verified: AJ1;<mark>;<rev>;<serial>`.
If a valid identity is already present, `write` shows it and exits unchanged.

Inputs:

- Build mark: month letter plus two-digit year, for example `K26` for October
  2026. Letters A-M skip I. The prompt lists them.
- Board revision: the digits after `Board Rev:` on the PCB, for example `3.61`.
- Serial: the device serial, for example `DPG-110`. Enter `NO-SN` for a board
  not fitted to a device.

Recovery from a wrong or interrupted write: run `read` to see what is stored,
then correct it deliberately with:

```sh
sudo ./device_identity.sh write --override
```

Override is not part of the normal build workflow.

REMOVE the script after use as it does not need to stay on the device:

```sh
rm device_identity.sh
```

## RTC And Shared Event Time (PCB 3.60 And Later)

The RV-3028 retains UTC event time while the device is off. Provisioned devices
use Score's current `pilots.dat` HTTP Date header on event load and explicit
refresh to set both system time and RTC. Ordinary navigation, round actions,
uploads, status checks, and reconnection do not synchronize the clock. Judging
and an already-loaded event remain usable offline.

At app startup, a confirmed RTC invalid-time flag opens **Device time lost -
resync to Score**. **Resync Clock** uses only the header from a new pilot GET;
it does not replace pilot/sequence files or rebuild caches. **Dismiss** continues
without contacting Score and suppresses the warning for that application run.
Read errors are logged and do not become time-loss warnings.

### Existing-Device Provisioning

Use a parked device. Install the compatible application before provisioning;
the script checks for its clock service, interceptor, and warning template.
For a published compatible release, obtain the standalone tool over SSH:

```sh
cd /home/judge
curl -fsSL https://raw.githubusercontent.com/AeroJudge/aerojudge-device-app/main/scripts/rtc_setup.sh -o rtc_setup.sh
sudo bash ./rtc_setup.sh
```

For an unreleased bench build, transfer the matching `judge.jar`,
`rtc_setup.sh`, `device_clock.py`, and `fetch_update.sh` from the same source
revision. Back up and install that JAR with the app stopped, then restart it.
Place the three scripts together in a staging directory and run:

```sh
sudo bash /home/judge/rtc-bench/rtc_setup.sh --staged /home/judge/rtc-bench
```

The ordinary standalone invocation downloads the helper and revised update
launcher **before** disabling internet time. `--staged` uses local files and
does not fetch them. Fresh setup stages the same files with its runtime files
and invokes the same provisioning after release installation, before enabling
services. Its existing PCB 3.61+ restriction and reboot prompt are unchanged.

Provisioning:

1. Checks the installed app, stages recovery support, and validates the current
   boot config. A missing, duplicate, conflicting, or unexpected RTC overlay
   alerts and stops for the owner to investigate/fix and rerun.
2. Creates a root-only backup directory under `/var/backups/aerojudge-clock/`
   and reports its exact path. It records boot config, timezone, update launcher,
   prior helper/sudo rule, fake-hwclock package/data, and service states.
3. Stops the app and installs the revised launcher and a `visudo`-validated
   rule for the root-owned helper. It replaces only the known basic RTC overlay
   with the following, or retains this exact line on a rerun:

   ```ini
   dtoverlay=i2c-rtc,rv3028,trickle-resistor-ohms=3000,backup-switchover-mode=3
   ```

4. Sets the OS timezone to UTC, removes fake-hwclock, and disables/stops
   systemd-timesyncd. Timesyncd remains installed and unmasked for explicit
   recovery. No custom RTC boot service is added.
5. Installs `/usr/local/sbin/aerojudge-clock` as root:root, mode 0755, **last**.
   The app activates clock support only with this executable and the RV-3028
   driver at rtc0. The app is restarted if it was running before provisioning.

The tool keeps `dtparam=i2c_arm=on`, board-specific GPIO lines, and EEPROM
identity untouched. Never copy the 3.61 boot config over a 3.60 device. Reruns
create a new backup and apply the same targeted settings. Preserve the first
pre-provisioning backup for software rollback. The former mode-1 line is no
longer accepted; it stops for investigation and manual correction, just like
other unexpected configurations. Do not reuse the original mode-1 bench ZIP.

Mode 3 selects level switching (LSM). The manufacturer discourages direct
switching (DSM, mode 1) with close supplies such as 3.3 V main and at least
3.0 V backup. This board measured 3.28 V main and 3.18-3.19 V backup at idle.
See the [RV-3028 application manual, sections 4.2.2 and 4.2.3](https://www.microcrystal.com/fileadmin/Media/Products/RTC/App.Manual/RV-3028-C7_App-Manual.pdf).

**RTC configuration persists in EEPROM.** On boot, the kernel driver applies
the overlay properties and saves changed configuration to the chip. Removing
those properties or restoring the basic overlay does not reset the saved
settings. Replacing the SD card or removing all power does not erase them
either. Configuration EEPROM is separate from the user EEPROM holding device
identity; provisioning must preserve identity and factory calibration.

Reboot after provisioning to apply the overlay, then verify:

```sh
sudo reboot
# Reconnect over SSH after reboot:
cat /sys/class/rtc/rtc0/name
cat /sys/class/rtc/rtc0/hctosys
timedatectl show --property=Timezone
systemctl show systemd-timesyncd.service --property=ActiveState --property=MainPID
systemctl is-enabled systemd-timesyncd.service
sudo -u judge sudo -n /usr/local/sbin/aerojudge-clock status
date --utc --iso-8601=seconds
sudo hwclock --rtc=/dev/rtc0 --show --utc
curl -fsS http://localhost:8080/actuator/health
```

Expected: driver `rtc-rv3028`, hctosys `1`, timezone UTC, timesyncd stopped with
MainPID `0` and disabled (`is-enabled` returns nonzero for disabled), helper
status containing `success: true` and `state: valid` or `invalid`. An invalid
state requires Score resync or dismissal of the startup warning. Load/refresh
from Score before event use, verify both clock readbacks, and inspect UTC app
log/display times. Keep backups until overnight retention, startup warning,
offline restart, update recovery, and two-device agreement have passed bench
acceptance. Workstation tests cannot establish those hardware results.

### Explicit Updates And Time Recovery

The app update check and installed `fetch_update.sh` use the helper before
HTTPS. Recovery stops timesyncd, requires the old synchronization marker to be
absent, then starts it and waits up to 30 seconds for a fresh marker. It stops
the service before returning. A fresh synchronization writes/verifies RTC;
without one, RTC is not overwritten from unvalidated system time.

For manual recovery on a provisioned, parked device:

```sh
sudo /usr/local/sbin/aerojudge-clock sync-internet
clock_result=$?
printf 'Clock helper exit: %s\n' "$clock_result"
```

- Exit `0`: clocks verified and timesyncd stopped.
- Exit `2`: recovery incomplete, but timesyncd confirmed stopped; ordinary
  certificate-validated HTTPS may proceed using the available system time.
- Exit `1`, missing JSON, or malformed JSON: do not proceed on an assumption
  that cleanup succeeded. Resolve the reported failure first. A busy operation
  also returns `1`; retry after the current operation finishes.

The helper's exit `2` is internal and **does not mean an update was installed**.
The launcher retains its existing exit meanings. Neither NTP failure nor a
network change bypasses HTTPS certificate validation. An actual HTTPS failure
uses the existing update error flow. After internet recovery, load/refresh from
Score before subsequent event use to restore the shared event time base.

If time recovery is needed before `device_reset.sh`, run the helper and inspect
its JSON/exit result **before any destructive reset steps or HTTPS fetches**.
Apply the same `0`/`2` stopped-service policy above; stop on `1` or an invalid
result. The reset script itself is unchanged and does not perform this initial
recovery for its own first HTTPS fetch.

### Provisioning Failure And Software Rollback

On provisioning failure after changes begin, the script reports the failed
step, retains its staged recovery files/backups, and attempts to restore the
previous clock policy before restarting the previously running app. This can
require reinstalling fake-hwclock. If restoration fails, the app remains
stopped and the retained backup path is reported for manual recovery. Do not
discard these files or rerun blindly over an unresolved restoration failure.

A JAR rollback alone does not undo RTC provisioning. For a software rollback on a
parked device, use the original pre-provisioning backup, stop the app, and:

1. Restore `config.txt` to `/boot/config.txt`, `localtime` to `/etc/localtime`,
   `timezone` to `/etc/timezone`, and `fetch_update.sh` to
   `/home/judge/fetch_update.sh`. Preserve file ownership/modes and the
   `/etc/localtime` symlink (`cp -a --remove-destination`). A `.absent` marker
   means that file did not exist before the run; restore that absence.
2. Set the timezone recorded in `timezone-name` using
   `sudo timedatectl set-timezone <recorded-timezone>`. Restore timesyncd's
   recorded `.enabled` and `.active` states. Use `systemctl enable`, `disable`,
   or `mask` as recorded (`--runtime` for runtime states), and `start` or `stop`
   for its prior active/inactive state. Unmask before starting if necessary;
   if a previously running service was also masked, reapply its mask afterward.
3. If `fake-hwclock.installed` says `yes`, reinstall it with
   `sudo apt-get install -y fake-hwclock`, restore its saved data to
   `/etc/fake-hwclock.data`, run `sudo systemctl daemon-reload`, and restore its
   saved service enablement/activity. Restore time support before this package
   operation so normal HTTPS remains usable.
4. Remove `/usr/local/sbin/aerojudge-clock` and
   `/etc/sudoers.d/aerojudge-clock` to restore the original
   unprovisioned software state; validate sudo configuration with `sudo visudo -cf
   /etc/sudoers`. When undoing only a rerun, restore the saved prior helper and
   sudo rule instead of removing them.
5. Restore the separately backed-up application JAR if required, restore the
   original app service state, and reboot. Verify kernel RTC restoration,
   timezone, services, and the app before returning the device to use.

These backups do not capture or restore RTC configuration EEPROM. A software
rollback therefore is not a complete hardware rollback. Restoring an older
overlay that explicitly specifies a mode can write that mode again on boot;
restoring the basic overlay leaves the saved chip settings in place. Any
hardware configuration reversal needs a separately reviewed procedure that
preserves calibration, FEDE, and identity. If RTC or identity access disappears,
stop and retain logs/backups for investigation; do not rerun provisioning or
assume a config/JAR rollback repaired the chip state.

## Known Follow-Ups

- `attempt_auto_sync_scores` is currently re-added to `settings.json` by the
  app after startup. That is an app storage bug, not a setup-script setting.
- `seasonYear` should become release-derived once figure-package metadata and a
  separately approved setup/asset validation flow are in place.

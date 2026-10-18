# AeroJudge Device SD Card Setup

Maintained setup implementation: [device-scripts](https://github.com/AeroJudge/device-scripts).
This directory retains compatibility entry points and app-owned runtime files.

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

## Sources and testing

| Setting | Default | Supplies |
|---|---|---|
| `AEROJUDGE_SCRIPTS_RAW_BASE_URL` | `https://raw.githubusercontent.com/AeroJudge/device-scripts/main` | Setup/update/identity scripts, config profiles, wallpaper |
| `AEROJUDGE_RAW_BASE_URL` | `https://raw.githubusercontent.com/AeroJudge/aerojudge-device-app/main` | App and kiosk units/launchers, settings guide, legacy data |
| `AEROJUDGE_RELEASE_API_URL` | `https://api.github.com/repos/AeroJudge/aerojudge-device-app/releases/latest` | Existing app release assets |

Unset or empty settings use their defaults. Each override controls only its own
source. Raw URLs identify repository/ref roots; do not append `/scripts`.
Overrides apply to the current process and its children, not future device sessions.
Selecting an app branch alone does not select migrated scripts.

To test a scripts branch on a disposable fresh device, explicitly fetch its launcher:

```sh
cd /home/judge
export AEROJUDGE_SCRIPTS_RAW_BASE_URL="https://raw.githubusercontent.com/<owner>/device-scripts/refs/heads/<branch>"
curl -fsSL "$AEROJUDGE_SCRIPTS_RAW_BASE_URL/build_aerojudge.sh" -o build_aerojudge.sh
chmod +x build_aerojudge.sh
./build_aerojudge.sh
```

For an app-source test, additionally set this before invoking the launcher:

```sh
export AEROJUDGE_RAW_BASE_URL="https://raw.githubusercontent.com/<owner>/aerojudge-device-app/refs/heads/<branch>"
```

For matching release assets, independently set:

```sh
export AEROJUDGE_RELEASE_API_URL="https://api.github.com/repos/<owner>/<repo>/releases/latest"
```

The release still supplies `judge.jar`, `figures.zip` and `volume_service.zip`.
Release tags retain `vYY.MAJOR.MINOR`, for example `v26.1.0`. Separate figure/volume
package consumption and hardware-aware selection are later work. Scripts currently
use `main`; no new version-selection mechanism is introduced here.

## Repository ownership and compatibility

Executable setup/maintenance scripts live at the repository root. Wallpaper lives
in `assets/`. Current setup uses `config/profiles/pcb-3.61/config.txt` with unchanged
contents. The stored 3.60 profile and `config/profiles/archive/pcb-3.50/config.txt`
do not enable automatic board selection or expand supported hardware.

The app repository retains judge/kiosk runtime files, restart helper, settings guide
and legacy data. Existing raw URLs must remain usable by base images and deployed
fetchers: old executable entry points will forward to this repository, while old
config/image/unit/data URLs retain actual files. Do not retire those links without
reviewing remaining device/image use (planned compatibility period: 12-18 months).

After successful modern setup validation, `/home/judge/build_aerojudge.sh` and
`/home/judge/judge_setup.sh` are removed before the reboot choice. The setup log
remains. Failed installation/validation leaves entry scripts available; use the
existing reimage recovery procedure rather than bypassing fresh-install guards.

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
- validates services, release assets, audio assets, runtime directories, and
  the PCB 3.61+ boot configuration
- removes the two one-time setup entry scripts after validation, retaining the log
- asks whether to reboot

## Settings

Fresh setup writes settings directly to:

```text
/var/opt/judge/settings.json
```

The old flow used `/boot/settings.json`; do not use that location for current
fresh-device setup.

Setup asks the builder for `seasonYear` and writes that value to
`/var/opt/judge/settings.json`. The app release version does not select the
figure season. Future figure-package selection and validation are separate work
and are not part of this scripts migration.

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
not need a reboot or service restart.

Obtain and run over SSH as `judge`:

```sh
cd /home/judge
curl -fsSL https://raw.githubusercontent.com/AeroJudge/device-scripts/main/device_identity.sh -o device_identity.sh
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

## Known Follow-Ups

- `attempt_auto_sync_scores` is currently re-added to `settings.json` by the
  app after startup. That is an app storage bug, not a setup-script setting.
- Figure-package selection and season validation require separate design; do not
  derive the figure season from the app release version.

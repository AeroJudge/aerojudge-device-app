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
not need a reboot or service restart.

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

## Known Follow-Ups

- `attempt_auto_sync_scores` is currently re-added to `settings.json` by the
  app after startup. That is an app storage bug, not a setup-script setting.
- `seasonYear` should become release-derived once figure-package metadata and a
  separately approved setup/asset validation flow are in place.

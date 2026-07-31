# Current Device Fresh Setup — Spec Summary

**Completion Date** • July 2026  
**Branch/Commit** • Merged to main as part of fresh device setup / PCB 3.61+ work  
**Status** • ✅ Delivered (design/reference history)

---

## Overview

A complete rewrite of the SD card setup flow for current production AeroJudge Device hardware (DPG-110 and later with PCB v3.61). Uses a prepared AeroJudge base image with a cleaner, more reliable setup script to configure fresh Bullseye 32-bit installs for headless device initialization.

---

## The Problem

The legacy setup script (`judge_setup.sh`) was a hybrid that mixed old IMAC-derived hardware detection with current device behavior. It:

- Prompted for legacy GPIO mappings and hardware revisions that no longer applied
- Silenced command output, making debugging difficult
- Required manual intervention for WiFi and SSH setup on headless devices (devices with no keyboard)
- Created settings in `/boot/settings.json` and symlinked them, instead of putting them directly in `/var/opt/judge/`
- Lacked clear confirmation steps and early failure detection
- Did not have repeatable first-boot access for no-keyboard devices

Real deployment risk: devices with no keyboard couldn't complete first-boot provisioning without either keyboard access or manual SSH intervention over network — making large-scale provisioning difficult.

---

## The Solution

**Fresh-install-only script:** Rewrote `judge_setup.sh` to target current AeroJudge devices only. No legacy prompts, no GPIO mapping choices, no custom hardware detection. Script refuses to run on non-fresh installs (checks for existing `/var/opt/judge`).

**Early verification:** Script checks at startup that the user is `judge`, the OS is Bullseye 32-bit, no existing AeroJudge install exists, and network is available. Fails early with clear messages if any check fails.

**Prepared base image:** Implementation uses a prepared AeroJudge base image that includes `judge` user, SSH enabled, and WiFi pre-configured. Devices download and run `judge_setup.sh` from the image via a launcher script (`build_aerojudge.sh`), making headless device provisioning repeatable without keyboard access.

**Settings strategy:** Changed from `/boot/settings.json` (symlinked) to direct `/var/opt/judge/settings.json` creation. Script prompts only for `seasonYear` (with validation), sets other fields to production defaults, and shows full settings summary before writing.

**Boot config artifact:** Established a production boot configuration in `scripts/config.txt` that contains all needed settings for current hardware (display, audio, I2C, GPIO keypad, volume thumbwheel, shutdown, poweroff). Setup installs it exactly without appending fragments or prompts, ensuring consistent configuration across devices.

**Explicit two-phase update:** Uses the new split-phase update flow (`--download-only` then `--install`) instead of a single all-in-one fetch, reducing risk of corruption.

**Verbose logging:** Added progress steps, visible output, line-number error reporting, and a setup log file (`aerojudge-setup.log`) for troubleshooting instead of silencing everything.

---

## What Changed

- **`scripts/judge_setup.sh`** → Complete rewrite for current-device fresh installs only; removed all legacy prompts and hardware detection; added Phase 0 provisioning integration, early validation checks, verbose logging, file backups.

- **`scripts/config.txt`** → New file containing production boot configuration for current hardware (display, audio, GPIO, volume controls, shutdown/poweroff).

- **`scripts/settings_readme.md`** → New documentation explaining the settings location change (`/boot/` to `/var/opt/judge/`).

- **`scripts/README.md`** → Updated to document current Bullseye setup process and launcher flow.

- **Services & packages** → Verified `judge.service`, `kiosk.service`, `volume.service` with correct dependencies (`python3-evdev`, `alsa-utils`, etc.); desktop/kiosk behavior refined.

---

## Technical Reference

**Primary Files Modified/Created:**
- `scripts/judge_setup.sh` (rewritten)
- `scripts/config.txt` (new)
- `scripts/settings_readme.md` (new)
- `scripts/README.md` (updated)

**Key Implementation Details:**
- Fresh-install guardrails: refuses to run if `/var/opt/judge/` exists; treats this as fresh-install-only.
- User/OS verification: checks `id`, `/etc/os-release` VERSION_CODENAME, and architecture (32-bit) before proceeding.
- Boot config copied exactly without transformation; no dynamic GPIO/display fragment appending.
- Settings file created directly in `/var/opt/judge/settings.json`, not `/boot/settings.json`.
- Two-phase update: `--download-only` first (to `/tmp/judge-update/`), then `--install` via systemd scope (survives service restart).
- Dependency validation: confirms `python3-evdev`, `alsa-utils`, and mixer controls for volume service.
- Wallpaper/desktop setup via PCManFM; Wastebasket hidden; PackageKit disabled to prevent desktop update prompts.
- File backups: `/boot/config.txt` backed up to `/boot/config.txt.before-aerojudge` before replacement.

**Testing Approach:**
- Bench validation on target hardware: Raspberry Pi 4B with prepared AeroJudge base image.
- Full service startup: `judge.service`, `kiosk.service`, `volume.service` confirmed active and enabled.
- Volume service validation: confirmed PCM mixer control accessible, evdev devices monitored, audio working after reboot.
- GPIO and thumbwheel input behavior confirmed.
- Network and Score sync tested.
- Fresh device correctly opens `/newcomp` (no competition data).

---

## Rollback

Setup is fresh-install-only; rollback is simply reimaging the SD card and rerunning setup. Boot config backup (`config.txt.before-aerojudge`) is preserved but not automatically restored.

Device recovery: inspect logs (`/home/judge/aerojudge-setup.log`, `/tmp/judge-update.log`) for failure details.

---

## Related Work

- Depends on existing `judge.service` and `kiosk.service` behavior (unchanged).
- Coordinates with `scripts/judge_update.sh` (two-phase update flow).
- Future work: SD card auto-expansion during system update; per-class KNOWN sequence counts; pilot carousel anomaly highlighting.
- Legacy preserved: `scripts/judge_setup_legacy.sh` and `scripts/README_legacy.md` remain as reference for old hardware.

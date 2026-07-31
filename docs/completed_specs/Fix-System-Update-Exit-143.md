# Fix System Update Exit Code 143 — Spec Summary

**Completion Date** • April 2026  
**Branch/Commit** • PR #116 (aa4e980) and PR #117 (807d079)  
**Status** • ✅ Delivered

---

## Overview

The system update flow was failing when a scorekeeper or device admin triggered updates from the Device Settings page. The solution splits the update into two independent phases so the installation can proceed even after the Spring Boot service restarts.

---

## The Problem

When a scorekeeper or device admin initiates a system update from Admin → Device Settings, the device downloads new code and then stops the judge service to install it. However, the download-and-install script runs *inside* the Spring Boot process. When the script stops that service, it kills itself with exit code 143 (SIGTERM), and the update fails.

This happened because the Java code waits for the entire script to finish before returning a result to the admin. But the script stops the service (and therefore the Java process itself) before it completes—a deadlock that always results in failure.

**Impact:** Devices could not auto-update, requiring manual SSH and file replacement to get new releases.

---

## The Solution

Split the update into two separate, independent phases:

**Phase 1 (Synchronous — Java waits):**
- Check GitHub for a newer version
- Download assets to a temporary staging directory (`/tmp/judge-update/`)
- Return success/failure to the user
- If successful, proceed to Phase 2

**Phase 2 (Asynchronous — Java doesn't wait):**
- Launch via `systemd-run` in its own isolated scope
- Backup the current JAR file
- Stop the services
- Install the downloaded assets
- Start the services
- Health-check the new version (30 attempts over 150 seconds)
- If healthy: mark the update as complete
- If not healthy: automatically restore the backup and restart the old version

The key insight: Phase 2 runs in a `systemd` scope that *survives* the judge.service being stopped. The installation continues independently, even after Java shuts down.

---

## What Changed

- **Java API (APIController.java)** → Split `runSystemUpdate()` into two methods: `runDownloadPhase()` (synchronous) and `launchInstallPhase()` (async via systemd-run). Version marker (`.judge_last_release`) is now written only after the new version passes health checks, not before download.

- **Fetch Script (fetch_update.sh)** → Changed from `curl | bash` (which cannot pass arguments) to download-then-execute pattern so `--download-only` and `--install` flags can be forwarded to the main update script.

- **Update Script (judge_update.sh)** → Completely redesigned with two separate functions (`do_download()` and `do_install()`). Added backup creation before install, health-check logic with automatic rollback on failure, and staged version markers. Legacy mode (no flags) still supported for backwards compatibility.

---

## Technical Reference

**Primary Files Modified:**
- `judge/src/main/java/com/aerojudge/judge/controller/APIController.java` (two new methods)
- `scripts/fetch_update.sh`
- `scripts/judge_update.sh`

**Key Implementation Details:**
- Version marker written *only after* successful health check, not before download. This prevents devices from getting stuck thinking they're already updated when a download fails partway through.
- Backup (`judge.jar.bak`) created before any install modifications, allowing manual recovery if needed.
- Health checks use `/actuator/health` endpoint with 30 retries × 5 seconds = 150 second window for Spring Boot to become ready.
- Automatic rollback: if health check fails, the old JAR is restored and version marker is not updated, so the next update cycle will retry.
- `systemd-run --unit=judge-update --scope` isolates the install process so stopping `judge.service` doesn't kill it.

**Testing Approach:**
- Simulated update available, then update with no changes available (already on latest)
- Verified health check rollback by staging an invalid JAR and confirming automatic restoration
- Tested legacy mode (old `fetch_update.sh` without phase flags) for backwards compatibility
- Network failure scenarios (download interrupted mid-way)

---

## Rollback

**Code rollback:** Revert `APIController.java` to single-phase `executeUpdateScript()`, restore `fetch_update.sh` to `curl | bash` pattern, and restore `judge_update.sh` to original single-pass version.

**Device recovery (if an update fails on hardware):** SSH into the device and manually restore the backup:
```bash
sudo systemctl stop judge.service
cp /var/opt/judge/bin/judge.jar.bak /var/opt/judge/bin/judge.jar
sudo systemctl start judge.service
```

The automatic health-check rollback is designed to make manual intervention unnecessary in most cases.

---

## Related Work

- Fixes the blocking issue that prevented remote device updates
- Depends on existing `management.endpoints.web.exposure.include=health,info` setting in `application.properties`
- Future work: device validation on hardware must confirm `systemd-run` sudoers access works without password prompt

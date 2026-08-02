# Floating Sync Action Feedback - Spec Summary

**Completion Date** - July 24, 2026  
**Branch/Commit** - main, `7ee12b6` (`Add judge sync FAB busy feedback`), released as `v26.1.2`  
**Status** - Delivered, device-tested, and update-verified

---

## Overview

Judge-facing sync actions now provide immediate visual feedback while slow requests are running. This helps field operators know their tap registered and reduces accidental repeat taps during live contest use.

---

## The Problem

On the main judge pages, pilot/sequence sync and score publish can take long enough that the floating action menu appears unchanged after a tap. Judges may interpret that pause as a missed tap and press the sync action again, creating extra requests and making the device feel less responsive at the flight line.

---

## The Solution

The floating action menu now closes immediately when a sync action starts. The main floating action button stays in place but changes to a small busy indicator and temporarily ignores taps until the existing success/error result path is reached. Score mismatch handling remains operator-driven: if mismatches are detected, the busy indicator clears before the warning modal opens, then starts again if the operator chooses to sync anyway.

---

## What Changed

- **Shared JavaScript** - Added a small reusable helper for floating action button busy feedback and failsafe restoration.
- **Judge pilot pages** - Added busy feedback to pilot/sequence sync and score publish flows.
- **Round setup page** - Added the same feedback behavior to the sync actions available from round selection.
- **Toast consistency** - Preserved existing success/error messages while standardizing the affected sync toasts to the existing `toastMessage` styling.

---

## Technical Reference

**Primary Files Modified:**
- `judge/src/main/resources/static/fab-sync-feedback.js`
- `judge/src/main/resources/templates/pilot-list-round.html`
- `judge/src/main/resources/templates/pilot-list-global.html`
- `judge/src/main/resources/templates/newround.html`

**Key Implementation Details:**
- The helper exposes `window.AeroJudgeFabSyncFeedback` with idempotent `start()` and `finish()` functions.
- The helper locates the page's existing Materialize floating action button at runtime, so it works with both explicit FAB initialization and `M.AutoInit()`.
- A 10-second failsafe restores the normal button UI if an async request path stalls; it does not cancel requests or create additional result messages.
- Score mismatch preflight keeps busy feedback active while checking, clears it while waiting for operator choice, and restarts it for "Sync Anyway."

**Testing Approach:**

Verified the affected pages on device after update. Checks included immediate menu collapse, busy indicator display, pilot sync redirect preservation, score publish success/error restoration, mismatch modal timing, "Sync Anyway" busy feedback, toast preservation, and usability on the 800x480 device target.

---

## Rollback

Rollback is limited to removing the shared helper script and reverting the three template integrations. No backend APIs, persisted data, Score upload contract, or device setup behavior were changed.

---

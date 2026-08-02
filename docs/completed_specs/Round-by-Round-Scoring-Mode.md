# Round-by-Round Scoring Mode - Spec Summary

**Completion Date** - May 2026  
**Branch/Commit** - `a9c2bec` through `0007172` (`Track active sequence per round type` through `Add RBR auto score sync setting`)  
**Status** - Delivered

---

## Overview

Round-by-Round (RBR) scoring mode was rebuilt into a true per-round judging workflow for AeroJudge device boxes. The project makes RBR safer for live contests by locking the active class/type/round, showing judges only the pilots who still need that round, and closing the round automatically when the work queue is complete.

---

## The Problem

RBR mode had a useful data model but behaved too much like Global scoring. Judges could still see broad pilot lists, choose the wrong round type, or navigate into states that did not match the active round. A partial Known double could also leave a pilot's sequence state stuck globally, causing Unknown or Freestyle rounds to fail to advance and, in the worst case, silently lose score rows during sync/import.

Round transitions were also too manual. A judge had to understand when a round was complete and navigate through the right screens to start the next one. That is fragile on small field devices during active contests, especially after power loss, battery swaps, or skipped/no-fly pilots.

---

## The Solution

RBR now behaves as a local active-round state machine. A device is either selecting the next round or judging exactly one locked active round. Starting a round uses a large-button, Pi-friendly flow with a confirmation step; the active round is persisted immediately so reboot or power loss returns to the same context.

The active pilot carousel became a work queue: scored pilots disappear, inactive pilots do not block completion, and behind/drift states stop judging until admin repair. Score submission is guarded on the server so RBR cannot rely on UI-only prevention. When the active cohort is complete, the round auto-closes, records close metadata, shows a round-complete splash, and returns to next-round selection.

The underlying per-pilot state was also fixed so active sequence tracking is isolated per round type. Known, Unknown, and Freestyle now maintain independent sequence progress, matching the existing per-type active round model.

Finally, Device Settings gained an optional RBR auto-sync setting. When enabled, the round-complete page attempts one score sync after mismatch preflight; if mismatches are detected or Score is unavailable, the normal manual push flow remains available.

---

## What Changed

- **RBR state model** - Added durable active-round metadata, immediate persistence on start/close/clear, event-boundary cleanup, and per-type active sequence tracking.
- **Round selection UI** - Replaced the old start path with touch-friendly Precision/Freestyle, class/type, and confirmation steps.
- **Judge workflow** - Filtered the RBR carousel to the active cohort, removed already-scored pilots, simplified the judge modal to one active-type start action, and guarded score submission server-side.
- **Round completion** - Added automatic close, `/round-complete` success screen, optional audio prompt, and return to next-round selection.
- **Admin and safety paths** - Added clear support for unflown mistaken active rounds, blocked unsafe mode changes, reused score-fix tools for recovery, and removed production access to old Flightline/Defined Rounds switching mechanics.
- **Auto-sync option** - Added an RBR-only setting that attempts score sync once at round close after checking for score mismatches.

---

## Technical Reference

**Primary Files Modified:**
- `judge/src/main/java/com/aerojudge/judge/dto/PilotScores.java`
- `judge/src/main/java/com/aerojudge/judge/dto/RoundDTO.java`
- `judge/src/main/java/com/aerojudge/judge/dto/RoundsDTO.java`
- `judge/src/main/java/com/aerojudge/judge/service/RoundsService.java`
- `judge/src/main/java/com/aerojudge/judge/service/PilotService.java`
- `judge/src/main/java/com/aerojudge/judge/controller/RootController.java`
- `judge/src/main/java/com/aerojudge/judge/controller/APIController.java`
- `judge/src/main/resources/templates/newround.html`
- `judge/src/main/resources/templates/pilot-list-round.html`
- `judge/src/main/resources/templates/judge.html`
- `judge/src/main/resources/templates/judge_start_select.html`
- `judge/src/main/resources/templates/round-complete.html`
- `judge/src/main/resources/templates/adminDevice.html`

**Key Implementation Details:**
- `RoundsDTO.scoringRoundNum` is the active local `RoundDTO.round_id` pointer, not the scored pilot round number.
- RBR round number comes from aligned active pilot counters for the selected class/type cohort, not from historical round records.
- `PilotScores.activeSequenceByType` mirrors `activeRoundByType`; each round type advances independently.
- Skipped/no-fly pilots must be advanced with valid zero-score/admin-fix records; omitted pilots do not clear an RBR round.
- Auto-sync is optional, defaults Off, and skips publishing when mismatch preflight detects uneven score data.

**Testing Approach:**

Verification included Maven test passes, compile-time safety from removing no-arg active sequence accessors, bench reproduction of the partial Known double bug, RBR route/navigation checks, active-cohort carousel behavior, wrong-type/wrong-round rejection, reboot/power-loss persistence reasoning, new-event cleanup checks, round-complete redirect/audio behavior, Global scoring smoke checks, and the auto-sync setting flow with mismatch skip behavior.

---

## Rollback

If RBR behavior needs rollback, preserve Global scoring first and disable or hide incomplete RBR entry points. The per-type sequence change has no forward data migration for normal new-comp flow, but mid-event rollback is unsafe because pilot files may contain the newer `activeSequenceByType` shape; rollback should happen between events with a fresh new-comp setup.

---

## Related Work

- Admin score repair tools and mismatch preflight provide the recovery model RBR depends on for zero-fill, missing sequence repair, and safe score sync.
- Floating sync action feedback later improved the manual sync button UX but did not change the RBR state model.

---

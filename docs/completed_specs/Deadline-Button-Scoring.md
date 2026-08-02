# Deadline Button Scoring - Spec Summary

**Completion Date** - May 30, 2026  
**Branch/Commit** - `44a6620` (`Add deadline button scoring`) and `80ca025` (`fix for deadline button issue found in testing`)  
**Status** - Delivered

---

## Overview

The AeroJudge hardware gained a physical Deadline button, and the software now treats that button as a real box/boundary violation from press through Score sync. Judges can mark the violation from a new physical device button, review it on-screen, hear the matching audio prompt, and sync it using the expected box-error flag.

---

## The Problem

The physical Deadline button was added to the device, but the first software behavior was only enough to make the button usable in the field. It did not yet represent the full intended scoring semantics end to end. During judging, a box or boundary violation needed to be captured cleanly from the hardware button and carried through the normal save and sync flow as a true Score box-error condition, not as a plain zero or another special score type.

Testing also exposed a key-handling issue in the initial implementation: extending keyboard support for the Deadline button had to preserve the existing numeric keypad actions and avoid repeated actions from held GPIO keys.

---

## The Solution

The initial Deadline-button software path was replaced with a proper first-class special score. The hardware key path maps period/numpad decimal input to a `deadline` action. The judge page stores that action as local score value `-3`, displays it as `D/LINE`, plays `deadline.mp3`, and shows `Deadline` in the score summary.

When scores are pushed to Score, the local `-3` marker is converted to `raw_score=0` with `box_err=true`, matching Score's box/boundary violation import behavior. A follow-up key handling fix tightened keypad detection so regular numeric keypad buttons continue to map only to their existing score/navigation actions.

---

## What Changed

- **Hardware support** - Added and validated the physical Deadline button as a real device input.
- **Device key handling** - Replaced the initial temporary behavior with Deadline mapping for period/numpad decimal and kept edge-triggered button behavior to avoid repeated actions while a hardware key is held.
- **Judge scoring UI** - Added Deadline handling to score entry, current-figure display, summary display, and audio playback.
- **Score sync conversion** - Converted local Deadline markers to Score upload rows with `box_err=true`.
- **Regression fix** - Corrected key detection after testing so numeric keypad digit handling and decimal/Deadline handling do not interfere with each other.

---

## Technical Reference

**Primary Files Modified:**
- `judge/src/main/resources/static/device-keys.js`
- `judge/src/main/resources/templates/judge.html`
- `judge/src/main/java/com/aerojudge/judge/service/PilotService.java`

**Key Implementation Details:**
- Physical Deadline input is handled through the period/numpad decimal key path.
- Local score value `-3` represents Deadline inside the device score array.
- Score upload maps `-1` to `break_err`, `-2` to `not_observed`, and `-3` to `box_err`, all with `raw_score=0`.
- Deadline remains subject to the same special-score figure constraints as Break and Not Observed.


---

## Rollback

Rollback is limited to reverting `device-keys.js`, `judge.html`, and the upload conversion in `PilotService.java`. Existing Zero, Break, Not Observed, score adjustment, summary, and navigation behavior are otherwise independent of this feature.

---

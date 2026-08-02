# Unsaved Score Navigation Guard - Spec Summary

**Completion Date** - May 26, 2026  
**Branch/Commit** - main, `b379239` (`Guard against unsaved score navigation`), merged by `4fb3388`  
**Status** - Delivered

---

## Overview

The judge scoring screen now protects in-progress scores from accidental loss when a judge uses the menu/ Home button. This applies to both Global scoring and Round-by-Round (RBR) scoring mode.

---

## The Problem

During live judging, scores are kept in browser memory until the judge completes the sequence and saves. Previously, tapping the menu icon and choosing Home immediately left the scoring screen. If the judge had entered scores but had not saved them yet, those scores were silently lost and could not be recovered.

---

## The Solution

The in-app Home action now checks whether the current judging session appears to have unsaved score work. If no scores have been entered and the judge is still at the starting point, Home behaves normally. If score input changed or the judge has advanced beyond Figure 1, the app opens a large warning modal instead of navigating away.

The warning requires two confirmations: the first acknowledges that unsaved scores will be lost, and the second explicitly confirms the destructive action. Cancel closes the modal and returns the judge to the scoring screen with the in-memory scores unchanged.

---

## What Changed

- **Judge navigation** - Replaced the direct Home link on the judge page with a guarded navigation handler.
- **Unsaved score tracking** - Marked the session dirty when score actions add, remove, or set a score.
- **Warning modal** - Added a high-visibility unsaved-score modal with two-step confirmation.
- **Save behavior** - Cleared the unsaved-score flag after successful final score submission.
- **Carousel safety** - Kept scoring carousel navigation locked while the warning modal is open.

---

## Technical Reference

**Primary Files Modified:**
- `judge/src/main/resources/templates/judge.html`

**Key Implementation Details:**
- The guard only applies to the in-app Home floating action button on the judge page.
- Unsaved score detection is based on score input changes or advancement beyond Figure 1.
- Score-changing paths call the shared dirty-marker behavior through `addScore(...)`, `removeScore(...)`, and `setScore(...)`.
- Successful `submitScores(true)` clears the dirty flag before continuing to the normal next-event navigation.
- This does not protect browser refresh, browser back, tab close, power loss, or crash scenarios.


---

## Rollback

Rollback is limited to reverting the judge-page Home handler, unsaved-score modal, dirty-state tracking, and save-success flag reset in `judge.html`. No backend APIs, scoring rules, score payloads, or persisted data were changed.

---

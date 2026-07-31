# Admin Pages Overhaul — Spec Summary

**Completion Date** • May 2026  
**Branch/Commit** • admin-pages-overhaul (21 commits, merged via PR)  
**Status** • ✅ Delivered

---

## Overview

A complete overhaul of the device admin interface for score-fixing operations. Replaces unreliable error detection with a deterministic algorithm, introduces four distinct resolver operations for different error types, adds preventive design to block unsafe configuration changes, and consolidates navigation into a unified admin menu.

---

## The Problem

The admin score-fixing workflow had several compounding issues:

**Detection was unreliable:** The algorithm for finding score anomalies used median-fallback logic and silent-drop gates that masked real problems. Scorekeepers couldn't trust whether the system was showing them *all* the issues or just some of them.

**One operation for many cases:** Only the "Move Round" operation existed, forcing scorekeepers to use it for cases it wasn't designed for (like catching up no-show pilots). This led to incorrect state mutations and pilots getting "bricked" (unable to enter scores).

**Format-change data corruption:** Changing the comp configuration mid-contest (e.g., from 2-sequence to 1-sequence rounds) could strand pilots in inconsistent states with no recovery path.

**Fragmented navigation:** Admin entry points scattered across three separate pages; cross-links created a maze; no consolidated admin menu.

**Unverified bug in production:** `moveRound()` silently wrote incorrect `activeRound` values for non-equal-count moves, causing pilots to brick after any move-round operation on unequal-count pairs.

---

## The Solution

**Deterministic detection:** Replaced median-based logic with a spread-invariant algorithm. For each (class, round-type) group, the detector enforces `max(pilot count) − min(pilot count) ≤ 1`. Any spread ≥ 2 or incomplete sequences are flagged. No median, no silent drops — all anomalies that match the invariant are reported.

**Four distinct operations:**
- **Fix Missing Sequence** — Mark a pilot's missing sequence 2 as zero when peers confirm it's a 2-sequence round.
- **Move Round** — Transfer a misscored round from the wrong pilot (source, higher count) to the intended pilot (destination, lower count).
- **Zero-fill** — Catch up a behind pilot by adding zero-score rounds for rounds already flown by peers.
- **Swap Round** — Exchange round scores between two pilots when the judge picked the wrong person twice for the same round.

Each operation has specific eligibility rules and is reached via dedicated UI entry points.

**Preventive format-change blocker:** When a scorekeeper tries to reconfigure the comp to fewer sequences mid-contest, the system checks two rules: (1) no pilot is mid-round waiting for sequence 2, and (2) all pilots in each class are at the same round-count boundary. Both must pass; if either fails, the reconfig is rejected with a detailed explanation pointing to Score Fixes. No recovery tool — the data must be clean before the change is allowed.

**Unified admin navigation:** New `/admin` landing page with three tiles (Event Admin, Score Fixes, Device Settings) replaces scattered cross-links. Consistent header with "Admin Menu" + "Home" on every admin page.

**activeRound integrity fix:** `moveRound()` now uses explicit `decrementActiveRound()` + `incrementActiveRound()` per pilot instead of force-writing both to `destRound + 1`, fixing the production bug where pilots would brick after moves.

---

## What Changed

- **Java backend** → New `ScoreResolverService` extracts pure-logic resolver helpers; `ContestClasses` utility consolidates class-order constant; five new endpoints (`fix-missing-sequence`, `zero-fill`, `swap-round`, format-change blocker, improved `/api/scores/mismatches`); `moveRound()` bug fix.

- **Admin pages** → New admin landing page; three existing admin pages renamed and reorganized; three new wizard pages (resolver, zero-fill, swap-round); new sync-gate modal.

- **UI/Navigation** → Single "Admin Menu" entry point; wizard-based multi-step operations; tile-based tool layout on Score Fixes page; eligibility-based greying and validation messaging.

---

## Technical Reference

**Primary Files Modified:**
- `judge/src/main/java/com/aerojudge/judge/controller/APIController.java` (endpoint rewrites)
- `judge/src/main/java/com/aerojudge/judge/service/ScoreResolverService.java` (new)
- `judge/src/main/java/com/aerojudge/judge/utils/ContestClasses.java` (new)
- All admin templates (`admin.html`, `adminComp.html`, `adminScores.html`, `adminDevice.html`, `adminScoresResolve.html`, `adminScoresZeroFill.html`, `adminScoresSwap.html`)

**Key Implementation Details:**
- Detection uses the spread invariant (`max − min ≤ 1`) as the only acceptable state; no median logic or threshold tuning.
- Per-pilot blocking rule: if a pilot has an unresolved incomplete sequence, Move Round and Zero-fill refuse to operate on that pilot until the sequence is fixed.
- Sequential constraint on zero-fill: only the next missing round (targetCount + 1) is fillable in one operation; admin walks the wizard once per round for multi-round gaps.
- Figure-count determination via peer-borrowing: the system copies the figure count from a peer who flew the same round, ensuring compatibility.
- `activeRound` and `activeSequence` state is recomputed by type and updated incrementally per operation, not force-written.

**Testing Approach:**
- Per-commit smoke tests (build clean, pass topic validation, pass cross-cutting tests).
- Device validation with minimum-viable demo contest (4 pilots in one class, 2 in another, 1 freestyle-eligible).
- Mixed-anomaly multi-pass test: group with both count-gap and incomplete-round; run Fix Missing Sequence; re-enter; run Move Round; verify end state.

---

## Rollback

Code rollback: revert the 21 commits or restore prior versions of the modified files. No data migration or schema changes involved.

Device rollback (if needed): the system preserves backup JAR files and automatic health checks catch bad states — manual rollback is not expected.

---

## Related Work

- Depends on existing `CompService` and `PilotService` infrastructure.
- Complements RBR (Round-by-Round) scoring mode, which benefits from better error detection.
- Future work: per-class KNOWN sequence counts; pilot carousel highlighting for anomalies; auto-expanding SD card partitions during updates.

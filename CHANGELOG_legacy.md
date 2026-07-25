# Legacy Changelog Snapshot

This file preserves the previous `CHANGELOG.md` content as historical context.
It was not consistently maintained and should not be treated as a complete or
authoritative release history.

---

# Changelog

All notable changes to the AeroJudge App will be documented in this file.

---

## [Unreleased] - rbr-overhaul

### Added

- **Round-by-Round scoring mode**: New active-round workflow for judge boxes. In By Round
  mode, `/` sends judges to `/newround` when no round is active, and to
  `/pilot-list-round` while a round is being judged.
- **RBR round selection** (`/newround`): Touch-first flow for selecting Precision/Freestyle,
  class, Known/Unknown type, and final confirmation before creating the active round.
  Choices are built from loaded pilot and schedule data; unavailable round choices are hidden.
- **Active round persistence**: Active RBR round state is saved and restored so the device
  returns to the correct judging workflow after navigation or restart.
- **RBR pilot queue** (`/pilot-list-round`): Shows only pilots in the active round cohort and
  hides pilots once their active round score is complete.
- **Round completion flow** (`/round-complete`): Clear completion state after an active RBR
  cohort is finished, with the next action returning to round selection.
- **Clear Active Round** (`/admin`): Admin landing tile for clearing a mistaken unscored
  active round. The tile only appears when an active round exists and no scores have been
  recorded for that round.

### Changed

- **RBR start modal**: By Round mode now uses the existing judge-start modal but locks it to
  the active round. Global mode still shows KNO/UNK/FREE choices; RBR shows the active round
  label and a single Start Judging action.
- **RBR carousel cleanup**: Active-round pilot carousel now follows the global pilot-list
  pattern more closely, showing one active round card instead of duplicate round text.
- **RBR modal sizing**: RBR pilot-list modals now use the same modal sizing rules as Global
  pilot-list modals.
- **Score-save failure feedback**: The shared judge scoring page now uses the correct jQuery
  `error` callback and shows a red toast when `/api/score` fails. This replaces an older
  `failure` callback path that did not reliably surface save errors.
- **Flightline cleanup**: Removed flightline round-switching UI from admin/device setup pages
  as part of the RBR workflow cleanup.

### Fixed

- **Wrong-round score guard**: RBR score submissions are checked against the active round
  context before saving, preventing a judge box from silently saving scores to the wrong
  round.
- **Behind-pilot guard**: If any pilot in the active RBR cohort is behind the active round,
  judging stops and the error page lists the affected pilot names and their current round,
  directing the device to the Contest Director or Scorekeeper for needed score edits.
- **New event reset**: Loading a new event clears persisted round state so an old active
  round cannot leak into the next competition.

The RBR implementation is split across small commits for state persistence, routing guards,
round selection, active queue filtering, score submission guarding, completion, clear-active-round,
flightline cleanup, event reset, and field UX polish.

---

## [Unreleased] - admin-pages-overhaul

### Added

- **Admin landing page** (`/admin`): New top-level admin menu with three full-width tile links -
  Event Admin (`/admin/comp`), Score Fixes (`/admin/scores`), Device Settings (`/admin/device`).
  Single Exit button in the header; no FAB nav inside the menu.
- **Event name display**: The loaded comp's event name now shows bold and prominently on
  `/admin` and on `/validate-sequences` (both success and issues states), closing the
  test/live event confusion gap.
- **Fix Missing Sequence**: New admin action to mark a pilot's missing seq 2 of a 2-sequence
  KNOWN round as 0.0 (didn't fly). Used when the pilot didn't fly seq 2 (mechanical, scratch)
  and a judge at the line didn't manually zero the figures. Backend
  `POST /api/scores/fix-missing-sequence`. Reachable from the Resolve Mismatches wizard.
- **Move Round eligibility checks**: Backend `POST /api/scores/move-round` now rejects 400
  when the source has no extra round to give, or when either pilot has an unresolved missing
  seq 2 - admin must run Fix Missing Sequence first. Failure response names the specific
  pilot and the action to take.
- **Zero-fill** (`/admin/scores/zero-fill`): New tile + wizard for catching a behind pilot up
  to peers who have already flown a round. Used when a pilot had to miss a round (mechanical,
  control issues, etc.). Sequential constraint enforced - must zero K+1 before K+2.
  Backend `POST /api/scores/zero-fill`.
- **Swap Round Scores** (`/admin/scores/swap`): New tile + wizard for swapping two pilots'
  scores in a single round when the judge attributed each pilot's flight to the other. Both
  records look valid so the detector can't surface this case; admin discovers it from a
  judge report. Backend `POST /api/scores/swap-round`.
- **Format-change preventive blocker**: 2-sequence -> 1-sequence reconfig is rejected with
  HTTP 409 when the data isn't in a clean state - any pilot mid-round in KNOWN
  (`activeSequence == 2`), or any class with uneven KNOWN round counts. Modal lists per-rule
  failures and links to Score Fixes. Pre-check on both `/api/comp/local` and
  `/api/comp?edit=true`; new comp creation skipped (backupAllFiles wipes pilots first).
- **Pre-sync mismatch modal**: Tapping the publish-icon FAB on `pilot-list-global` /
  `pilot-list-round` now runs a pre-flight check against `/api/scores/mismatches`; if
  anomalies are detected the modal lists them with equal-styling Get admin / Sync anyway
  choices.

### Changed

- **Detection rewrite** (`/api/scores/mismatches`): Replaces the median/mode "expectedCount"
  algorithm and the silent-drop gate (which only emitted when both `tooMany` AND `tooFew`
  were non-empty - hiding single-sided gaps). New algorithm computes per-(class, roundType)
  min/max/spread plus per-pilot incomplete-round detection (seq 1 present, seq 2 missing,
  peer evidence required). Payload now exposes both `mismatches` (anomalous groups) and
  `allGroups` (every group with at least one pilot, used by manual-fix and zero-fill
  pickers). Anomaly tags (`count_gap`, `incomplete_round`) on each group.
- **Move-round activeRound integrity** (production bug fix): Non-equal-count moves previously
  bricked the affected pilots by force-writing both activeRounds to `destRound + 1`. Now
  uses explicit `decrementActiveRound` (source) + `incrementActiveRound` (destination)
  matching the delta of the move.
- **Page renames**:
  - `/admin/comp`: Scorekeeper Admin -> **Event Admin**
  - `/admin/scores`: Score Admin -> **Score Fixes**
  - `/admin/device`: Device Settings - unchanged
- **Admin navigation simplified**: Cross-link cleanup on every admin page - separate
  Scorekeeper / Device / Scores buttons replaced with a single Admin Menu button -> `/admin`.
  The previous "Home" button (a misnomer - `/` is the judging interface) becomes "Exit"
  with the `exit_to_app` icon; FAB green-circle picks up the same icon swap. FAB cleanup on
  `/admin/scores` and `/admin/device` removes the secondary `/admin/comp` settings entry.
- **Score Fixes tile layout**: Stacked full-width tiles using `.admin-tile-link` /
  `.admin-tile` / `.admin-tile-title` / `.admin-tile-text` (matching the admin landing). Tile
  order: Resolve Mismatches, Swap Round Scores, Zero Unflown Rounds, Audit Log (placeholder).
  The previously-placeholder Edit Scores tile is removed entirely.
- **Pilot-list admin warning Continue button**: Now lands on `/admin` (the menu) instead of
  `/admin/comp` directly.
- **Resolver wizard** (`/admin/scores/resolve`): The single-page move-only flow is replaced
  wholesale by a two-operation wizard. Detected-mismatch entry from the new detection
  algorithm; manual entry via Force Round Edits for cases the detector can't surface from
  data. Step 1 offers Fix Missing Sequence and Move Round side-by-side, with per-op greying
  driven by the group's incomplete-round and count-spread state.
- **DESIGN.md tier sweep**: All admin templates (admin landing, Event Admin, Device Settings,
  Score Fixes, all wizards, sync-mismatch modal) now use the locked text-size tiers (1.5 /
  1.75 / 1.9 / 2.1 rem) and spacing tiers from `.idea/DESIGN.md`. Inline icons unified to
  1.5rem. Disabled-row contrast palette applied uniformly with `cursor: not-allowed`.
- **`ContestClasses.ORDER` consolidation**: New `co.za.imac.judge.utils.ContestClasses`
  utility is the single source of truth for the IMAC class-order constant. Duplicate copies
  in `SequenceValidationService` and `RootController` removed.
  `ContestClasses.orderIndex(...)` helper handles case-insensitive lookup with an
  `ORDER.size()` fallback for null / unknown / FREESTYLE.
- **`ScoreResolverService`**: Pure-logic resolver helpers extracted from `APIController` to a
  new `@Service` class mirroring `SequenceValidationService`. Public methods: `getMismatches`,
  `countRoundsForType`, `findUnresolvedMissingSeq2Round`. Controller is now a thin HTTP layer.

The 21 commits on this branch are sequenced so each lands a single coherent concern that a
reviewer can read in isolation.

---

## [Unreleased] - fix/update-exit-143-bug

### Fixed

- **System Update exit code 143 (SIGTERM)**: The "Install Update" flow on the admin Device Settings page
  failed with error code 143. The update script (`judge_update.sh`) calls `systemctl stop judge.service`
  as part of the update process, which kills the Spring Boot application that spawned it via
  `ProcessBuilder.waitFor()`. The child shell process receives SIGTERM (exit 128+15=143) and the update
  fails every time.

  **Root cause:** The Java process blocked on `process.waitFor()` while the script stopped the very
  service that owned that process. The script killed its own parent.

  **Fix:** Split the update into two phases:
  1. **Download phase** (synchronous): Java calls `fetch_update.sh --download-only`, which checks for a
     newer version on GitHub and downloads assets to `/tmp/judge-update/`. Java waits for the exit code
     and returns an accurate response to the browser (`restart: true/false`).
  2. **Install phase** (fire-and-forget): If assets were downloaded, Java launches the install via
     `sudo systemd-run --unit=judge-update --scope fetch_update.sh --install`. This runs the script
     in its own systemd scope, independent of `judge.service`. When the script stops the service,
     the install process continues running.

  **Safety features added:**
  - **Backup before install**: Current `judge.jar` is copied to `judge.jar.bak` before any files
    are overwritten. If the new version fails, the backup is available for recovery.
  - **Automatic health check**: After installing and restarting services, the script polls
    `/actuator/health` (30 retries at 5-second intervals = 150-second window) to verify the new
    version started successfully.
  - **Automatic rollback**: If the health check fails, the script restores `judge.jar.bak`,
    restarts both `judge.service` and `kiosk.service`, and verifies the rollback with the same
    health check window. The device returns to the previous working version without manual
    intervention.
  - **Version marker moved to post-install**: `.judge_last_release` is now only written after the
    health check passes. Previously it was written before downloading assets, which meant a failed
    download would leave the device thinking it was already updated (pre-existing bug, now fixed).
  - **Isolated staging directory**: Assets download to `/tmp/judge-update/` instead of the working
    directory. A download failure leaves the installed binary completely untouched.
  - **Absolute paths**: All file references now use absolute paths (e.g., `/home/judge/.judge_last_release`)
    instead of relative paths, eliminating working directory ambiguity.

  **Backwards compatibility:** `judge_update.sh` supports a legacy mode (no flags) that runs the
  full download-then-install flow in one pass. Devices with the old `fetch_update.sh` (which cannot
  pass flags) will use this mode and still receive the backup, health check, and rollback features.

  **Frontend unchanged:** The API contract (`result`, `message`, `restart` fields) is preserved
  exactly. The two-phase browser flow (check for updates -> confirm -> install) works as before with
  no JavaScript changes.

  **Files changed:**
  - `APIController.java`: Replaced `executeUpdateScript()` (blocking) with `runDownloadPhase()`
    (synchronous) + `launchInstallPhase()` (fire-and-forget via `systemd-run`)
  - `fetch_update.sh`: Changed from `curl | bash` to download-then-execute pattern so arguments
    (`--download-only`, `--install`) can be forwarded to `judge_update.sh`
  - `judge_update.sh`: Full rewrite - split into `do_download()` and `do_install()` functions with
    backup, health check, rollback, and legacy mode support

---

## [2.1] - 2025-12-20

### Added

- **Direction Toggle**: Added direction toggle functionality to the Judge page [#108, #112]

### Changed

- **Removed Eureka client**: Removed use of Eureka service discovery client
- **Settings format**: Corrected settings format
- **Docker**: Updated Dockerfile
- **Scripts**: Added useful utility scripts

---

## [2.0] - 2025-11-27

### Added

#### Core Features

- **Per-Type Round Numbering**: Sequential numbering by round type (e.g., Known 3, Unknown 1, Freestyle 1)
  - Updated XML output to Score to reflect per-type numbering
  - Round number used for `sequences.dat` lookup and folder resolution [Issue 70]
  - Supports multiple known/unknown sequences in one competition
  - Score sequences.dat controls std vs alt sequence option
- **Incomplete KNOWN Round Warning**: Detects when pilot completed Seq 1 but not Seq 2
  - Triggers when: 2-sequence competition AND pilot at sequence 2 AND KNOWN button clicked
  - Options: Continue Seq 2 | Start New Round | Exit for Review
  - API: `POST /api/pilot/{pilotId}/advance-round?type=KNOWN`
- **Freestyle Support**: Added Freestyle as valid round type [Issue 103]
  - Complete scoring functionality implemented and tested
  - Output includes `duration_seconds` (default 240, editable in Score)
  - Restored Freestyle button on judge popup
- **Sequence Validation with Error Handling** [Issue 66]
  - Added error checking for new/update contest and pilot sync [Issue 66]
  - Validates sequence availability with graceful fallbacks (no white screen crashes)
  - Always-Show Validation Page after sync with three states:
    - Success (green): "All Sequences Validated!" with Continue button
    - Warnings (orange): Warning list with Re-Sync + Continue buttons
    - Errors (red): Error list with Re-Sync + Continue Anyway buttons
  - Auto-advances after 10 seconds if not manually confirmed
- **Score Mismatch Resolution:** Detect and fix scores assigned to wrong pilots
  - Scans for mismatches between judge device and Score server
- **Unknown Figures sync from Score** Ability to pull unknown figures package from Score (4.71 or newer required)

#### Admin Pages & Settings

- **Admin Pages Redesign**: Compact layout with header + nav on single line for mobile optimization
  - **`/admin/comp`** - Scorekeeper Admin: Known Sequence Count, Default Sequence Type, Refresh/Load Event
  - **`/admin/device`** - Device Settings: Scoring Mode, Device Identity (Line/Judge), System Update
  - **`/admin/scores`** - Score Admin: Score Mismatch Resolution with detection and fixing UI at `/admin/scores/resolve`
  - **`/newcomp`** - First Boot Wizard: 3-step setup (Event Settings -> Device Identity -> Load Event)
- **System Update Revamp**: Complete overhaul of the judge update system
  - New `/api/system/check-update` endpoint: Queries GitHub releases API to check for available updates
  - Version comparison: Shows current vs available version before updating
  - Confirmation modal: Requires user confirmation with internet connectivity warning before updating
  - Distinct exit codes: Update script returns 0 (no update), 1 (error), or 2 (update applied)
  - `fetch_update.sh`: New lightweight fetcher script installed on devices that fetches and runs main update script from GitHub
- **Lightweight Battery API**: New `/api/battery` endpoint for fast battery percentage checks
  - Returns only `{"percent": 85}` - single I2C read operation
- **Local Settings API**: New `POST /api/comp/local` endpoint for updating settings without Score server contact
  - Used by judge modal battery warnings for responsive UI
- **Configuration Settings**: Added configuration options
  - `score_poll_timeout` (default 2s) and `score_timeout` (default 10s) for Score communication timeouts
  - `language` setting added for future ability to change device language (prompts, audio, etc.)

#### UI Improvements

- **Judge Popup**: Always shows all three round type buttons; availability based on `sequences.dat`
- **Contest Setup**: Added confirmation prompt to prevent accidental edits [Issue 62 & 70]
- **Judging Page Display**:
  - KNOWN: "Round X Seq Y Dir Z"
  - UNKNOWN: "Round X Dir Z"
  - FREESTYLE: "Round X" (figure numbers hidden, shows description only)
- **FREESTYLE Filter**: Added FREESTYLE option to pilot class filters (appears when any pilot has freestyle=true)
- **Pilot Next Round Cards**: Minimal underline style (transparent background, bottom border only)
- **Score Summary**: Dynamic modal supports varied figure counts
- **Audio**: Removed hard-coded volume limits; caller button stops current audio when pressed [Issue 69]
- **Instructions Audio**: Numpad 0 plays `instructions.mp3` on pilot list pages
- **Battery Alerts**: Orange warning at <30%, red warning at <20% on judge popup (uses Materialize onOpenStart callback for reliable timing)
- **Info Icon**: Enlarged to 2.2rem for better visibility
- **Status Popup**: Auto-closes when carousel is rotated

#### Error Handling & Stability

- **Global Error Page (`error.html`)**: Catches unhandled 500 errors with user-friendly display
  - Red warning: "Please give this device to the Contest Director or Scorekeeper"
  - Admin button with confirmation modal (same warning as pilot list pages)
  - Prevents judges from being stuck on white screen errors
- **Modal Cleanup on Navigation**: All open modals (except summary) are now properly closed when navigating carousel
- **Summary Modal Lock**: Carousel navigation blocked while score summary modal is open
- **Code Refactoring**: Navigation logic consolidated into `carouselPrev()` and `carouselNext()` functions
  - Applies to: `judge.html`, `pilot-list-global.html`, `pilot-list-round.html`

### Changed

- **newcomp.html**: Complete redesign as 3-step first boot wizard (Event Settings -> Device Identity -> Load Event)
- **Admin navigation**: Links changed from /newcomp to /admin/comp throughout app
- **adminComp.html**: Labels updated ("Known Sequences" -> "Known Sequence Count", "Fallback Type" -> "Default Sequence Type")
- **adminDevice.html**: Now preloads current Line/Judge numbers from settings; compact warning box
- **Confirmation modals**: Condensed to single-line descriptions (no scrolling required)
- **Battery warning**: Threshold changed from 40% to 30%
- **Folder Structure**: Flattened `figures/en/` structure and created 2026 sequence structure [Issue 94]
- **Audio folder structure**: Updated for new organization
- **Update Architecture**: Single entry point for both Admin menu and SSH updates
  - Admin menu and SSH now both use `/home/judge/fetch_update.sh`
  - Main update logic (`judge_update.sh`) stays on GitHub and can be updated remotely
  - `judge_setup.sh` updated to install `fetch_update.sh` instead of full update script
- **Pilot sync**: Renamed and now properly refreshes sequence cache

### Fixed

- **Admin page 404 errors**: Fixed resource paths (relative -> absolute) for nested routes like /admin/comp
- **Sequence validation**: Fixed error checking for new/update contest and pilot sync [Issue 66]
  - Graceful fallbacks eliminate white screen crashes

### Technical Notes

- All admin pages use consistent compact styling for device screens
- API: `GET /api/system/check-update` - Returns version comparison JSON
- API: `POST /api/system/update` - Calls local `fetch_update.sh` script
- API: `POST /api/comp/local` - Updates settings without Score contact
- API: `GET /api/battery` - Returns battery percentage
- API: `GET /api/scores/mismatches` - Scans for score mismatches
- API: `POST /api/scores/resolve` - Fixes mismatched scores
- Project targets Java 17 (compatible with Java 17-21)
- All deprecated API warnings resolved
- Removed unused imports

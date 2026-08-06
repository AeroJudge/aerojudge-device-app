# AeroJudge Device App Structure Reference

Snapshot date: 2026-08-06 06:54:34 -04:00

Code snapshot: `74bad1609fd97c69b836615712ca99abe19ac322`

Current-state frontend/page structure and UI architecture reference.

This document describes the application as reviewed from repository code. It is
intended as a foundation for a broader design reference guide, not as an
implementation plan. Facts below are grounded in direct review of controllers,
templates, static assets, `.idea/agents.md`, and `docs/UI_DESIGN_REFERENCE.md`.

## Scope And Safety Context

Fact: the real runtime target is a Raspberry Pi 4B judge device with a
Waveshare 21973 800x480 touch display. The repository instructions treat
judging, score entry, sequence display, device setup, score upload, and
deployment paths as event-impacting (`.idea/agents.md:3`,
`.idea/agents.md:13`).

Fact: current UI guidance is split between admin screens and field/judge/RBR
screens. The existing admin reference defines an admin text/spacing scale and
navigation model (`docs/UI_DESIGN_REFERENCE.md:1`,
`docs/UI_DESIGN_REFERENCE.md:7`). The field reference separately covers
`/newround`, `/pilot-list-round`, `judge_start_select.html`, `/judge`, and
`/round-complete` (`docs/UI_DESIGN_REFERENCE.md:229`,
`docs/UI_DESIGN_REFERENCE.md:233`).

Opinion: future structure guidance should keep this event-safety framing visible.
The pages reviewed here are not generic web pages; several are active contest
operation surfaces.

## Findings Summary

Fact: the frontend is primarily Spring MVC plus Thymeleaf templates. Page routes
that return templates are concentrated in `RootController`, with sequence
validation in `ValidationController` and an IOException recovery template in
`GlobalExceptionHandler`.

Fact: the app does not currently have a true shared Thymeleaf page layout or
shared head/import mechanism. Reuse exists at fragment level only:
`banner::line_judge`, `banner::status`, `judge_start_select::modal_select`,
`class_filter_modal::class_filter_modal`, `summary_modal::summary_modal`, and
`sync-mismatch-modal::modal`.

Fact: static assets are imported directly by each template. Materialize CSS/JS
and Material Icons are broadly active. App-owned shared assets include
`device-toast.css`, `device-keys.js`, and `fab-sync-feedback.js`.

Opinion: the biggest current structural risk is the combination of fragmented
page heads and large page-local inline CSS/JS blocks. This makes event-facing
800x480 behavior, modal behavior, carousel behavior, and toast consistency harder
to verify and maintain.

## 1. Application Page Map

### Route-Backed Pages

| Route | Controller method | Template returned | Role | Surface type |
|---|---|---|---|---|
| `/` | `RootController.home` (`RootController.java:54`) | No direct template; redirects through `redirectForCurrentScoringMode` (`RootController.java:92`) | Main entry that chooses setup/global/RBR flow | Shared utility |
| `/pilot-list-global` | `RootController.pilotListGlobal` (`RootController.java:107`) | `pilot-list-global` (`RootController.java:169`) | Global pilot work queue and start point for per-pilot scoring | Judge-facing |
| `/pilot-list-round` | `RootController.pilotListRound` (`RootController.java:172`) | `pilot-list-round` (`RootController.java:274`), `error` on RBR alignment/close failures (`RootController.java:227`, `RootController.java:241`) | Active round-by-round pilot work queue | Judge-facing / recovery |
| `/judge` | `RootController.judge` (`RootController.java:277`) | `judge` (`RootController.java:355`), `noseq` when no schedule figures exist (`RootController.java:327`) | Active figure scoring screen | Judge-facing |
| `/newcomp` | `RootController.newcomp` (`RootController.java:358`) | `newcomp` (`RootController.java:386`) | Initial event/device setup when no comp is loaded | Admin/setup |
| `/rounds` | `RootController.showRounds` (`RootController.java:389`) | No direct template; redirects through `redirectForCurrentScoringMode` (`RootController.java:392`) | Legacy/compat route into current score-mode flow | Shared utility |
| `/newround` | `RootController.newRound` (`RootController.java:395`) | `newround` (`RootController.java:413`) | RBR round selection and start flow | Judge-facing/admin-adjacent setup |
| `/round-complete` | `RootController.roundComplete` (`RootController.java:416`) | `round-complete` (`RootController.java:432`) | RBR completion state after active cohort is complete | Judge-facing recovery/completion |
| `/admin` | `RootController.admin` (`RootController.java:476`) | `admin` (`RootController.java:484`) | Admin menu and active-round clear entry when allowed | Admin/setup |
| `/admin/comp` | `RootController.adminComp` (`RootController.java:435`) | `adminComp` (`RootController.java:458`) | Event settings, scoring mode, refresh/load event | Admin/setup |
| `/admin/device` | `RootController.adminDevice` (`RootController.java:461`) | `adminDevice` (`RootController.java:473`) | Device identity, Score settings, update flow | Admin/setup |
| `/admin/scores` | `RootController.adminScores` (`RootController.java:487`) | `adminScores` (`RootController.java:491`) | Score Fixes landing page | Admin/score correction |
| `/admin/scores/resolve` | `RootController.adminScoresResolve` (`RootController.java:494`) | `adminScoresResolve` (`RootController.java:498`) | Mismatch detection, missing sequence, move round workflow | Admin/score correction |
| `/admin/scores/zero-fill` | `RootController.adminScoresZeroFill` (`RootController.java:501`) | `adminScoresZeroFill` (`RootController.java:505`) | Zero-fill score correction workflow | Admin/score correction |
| `/admin/scores/swap` | `RootController.adminScoresSwap` (`RootController.java:508`) | `adminScoresSwap` (`RootController.java:512`) | Swap-round score correction workflow | Admin/score correction |
| `/validate-sequences` | `ValidationController.validateSequences` (`ValidationController.java:56`) | `sequence_validation` (`ValidationController.java:76`) | Sequence validation status, resync, continue path | Recovery/validation |

### Form/Post Routes With Page Impact

| Route | Controller method | Result | Role |
|---|---|---|---|
| `/validate-sequences/continue` | `ValidationController.continueAnyway` (`ValidationController.java:83`) | Redirects to `/rounds` (`ValidationController.java:87`) | User accepts validation state and returns to normal flow |
| `/validate-sequences/resync` | `ValidationController.resync` (`ValidationController.java:94`) | Redirects to `/validate-sequences` (`ValidationController.java:123`) | User re-syncs pilots/sequences from validation page |

### Exception/Recovery Pages

| Trigger | Handler | Template | Role |
|---|---|---|---|
| `IOException` from `RootController` | `GlobalExceptionHandler.scoreUnavailable` (`GlobalExceptionHandler.java:21`) | `/needscore` (`GlobalExceptionHandler.java:30`) | Score unavailable/retry recovery surface |
| No schedule figures for selected judging context | `RootController.judge` (`RootController.java:327`) | `noseq` | No sequence recovery surface |
| RBR alignment or close failure | `RootController.pilotListRound` (`RootController.java:227`, `RootController.java:241`) | `error` | Admin-actionable blocking error |

### Non-Returned Or Legacy Template

Fact: `rounds.html` is a full independent HTML document, but the current
`/rounds` controller method does not return it. The method redirects through
`redirectForCurrentScoringMode` (`RootController.java:389`,
`RootController.java:392`). The template still imports noUiSlider CSS
(`rounds.html:12`).

Uncertainty: `rounds.html` may be retained as legacy, fallback, or unfinished
UI. Code review found no active controller return for it.

## 2. Template Structure

### Full Independent HTML Documents

Fact: these route-backed templates are full documents with `<!DOCTYPE html>` and
`<html>`:

- `admin.html`
- `adminComp.html`
- `adminDevice.html`
- `adminScores.html`
- `adminScoresResolve.html`
- `adminScoresSwap.html`
- `adminScoresZeroFill.html`
- `error.html`
- `judge.html`
- `needscore.html`
- `newcomp.html`
- `newround.html`
- `noseq.html`
- `pilot-list-global.html`
- `pilot-list-round.html`
- `round-complete.html`
- `sequence_validation.html`

Fact: `rounds.html` is also a full document but is not currently returned by
`RootController`.

### Fragment Documents

Fact: the fragment files also include document wrappers. They are used through
`th:replace`, not returned directly by controllers in the reviewed code.

| Fragment file | Fragment | Used by |
|---|---|---|
| `banner.html` | `line_judge`, `status` (`banner.html:6`, `banner.html:19`) | Admin pages, field pages, setup pages, pilot lists, new round |
| `judge_start_select.html` | `modal_select` (`judge_start_select.html:6`) | `pilot-list-global.html`, `pilot-list-round.html` |
| `class_filter_modal.html` | `class_filter_modal` (`class_filter_modal.html:6`) | `pilot-list-global.html` |
| `summary_modal.html` | `summary_modal` (`summary_modal.html:6`) | `judge.html` |
| `sync-mismatch-modal.html` | `modal` (`sync-mismatch-modal.html:5`) | `newround.html`, `pilot-list-global.html`, `pilot-list-round.html` |

### Fragment Reuse References

Fact: fragment reuse is explicit in templates:

- `banner::line_judge` and `banner::status` are inserted in admin, score-admin,
  field, and setup templates (`admin.html:76`, `admin.html:77`,
  `pilot-list-global.html:55`, `pilot-list-global.html:56`,
  `newround.html:153`, `newround.html:154`).
- `judge_start_select::modal_select` is inserted per pilot modal in both pilot
  lists (`pilot-list-global.html:107`, `pilot-list-round.html:86`).
- `class_filter_modal::class_filter_modal` is inserted by the global pilot list
  (`pilot-list-global.html:128`).
- `sync-mismatch-modal::modal` is inserted by the field sync surfaces
  (`newround.html:219`, `pilot-list-global.html:131`,
  `pilot-list-round.html:109`).
- `summary_modal::summary_modal` is inserted by `judge.html`
  (`judge.html:179`).

### Shared Layout/Head Mechanism

Fact: no true shared layout/head mechanism was found. Each full template defines
its own head imports and page CSS. Static imports are repeated rather than
centralized.

Examples:

- Admin pages import `/materialize/css/material_lcons.css`,
  `/materialize/css/materialize.min.css`, `/device-toast.css`,
  `/jquery-3.6.4.min.js`, and `/materialize/js/materialize.js`
  (`admin.html:10`-`admin.html:12`, `admin.html:146`-`admin.html:147`).
- Field pages mostly import relative `materialize/...`, `device-toast.css`, and
  app JS (`pilot-list-global.html:46`-`pilot-list-global.html:50`,
  `newround.html:9`-`newround.html:11`, `newround.html:234`-`newround.html:236`).

Opinion: this is the main architectural maintenance risk for UI consistency.
Even small framework, viewport, or toast import changes would need repeated
manual edits.

## 3. Shared UI Assets

### Active Static CSS

| Asset | Current use |
|---|---|
| `materialize/css/material_lcons.css` | Material Icons font CSS imported by most templates |
| `materialize/css/materialize.min.css` | Main active Materialize CSS imported by active route-backed pages |
| `device-toast.css` | Shared toast treatment for pages that call `M.toast(...)` |
| `materialize/css/nouislider.css` | Imported by `rounds.html` only; current route does not return that template |

Fact: `device-toast.css` styles Materialize toasts with bottom-center placement,
large bold text, white panel, and strong border (`device-toast.css:1`,
`device-toast.css:11`, `device-toast.css:20`, `device-toast.css:24`).

### Active Static JavaScript

| Asset | Current use |
|---|---|
| `jquery-3.6.4.min.js` | Used by templates with AJAX, click handlers, DOM construction |
| `materialize/js/materialize.js` | Used for modals, floating action buttons, carousels, toast |
| `device-keys.js` | Used by judge and pilot-list surfaces for physical key mapping |
| `fab-sync-feedback.js` | Used by pilot list and new-round sync surfaces for floating action button feedback |

Fact: `device-keys.js` maps digits to named actions and suppresses held-key
repeat (`device-keys.js:4`-`device-keys.js:17`,
`device-keys.js:85`-`device-keys.js:91`).

Fact: `fab-sync-feedback.js` depends on Materialize FloatingActionButton and
provides a global `AeroJudgeFabSyncFeedback` object
(`fab-sync-feedback.js:39`, `fab-sync-feedback.js:98`).

### Materialize And MDL Usage

Fact: Materialize is the active UI framework. Templates call `M.Modal.init`,
`M.FloatingActionButton.init`, `M.toast`, `M.AutoInit`, and jQuery Materialize
carousel wrappers (`admin.html:150`, `pilot-list-global.html:195`,
`pilot-list-global.html:203`, `pilot-list-global.html:208`,
`newround.html:239`).

Fact: MDL assets exist under `judge/src/main/resources/static/mdl`, including
`package.json`, `material.css`, and `material.js`. Searches did not find active
template imports for `/mdl` or `material.min.js`.

Uncertainty: MDL may be legacy/deployable baggage. No active page dependency was
verified from templates.

### Duplicate Or Suspicious Legacy Assets

Fact: the static tree contains:

- `materialize/`
- `materialize-v1.0.0/materialize/`
- `mdl/`
- `slider/`
- `materialize/js/nouislider.js`
- `materialize/css/nouislider.css`

Fact: current template references use `materialize/...`, not
`materialize-v1.0.0/...`. noUiSlider appears only through `rounds.html`, which is
not currently route-returned.

Opinion: vendor directories deserve a separate asset cleanup/design decision,
not an incidental UI change.

## 4. Workflow Surfaces

### Judge Scoring Flow

Fact: `/judge` loads the pilot, pilot scores, active round/sequence, round type,
sequence type, and figure list. It returns `noseq` if the schedule has no figures
(`RootController.java:277`, `RootController.java:327`,
`RootController.java:355`).

Fact: `judge.html` renders a full-screen Materialize carousel of maneuvers and a
fixed scoring button strip (`judge.html:123`-`judge.html:136`). It posts score
data to `/api/score` (`judge.html:631`).

Fact: `judge.html` has a summary modal from `summary_modal.html`, an unsaved
score guard modal, and page-local carousel lock behavior around the summary
modal (`judge.html:179`, `judge.html:181`, `judge.html:246`,
`judge.html:260`, `judge.html:280`).

### Pilot List / Global Scoring Flow

Fact: `/pilot-list-global` is used when the comp is in global scoring mode.
`RootController.pilotListGlobal` redirects to RBR pages if by-round mode is
active (`RootController.java:107`, `RootController.java:120`,
`RootController.java:122`).

Fact: `pilot-list-global.html` presents pilots as a full-screen Materialize
carousel (`pilot-list-global.html:58`) and uses `judge_start_select::modal_select`
inside each pilot modal (`pilot-list-global.html:106`,
`pilot-list-global.html:107`).

Fact: the global page includes sync pilots, sync scores, class filter, and admin
warning actions in a floating action button (`pilot-list-global.html:121`-
`pilot-list-global.html:124`).

### Pilot List / By-Round Scoring Flow

Fact: `/pilot-list-round` requires an active scoring round. Without one, it
redirects to `/newround` in by-round mode or `/rounds` otherwise
(`RootController.java:196`, `RootController.java:198`).

Fact: the controller filters pilots to the active round cohort and blocks with
`error` if any cohort pilot is behind the active round
(`RootController.java:201`-`RootController.java:227`).

Fact: when all active-cohort pilots are complete, the controller closes the round
and redirects to `/round-complete` (`RootController.java:232`-
`RootController.java:237`).

Fact: `pilot-list-round.html` mirrors the global pilot carousel structure but
shows the active round context rather than all round types
(`pilot-list-round.html:58`, `pilot-list-round.html:69`-
`pilot-list-round.html:78`).

### Admin / Setup Flow

Fact: `/admin` is the landing menu. The existing design reference explicitly
states that other admin pages return to `/admin`, and score wizard pages return
to `/admin/scores` (`docs/UI_DESIGN_REFERENCE.md:7`).

Fact: `admin.html` includes an active-round clear tile only when
`activeRoundCanClear` is true, and confirms through a modal before posting
`/api/rounds/active/clear` (`admin.html:93`, `admin.html:129`,
`admin.html:157`).

Fact: `/newcomp`, `/admin/comp`, and `/admin/device` all touch setup-related
data. They post settings, comp changes, event refresh/load, device identity, and
system update APIs (`newcomp.html:294`, `newcomp.html:313`,
`adminComp.html:286`, `adminComp.html:318`, `adminDevice.html:388`,
`adminDevice.html:541`, `adminDevice.html:596`).

### Round Creation / Round Management Flow

Fact: `/newround` is the active RBR round start point when no round is active.
It renders available round options from controller-provided `roundOptions`
(`RootController.java:395`, `RootController.java:413`,
`RootController.java:609`).

Fact: `newround.html` implements a page-local multi-step family/class/type/
confirmation selection in JavaScript, then posts `/api/rounds`
(`newround.html:243`-`newround.html:245`, `newround.html:299`,
`newround.html:307`).

Fact: `/round-complete` displays completion state and links to `/newround`
(`round-complete.html:88`). It also includes audio/autosync logic
(`round-complete.html:92`, `round-complete.html:115`).

### Score Sync / Score Correction Flow

Fact: field sync controls call `/api/pilots/sync`, `/api/scores/mismatches`, and
`/api/scores/sync` from new round and pilot list pages (`newround.html:445`,
`newround.html:464`, `newround.html:481`, `pilot-list-global.html:241`,
`pilot-list-global.html:260`, `pilot-list-global.html:277`).

Fact: `sync-mismatch-modal.html` provides a shared pre-sync mismatch warning
fragment with "Get admin" and "Sync anyway" choices (`sync-mismatch-modal.html:6`,
`sync-mismatch-modal.html:18`, `sync-mismatch-modal.html:19`).

Fact: score correction admin pages use `/api/scores/mismatches`,
`/api/scores/fix-missing-sequence`, `/api/scores/move-round`,
`/api/scores/zero-fill`, and `/api/scores/swap-round`
(`adminScoresResolve.html:507`, `adminScoresResolve.html:793`,
`adminScoresResolve.html:822`, `adminScoresZeroFill.html:516`,
`adminScoresSwap.html:518`).

### Error / No-Sequence / Recovery Surfaces

Fact: `error.html` is a full-page blocking error with an admin modal
(`error.html:71`, `error.html:77`).

Fact: `noseq.html` is returned when `/judge` cannot find sequence data. It links
back to `/pilot-list-global` (`noseq.html:26`). This may be incomplete for
by-round mode because the link target is hard-coded to the global list.

Fact: `needscore.html` is the IOException recovery page. It reloads on body
click (`needscore.html:35`).

Fact: `sequence_validation.html` provides success, warning/error, resync, and
continue flows through forms (`sequence_validation.html:229`,
`sequence_validation.html:239`, `sequence_validation.html:245`,
`sequence_validation.html:255`, `sequence_validation.html:261`).

## 5. Interaction Model

### Physical Keyboard / Button Handling

Fact: `device-keys.js` maps:

- `0` to `caller`
- `1` to `not-observed`
- `2` to `minus-half`
- `3` to `minus-one`
- `4` to `back`
- `5` to `zero`
- `6` to `next`
- `7` to `break`
- `8` to `plus-half`
- `9` to `plus-one`
- `.` to `deadline`

The mapping is implemented in `device-keys.js:4`-`device-keys.js:17`.

Fact: key repeat is suppressed by tracking pressed keys and clearing on keyup or
blur (`device-keys.js:67`-`device-keys.js:76`,
`device-keys.js:85`-`device-keys.js:91`).

Fact: `pilot-list-global.html`, `pilot-list-round.html`, and `judge.html` bind
document-level `keydown` handlers and call `preventDefault()` for mapped device
actions (`pilot-list-global.html:332`-`pilot-list-global.html:343`,
`pilot-list-round.html:310`-`pilot-list-round.html:321`,
`judge.html:770`-`judge.html:790`).

### Touch / Tap Handling

Fact: touch interactions are primarily Materialize buttons, modal triggers,
floating action buttons, and carousel gestures. Many actions are `<a href="#">`
or `<a href="#!">` with click handlers that call `preventDefault()`.

Fact: Materialize modal triggers are used for judge start, admin warnings,
summary, unsaved score guard, score correction confirmations, and setup
confirmation dialogs.

Opinion: the app is touch-heavy but most touch behavior is page-local rather
than centralized. That is workable but increases the need for workflow-specific
verification on the 800x480 device.

### Carousel Usage

Fact: pilot lists use `.carousel.carousel-slider` with `fullWidth: true` and
`indicators: false` (`pilot-list-global.html:208`-`pilot-list-global.html:210`,
`pilot-list-round.html:186`-`pilot-list-round.html:188`).

Fact: `judge.html` uses `.carousel.carousel-slider` with `fullWidth: true`,
`indicators: true`, and `noWrap: true` (`judge.html:541`-`judge.html:544`).

Fact: carousel navigation closes open modals on pilot-list and judge pages
(`pilot-list-global.html:214`-`pilot-list-global.html:218`,
`pilot-list-round.html:191`-`pilot-list-round.html:195`,
`judge.html:398`-`judge.html:404`).

Opinion: the carousel is a core workflow dependency, not decoration. Any design
guide should document its expected gesture, keyboard, modal, and no-wrap
behavior.

### Modal Usage

Fact: modals appear across all major surfaces:

- judge start selection (`judge_start_select.html`)
- score summary and unsaved-score guard (`summary_modal.html`, `judge.html`)
- admin warning from field surfaces (`pilot-list-global.html`,
  `pilot-list-round.html`, `newround.html`, `error.html`)
- sync mismatch warning (`sync-mismatch-modal.html`)
- admin event/device/update confirmations (`adminComp.html`, `adminDevice.html`)
- active-round clear (`admin.html`)

Opinion: modal behavior should become an intentional design rule because some
modals block event-critical actions, while others are advisory.

### Vertical Scrolling Needs

Fact: field carousel pages and completion/start pages are generally viewport
locked at `100vh`. `newround.html` and `round-complete.html` explicitly set
`overflow: hidden` (`newround.html:13`-`newround.html:15`,
`round-complete.html:12`-`round-complete.html:15`).

Fact: admin wizard pages generally allow normal document flow; one wizard has an
explicit scrollable result area (`adminScoresResolve.html:284`).

Opinion: vertical-scroll policy should be documented separately for field pages
and admin pages. Field pages benefit from predictable full-screen behavior, but
admin correction surfaces need recoverable access to longer data.

### Horizontal Gesture / Browser History Risks

Fact: app-owned searches did not find `history.pushState`, `popstate`, or custom
browser-history handling. The route transitions use normal links,
`window.location`, and `window.location.replace` in several pages.

Fact: app-owned searches did not find a global `touch-action` or
`overscroll-behavior` rule outside vendor CSS. Most pages set
`maximum-scale=1.0` in viewport metadata.

Uncertainty: actual horizontal back/forward gesture risk depends on Chromium
kiosk flags and the Waveshare device browser environment, not only repository
CSS/JS.

### Pinch Zoom / Small Text

Fact: active templates use viewport metadata with `maximum-scale=1.0`. They do
not use `user-scalable=no`, but `maximum-scale=1.0` constrains zoom on browsers
that honor it.

Opinion: because pinch zoom is constrained, small text and modal density should
be treated as design risks on the 800x480 target rather than relying on user
zoom as a fallback.

## 6. Structural Risks

### Fragmented Page Heads

Fact: there is no single source of truth for page imports. Admin and field pages
repeat similar imports with root-relative vs relative path differences.

Risk: a CSS/JS dependency, viewport rule, or Materialize version change could
diverge between pages.

### Inline CSS / JS Concentration

Fact: route-backed templates contain large `<style>` and `<script>` blocks.
Examples include `judge.html`, `newround.html`, `adminDevice.html`,
`adminScoresResolve.html`, `adminScoresZeroFill.html`, and
`adminScoresSwap.html`.

Risk: page behavior and visual rules are tightly coupled to individual templates.
This makes visual regression review and shared workflow behavior harder.

### Repeated Route / Template Patterns

Fact: global and RBR pilot list templates share structure: banner fragments,
carousel, per-pilot start modal, sync FAB actions, admin-warning modal, battery
check, sync mismatch logic, and keyboard navigation.

Risk: fixes to pilot-list behavior may need to be duplicated.

### Vendor Framework Coupling

Fact: Materialize is coupled into HTML classes, JavaScript initialization, modal
APIs, floating action buttons, carousel APIs, toast APIs, and color classes.

Risk: framework migration or even framework-version cleanup would be broad and
event-facing.

### Hidden Carousel / Modal Assumptions

Fact: pilot-list and judge pages close modals before carousel navigation.
`judge.html` additionally locks carousel navigation while the summary modal is
open.

Risk: these assumptions are critical to score safety. If modal and carousel
interactions diverge between pages, judges could navigate away from required
confirmation states.

### Design Inconsistencies

Fact: the existing UI reference documents admin scale and field UI principles,
but templates still contain many one-off inline sizes and colors.

Risk: visual changes may unintentionally break outdoor readability or tap target
comfort on the 5-inch display.

## 7. Design Reference Recommendations

### Document As Current State

The design reference should document:

- Runtime target and event-safety scope.
- Full route-to-template map.
- Redirect rules for global vs by-round scoring.
- Fragment inventory and fragment ownership.
- Active and inactive templates, including `rounds.html`.
- Static asset import matrix by template.
- Materialize APIs used by each workflow.
- Device key map and repeat-suppression behavior.
- Carousel rules for pilot lists and judge scoring.
- Modal types and when each is blocking vs advisory.
- Field sync and score correction API surfaces.
- Error, no-sequence, Score-unavailable, and validation recovery surfaces.

### Make Intentional Rules

The broader design reference should establish rules for:

- Shared page head/import expectations.
- Toast inclusion and message style.
- Field page viewport/overflow behavior.
- Admin page scroll behavior.
- Minimum tap target and text size by surface.
- Global vs RBR start-modal behavior.
- Field utility FAB actions.
- Admin warning modal language and button hierarchy.
- Score correction wizard step structure.
- Vendor asset ownership and supported framework version.

### Separate Future Specs Or Projects

These should be separate future specs/projects rather than incidental cleanup:

- Shared layout/head consolidation.
- Vendor asset cleanup and duplicate directory disposition.
- `rounds.html` retention/removal decision.
- Carousel gesture safety on Waveshare/Chromium kiosk.
- Admin score wizard component standardization.
- Field page 800x480 visual regression checklist.
- Recovery-page routing review, especially `noseq.html` returning to
  `/pilot-list-global`.

## Open Questions

1. Is `rounds.html` intentionally retained as a fallback, or is it dead UI?
2. Should `mdl/` and `materialize-v1.0.0/` remain deployable static assets?
3. Should noUiSlider assets remain if the only observed template reference is
   inactive?
4. Should horizontal gesture/back-swipe behavior be controlled in app CSS, kiosk
   Chromium configuration, or both?
5. Should `noseq.html` return to the current score-mode page rather than always
   linking to `/pilot-list-global`?
6. Should fragment files keep full document wrappers, or should they become
   fragment-only files as part of a future template cleanup?

## Proposed Future Table Of Contents

1. Runtime Target and Event-Safety Scope
2. Route and Template Page Map
3. Score Modes and Redirect Rules
4. Template and Fragment Architecture
5. Shared Static Assets and Vendor Bundles
6. Field Workflows: Global, RBR, Judge Scoring
7. Admin and Setup Workflows
8. Score Sync and Score Correction Workflows
9. Recovery and Error Surfaces
10. Interaction Model: Touch, Keyboard, Carousel, Modal
11. 800x480 Layout Rules and Text Scale
12. Known Structural Risks
13. Open Questions and Future Specs

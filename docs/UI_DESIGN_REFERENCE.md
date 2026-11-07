# AeroJudge Device App UI Design Reference

Original admin-scale snapshot: 2026-08-06 06:59:53 -04:00

Banner, modal and popover standards updated: 2026-11-07 (approved UI cleanup).

Original admin-scale code snapshot: `74bad1609fd97c69b836615712ca99abe19ac322`

Shared text-size and spacing scale for all admin pages. Locked in 2026-05-01 during the post-resolver-overhaul cleanup. Apply these values to any new admin page so the project stays consistent.

**Admin-scale scope:** admin templates only - `admin.html` (landing menu), `adminComp.html` (Event Admin), `adminDevice.html` (Device Settings), `adminScores.html` (Score Fixes), `adminScoresResolve.html`, `adminScoresZeroFill.html`, `adminScoresSwap.html`. Judge screens (`/judge`, `pilot-list-*.html`, `judge_start_select.html`) are not part of this page scale and have their own conventions. The shared modal standard below applies across admin and field pages, with explicit exceptions.

**Admin navigation model.** `/admin` is the landing menu. Every other admin page (Event Admin / Score Fixes / Device Settings) has a single `Admin Menu` button in its header to return to the landing. No cross-links between admin pages - admin always pivots through the menu. Wizard sub-pages of Score Fixes (`adminScoresResolve.html` etc.) have a `Score Fixes` back button that returns to `/admin/scores`, not all the way to the menu.

**Operating constraints driving the scale:**
- 5" touch screens, often used outdoors in bright light
- Tap-target floor 44px
- Vertical real estate is the bottleneck - page height matters more than width

---

## Text size tiers

Four named tiers. Use exactly these values; do not introduce new sizes.

| Tier | Value | Used for |
|---|---|---|
| Small | `1.5rem` | captions, meta labels, hint text, warning notes, modal body text when secondary, inline icons |
| Body | `1.75rem` | labels, list rows, paragraphs, modal primary text, button labels in wizards, wizard-strip header |
| Emphasis | `1.9rem` | section/box titles, step titles, mismatch-card headers, manual-pick class names, round-pick rows |
| Strong | `2.1rem` | resolver "All scores look good" all-clear (rare; for celebratory or large-summary states only) |

**Page heading** (`.heading`): viewport-scaled `calc(1.5vw + 2.5vh)` for top-level pages, `calc(1.2vw + 2vh)` for wizard sub-pages. Don't replace these - they already adapt to viewport.

**Decorative icons** (tile icons, all-clear check_circle): `2.5rem` for tile icons, `3.5rem` for the resolver landing all-clear. These are visual elements, not text - leave at current sizes.

**Inline icons** (next to titles, in warning notes): `1.5rem`. Prior values were 1.0rem and 1.2rem; both unified.

---

## Spacing tiers

Use these values for vertical margin and padding. Horizontal spacing is unconstrained - text is already edge-to-edge on a 5" screen.

| Tier | Value | Typical use |
|---|---|---|
| Tight | `3px` - `4px` | within tightly-packed rows (pilot-context-row padding, caption margin-top) |
| Small | `6px` | row-to-row margin-bottom, gap between related elements, step1-buttons margin-top |
| Medium | `8px` - `10px` | section-to-section margin-bottom, box padding (top/bottom), confirm-box margin |
| Loose | `12px` - `16px` | result-box padding, force-edit-btn padding, between major page sections |
| Page edges | `20px` top, `6px` bottom | Admin header-row margins; includes the approved extra 10px separation from the banner |

**Box padding pattern:** `padding: <vertical> <horizontal>` where vertical <= horizontal. Standard for content boxes is `10px 14px`.

**Tap targets:** anything tappable must clear ~44px tall. Pick-row at `padding: 10px 14px` + ~28px text content lands at ~48px - comfortable. Don't trim padding below `8px` on tappable elements without re-checking.

---

## Standard component values

All admin templates use the same values for these classes. If you copy a template to start a new admin page, these are already correct.

### Layout

```css
.header-row             { margin: 20px 0 6px 0; }
.heading                { /* viewport-scaled */ }
.nav-buttons            { gap: 5px; }
.admin-nav-btn          { margin: 2px; padding: 0 12px; }
```

### Content boxes

```css
.admin-box              { padding: 10px 14px; margin-bottom: 8px; }
.warning-box            { padding: 10px 14px; margin-bottom: 8px; }
.mismatch-card          { padding: 10px 14px; margin-bottom: 8px; }
.guidance-hint          { padding: 8px 14px;  margin-bottom: 10px; }
.confirm-box            { padding: 10px 14px; margin-bottom: 10px; }
.pilot-context-table    { padding: 8px 14px;  margin-bottom: 10px; }
.result-box             { padding: 12px 16px; margin: 10px 0; }
```

### Wizard chrome

```css
.wizard-strip           { padding: 6px 14px;  margin-bottom: 8px; }
.step-title             { margin: 8px 0 6px 0; }
.step-actions           { gap: 10px; margin-top: 8px; }
```

### Pickable rows

```css
.pick-row               { padding: 10px 14px; margin-bottom: 6px; }
.round-pick-row         { padding: 12px 16px; margin-bottom: 6px; }
.manual-pick-row        { padding: 8px 14px;  margin-bottom: 6px; }
.pilot-context-row      { padding: 4px 0; }
.pilot-row-confirm      { padding: 4px 0; }
.pick-row-caption       { margin-top: 3px; }
```

### Step 1 (resolver Op-choice)

```css
.step1-buttons          { gap: 10px; margin-top: 6px; }
.step1-buttons .btn-large { min-height: 60px; padding: 6px 12px; }
.step1-empty            { padding: 6px 12px; margin-top: 6px; }
.step1-caption          { margin-top: 10px; }
```

### Standalone elements

```css
.force-edit-btn         { margin: 12px auto 6px auto; padding: 12px; }
.all-clear              { padding: 16px 10px; }
.all-clear i            { margin-bottom: 6px; }
.loading-spinner        { padding: 20px; }
.tool-icon              { margin-bottom: 6px; }
.tool-title             { margin-bottom: 4px; }
```

---

## Disabled-row contrast

Outdoor 5" screens need stronger disabled-state cues than office monitors. The following values are tuned for that:

```css
.pick-row.disabled,
.round-pick-row.disabled {
  background: #e0e0e0;       /* was #f5f5f5 - too close to white */
  color:      #757575;       /* was #9e9e9e - too pale */
  border-color: #bdbdbd;     /* was #e0e0e0 */
  cursor: not-allowed;
}
.pick-row.disabled .pick-row-caption,
.round-pick-row.disabled .pick-row-caption {
  color: #616161;            /* was #757575 - captions need to stay legible */
}
```

For disabled buttons, prefer Materialize's built-in `.btn-large.disabled` / `.btn.disabled` - the contrast is already strong enough.

---

## Color conventions

Type buttons (KNOWN / UNKNOWN / FREESTYLE) use a single shared style - no per-type color. The convention comes from `judge_start_select.html .type-btn`:

```css
border: 2px solid #424242;
background: #fafafa;
color: #212121;
```

Differentiation is by **icon** (`visibility` / `visibility_off` / `diversity_2`), not color.

Operation buttons (Fix Missing Sequence / Move) use Materialize colors per operation: `blue darken-1` for Fix, `green darken-1` for Move. Cancel buttons are `grey darken-1`. Result/confirm primary actions are `orange darken-1` or operation-color.

Error/blocker modals: red icon (`red-text`) in heading, modal-close uses `btn-flat` styling, primary action stays neutral or operation-color. No primary/secondary hierarchy when the modal is "we noticed something, you decide" - both choices get equal styling.

---

## Toast notifications

Toast notifications are shared device feedback, not page-specific decoration. Any template that creates `M.toast(...)` should include `judge/src/main/resources/static/device-toast.css` and should keep toast behavior consistent with the 800x480 field display standard.

Use this standard for new toasts:

- Position: bottom center through the shared `#toast-container` styling.
- Duration: `displayLength: 5000`.
- Visual treatment: white panel, strong neutral border, large bold centered text, and clean wrapping.
- Icons/decorations: none.
- Message behavior: keep text short and actionable; do not introduce new toast triggers in active judging unless the workflow truly needs interruption.
- Classes such as `green`, `red`, `orange`, `blue`, and `toastMessage` may remain on calls, but the shared CSS controls the readable panel treatment.

Current shared values in `device-toast.css`:

```css
#toast-container {
  bottom: 24px;
  left: 50%;
  width: 700px;
  max-width: calc(100vw - 48px);
}

#toast-container .toast {
  min-height: 72px;
  padding: 16px 24px;
  font-size: 26px;
  font-weight: 700;
  background: #fff;
  border: 5px solid #424242;
  border-radius: 8px;
}
```

---

## What goes where

- **Shared component CSS** - modal framing belongs in `judge/src/main/resources/static/device-modal.css`; include it after Materialize CSS. Toast styling remains in `device-toast.css`.
- **CSS in `<style>` block** - page-specific structure and documented exceptions. Do not duplicate shared modal rules here.
- **Inline `style="..."`** - only for one-off dynamic values (e.g., a hidden div that JS toggles). Avoid for styling that could be a class.
- **JS-rendered HTML** - when JS appends DOM, prefer adding a class and styling in CSS. Only inline-style for truly per-instance values.

---

## Class ordering

`ContestClasses.ORDER`:

```java
BASIC, SPORTSMAN, INTERMEDIATE, ADVANCED, UNLIMITED, INVITATIONAL
```

FREESTYLE, null, and any unknown classes sort last through `ContestClasses.orderIndex(String)`. As of this snapshot, `ContestClasses` is the shared source used by `RootController.java`, `ScoreResolverService.java`, and `SequenceValidationService.java`. This is a manual fallback until Score returns ordered classes - when that lands, `ContestClasses.ORDER` should be replaced with a Score-driven source so current consumers pick up the new order through the same utility.

---

## Future changes

When adding a new admin page or component:
1. Use only the four text tiers and the spacing tiers above.
2. If a value doesn't fit existing tiers, propose adding a new tier here rather than introducing a one-off.
3. Match the disabled-row contrast values for any new pickable element.
4. Keep tap targets >= 44px.

When the Score-driven class ordering lands:
1. Replace the hardcoded `ContestClasses.ORDER` source with a Score-driven source, preferably behind the existing `ContestClasses` utility boundary.
2. Update this doc's "Class ordering" section.

---

# Judge / RBR field UI design reference

This section covers judge-facing and round-by-round screens such as `/newround`, `/pilot-list-round`, `judge_start_select.html`, `/judge`, and `/round-complete`.

These screens are used in the field on Raspberry Pi 4B judge boxes with 5" touch displays. They should be fast to read, hard to mis-tap, and obvious under outdoor conditions. Admin pages keep the stricter admin scale above; judge-facing screens may use their existing conventions when they better fit the scoring workflow.

## Field UI principles

- Prefer large touch buttons over dropdowns for normal judging and RBR start flows.
- Keep one primary action per state. If the judge is in RBR, the modal should start the locked active round rather than offering KNO/UNK/FREE choices.
- Keep repair, override, and configuration actions inside admin pages. Judge screens can link to admin, but should not expose repair controls directly.
- Field utility actions such as sync pilots and push scores must remain available from judge/RBR field screens, including `/newround`.
- Use short labels that can be read at a glance on a 5" display.
- Preserve tap targets at 44px minimum; primary field actions should normally be larger.
- Avoid dense explanatory text on judge screens. Use state, labels, and confirmation screens instead.

## RBR start flow

`/newround` is the normal RBR start point when no round is active.

- Use large button selection for Precision/Freestyle, class, and type.
- Stack Precision/Freestyle choices vertically.
- Stack Known/Unknown type choices vertically.
- Class choices may use two columns so the full class list stays visible.
- Use neutral admin-like button styling for selection choices; do not assign colors by family, class, or type.
- Selection screens should use explicit action instructions such as "Tap to Select Class" at heading scale.
- Two-choice selection screens should have extra vertical padding/gap and larger touch areas for field use.
- Ordinary wrong-tap recovery belongs in the page flow with visible Back/Reselect controls, not in the floating utility menu.
- Confirm the selected round before creating it.
- Do not create the round until final confirmation.
- Do not expose an alternate Fly/Switch/Close path outside the RBR flow.
- Show only choices that are available from loaded pilot and schedule data.

## RBR active round flow

`/pilot-list-round` is the active-round work queue.

- Show only pilots in the locked active cohort.
- Hide pilots once their active round is scored.
- If pilots are behind the active round, stop judging and show an admin-actionable error with the pilot names.
- Auto-close the round when the active cohort is complete.

## Type buttons and scoring modal

Global mode may continue using the KNO/UNK/FREE type-button modal.

RBR mode should not ask the judge to choose a type. The active round already defines class, type, and round number.

Type buttons follow the existing convention from `judge_start_select.html`:

```css
border: 2px solid #424242;
background: #fafafa;
color: #212121;
```

Differentiate type buttons by icon and label, not by per-type color.

## Completion and recovery states

- Round completion should be a clear success state with an obvious next action.
- Missing optional audio must fail quietly.
- Wrong unflown active round recovery belongs in admin.
- Scored wrong-round cases must not be silently cleared by a lightweight UI action.

# Shared device modal standard

Applies to admin and judge-facing dialogs on the Raspberry Pi 4B / Waveshare
21973 display at **800x480**. Use the existing Materialize modal lifecycle and
`judge/src/main/resources/static/device-modal.css`. This standard governs
presentation; it does not authorize changes to judging or confirmation behavior.

## Shell and scrolling

| Property | Required value / behavior |
|---|---|
| Width | `92%`, maximum `740px`; 736px outer width at an 800px viewport |
| Position | Horizontally centered; `top: 6%` (28.8px at 480px height) |
| Height | Content-driven, maximum `90%` (432px at 480px height); no fixed empty height |
| Box sizing | `border-box`, including the border within the width |
| Corners | `20px` radius |
| Ordinary border | Crisp `1px solid #424242` |
| Content padding | `16px 20px` |
| Overflow | Admin content may scroll vertically with `min-height: 0`; field content must fit without scrolling (Score Summary/review is the exception) |
| Open layout | Flex column; content can shrink while footer remains reachable |
| Footer | Automatic height, `12px 20px` padding, right-aligned actions, `10px` gap; wrap when necessary |

Use direct `.modal-content` and `.modal-footer` children when there is a footer.
Do not inherit a fixed 56px footer or introduce page-wide `.modal` overrides
that defeat this shell. Only admin pages and Score Summary/review may scroll.
All other pages and dialogs must fit at 800x480, with every action visible.
Pilot start must fit in both global and RBR modes, including battery warnings.
Use compact 36px single-line battery warnings, a 280px wind image inside its
existing tappable selector, and reduced vertical gaps. Preserve judging-control
sizes and the shared outer margins. Do not hide overflow to conceal controls.

## Typography and controls

| Element | Required treatment |
|---|---|
| Ordinary heading | `1.9rem`, weight `500`, line-height `1.2`, margin `0 0 12px` |
| Primary paragraphs and lists | `1.75rem`, line-height `1.3`, margin `10px 0` |
| Secondary text | `1.5rem`, using `device-modal__secondary` |
| Alignment | Ordinary headings and content left aligned |
| Heading icons | Existing meaningful icons only; `1.5rem`, vertically aligned, `8px` right margin |
| Lists | `24px` left padding, disc markers, `6px` bottom margin per item |
| List labels | `1.75rem`, weight `500` |
| Footer buttons/links | Minimum `44px` high, automatic height, `10px 16px` padding, no external margin |
| Footer button text | `1.5rem`, line-height `1.2`, authored case (`text-transform: none`) |

Use `device-modal device-modal--standard` for ordinary confirmations and
warnings. The shared heading selectors cover `h1`, `h5` and `h4.heading`;
the existing unsaved-score `h3` explicitly matches the same heading values.
Preserve labels, action order, IDs, JavaScript hooks and disabled/busy states.
Do not force equal button widths or change the meaning of primary/secondary
actions. Pre-sync mismatch choices deliberately keep equal visual weight.

## Warnings and specific modal treatments

| Modal / family | Treatment |
|---|---|
| Admin Access on pilot lists, New Round and error page | Shared ordinary shell. The caution about affecting competition setup is red; preserve existing warning icons where present. |
| Pre-sync mismatch warning | Shared shell, scrolling list, equally styled Get admin / Sync anyway actions. |
| Clear Active Round | Shared shell; retain red caution and orange Clear Round action. |
| Load NEW Event / Change Device Identity | Shared shell; retain red warning icons and destructive-action colors. |
| Cannot Change Sequence Mode | Shared shell; retain red warning icon, list labels and reachable Go to Score Fixes action. |
| Install Update | Shared shell; retain blue update icon/action, orange caution and version comparison. |
| Pilot start, global and RBR | `device-modal device-modal--pilot`: shared neutral shell; retain large pilot identity, wind control, type choices or locked RBR action, and task-specific typography. |
| Class Select | Add `device-modal--selection` to standard classes. Retain yellow surface and two-column grid; `5px` cell padding, full-width cell buttons, centered Global List action and minimum 44px controls. |
| Unsaved Scores and Home completion | Shared shell, text scale and footer; intentional `8px solid #b71c1c` border instead of the neutral border. Retain the large `5rem` warning icon, line-height `1`, `6px` bottom margin, and red title. |
| Device-time warning | Shared ordinary shell for warning, progress, failure and success. Preserve approved wording, busy controls and recovery behavior. |
| Score Summary | Entirely excluded. Preserve its existing geometry, content, score colors, actions and behavior; do not add shared modal classes. |

Do not replace intentional colored borders with a neutral border. Nested battery
warnings retain their orange/red borders. CLOCK/INFO popovers, toasts and inline
workflow panels are not modals and are not restyled by this standard.

## Confirmation states and timing

- Unsaved Scores, its second confirmation, Finish Round and recovery states use
  the **same width, top position, padding, text scale and footer layout**. The
  `completion-guard` state must not switch to a different geometry. Text length
  can change the content-driven height; it must not change the style.
- Retain the large warning icon and red border throughout those guard states.
- Preserve existing outside-click/Escape dismissal settings. Clock warning and
  judging guards retain their existing explicit-action requirements.
- Preserve callback ordering, navigation locks and duplicate-click protections.
  A visual cleanup must not add or remove confirmation steps.
- Keep Materialize's existing `250ms` opening/closing animations and `0.5`
  backdrop opacity. Do not add new timing values.
- Clock success retains Continue and the approved `5000ms` automatic return.
  Other confirmations do not gain automatic dismissal.
- The shared CLOCK/INFO banner stays hidden while a modal is open and through
  its closing animation, then returns through the existing banner observer.

## Verification requirements

At 800x480, verify initial, confirmation, busy, failure and recovery states
where applicable. Check long lists and wrapped labels, reachable actions,
visible warning colors, scrolling, banner restoration and browser errors.
Check shared fragments on each parent page, including global/RBR pilot lists
and New Round. Preserve Summary without style leakage from shared selectors.

Local browser rendering is developer validation, not physical Pi/touch proof.
Record device checks and any remaining limits separately. Keep local fixtures
and screenshots under ignored `.idea/testfiles/`; do not add test artifacts to Git.

# Shared banner standard

The shared source is `judge/src/main/resources/templates/banner.html`.
`banner::line_judge` supplies the identity text; `banner::status` supplies
CLOCK, INFO and their popovers. Keep styling consistent across yellow field
pages and admin pages without adding an opaque banner background.

## Identity and control geometry

| Element | Required treatment |
|---|---|
| Identity | `Line 1 Judge 1` format; normal spaces, no pipe separator or extra spacing between Line and Judge |
| Identity placement | Absolute right alignment, `right: 0`, `margin-right: 190px`, z-index `2` |
| Controls | Absolute right alignment at `10px`; flex row, vertically centered, `14px` gap, z-index `1000` |
| CLOCK and INFO boxes | Transparent fill, `2px solid currentColor` outline, `3px` corners; outline applies in every clock state and to INFO |
| Total control height | **30px including borders**, using `box-sizing: border-box` |
| Control text | Inherit surrounding font, weight `600`, line-height `26px`, horizontal padding `6px` |
| INFO color | Black text and outline |
| Admin separation | `.header-row { margin: 20px 0 6px 0; }`, an extra 10px compared with the old header spacing |

The 30px banner controls are an explicitly approved compact exception to the
usual 44px touch-target minimum. Do not enlarge the banner to accommodate its
border. The admin spacing applies to the landing, Event Admin, Device Settings,
Score Fixes and its Resolve/Zero Fill/Swap pages. Keep the existing page-specific
fragment inclusion: Device Settings and New Event include status controls
without the Line/Judge identity fragment.

## Clock state presentation

| State | Text and outline | Explanation |
|---|---|---|
| Trusted | Green `#24752a` | Device time is trusted. |
| Untrusted | Red `#c62828` | Device time cannot be trusted. |
| No supported clock | Gray `#bdbdbd`, CLOCK text struck through | No supported device clock is installed. |

The outlined word remains the control in all states. Preserve the existing
server-provided state and trust fields; visual changes do not add clock polling
or new status requests. INFO continues to show its existing information and
refresh behavior. Popover presentation and dismissal are specified below.

## Visibility during modals

- Hide Line/Judge, CLOCK/INFO controls and both popovers whenever any page modal
  is open or still displaying its closing animation. This includes Summary and
  warning/confirmation dialogs, without changing those dialogs themselves.
- Use the existing `banner-modal-open` class and `visibility: hidden` rules;
  the observer tracks modal class/style changes. Do not replace modal callbacks.
- Restore the banner after all modals finish closing. Hiding is not equivalent
  to dismissing a popover or changing INFO refresh behavior. A panel still open
  may become visible again; CLOCK's existing five-second timeout continues.
- Opening a popover does not hide the banner controls.

Validate banner placement on both yellow field and admin backgrounds at
800x480, including modal opening/closing and the admin navigation buttons.

# Banner popover standard

Approved 2026-11-07. CLOCK and INFO share the following treatment in
`templates/banner.html`; these are popovers, not modal dialogs.

| Property | Value |
|---|---|
| Position | Absolute, `right: 10px`, `top: 45px`, below banner controls |
| Width | Content-sized (`max-content`), maximum `calc(100vw - 20px)`; no fixed wide panel |
| Surface | White, text `#212121`, crisp `1px solid #424242` border |
| Corners / padding | `8px` radius / `12px` padding |
| Text | `1.5rem`, line-height `1.4` |
| INFO rows | Labels left; names, values and status icons grouped right; minimum `14px` gap after labels |
| INFO heading | App name/version right aligned, `18px` left reserve |
| Countdown | Small raised number at top-left: `.8rem`, line-height `1`, top `5px`, left `7px` |

Opening CLOCK closes INFO; opening INFO closes CLOCK. Tapping the same control
or its open panel closes it. CLOCK closes automatically after `5000ms` from
opening; closing it cancels that timer, so reopening gets a fresh five seconds.
INFO has no new automatic dismissal. Its existing refresh tiers, countdown,
request timing and error handling remain unchanged. Closing INFO uses its
existing stop-updates path. Pilot-list navigation closes either popover.
Existing modal visibility handling continues to hide banner popovers.

A future USB status popover should use this visual treatment, mutual exclusion
and the five-second auto-close rule. This is a design requirement only; no USB
control or implementation is included in this work.

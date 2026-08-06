# AeroJudge Device App UI Design Reference

Snapshot date: 2026-08-06 06:59:53 -04:00

Code snapshot: `74bad1609fd97c69b836615712ca99abe19ac322`

Shared text-size and spacing scale for all admin pages. Locked in 2026-05-01 during the post-resolver-overhaul cleanup. Apply these values to any new admin page so the project stays consistent.

**Scope:** admin templates only - `admin.html` (landing menu), `adminComp.html` (Event Admin), `adminDevice.html` (Device Settings), `adminScores.html` (Score Fixes), `adminScoresResolve.html`, `adminScoresZeroFill.html`, `adminScoresSwap.html`. Judge screens (`/judge`, `pilot-list-*.html`, `judge_start_select.html`) are not part of this scale and have their own conventions.

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
| Page edges | `10px` top, `6px` bottom | header-row margins (was `20px / 8px` - pre-cleanup) |

**Box padding pattern:** `padding: <vertical> <horizontal>` where vertical <= horizontal. Standard for content boxes is `10px 14px`.

**Tap targets:** anything tappable must clear ~44px tall. Pick-row at `padding: 10px 14px` + ~28px text content lands at ~48px - comfortable. Don't trim padding below `8px` on tappable elements without re-checking.

---

## Standard component values

All admin templates use the same values for these classes. If you copy a template to start a new admin page, these are already correct.

### Layout

```css
.header-row             { margin: 10px 0 6px 0; }
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

- **CSS in `<style>` block** - page-level structural and reusable styles. Default home for everything in this doc.
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

# Readable Toast Notifications - Spec Summary

**Completion Date** - May 29, 2026  
**Branch/Commit** - main, `fee17cb` (`Improve toast notification readability`), released as `v5.1.11`  
**Status** - Delivered

---

## Overview

Toast notifications across the device UI are now easier to read on the Raspberry Pi 4B Waveshare 21973 800x480 display. This gives judges and administrators a consistent status message experience without changing scoring or sync workflows.

---

## The Problem

Existing Materialize toast notifications were small, visually inconsistent, and easy to miss during device use. Some messages appeared for only two or three seconds, while others stayed longer, and only a subset used the older enlarged `toastMessage` class. On the target field display, that made important success and error feedback harder to scan quickly.

---

## The Solution

A shared device toast stylesheet now gives all toast messages the same readable presentation: bottom-centered placement, a white panel, strong neutral border, large bold text, and clean wrapping. Existing toast calls were updated to use a consistent five-second display length, including the rare judge-page score-save failure path.

The change intentionally avoided adding icons, new message categories, workflow interruptions, or server-side behavior changes.

---

## What Changed

- **Shared CSS** - Added a reusable device toast stylesheet for the 800x480 target display.
- **Template coverage** - Included the shared stylesheet on pages that can show `M.toast(...)` notifications.
- **Toast timing** - Normalized explicit toast durations to `displayLength: 5000`.
- **Visual consistency** - Kept existing toast message text and classes while overriding the visible panel treatment for readability.

---

## Technical Reference

**Primary Files Modified:**
- `judge/src/main/resources/static/device-toast.css`
- `judge/src/main/resources/templates/judge.html`
- `judge/src/main/resources/templates/adminDevice.html`
- `judge/src/main/resources/templates/newround.html`
- `judge/src/main/resources/templates/pilot-list-round.html`

**Key Implementation Details:**
- `device-toast.css` positions the Materialize toast container at the bottom center, constrains it to a 700px width with viewport margins, and styles each toast as a readable white status panel.
- Toast panels use 26px bold centered text, 72px minimum height, 16px/24px padding, a 5px neutral border, and wrapping safeguards for long messages.
- Existing semantic classes such as `green`, `red`, `orange`, `blue`, and `toastMessage` may remain on calls, but the shared CSS controls the device-readable panel treatment.
- New toast notifications should follow this UX standard: include the shared stylesheet on any toast-using page, use `displayLength: 5000`, avoid icons or decorative elements, and let the shared CSS handle toast layout and readability.

**Testing Approach:**

Verification covered all templates using `M.toast(...)`, confirmed explicit display lengths were normalized to 5000ms, confirmed toast-using templates include the shared stylesheet, and reviewed representative success, error, warning, and default/info messages at the 800x480 device target.

---

## Rollback

Rollback is limited to removing the shared toast stylesheet and its template includes, then restoring the previous toast display lengths if needed. No Java behavior, API endpoints, sync behavior, message text, Materialize library files, or persisted data were changed.

---

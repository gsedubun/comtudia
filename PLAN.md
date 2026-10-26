# comtudia — Implementation Plan

> Status: implemented with Vala, GTK4, and libadwaita. See
> [README.md](README.md) for usage and screenshots.
> `comtudia` comes from the Spanish phrase “comienza tu día” — start your day.

## Identity

| Item | Value |
| --- | --- |
| Display name / window title | `comtudia` |
| GApplication / desktop ID | `com.comtudia.app` |
| Language / toolkit | Vala + GTK4 + libadwaita |
| Binary | `comtudia` |
| Config path | `$XDG_CONFIG_HOME/comtudia/plan.json` |
| Legacy config | `$XDG_CONFIG_HOME/eatime/plan.json` |
| Desktop file | `com.comtudia.app.desktop` |
| Icon name | `com.comtudia.app` |
| Agent instructions | `AGENTS.md` |

## Goal

A Linux-only native desktop app that tracks a recurring daily eating plan and
counts down to the next eating time.

The user configures:

- total eating time (e.g. 6h)
- number of eating slots (e.g. 3)
- first meal start time (e.g. 12:00)
- optional gaps between meals (default 0)

The app shows the current phase (fasting/eating), a live countdown, the
relevant day's schedule, a 24-hour timeline, tray status, and desktop
notifications. It also keeps the legacy `eatime` config migration.

## Schedule semantics

The daily cycle is always 24 hours.

- `EatingMinutes` is the total time spent eating.
- Meals are equally sized: `EatingMinutes / Meals`, with leftover minutes
  distributed to the earliest meals.
- `GapMinutes` is optional fasting time between meals.
- Total fasting = `24h - EatingMinutes`; it includes meal gaps.
- Long fast = `24h - (EatingMinutes + (Meals-1) * GapMinutes)`.
- Validation requires the meal spread to be at most 23h, leaving at least 1h
  outside the meals (also prevents DST overlaps).

Example: 18h fast / 6h eat, 3 meals, start 12:00.

| Setting | Value |
|---|---|
| Eating total | 6h |
| Meals | 3 × 2h |
| Gap | 0 |
| Meal times | 12:00–14:00, 14:00–16:00, 16:00–18:00 |
| Fasting | 18:00–12:00 |

With a 1h gap:

- Meals: 12:00–14:00, 15:00–17:00, 18:00–20:00
- Total eating: 6h
- Total fasting: 18h = 16h outside the meal spread + 2 × 1h gaps

Meal times are anchored to local wall-clock time and preserve their durations
across DST, so later meals can shift slightly on a transition day. Schedule
lookup scans yesterday/today/tomorrow to handle windows that cross midnight.

## Architecture

```text
comtudia/
├── meson.build
├── Makefile
├── README.md
├── PLAN.md
├── AGENTS.md
├── VALA_GTK4_PLAN.md
├── LICENSE
├── src/
│   ├── main.vala
│   ├── application.vala
│   ├── window.vala
│   ├── dashboard.vala
│   ├── settings.vala
│   ├── plan.vala
│   ├── schedule.vala
│   ├── store.vala
│   ├── format.vala
│   ├── notifier.vala
│   ├── tray-item.vala
│   ├── ring.vala
│   ├── timeline.vala
│   └── colors.vala
├── data/
│   ├── com.comtudia.app.desktop
│   ├── com.comtudia.app.metainfo.xml
│   ├── com.comtudia.app.svg
│   ├── com.comtudia.app.gresource.xml
│   └── style.css
├── tests/
│   ├── plan-test.vala
│   ├── schedule-test.vala
│   ├── store-test.vala
│   ├── format-test.vala
│   ├── notify-test.vala
│   ├── settings-test.vala
│   ├── timeline-test.vala
│   └── tray-test.vala
└── docs/
    ├── screenshot.png
    └── settings.png
```

`plan`, `schedule`, `store`, `format`, and `notifier` contain no GTK code so
they can be tested without a display. `dashboard`, `settings`, `ring`,
`timeline`, `tray-item`, `window`, and `application` form the GTK layer.

## Persistence

- Location: `$XDG_CONFIG_HOME/comtudia/plan.json`
- JSON via json-glib, preserving schema version 1.
- Atomic writes via `Gio.File.replace_contents`.
- File mode 0600, config directory 0700.
- Missing file loads defaults.
- Corrupt/invalid file is reported and is not deleted; the app uses defaults
  and shows a warning. It never silently falls back to the legacy plan.
- On load, if the current plan is missing and the legacy
  `$XDG_CONFIG_HOME/eatime/plan.json` exists, the legacy plan is loaded and
  saved to the new path. The legacy file is left as a backup.

## UI

### Dashboard

- Header with the app name and a "Plan" button.
- Status pill: `FASTING` or `EATING NOW`.
- Large Cairo progress ring with countdown in the centre.
- Context line: next meal/window time or current meal number.
- Two summary cards: next eating / eating ends, and current plan summary.
- 24-hour Cairo timeline with meal blocks and a live "now" marker.
- Schedule list for the relevant cycle day, highlighting current/next meal.
- Warning banner when saved settings could not be loaded.

### Settings

- Presets: 16:8, 18:6, 20:4, custom.
- Grouped eating-time input: hours + minutes with a live total readout.
- Meal count, gap minutes, and first meal start.
- Toggles: desktop notifications, keep running in tray when window closes.
- Live preview of meal times and fasting breakdown.
- Inline validation, reset, cancel, save.
- "Send test" notification action.
- Config file path shown.
- Tray toggle is disabled when no StatusNotifier watcher is available.

### Tray and notifications

- Tray icon with menu: status line, "Show comtudia", "Quit".
- Tray click/activate shows the window.
- Status label refreshes only when the displayed value changes.
- Notifications fire on phase changes (eating ↔ fasting), not every contiguous
  meal slot.
- Contiguous meals are merged into one eating phase, avoiding notification
  spam.
- Startup primes the notifier so it does not fire immediately; a plan change
  re-primes it.
- Notifications use `GApplication.send_notification` with `GNotification`.
- The tray is implemented directly with `org.kde.StatusNotifierItem` and
  `com.canonical.dbusmenu`.
- Tray availability depends on a StatusNotifier watcher; GNOME needs the
  AppIndicator extension. The app remains usable without the tray.

## Testing

- Plan validation and defaults.
- Meal splitting, leftover-minute distribution, gaps, and span limits.
- Schedule state at exact boundaries.
- Midnight-crossing windows.
- DST transitions (America/New_York) without overlapping meals.
- Store round-trip, missing/corrupt files, atomic writes, permissions, and
  legacy migration.
- Formatting of countdowns, durations, day labels, ranges.
- Notification phase-change behavior, including contiguous meals and gaps.
- Timeline segment clipping.
- Tray status text.
- Manual: run under GNOME Wayland, verify the tray icon/menu with the
  AppIndicator extension, close-to-tray and Quit, a real notification at a meal
  boundary, the test-notification action, and screenshots.
- `meson test -C build --print-errorlogs`, `make check`, and a manual run.

## Build

System dependencies (Debian/Ubuntu):

```bash
sudo apt install -y valac meson ninja-build \
  libgtk-4-dev libadwaita-1-dev libjson-glib-dev \
  libglib2.0-dev desktop-file-utils hicolor-icon-theme \
  gnome-shell-extension-appindicator
```

Commands:

```bash
make build     # meson setup + compile
make run       # run ./build/comtudia
make test      # meson test
make check     # test + desktop/metainfo validation
make install   # meson install
make clean     # remove build directory
```

## Implementation order

1. Meson project and GResource scaffolding.
2. `plan`, `schedule`, `format`, `store`, `notifier` + tests.
3. GTK4/libadwaita dashboard, settings, ring, and timeline.
4. StatusNotifierItem/DBusMenu tray service.
5. `GNotification` notifications and test action.
6. Desktop file, metainfo, icon, CSS, and install rules.
7. README, PLAN, AGENTS, Makefile, and `.gitignore` updates.
8. Full verification, screenshots, and removal of the Go/Fyne implementation.

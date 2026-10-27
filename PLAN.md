# comtudia — Implementation Plan

> Status: implemented. See [README.md](README.md) for usage and screenshots.
> `comtudia` comes from the Spanish phrase “comienza tu día” — start your day.

## Platforms

| Platform | Location | Notes |
| --- | --- | --- |
| Linux desktop | Go + Fyne, this document | `make run` |
| Android | `android/` (Kotlin + Compose) | Ports `internal/plan`; parity-tested by `internal/plan/golden_test.go` |
| Xiaomi Smart Band 9 Pro | `band/` (Vela JS Quick App) | Sideloaded; see `docs/band.md` |

All platforms share the same 24-hour cycle and the same `plan.json` schema.
See [docs/android.md](docs/android.md) and [docs/band.md](docs/band.md).

## Identity

| Item | Value |
| --- | --- |
| Display name / window title | `comtudia` |
| Fyne app ID / package name | `com.comtudia.app` |
| Go module | `com.comtudia.app` |
| Binary | `bin/comtudia` |
| Config path | `$XDG_CONFIG_HOME/comtudia/plan.json` |
| Legacy config | `$XDG_CONFIG_HOME/eatime/plan.json` |
| Agent instructions | `AGENTS.md` |

## Goal

A Linux desktop app (Go + Fyne) that tracks a recurring daily eating plan and
counts down to the next eating time.

The user configures:

- total eating time (e.g. 6h)
- number of eating slots (e.g. 3)
- first meal start time (e.g. 12:00)
- optional gaps between meals (default 0)

The app shows the current phase (fasting/eating), a live countdown, the
relevant day's schedule, a 24-hour timeline, tray status, and desktop
notifications.

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

```
comtudia/
├── main.go
├── go.mod                 # module com.comtudia.app
├── Makefile
├── README.md
├── PLAN.md
├── AGENTS.md
├── FyneApp.toml
├── assets/
│   ├── icon.png
│   └── tray.png
└── internal/
    ├── plan/              # model, validation, schedule calculation (pure Go)
    ├── store/             # atomic JSON persistence + legacy migration
    ├── notify/            # phase-change notification logic
    ├── format/            # countdown/time formatting
    └── ui/                # dashboard, settings, ring, timeline, tray
```

The source directory is currently `/home/del/source/eatime`; that folder name
is independent of the app/module name.

## Persistence

- Location: `os.UserConfigDir()/comtudia/plan.json`
- Atomic writes: temp file in the same directory, then rename.
- File mode 0600, config directory 0700.
- Missing file loads defaults.
- Corrupt/invalid file falls back to defaults, shows a warning, and is not
  deleted.
- Versioned JSON schema.
- On load, if the current plan is missing and the legacy
  `os.UserConfigDir()/eatime/plan.json` exists, the legacy plan is loaded and
  saved to the new path. The legacy file is left as a backup.

## UI

### Dashboard

- Header with the app name and a "Plan" settings button.
- Status pill: `FASTING` or `EATING NOW`.
- Large progress ring with countdown in the centre.
- Context line: next meal/window time or current meal number.
- Two summary cards: next eating / eating ends, and current plan summary.
- 24-hour timeline with meal blocks and a live "now" marker.
- Schedule list for the relevant cycle day, highlighting current/next meal.
- Warning banner when saved settings could not be loaded.

### Settings

- Presets: 16:8, 18:6, 20:4, custom.
- Grouped eating-time input: hours + minutes with a live total readout.
- Meal count, gap minutes, and first meal start.
- Toggles: desktop notifications, keep running in tray when window closes.
- Live preview of meal times and fasting breakdown.
- Inline validation, reset, cancel, save.
- "Send test notification" button.
- Config file path shown.

### Tray and notifications

- Tray icon with menu: status line, "Show comtudia", "Quit".
- Tray click shows the window.
- Status label refreshes only when the displayed value changes.
- Notifications fire on phase changes (eating ↔ fasting), not every contiguous
  meal slot.
- Contiguous meals are merged into one eating phase, avoiding notification
  spam.
- Startup primes the notifier so it does not fire immediately.
- Notifications are best-effort via Fyne; Linux requires a notification daemon.
- Tray availability depends on the desktop environment; GNOME may need the
  AppIndicator extension. The app remains usable without the tray.

## Testing

- Plan validation and defaults.
- Meal splitting, leftover-minute distribution, gaps, and span limits.
- Schedule state at exact boundaries.
- Midnight-crossing windows.
- DST transitions (America/New_York) without overlapping meals.
- Store round-trip, missing/corrupt files, atomic writes, and legacy migration.
- Formatting of countdowns, durations, day labels, ranges.
- Notification phase-change behavior, including contiguous meals and gaps.
- Timeline segment clipping.
- Tray menu/status behavior.
- Fyne smoke tests using `fyne.io/fyne/v2/test`.
- `gofmt`, `go vet ./...`, `go test ./...`, `go test -race ./...`, manual run
  and screenshots.

## Build

System dependencies (Debian/Ubuntu):

```bash
sudo apt install golang-go gcc pkg-config libgl1-mesa-dev xorg-dev \
  libwayland-dev libxkbcommon-dev
```

Commands:

```bash
make run     # run the app
make test    # go test ./...
make vet     # go vet ./...
make build   # build bin/comtudia
make check   # fmt + vet + test
```

## Implementation order

1. Module init with `com.comtudia.app` and Fyne v2.8.2.
2. `internal/plan` + tests.
3. `internal/format`, `internal/store`, `internal/notify` + tests.
4. Dashboard and live countdown.
5. Settings and persistence wiring.
6. Timeline, icon, tray, notifications.
7. README, AGENTS.md, Makefile, Fyne metadata.
8. Full verification, config migration test, and manual run.

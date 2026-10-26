# AGENTS.md

Instructions for coding agents working on **comtudia** (from the Spanish
“comienza tu día” — start your day).

## Project identity

- Display name: `comtudia`
- Go module: `com.comtudia.app`
- Fyne app ID / package name: `com.comtudia.app`
- Binary: `bin/comtudia`
- Config: `$XDG_CONFIG_HOME/comtudia/plan.json`
- Legacy config: `$XDG_CONFIG_HOME/eatime/plan.json`

Read `README.md` and `PLAN.md` before making changes. `prompt.md` is the
original brief and should not be edited.

## Commands

```bash
make run                    # run the app
make build                  # build bin/comtudia
make check                  # gofmt + vet + test
go test ./...
go test -race ./...
go vet ./...
gofmt -l .
```

Debian/Ubuntu build dependencies:

```bash
sudo apt install golang-go gcc pkg-config libgl1-mesa-dev xorg-dev \
  libwayland-dev libxkbcommon-dev
```

`bin/` is ignored by git.

## Architecture

- `main.go` — entry point, embedded app/tray icons.
- `internal/plan/` — pure plan model, validation, and schedule computation.
- `internal/store/` — atomic JSON persistence and legacy config migration.
- `internal/notify/` — phase-change notification messages.
- `internal/format/` — countdown/time formatting.
- `internal/ui/` — Fyne dashboard, settings, custom widgets, tray.

Keep schedule and persistence logic out of the UI package when possible so it
remains testable without a display.

## Schedule invariants

- The cycle is always 24 hours.
- `eating_minutes` is the total time spent eating, split evenly across meals;
  leftover minutes go to the earliest meals.
- Gaps between meals count as fasting.
- Total fasting = `24h - eating_minutes`.
- The full meal spread must be at most 23 hours.
- Meal boundaries are anchored to local wall-clock time, and durations are
  preserved across DST transitions. Later meals may shift on a transition day.
- Schedule lookup must handle windows crossing midnight by scanning yesterday,
  today, and tomorrow.
- Do not change the daily-cycle or gap semantics without updating `PLAN.md`,
  tests, and the settings preview.

## UI rules

- Never update Fyne widgets directly from a background goroutine. Use
  `fyne.Do` / `fyne.DoAndWait`.
- The dashboard is updated by a one-second ticker; the countdown is computed
  from `time.Now()` rather than decremented, to avoid drift.
- Notifications fire only when a contiguous phase changes. Do not notify at
  every meal boundary when meals are back-to-back.
- Prime the notifier on startup and after a plan change so it does not fire
  immediately.
- Tray availability is detected with D-Bus `StatusNotifierWatcher` probing on
  Linux. Do not assume the tray exists.
- Keep the UI usable without a tray.

## Persistence rules

- Write plans atomically: temp file in the same directory, then rename.
- Keep plan/config files at mode `0600` and the config directory at `0700`.
- Never delete a corrupt or legacy config automatically.
- If the current path is missing and the legacy `eatime` path exists, migrate
  the plan to `comtudia` and leave the old file in place.
- A corrupt current plan must not silently fall back to the legacy plan.
- Any change to the config path or app ID requires a migration plan and updates
  to `README.md`, `PLAN.md`, `AGENTS.md`, and `FyneApp.toml`.

## Testing expectations

- Add tests for schedule boundaries, gaps, midnight-crossing windows, and DST.
- Add store tests for save/load, corrupt files, atomic writes, and migration.
- Add notification tests for contiguous meals and gap-separated meals.
- UI changes should keep the existing Fyne smoke tests passing.
- Run `make check` and `go test -race ./...` before finishing.

## Screenshots

- Dashboard: `docs/screenshot.png`
- Settings: `docs/settings.png`
- Regenerate both after UI/name changes.
- When generating screenshots, use a temporary `XDG_CONFIG_HOME` so the user's
  real plan is not modified.
- The settings screenshot may be captured by temporarily starting the app on
  the settings view, then reverting that temporary code.

## Guardrails

- Do not edit `prompt.md`.
- Do not commit build artifacts from `bin/`.
- Do not silently change the app ID, module path, or config location.
- Keep the app name lowercase: `comtudia`.
- Preserve existing user plans through the legacy config migration.

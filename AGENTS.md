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

Android (toolchain is local, no sudo):

```bash
make android-env            # JDK 17 + Android SDK 35 + Gradle 8.9 under ~/.local
source android/env.sh
make android-build          # android/app/build/outputs/apk/debug/app-debug.apk
make wear-build             # android/wear/build/outputs/apk/debug/wear-debug.apk
make android-test           # JVM unit tests + Go/Kotlin golden parity
```

Band (Vela Quick App):

```bash
tools/band-build.sh         # RPK in band/comtudia/dist/ (needs Node or AIoT-IDE)
tools/deploy-rpk.sh --rpk <file>   # sideload via Mi Fitness debug page over ADB
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
- `android/` — Kotlin + Compose phone app; `data/` persists the same
  `plan.json`, `notify/` and `alarm/` own Android notifications.
- `android/core/` — shared JVM module (plan, schedule, format, notify, JSON)
  used by both phone and watch.
- `android/wear/` — Wear OS app; renders the plan pushed by the phone over the
  Wearable Data Layer.
- `band/` — standalone Vela JS Quick App for the Xiaomi Smart Band 9 Pro.
- `tools/` — Android toolchain bootstrap, RPK build and deploy scripts.

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

## Android rules

- The schedule semantics are shared with the desktop app. `android/.../core/`
  is a port of `internal/plan`, not an independent implementation. Never change
  one side alone.
- After an intentional schedule change, regenerate the parity vectors:
  `go test ./internal/plan/ -run TestGoldenVectors -update`, then
  `make android-test`.
- `plan.json` must stay byte-compatible with the desktop schema (version 1).
  Android-only settings belong in `SharedPreferences`, never in the plan file.
- A corrupt plan is never deleted or overwritten implicitly; report it and fall
  back to the default in memory only.
- Notify once per contiguous phase; record the phase key before posting so a
  killed process cannot replay an alert; prime on launch and after a plan edit.
- Exact alarms are best-effort: check `canScheduleExactAlarms()` and degrade to
  `setAndAllowWhileIdle` instead of failing.
- Do not add a foreground service for the countdown. The ongoing notification
  uses a SystemUI chronometer, which keeps ticking without the app running.
- Keep `applicationId` `com.comtudia.app`; the band RPK `package` must match for
  a future Interconnect bridge.
- `MainActivity` may only be edited together with the Compose screens it wires.

## Wear OS rules

- The phone is the source of truth. The watch renders the last plan it received
  and must never edit it.
- Both APKs use `applicationId` `com.comtudia.app`; the Wearable Data Layer is
  scoped by package name, so this must not drift.
- Wire format: data item `/comtudia/plan` carrying the `json` field, and message
  `/comtudia/request` from the watch. Keep the path constants in one place per
  side.
- The watch must work offline with the last synced document.
- Watch UI uses Wear Material 1.4.x with Compose 1.7. Wear Material3 only ships
  from 1.5 and needs a newer Compose; do not mix them casually.
- Update `docs/wear.md` whenever the install or sync flow changes.

## Band rules

- The Xiaomi Smart Band 9 Pro runs Vela OS, not Wear OS. Apps are Vela JS Quick
  Apps (`.rpk`) sideloaded through Mi Fitness's hidden debug page over ADB.
- Keep `band/comtudia/src/common/plan.js` in step with `core`; run
  `node band/comtudia/test/schedule-check.js` after touching it.
- Target 336x480 with `designWidth: 168`, AMOLED black, no images, no network.
- Compute the countdown from the phase-end timestamp; timers run only while the
  page is visible.
- Never claim the RPK works on hardware until it has been tried on the device.
  Global firmware reportedly crashes on custom RPKs.

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
- Android changes must keep `make android-test` green, including the Go/Kotlin
  golden vectors. Add a Kotlin test for every new schedule rule.
- Band changes must keep `node band/comtudia/test/schedule-check.js` green.

## Screenshots

- Dashboard: `docs/screenshot.png`
- Settings: `docs/settings.png`
- Android: `docs/android-dashboard.png`, `docs/android-settings.png`
- Band: `docs/band-dashboard.png` (336x480, from the emulator or the device)
- Regenerate after UI/name changes.
- When generating screenshots, use a temporary `XDG_CONFIG_HOME` so the user's
  real plan is not modified.
- The settings screenshot may be captured by temporarily starting the app on
  the settings view, then reverting that temporary code.
- Android screenshots come from the emulator with a temporary plan; never
  capture the user's real timetable.

## Guardrails

- Do not edit `prompt.md`.
- Do not commit build artifacts from `bin/`, `android/**/build/`, or
  `band/**/dist/`.
- Do not commit APKs, keystores, RPKs, `node_modules/`, or the generated
  `android/env.sh` / `android/local.properties`.
- Do not silently change the app ID, module path, or config location.
- Keep the app name lowercase: `comtudia`.
- Preserve existing user plans through the legacy config migration.

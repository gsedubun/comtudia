# AGENTS.md

Instructions for coding agents working on **comtudia** (from the Spanish
“comienza tu día” — start your day).

## Project identity

- Display name / window title: `comtudia`
- Language / toolkit: Vala + GTK4 + libadwaita
- GApplication / desktop ID: `com.comtudia.app`
- Binary: `comtudia` (built at `build/comtudia`)
- Config: `$XDG_CONFIG_HOME/comtudia/plan.json`
- Legacy config: `$XDG_CONFIG_HOME/eatime/plan.json`
- Desktop file: `com.comtudia.app.desktop`
- Icon name: `com.comtudia.app`

Read `README.md` and `PLAN.md` before making changes. `prompt.md` is the
original brief and must not be edited. `VALA_GTK4_PLAN.md` is the migration
brief that led to the current implementation.

## Commands

```bash
make build                 # meson setup + compile
make run                   # build and run ./build/comtudia
make test                  # meson test
make check                 # tests + desktop/metainfo validation
make install               # meson install (use PREFIX=$HOME/.local if needed)
make clean                 # remove build/
meson setup build
meson compile -C build
meson test -C build --print-errorlogs
```

Debian/Ubuntu build dependencies:

```bash
sudo apt install -y valac meson ninja-build \
  libgtk-4-dev libadwaita-1-dev libjson-glib-dev \
  libglib2.0-dev desktop-file-utils hicolor-icon-theme \
  gnome-shell-extension-appindicator
```

The AppIndicator extension is a desktop-environment requirement for GNOME tray
icons, not an application dependency.

## Architecture

- `src/plan.vala` — pure plan model, validation, presets.
- `src/schedule.vala` — schedule computation, phases, slots, state.
- `src/format.vala` — countdown/time formatting.
- `src/store.vala` — json-glib persistence, atomic writes, legacy migration.
- `src/notifier.vala` — phase-change notification messages.
- `src/application.vala` — `Adw.Application`, plan lifecycle, timer,
  `GNotification`, tray setup.
- `src/window.vala` — `Adw.ApplicationWindow` + `Adw.NavigationView`.
- `src/dashboard.vala` — dashboard and schedule list.
- `src/settings.vala` — `Adw.PreferencesPage` settings view.
- `src/ring.vala` — Cairo countdown ring.
- `src/timeline.vala` — Cairo 24-hour timeline.
- `src/colors.vala` — shared Cairo accent colours.
- `src/tray-item.vala` — StatusNotifierItem and DBusMenu over GIO D-Bus.
- `data/` — desktop file, metainfo, icon, CSS, GResource manifest.
- `tests/` — GLib.Test suites.

Keep schedule and persistence logic out of the GTK layer so it remains
testable without a display.

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

- GTK objects may only be touched from the GLib main thread. Use
  `GLib.Timeout`/`GLib.idle_add` rather than background threads.
- The dashboard is updated by a one-second `GLib.Timeout`; the countdown is
  computed from `GLib.DateTime.now_local()` rather than decremented, to avoid
  drift.
- Notifications fire only when a contiguous phase changes. Do not notify at
  every meal boundary when meals are back-to-back.
- Prime the notifier on startup and after a plan change so it does not fire
  immediately.
- Tray availability is detected with a D-Bus
  `org.kde.StatusNotifierWatcher` probe. Do not assume the tray exists.
- Keep the UI usable without a tray.
- Use `Adw.NavigationView` for dashboard → plan settings and
  `Adw.PreferencesPage` for settings.
- Preserve the existing Cairo ring/timeline design when changing the UI.

## Persistence rules

- Write plans atomically with `Gio.File.replace_contents`.
- Keep plan/config files at mode `0600` and the config directory at `0700`.
- Never delete a corrupt or legacy config automatically.
- If the current path is missing and the legacy `eatime` path exists, migrate
  the plan to `comtudia` and leave the old file in place.
- A corrupt current plan must not silently fall back to the legacy plan.
- Any change to the config path or app ID requires a migration plan and updates
  to `README.md`, `PLAN.md`, `AGENTS.md`, and the desktop file.

## Testing expectations

- Add tests for schedule boundaries, gaps, midnight-crossing windows, and DST.
- Add store tests for save/load, corrupt files, atomic writes, permissions, and
  migration.
- Add notification tests for contiguous meals and gap-separated meals.
- Add timeline tests for clipping and midnight-crossing segments.
- Run `make check` and `meson test -C build --print-errorlogs` before finishing.
- Verify the tray service manually with a StatusNotifier watcher if you change
  `tray-item.vala`. `gdbus introspect` and `gdbus call` are useful for checking
  the SNI properties and DBusMenu layout.

## Screenshots

- Dashboard: `docs/screenshot.png`
- Settings: `docs/settings.png`
- Regenerate both after UI/name changes.
- When generating screenshots, use a temporary `XDG_CONFIG_HOME` so the user's
  real plan is not modified.
- The settings screenshot may be captured by temporarily starting the app on
  the settings view, then reverting that temporary code. Do not leave a
  screenshot-only path in the app unless it is a deliberate, documented
  developer option.

## Guardrails

- Do not edit `prompt.md`.
- Do not commit build artifacts from `build/` or `bin/`.
- Do not silently change the app ID, module path, or config location.
- Keep the app name lowercase: `comtudia`.
- Preserve existing user plans through the legacy config migration.

# comtudia

A small native Linux desktop app for tracking a recurring eating window. The
name comes from the Spanish phrase **“comienza tu día”** — start your day.

Set your fasting/eating split, split the eating window into meals, and see how
long until the next time you eat. Built with **Vala**, **GTK4**, and
**libadwaita** — no Node, WebView, or Go runtime.

![comtudia dashboard](docs/screenshot.png)

## App identity

| Item | Value |
| --- | --- |
| Display name / window title | `comtudia` |
| GApplication / desktop ID | `com.comtudia.app` |
| Binary | `comtudia` |
| Config | `$XDG_CONFIG_HOME/comtudia/plan.json` |
| Legacy config | `$XDG_CONFIG_HOME/eatime/plan.json` |
| Desktop file | `com.comtudia.app.desktop` |
| Icon name | `com.comtudia.app` |

## Features

- Live countdown to the next eating time
- 24-hour timeline with meal blocks and a current-time marker
- Daily schedule list with the current/next meal highlighted
- Configurable fasting/eating split, meal count, meal gaps, and start time
- Grouped eating-time input with a live total readout
- Presets for 16:8, 18:6, and 20:4
- Progress ring for the current fasting or eating phase
- Desktop notifications on eating/fasting transitions
- StatusNotifier tray icon with a status line, Show, and Quit
- Optional close-to-tray behavior
- Settings persisted locally with atomic, private writes
- Single-instance GApplication behavior

![comtudia plan settings](docs/settings.png)

## How the schedule works

The cycle is always 24 hours. Meals are equally sized and the optional gap
between meals counts as fasting time.

For example, 18h fasting / 6h eating, 3 meals, starting at 12:00:

| Setting | Value |
| --- | --- |
| Eating total | 6h |
| Meals | 3 × 2h |
| Gap | 0 |
| Meal times | 12:00–14:00, 14:00–16:00, 16:00–18:00 |
| Fasting | 18:00–12:00 |

With a 1h gap between meals:

- Meals: 12:00–14:00, 15:00–17:00, 18:00–20:00
- Total eating is still 6h
- Total fasting is still 18h: 16h outside the meal spread + two 1h meal gaps

On the settings screen, eating time is entered as hours + minutes and the
resulting total is shown underneath the fields.

Meal times are anchored to the local wall clock, and meal durations are
preserved across DST transitions. On the day the clock changes, a later meal
can shift slightly relative to the wall clock.

## Requirements

- A Linux desktop with GTK4 and libadwaita
- GTK4 4.18 or newer
- libadwaita 1.6 or newer
- GLib 2.80 or newer
- json-glib
- Vala, Meson, and Ninja
- For tray icons on GNOME: the AppIndicator extension

On Debian/Ubuntu:

```bash
sudo apt update
sudo apt install -y valac meson ninja-build \
  libgtk-4-dev libadwaita-1-dev libjson-glib-dev \
  libglib2.0-dev desktop-file-utils hicolor-icon-theme \
  gnome-shell-extension-appindicator
```

Check the installed library versions:

```bash
pkg-config --modversion gtk4 libadwaita-1 json-glib-1.0
```

Enable the GNOME StatusNotifier/AppIndicator extension:

```bash
gnome-extensions enable appindicatorsupport@rgcjonas.gmail.com
```

A logout/login may be required before GNOME displays tray icons. The extension
is a desktop-environment requirement; comtudia detects whether a
StatusNotifier watcher is available and remains usable without one.

## Build and run

```bash
make build     # meson setup + compile
make run       # build and run ./build/comtudia
make test      # meson test
make check     # tests + desktop/metainfo validation
make install   # meson install
make clean     # remove the build directory
```

To build a debug binary:

```bash
make build BUILDTYPE=debug
```

To install into a user prefix:

```bash
make install PREFIX=$HOME/.local
```

`make install` installs:

```text
$PREFIX/bin/comtudia
$PREFIX/share/applications/com.comtudia.app.desktop
$PREFIX/share/metainfo/com.comtudia.app.metainfo.xml
$PREFIX/share/icons/hicolor/scalable/apps/com.comtudia.app.svg
```

After a manual user-prefix install, refresh the desktop database and icon cache
if your desktop does not pick the app up immediately:

```bash
update-desktop-database ~/.local/share/applications
gtk-update-icon-cache ~/.local/share/icons/hicolor 2>/dev/null || true
```

Then launch `comtudia` from your application menu or from a terminal.

Direct Meson commands also work:

```bash
meson setup build --prefix=/usr/local
meson compile -C build
meson test -C build --print-errorlogs
meson install -C build
```

## Configuration

The plan is stored at:

```text
$XDG_CONFIG_HOME/comtudia/plan.json
```

On most systems this is `~/.config/comtudia/plan.json`. The settings screen
shows the exact path. A fresh install with no saved plan uses the 18:6 default:
3 meals of 2h starting at 12:00, with notifications enabled and tray-on-close
disabled.

A default plan looks like this:

```json
{
  "version": 1,
  "eating_minutes": 360,
  "meals": 3,
  "gap_minutes": 0,
  "window_start_minutes": 720,
  "notifications": true,
  "run_in_tray": false
}
```

`window_start_minutes` is minutes after local midnight, so `720` is 12:00.

Writes are atomic via `Gio.File.replace_contents`, the plan file is mode
`0600`, and the config directory is mode `0700`.

### Migrating from eatime

The app was previously named `eatime`. On first run, if the new
`comtudia/plan.json` does not exist but the old
`$XDG_CONFIG_HOME/eatime/plan.json` does, the old plan is loaded and copied to
the new location. The old file is left in place as a backup. A corrupt current
plan is reported and never silently replaced by the legacy plan.

### Resetting the plan

Use the **Reset** button on the settings screen to restore the default 18:6
plan, or move/delete the plan file while the app is not running. The app does
not delete a corrupt plan automatically.

## Notifications and tray

- Notifications fire when the eating/fasting phase changes, not at every
  meal-slot boundary. Contiguous meals are treated as one eating phase.
- Notifications use `GApplication.send_notification` / `GNotification`, so no
  libnotify dependency is needed.
- The app must be running to deliver notifications. Notifications are best
  effort and depend on the desktop’s notification service.
- The settings screen has a **Send test** action for notifications.
- Tray support implements `org.kde.StatusNotifierItem` and
  `com.canonical.dbusmenu` directly over GIO D-Bus. No GTK3, Ayatana, or
  libappindicator dependency is needed.
- The tray menu provides a status line, **Show comtudia**, and **Quit**.
  Activating the icon shows the window.
- GNOME requires the AppIndicator extension for StatusNotifier icons. If no
  `org.kde.StatusNotifierWatcher` is present, comtudia skips the tray and
  closing the window quits.
- “Keep running in tray when closed” only has an effect when a StatusNotifier
  watcher is available.
- `make install` installs the desktop file and icon under the hicolor theme so
  notifications and tray icons have the correct identity. Development runs also
  copy the icon into the user’s hicolor theme as a fallback.

## Troubleshooting

### The tray icon is missing

1. Install and enable the GNOME AppIndicator extension.
2. Log out and back in.
3. Check that a watcher is running:

   ```bash
   gdbus call --session \
     --dest org.freedesktop.DBus \
     --object-path /org/freedesktop/DBus \
     --method org.freedesktop.DBus.NameHasOwner \
     org.kde.StatusNotifierWatcher
   ```

   The result should be `(true,)`. If it is `(false,)`, the desktop is not
   providing a StatusNotifier host; the app stays usable without the tray and
   closing the window quits.

### Notifications do not appear

- Make sure a notification daemon is running.
- Try the **Send test** action in settings.
- Install the app with `make install` so the desktop file and icon have the
  correct app identity.
- Check your desktop’s notification settings and Do Not Disturb state.

### The build cannot find GTK4 or libadwaita

Install the development packages listed above and verify them:

```bash
pkg-config --modversion gtk4 libadwaita-1 json-glib-1.0
```

### The app shows a warning about the saved plan

The current plan could not be read or is invalid. The app uses defaults and
shows the error without deleting the file. Move the file aside, or fix it using
the settings screen and save again.

### A second launch does not open a new window

This is expected: comtudia uses a single GApplication instance. Launching it
again activates the existing window, even if it is hidden in the tray.

## Development

### Project layout

```text
meson.build              Meson project and install rules
Makefile                 build/run/test/install helpers
src/
  main.vala              entry point
  application.vala       Adw.Application, plan lifecycle, notifications, timer
  window.vala            Adw.ApplicationWindow + NavigationView
  dashboard.vala         dashboard UI and schedule list
  settings.vala          plan settings page
  plan.vala              plan model, validation, presets
  schedule.vala          schedule calculation and phase state
  store.vala             json-glib persistence and legacy migration
  format.vala            countdown/time formatting
  notifier.vala          phase-change notification messages
  tray-item.vala         StatusNotifierItem + DBusMenu
  ring.vala              Cairo countdown ring
  timeline.vala          Cairo 24-hour timeline
  colors.vala            shared Cairo accent colours
data/
  com.comtudia.app.desktop
  com.comtudia.app.metainfo.xml
  com.comtudia.app.svg
  com.comtudia.app.gresource.xml
  style.css
tests/                   GLib.Test suites
docs/                    screenshots
AGENTS.md                instructions for coding agents
VALA_GTK4_PLAN.md        Vala/GTK4 migration brief
LICENSE                  BSD 2-Clause license
```

### Tests

```bash
meson test -C build --print-errorlogs
make check
```

The test suite covers validation, meal splitting, gaps, midnight-crossing
windows, DST behavior, persistence and config migration, notification
transitions, formatting, timeline segments, settings preview text, and tray
status text.

The tray service itself can be inspected manually with `gdbus` while the app is
running and a StatusNotifier watcher is available:

```bash
# The bus name is usually org.kde.StatusNotifierItem-<pid>-1
gdbus introspect --session \
  --dest org.kde.StatusNotifierItem-<pid>-1 \
  --object-path /StatusNotifierItem

gdbus call --session \
  --dest org.kde.StatusNotifierItem-<pid>-1 \
  --object-path /MenuBar \
  --method com.canonical.dbusmenu.GetLayout 0 0 "[]"
```

### Screenshots

- Dashboard: `docs/screenshot.png`
- Settings: `docs/settings.png`

Regenerate them after UI changes. When generating screenshots, use a temporary
`XDG_CONFIG_HOME` so the real plan is not modified.

## License

comtudia is released under the BSD 2-Clause license. See [LICENSE](LICENSE).

The previous Go/Fyne implementation is preserved in git under the tag
`fyne-v0.1`.

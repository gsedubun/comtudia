# comtudia — Vala/GTK4 Migration Plan

> **Status:** proposed, not implemented.  
> The current `main` branch still contains the Fyne implementation. This plan
> supersedes it once the migration is completed and verified.  
> **Date:** 2026-10-26

## Goal

Replace the Go/Fyne implementation with a **Linux-only native desktop app**
written in **Vala** using **GTK4 + libadwaita**.

Hard requirements:

- Native Linux desktop application
- System tray is mandatory
- Desktop notifications are mandatory
- No Node/npm, WebView, or Go runtime
- Keep the existing eating-plan behavior and config migration

## App identity

| Item | Value |
|---|---|
| Display name / window title | `comtudia` |
| GApplication / desktop ID | `com.comtudia.app` |
| Binary | `comtudia` |
| Config | `$XDG_CONFIG_HOME/comtudia/plan.json` |
| Legacy config | `$XDG_CONFIG_HOME/eatime/plan.json` |
| Desktop file | `com.comtudia.app.desktop` |
| Icon name | `com.comtudia.app` |

## Why Vala/GTK4

- Native GNOME/Wayland application
- No Node/npm and no bundled WebView
- No Go/CGO/Fyne dependencies
- Notifications through GIO’s `GNotification`
- Tray through the Linux StatusNotifierItem protocol
- System GTK4/libadwaita/json-glib instead of a bundled UI runtime

The tradeoff is that this is Linux-only and depends on system libraries. A
Flatpak package can bundle those later if needed, but it is not required for
the first version.

## Dependency footprint

| Layer | Fyne | Wails | Vala/GTK4 |
|---|---|---|---|
| Language/runtime | Go | Go + Node | Vala + C |
| UI toolkit | Fyne + cgo | WebView + GTK/WebKit | GTK4 + libadwaita |
| Tray | Fyne systray | Wails systray | Custom GIO SNI/DBusMenu |
| Notifications | Fyne | Wails service | `GNotification` (GIO) |
| Config parsing | Go stdlib | Go stdlib | json-glib |
| Node/npm | No | Yes | No |
| WebView | No | Yes | No |

No libnotify, libayatana-appindicator, GTK3, Node, or WebView are required.

## Prerequisites

```bash
sudo apt install -y valac meson ninja-build \
  libgtk-4-dev libadwaita-1-dev libjson-glib-dev \
  libglib2.0-dev desktop-file-utils hicolor-icon-theme \
  gnome-shell-extension-appindicator
```

Enable the GNOME StatusNotifier/AppIndicator extension:

```bash
gnome-extensions enable appindicatorsupport@rgcjonas.gmail.com
```

A logout/login may be required before GNOME displays tray icons.

The extension is a desktop-environment requirement, not an application
dependency. Without it, GNOME cannot display any StatusNotifier tray icon.

## Target structure

```text
comtudia/
├── meson.build
├── Makefile
├── README.md
├── PLAN.md
├── AGENTS.md
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
│   ├── tray-item.vala        # StatusNotifierItem
│   ├── tray-menu.vala        # com.canonical.dbusmenu
│   ├── ring.vala             # Gtk.DrawingArea + Cairo
│   └── timeline.vala         # Gtk.DrawingArea + Cairo
├── data/
│   ├── com.comtudia.app.desktop
│   ├── com.comtudia.app.metainfo.xml
│   ├── com.comtudia.app.svg
│   └── style.css
├── tests/
│   ├── plan-test.vala
│   ├── schedule-test.vala
│   ├── store-test.vala
│   ├── format-test.vala
│   └── notify-test.vala
└── docs/
    ├── screenshot.png
    └── settings.png
```

## Core design

### Schedule logic

Port the existing Go behavior to Vala:

- 24-hour daily cycle
- total eating time split evenly across meals
- leftover minutes assigned to the earliest meals
- gaps count as fasting
- total fasting = `24h - eating time`
- long fast = `24h - (eating time + gaps)`
- meal spread must be at most 23 hours
- local wall-clock anchor with preserved meal durations
- DST-safe scanning of yesterday/today/tomorrow
- support windows crossing midnight

Use `GLib.DateTime` and `GLib.TimeZone`. The existing Go tests are the
specification for the ported behavior.

### Persistence

- Keep `$XDG_CONFIG_HOME/comtudia/plan.json`
- Parse and write JSON with **json-glib**
- Maintain the existing schema/version
- Atomic writes via `Gio.File.replace_contents`
- File mode `0600`, config directory `0700`
- On first run, migrate `$XDG_CONFIG_HOME/eatime/plan.json` to the new path
- Leave the legacy file in place as a backup
- Do not silently fall back to the legacy file when the current file is corrupt

### UI

Use **GTK4 + libadwaita**:

- `Adw.Application` / `Adw.ApplicationWindow`
- `Adw.NavigationView` for dashboard → plan settings
- `Adw.PreferencesPage` for settings
- `Gtk.DrawingArea` + Cairo for the countdown ring and timeline
- Native `Gtk.SpinButton`, `Gtk.DropDown`, `Gtk.Switch`, `Gtk.ListBox`
- Grouped eating-time control with a live total readout
- CSS for accent colors and dark/light mode
- Dashboard structure:
  - header with app name and Plan button
  - fasting/eating status pill
  - countdown ring
  - next-eating / plan summary cards
  - 24-hour timeline
  - schedule list with current/next highlighting
- Settings:
  - presets 16:8, 18:6, 20:4
  - grouped eating time (hours + minutes + total)
  - meals, gap, first meal
  - notifications and tray-on-close toggles
  - live preview and validation
  - save/cancel

### Tray

GTK4 has no built-in tray API. Implement StatusNotifierItem directly over GIO
D-Bus:

- `org.kde.StatusNotifierItem`
- `com.canonical.dbusmenu`
- Own a session-bus name
- Register with `org.kde.StatusNotifierWatcher`
- Menu items:
  - disabled status line
  - “Show comtudia”
  - “Quit”
- Update the menu/status through `LayoutUpdated`
- Handle SNI `Activate`, `SecondaryActivate`, and `ContextMenu`
- Detect whether `org.kde.StatusNotifierWatcher` exists; if not:
  - do not enable close-to-tray
  - closing the window quits
- Install the icon under the hicolor theme and expose `IconName`
- Optionally provide `IconThemePath` for development builds

This avoids adding GTK3/Ayatana or a third-party tray library.

### Notifications

Use `GApplication.send_notification` with `GNotification`:

- App ID `com.comtudia.app`
- Install `com.comtudia.app.desktop`
- Install the app icon under the hicolor theme
- Send phase-change notifications from the ported notifier logic
- Works while the window is hidden in the tray
- Add a “Send test notification” action in settings
- No libnotify dependency

### Window and tray behavior

- Register a `Gtk.Window.close_request` handler.
- If “keep running in tray” is enabled and the SNI watcher is available:
  - hide the window
  - prevent the close
  - keep the process running
- Otherwise allow the app to quit.
- Tray “Show comtudia” presents/focuses the window.
- Tray “Quit” quits the `Gtk.Application`.
- Single-instance behavior comes from the GApplication ID; a second launch
  activates the existing window.

## Build

Use Meson + Ninja:

```meson
project('comtudia', ['vala', 'c'], version: '0.1.0')

gtk4 = dependency('gtk4', version: '>=4.18')
adw = dependency('libadwaita-1', version: '>=1.6')
json_glib = dependency('json-glib-1.0')
gio = dependency('gio-2.0')
```

- Compile the CSS as a GResource.
- Install the desktop file, metainfo, and icons.
- Build tests as separate Vala executables.
- Makefile targets:

```bash
make build     # meson setup + ninja
make run       # build and run ./build/src/comtudia
make test      # meson test
make install   # meson install
make clean     # remove build directory
```

## Testing

Port the existing Go tests to Vala/`GLib.Test`:

- plan validation and defaults
- meal splitting and leftover-minute distribution
- gaps, span, and long-fast calculations
- exact phase boundaries
- midnight-crossing windows
- DST transitions (`America/New_York`)
- store round-trip, corrupt files, atomic writes
- legacy config migration
- countdown/time formatting
- notification phase changes
- tray menu/status behavior where testable

Manual verification:

- Run under GNOME Wayland
- Verify tray icon and menu with the AppIndicator extension enabled
- Verify close-to-tray and Quit
- Verify a real GNotification at a meal boundary
- Verify the test-notification action
- Capture new dashboard/settings screenshots

## Migration steps

1. Tag the current Fyne implementation, e.g. `fyne-v0.1`.
2. Create branch `vala-gtk4` from `main`.
3. Scaffold the Meson/Vala project.
4. Port `plan`, `schedule`, `store`, `format`, and notifier logic with tests.
5. Build the libadwaita dashboard and settings views.
6. Add CSS, app icon, tray icon, desktop file, and metainfo.
7. Implement the StatusNotifierItem/DBusMenu tray service.
8. Wire `GNotification` notifications and the test action.
9. Remove Go/Fyne code and dependencies from the branch.
10. Update Makefile, `.gitignore`, README, PLAN, and AGENTS for Vala/GTK4.
11. Run tests, build, manual tray/notification checks, and screenshots.
12. Merge `vala-gtk4` into `main` and push.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Custom SNI/DBusMenu bugs | Keep the menu minimal; test with GNOME AppIndicator extension; add D-Bus-level tests where practical |
| GNOME tray extension missing | Document as a hard requirement; detect the watcher and degrade to window-only close behavior |
| Vala port regressions | Port the existing Go test cases as the specification |
| DST/timezone mistakes | Use `GLib.TimeZone` tests with fixed zones |
| JSON compatibility | Preserve schema/version and add migration tests |
| System-library dependency | Document packages; use Flatpak later if a bundled runtime is needed |
| Notification identity missing | Install desktop file and icon with app ID `com.comtudia.app` |
| UI parity | Recreate the current ring/timeline design with Cairo/CSS and regenerate screenshots |

## Git strategy

- Keep the current Fyne implementation reachable via tag `fyne-v0.1` and/or
  branch `fyne`.
- Perform the rewrite on `vala-gtk4`.
- Do not merge until `meson test`, the app run, tray, and notifications are
  verified.
- Merge into `main` and push the result to the existing remote.
- Preserve `LICENSE` and the legacy config migration.

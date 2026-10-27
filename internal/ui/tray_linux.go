//go:build linux

package ui

import "github.com/godbus/dbus/v5"

// trayAvailable reports whether a StatusNotifier watcher is present. Fyne's
// systray backend needs one, and GNOME only provides it when an AppIndicator
// extension is installed.
func trayAvailable() bool {
	conn, err := dbus.SessionBus()
	if err != nil {
		return false
	}
	var names []string
	if err := conn.BusObject().Call("org.freedesktop.DBus.ListNames", 0).Store(&names); err != nil {
		return false
	}
	for _, name := range names {
		if name == "org.kde.StatusNotifierWatcher" {
			return true
		}
	}
	return false
}

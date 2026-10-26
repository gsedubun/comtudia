//go:build !linux

package ui

func trayAvailable() bool {
	return true
}

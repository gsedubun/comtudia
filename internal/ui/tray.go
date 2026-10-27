package ui

import (
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/driver/desktop"

	"com.comtudia.app/internal/format"
	"com.comtudia.app/internal/plan"
)

type tray struct {
	app        *App
	desktopApp desktop.App
	win        fyne.Window
	lastStatus string
}

func newTray(app *App, desktopApp desktop.App, win fyne.Window, icon fyne.Resource) *tray {
	t := &tray{app: app, desktopApp: desktopApp, win: win}
	desktopApp.SetSystemTrayIcon(icon)
	desktopApp.SetSystemTrayWindow(win)
	return t
}

func (t *tray) Update(st plan.State) {
	status := trayStatus(st)
	if status == t.lastStatus {
		return
	}
	t.lastStatus = status

	statusItem := fyne.NewMenuItem(status, nil)
	statusItem.Disabled = true
	show := fyne.NewMenuItem("Show comtudia", func() {
		t.win.Show()
		t.win.RequestFocus()
	})
	quit := fyne.NewMenuItem("Quit", t.app.quit)

	t.desktopApp.SetSystemTrayMenu(fyne.NewMenu("comtudia", statusItem, show, quit))
}

func trayStatus(st plan.State) string {
	if st.Phase == plan.PhaseEating {
		return "Eating until " + format.Clock(st.PhaseEnd)
	}
	return "Next eating in " + format.Duration(st.Remaining)
}

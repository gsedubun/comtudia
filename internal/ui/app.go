package ui

import (
	"log"
	"sync"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/driver/desktop"

	"com.comtudia.app/internal/notify"
	"com.comtudia.app/internal/plan"
	"com.comtudia.app/internal/store"
)

// Run starts the comtudia desktop application.
func Run(appIcon, trayIcon fyne.Resource) {
	fyneApp := app.NewWithID("com.comtudia.app")
	fyneApp.Settings().SetTheme(appTheme{})
	if appIcon != nil {
		fyneApp.SetIcon(appIcon)
	}

	win := fyneApp.NewWindow("comtudia")
	win.Resize(fyne.NewSize(480, 860))
	win.CenterOnScreen()

	application := newApp(fyneApp, win, trayIcon)
	application.start()
	win.ShowAndRun()
	application.stop()
}

type App struct {
	fyneApp fyne.App
	win     fyne.Window
	store   *store.Store

	mu sync.RWMutex
	p  plan.Plan

	dashboard *dashboard
	settings  *settingsView
	notifier  *notify.Notifier
	tray      *tray

	done     chan struct{}
	stopOnce sync.Once
}

func newApp(fyneApp fyne.App, win fyne.Window, trayIcon fyne.Resource) *App {
	path, pathErr := store.DefaultPath()
	legacyPath, _ := store.LegacyPath()
	if pathErr != nil {
		path = "plan.json"
		legacyPath = ""
	}
	s := store.New(path)
	p, migrated, loadErr := s.LoadWithFallback(legacyPath)
	if migrated {
		log.Printf("comtudia: migrated plan from %s", legacyPath)
	}
	if loadErr == nil {
		loadErr = pathErr
	}
	if loadErr != nil {
		if err := p.Validate(); err != nil {
			p = plan.Default()
		}
		log.Printf("comtudia: %v", loadErr)
	}

	a := &App{
		fyneApp:  fyneApp,
		win:      win,
		store:    s,
		p:        p,
		notifier: notify.New(),
		done:     make(chan struct{}),
	}
	a.dashboard = newDashboard(p, loadErr, a.showSettings)
	a.settings = newSettings(p, s.Path(), a.savePlan, a.showDashboard, a.testNotification)
	a.win.SetContent(a.dashboard.root)
	a.win.SetCloseIntercept(a.onClose)
	a.setupTray(trayIcon)
	return a
}

func (a *App) start() {
	now := time.Now()
	p := a.currentPlan()
	st, err := plan.Compute(p, now)
	if err == nil {
		a.dashboard.Update(st)
		a.notifier.Update(st)
		if a.tray != nil {
			a.tray.Update(st)
		}
	}
	go a.loop()
}

func (a *App) loop() {
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-a.done:
			return
		case now := <-ticker.C:
			p := a.currentPlan()
			st, err := plan.Compute(p, now)
			if err != nil {
				continue
			}

			var message *notify.Message
			if p.Notifications {
				if m, ok := a.notifier.Update(st); ok {
					message = &m
				}
			}

			fyne.Do(func() {
				a.dashboard.Update(st)
				if a.tray != nil {
					a.tray.Update(st)
				}
				if message != nil {
					a.fyneApp.SendNotification(fyne.NewNotification(message.Title, message.Body))
				}
			})
		}
	}
}

func (a *App) currentPlan() plan.Plan {
	a.mu.RLock()
	defer a.mu.RUnlock()
	return a.p
}

func (a *App) savePlan(p plan.Plan) error {
	if err := a.store.Save(p); err != nil {
		return err
	}

	a.mu.Lock()
	a.p = p
	a.mu.Unlock()

	a.notifier.Reset()
	a.dashboard.SetPlan(p)
	a.settings.SetPlan(p)
	if st, err := plan.Compute(p, time.Now()); err == nil {
		a.notifier.Update(st)
	}
	a.showDashboard()
	return nil
}

func (a *App) showDashboard() {
	a.win.SetContent(a.dashboard.root)
	a.refresh()
}

func (a *App) showSettings() {
	a.settings.SetPlan(a.currentPlan())
	a.win.SetContent(a.settings.root)
}

func (a *App) refresh() {
	p := a.currentPlan()
	st, err := plan.Compute(p, time.Now())
	if err != nil {
		return
	}
	a.dashboard.Update(st)
	if a.tray != nil {
		a.tray.Update(st)
	}
}

func (a *App) testNotification() {
	a.fyneApp.SendNotification(fyne.NewNotification("comtudia", "Notifications are working."))
}

func (a *App) setupTray(icon fyne.Resource) {
	if icon == nil || !trayAvailable() {
		return
	}
	desktopApp, ok := a.fyneApp.(desktop.App)
	if !ok {
		return
	}
	a.tray = newTray(a, desktopApp, a.win, icon)
}

func (a *App) onClose() {
	if a.currentPlan().RunInTray && a.tray != nil {
		a.win.Hide()
		return
	}
	a.quit()
}

func (a *App) quit() {
	a.stop()
	a.fyneApp.Quit()
}

func (a *App) stop() {
	a.stopOnce.Do(func() {
		close(a.done)
	})
}

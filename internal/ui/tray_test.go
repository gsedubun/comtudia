package ui

import (
	"testing"
	"time"

	"fyne.io/fyne/v2"

	"com.comtudia.app/internal/plan"
)

type fakeDesktop struct {
	icon   fyne.Resource
	menu   *fyne.Menu
	window fyne.Window
}

func (f *fakeDesktop) SetSystemTrayMenu(menu *fyne.Menu) { f.menu = menu }
func (f *fakeDesktop) SetSystemTrayIcon(icon fyne.Resource) {
	f.icon = icon
}
func (f *fakeDesktop) SetSystemTrayWindow(window fyne.Window) { f.window = window }

func TestTrayStatusAndMenu(t *testing.T) {
	fake := &fakeDesktop{}
	tr := newTray(&App{}, fake, nil, nil)

	now := time.Date(2026, 10, 26, 10, 0, 0, 0, time.UTC)
	st, err := plan.Compute(plan.Default(), now)
	if err != nil {
		t.Fatal(err)
	}
	trayStatusFasting := trayStatus(st)
	if trayStatusFasting != "Next eating in 2h" {
		t.Fatalf("fasting status = %q", trayStatusFasting)
	}

	tr.Update(st)
	if fake.menu == nil || len(fake.menu.Items) < 3 {
		t.Fatalf("menu = %+v", fake.menu)
	}
	if fake.menu.Items[0].Label != trayStatusFasting || !fake.menu.Items[0].Disabled {
		t.Fatalf("first menu item = %+v", fake.menu.Items[0])
	}
	if fake.menu.Items[1].Label != "Show comtudia" {
		t.Fatalf("show item = %q", fake.menu.Items[1].Label)
	}
	if fake.menu.Items[2].Label != "Quit" {
		t.Fatalf("quit item = %q", fake.menu.Items[2].Label)
	}

	eating, err := plan.Compute(plan.Default(), time.Date(2026, 10, 26, 12, 30, 0, 0, time.UTC))
	if err != nil {
		t.Fatal(err)
	}
	if got := trayStatus(eating); got != "Eating until 18:00" {
		t.Fatalf("eating status = %q", got)
	}
	tr.Update(eating)
	if fake.menu.Items[0].Label != "Eating until 18:00" {
		t.Fatalf("updated status = %q", fake.menu.Items[0].Label)
	}
}

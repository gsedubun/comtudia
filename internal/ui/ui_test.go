package ui

import (
	"strings"
	"testing"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/test"

	"com.comtudia.app/internal/plan"
)

func TestDashboardSmoke(t *testing.T) {
	test.NewApp()
	d := newDashboard(plan.Default(), nil, func() {})
	w := test.NewWindow(d.root)
	w.Resize(fyne.NewSize(480, 860))

	now := time.Date(2026, 10, 26, 14, 30, 0, 0, time.UTC)
	st, err := plan.Compute(plan.Default(), now)
	if err != nil {
		t.Fatalf("Compute: %v", err)
	}
	d.Update(st)

	changed := plan.Default()
	changed.GapMinutes = 60
	d.SetPlan(changed)
	st, err = plan.Compute(changed, now)
	if err != nil {
		t.Fatalf("Compute changed: %v", err)
	}
	d.Update(st)
}

func TestSettingsSmoke(t *testing.T) {
	test.NewApp()
	s := newSettings(plan.Default(), "/tmp/plan.json", func(plan.Plan) error { return nil }, func() {}, func() {})
	w := test.NewWindow(s.root)
	w.Resize(fyne.NewSize(480, 860))

	p, err := s.currentPlan()
	if err != nil {
		t.Fatalf("currentPlan: %v", err)
	}
	if p != plan.Default() {
		t.Fatalf("currentPlan = %+v, want default", p)
	}

	changed := plan.Default()
	changed.EatingMinutes = 8 * 60
	changed.Meals = 2
	changed.GapMinutes = 30
	changed.WindowStartMinutes = 9*60 + 30
	s.SetPlan(changed)
	p, err = s.currentPlan()
	if err != nil {
		t.Fatalf("currentPlan changed: %v", err)
	}
	if p != changed {
		t.Fatalf("currentPlan = %+v, want %+v", p, changed)
	}

	s.preset.SetSelected("16:8")
	p, err = s.currentPlan()
	if err != nil {
		t.Fatalf("currentPlan preset: %v", err)
	}
	if p.EatingMinutes != 8*60 || p.Meals != 2 || p.GapMinutes != 0 {
		t.Fatalf("preset plan = %+v", p)
	}
}

func TestPreviewText(t *testing.T) {
	p := plan.Default()
	p.GapMinutes = 60
	text := previewText(p)
	for _, want := range []string{"Meals", "12:00–14:00", "15:00–17:00", "18:00–20:00", "Eating 6h", "Long fast 16h", "meal gaps 2h"} {
		if !strings.Contains(text, want) {
			t.Fatalf("preview %q missing %q", text, want)
		}
	}
}

func TestEatingTimeReadout(t *testing.T) {
	test.NewApp()
	s := newSettings(plan.Default(), "/tmp/plan.json", func(plan.Plan) error { return nil }, func() {}, func() {})
	test.NewWindow(s.root)

	if got := s.eatTotal.Text; got != "= 6h total" {
		t.Fatalf("initial total = %q", got)
	}

	s.eatHours.SetSelected("07")
	s.eatMinutes.SetText("30")
	s.changed()
	if got := s.eatTotal.Text; got != "= 7h 30m total" {
		t.Fatalf("updated total = %q", got)
	}

	s.eatMinutes.SetText("99")
	s.changed()
	if got := s.eatTotal.Text; got != "= —" {
		t.Fatalf("invalid total = %q", got)
	}
}

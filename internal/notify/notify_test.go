package notify

import (
	"strings"
	"testing"
	"time"

	"com.comtudia.app/internal/plan"
)

func stateAt(t *testing.T, p plan.Plan, now time.Time) plan.State {
	t.Helper()
	st, err := plan.Compute(p, now)
	if err != nil {
		t.Fatalf("Compute: %v", err)
	}
	return st
}

func TestNotifierContiguousMeals(t *testing.T) {
	p := plan.Default()
	day := time.Date(2026, 10, 26, 0, 0, 0, 0, time.UTC)
	n := New()

	if msg, ok := n.Update(stateAt(t, p, day.Add(10*time.Hour))); ok {
		t.Fatalf("first update should prime without message, got %+v", msg)
	}
	if msg, ok := n.Update(stateAt(t, p, day.Add(11*time.Hour))); ok {
		t.Fatalf("same phase should be silent, got %+v", msg)
	}

	msg, ok := n.Update(stateAt(t, p, day.Add(12*time.Hour)))
	if !ok {
		t.Fatal("expected eating message")
	}
	if msg.Title != "Eating window open" {
		t.Fatalf("title = %q", msg.Title)
	}
	if !strings.Contains(msg.Body, "Meals 1–3 until 18:00") {
		t.Fatalf("body = %q", msg.Body)
	}

	if msg, ok := n.Update(stateAt(t, p, day.Add(14*time.Hour))); ok {
		t.Fatalf("contiguous meal boundary should be silent, got %+v", msg)
	}
	if msg, ok := n.Update(stateAt(t, p, day.Add(17*time.Hour))); ok {
		t.Fatalf("same eating phase should be silent, got %+v", msg)
	}

	msg, ok = n.Update(stateAt(t, p, day.Add(18*time.Hour)))
	if !ok {
		t.Fatal("expected fasting message")
	}
	if msg.Title != "Fasting started" {
		t.Fatalf("title = %q", msg.Title)
	}
	if !strings.Contains(msg.Body, "tomorrow at 12:00") {
		t.Fatalf("body = %q", msg.Body)
	}

	if msg, ok := n.Update(stateAt(t, p, day.Add(23*time.Hour))); ok {
		t.Fatalf("same fasting phase should be silent, got %+v", msg)
	}
}

func TestNotifierWithGaps(t *testing.T) {
	p := plan.Default()
	p.GapMinutes = 60
	day := time.Date(2026, 10, 26, 0, 0, 0, 0, time.UTC)
	n := New()

	if _, ok := n.Update(stateAt(t, p, day.Add(14*time.Hour+30*time.Minute))); ok {
		t.Fatal("first update should prime")
	}

	msg, ok := n.Update(stateAt(t, p, day.Add(15*time.Hour)))
	if !ok {
		t.Fatal("expected meal two message")
	}
	if msg.Title != "Eating time" || !strings.Contains(msg.Body, "Meal 2 of 3") {
		t.Fatalf("message = %+v", msg)
	}

	msg, ok = n.Update(stateAt(t, p, day.Add(17*time.Hour)))
	if !ok {
		t.Fatal("expected gap message")
	}
	if msg.Title != "Fasting started" || !strings.Contains(msg.Body, "today at 18:00") {
		t.Fatalf("message = %+v", msg)
	}

	msg, ok = n.Update(stateAt(t, p, day.Add(18*time.Hour)))
	if !ok {
		t.Fatal("expected meal three message")
	}
	if msg.Title != "Eating time" || !strings.Contains(msg.Body, "Meal 3 of 3") {
		t.Fatalf("message = %+v", msg)
	}
}

func TestResetPrimesAgain(t *testing.T) {
	p := plan.Default()
	day := time.Date(2026, 10, 26, 0, 0, 0, 0, time.UTC)
	n := New()

	n.Update(stateAt(t, p, day.Add(10*time.Hour)))
	if _, ok := n.Update(stateAt(t, p, day.Add(12*time.Hour))); !ok {
		t.Fatal("expected first transition")
	}

	n.Reset()
	if msg, ok := n.Update(stateAt(t, p, day.Add(12*time.Hour))); ok {
		t.Fatalf("reset should suppress message, got %+v", msg)
	}
	if _, ok := n.Update(stateAt(t, p, day.Add(18*time.Hour))); !ok {
		t.Fatal("expected transition after reset")
	}
}

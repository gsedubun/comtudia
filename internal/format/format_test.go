package format

import (
	"testing"
	"time"
)

func TestCountdown(t *testing.T) {
	tests := []struct {
		d    time.Duration
		want string
	}{
		{-time.Second, "00:00:00"},
		{0, "00:00:00"},
		{time.Second, "00:00:01"},
		{59*time.Minute + 59*time.Second, "00:59:59"},
		{17*time.Hour + 42*time.Minute + 10*time.Second, "17:42:10"},
		{23*time.Hour + 59*time.Minute + 59*time.Second, "23:59:59"},
		{17*time.Hour + 42*time.Minute + 10*time.Second + 900*time.Millisecond, "17:42:10"},
	}
	for _, tt := range tests {
		if got := Countdown(tt.d); got != tt.want {
			t.Errorf("Countdown(%v) = %q, want %q", tt.d, got, tt.want)
		}
	}
}

func TestDuration(t *testing.T) {
	tests := []struct {
		d    time.Duration
		want string
	}{
		{-time.Minute, "0m"},
		{0, "0m"},
		{30 * time.Second, "1m"},
		{time.Minute, "1m"},
		{59 * time.Minute, "59m"},
		{time.Hour, "1h"},
		{time.Hour + 30*time.Minute, "1h 30m"},
		{18 * time.Hour, "18h"},
		{17*time.Hour + 42*time.Minute + 30*time.Second, "17h 43m"},
	}
	for _, tt := range tests {
		if got := Duration(tt.d); got != tt.want {
			t.Errorf("Duration(%v) = %q, want %q", tt.d, got, tt.want)
		}
	}
}

func TestDurationCompact(t *testing.T) {
	tests := []struct {
		d    time.Duration
		want string
	}{
		{0, "0m"},
		{45 * time.Minute, "45m"},
		{time.Hour, "1h"},
		{90 * time.Minute, "1h 30m"},
		{2 * time.Hour, "2h"},
	}
	for _, tt := range tests {
		if got := DurationCompact(tt.d); got != tt.want {
			t.Errorf("DurationCompact(%v) = %q, want %q", tt.d, got, tt.want)
		}
	}
}

func TestClockMinutes(t *testing.T) {
	tests := []struct {
		m    int
		want string
	}{
		{0, "00:00"},
		{90, "01:30"},
		{750, "12:30"},
		{1439, "23:59"},
		{1440, "00:00"},
		{1500, "01:00"},
		{-60, "23:00"},
	}
	for _, tt := range tests {
		if got := ClockMinutes(tt.m); got != tt.want {
			t.Errorf("ClockMinutes(%d) = %q, want %q", tt.m, got, tt.want)
		}
	}
}

func TestRanges(t *testing.T) {
	start := time.Date(2026, 10, 26, 12, 0, 0, 0, time.UTC)
	end := time.Date(2026, 10, 26, 14, 30, 0, 0, time.UTC)
	if got := Range(start, end); got != "12:00–14:30" {
		t.Fatalf("Range = %q", got)
	}

	next := time.Date(2026, 10, 27, 1, 0, 0, 0, time.UTC)
	if got := Range(start, next); got != "12:00–01:00 (+1)" {
		t.Fatalf("Range across midnight = %q", got)
	}

	if got := RangeMinutes(12*60, 14*60); got != "12:00–14:00" {
		t.Fatalf("RangeMinutes = %q", got)
	}
	if got := RangeMinutes(23*60, 25*60); got != "23:00–01:00 (+1)" {
		t.Fatalf("RangeMinutes across midnight = %q", got)
	}
}

func TestDayClockAndWhen(t *testing.T) {
	now := time.Date(2026, 10, 26, 10, 0, 0, 0, time.UTC)
	today := time.Date(2026, 10, 26, 12, 0, 0, 0, time.UTC)
	tomorrow := time.Date(2026, 10, 27, 12, 0, 0, 0, time.UTC)
	yesterday := time.Date(2026, 10, 25, 18, 0, 0, 0, time.UTC)
	later := time.Date(2026, 10, 29, 8, 30, 0, 0, time.UTC)

	if got := DayClock(today, now); got != "Today 12:00" {
		t.Fatalf("today = %q", got)
	}
	if got := DayClock(tomorrow, now); got != "Tomorrow 12:00" {
		t.Fatalf("tomorrow = %q", got)
	}
	if got := DayClock(yesterday, now); got != "Yesterday 18:00" {
		t.Fatalf("yesterday = %q", got)
	}
	if got := DayClock(later, now); got != "Thu 08:30" {
		t.Fatalf("later = %q", got)
	}
	if got := When(tomorrow, now); got != "tomorrow at 12:00" {
		t.Fatalf("when tomorrow = %q", got)
	}
	if got := When(today, now); got != "today at 12:00" {
		t.Fatalf("when today = %q", got)
	}
}

func TestDayNameAndSameDate(t *testing.T) {
	now := time.Date(2026, 10, 26, 10, 0, 0, 0, time.UTC)
	if !SameDate(now, time.Date(2026, 10, 26, 23, 0, 0, 0, time.UTC)) {
		t.Fatal("expected same date")
	}
	if SameDate(now, time.Date(2026, 10, 27, 0, 0, 0, 0, time.UTC)) {
		t.Fatal("expected different date")
	}
	if got := DayName(time.Date(2026, 10, 26, 12, 0, 0, 0, time.UTC), now); got != "Today" {
		t.Fatalf("DayName = %q", got)
	}
	if got := DayName(time.Date(2026, 10, 27, 12, 0, 0, 0, time.UTC), now); got != "Tomorrow" {
		t.Fatalf("DayName = %q", got)
	}
	if got := DayName(time.Date(2026, 10, 25, 12, 0, 0, 0, time.UTC), now); got != "Yesterday" {
		t.Fatalf("DayName = %q", got)
	}
	if got := DayName(time.Date(2026, 10, 29, 12, 0, 0, 0, time.UTC), now); got != "Thursday" {
		t.Fatalf("DayName = %q", got)
	}
}

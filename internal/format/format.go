package format

import (
	"fmt"
	"math"
	"time"
)

// Countdown formats a duration as HH:MM:SS, truncating partial seconds.
func Countdown(d time.Duration) string {
	if d <= 0 {
		return "00:00:00"
	}
	total := int(d / time.Second)
	h := total / 3600
	m := (total % 3600) / 60
	s := total % 60
	return fmt.Sprintf("%02d:%02d:%02d", h, m, s)
}

// Duration formats a duration for cards and tray labels, e.g. "17h 42m".
func Duration(d time.Duration) string {
	if d <= 0 {
		return "0m"
	}
	d = d.Round(time.Minute)
	if d < time.Minute {
		return "1m"
	}
	h := int(d / time.Hour)
	m := int((d % time.Hour) / time.Minute)
	if h == 0 {
		return fmt.Sprintf("%dm", m)
	}
	if m == 0 {
		return fmt.Sprintf("%dh", h)
	}
	return fmt.Sprintf("%dh %02dm", h, m)
}

// DurationCompact formats a precise minute-ish duration, e.g. "2h" or "1h 30m".
func DurationCompact(d time.Duration) string {
	if d <= 0 {
		return "0m"
	}
	total := int(math.Round(d.Minutes()))
	h := total / 60
	m := total % 60
	if h == 0 {
		return fmt.Sprintf("%dm", m)
	}
	if m == 0 {
		return fmt.Sprintf("%dh", h)
	}
	return fmt.Sprintf("%dh %02dm", h, m)
}

// Minutes formats an integer number of minutes as a duration.
func Minutes(minutes int) string {
	return Duration(time.Duration(minutes) * time.Minute)
}

// Clock formats a time as 24-hour HH:MM.
func Clock(t time.Time) string {
	return t.Format("15:04")
}

// ClockMinutes formats minutes after midnight (which may overflow into
// following days) as HH:MM.
func ClockMinutes(minutes int) string {
	minutes = ((minutes % (24 * 60)) + 24*60) % (24 * 60)
	return fmt.Sprintf("%02d:%02d", minutes/60, minutes%60)
}

// Range formats two times as a range, noting a following day when needed.
func Range(start, end time.Time) string {
	suffix := ""
	if !sameDate(start, end) {
		suffix = " (+1)"
	}
	return Clock(start) + "–" + Clock(end) + suffix
}

// RangeMinutes formats minute offsets from the start of the eating window.
func RangeMinutes(start, end int) string {
	suffix := ""
	if end/(24*60) > start/(24*60) {
		suffix = " (+1)"
	}
	return ClockMinutes(start) + "–" + ClockMinutes(end) + suffix
}

// DayClock formats a time relative to now, e.g. "Today 12:00".
func DayClock(t, now time.Time) string {
	switch {
	case sameDate(t, now):
		return "Today " + Clock(t)
	case sameDate(t, now.AddDate(0, 0, 1)):
		return "Tomorrow " + Clock(t)
	case sameDate(t, now.AddDate(0, 0, -1)):
		return "Yesterday " + Clock(t)
	default:
		return t.Format("Mon 15:04")
	}
}

// When formats a time for a sentence, e.g. "tomorrow at 12:00".
func When(t, now time.Time) string {
	switch {
	case sameDate(t, now):
		return "today at " + Clock(t)
	case sameDate(t, now.AddDate(0, 0, 1)):
		return "tomorrow at " + Clock(t)
	case sameDate(t, now.AddDate(0, 0, -1)):
		return "yesterday at " + Clock(t)
	default:
		return t.Format("Mon") + " at " + Clock(t)
	}
}

// SameDate reports whether two times fall on the same calendar date.
func SameDate(a, b time.Time) bool {
	return sameDate(a, b)
}

// DayName names a date relative to now: Today, Tomorrow, Yesterday, or the
// weekday name.
func DayName(t, now time.Time) string {
	switch {
	case sameDate(t, now):
		return "Today"
	case sameDate(t, now.AddDate(0, 0, 1)):
		return "Tomorrow"
	case sameDate(t, now.AddDate(0, 0, -1)):
		return "Yesterday"
	default:
		return t.Format("Monday")
	}
}

func sameDate(a, b time.Time) bool {
	ay, am, ad := a.Date()
	by, bm, bd := b.Date()
	return ay == by && am == bm && ad == bd
}

package plan

import "fmt"

const (
	// Version is the current persisted plan schema version.
	Version = 1

	MinutesPerDay = 24 * 60
	MaxMeals      = 8
	MinMealMinute = 15
	MinEating     = 60
	MinFasting    = 60

	DefaultEatingMinutes    = 6 * 60
	DefaultMeals            = 3
	DefaultGapMinutes       = 0
	DefaultWindowStart      = 12 * 60
	DefaultNotifications    = true
	DefaultRunInTray        = false
	DefaultWindowStartLabel = "12:00"
)

// Plan describes a recurring daily eating schedule.
type Plan struct {
	Version            int  `json:"version"`
	EatingMinutes      int  `json:"eating_minutes"`
	Meals              int  `json:"meals"`
	GapMinutes         int  `json:"gap_minutes"`
	WindowStartMinutes int  `json:"window_start_minutes"`
	Notifications      bool `json:"notifications"`
	RunInTray          bool `json:"run_in_tray"`
}

// Preset is a named starting point for the settings screen.
type Preset struct {
	Name          string
	EatingMinutes int
	Meals         int
	GapMinutes    int
}

var Presets = []Preset{
	{Name: "16:8", EatingMinutes: 8 * 60, Meals: 2},
	{Name: "18:6", EatingMinutes: 6 * 60, Meals: 3},
	{Name: "20:4", EatingMinutes: 4 * 60, Meals: 2},
}

// Default returns the plan used on first launch and by Reset.
func Default() Plan {
	return Plan{
		Version:            Version,
		EatingMinutes:      DefaultEatingMinutes,
		Meals:              DefaultMeals,
		GapMinutes:         DefaultGapMinutes,
		WindowStartMinutes: DefaultWindowStart,
		Notifications:      DefaultNotifications,
		RunInTray:          DefaultRunInTray,
	}
}

// ValidationError describes an invalid plan field.
type ValidationError struct {
	Field   string
	Message string
}

func (e *ValidationError) Error() string {
	return e.Message
}

// Validate checks that the plan can be laid out inside a 24-hour day.
func (p Plan) Validate() error {
	if p.Meals < 1 || p.Meals > MaxMeals {
		return &ValidationError{Field: "meals", Message: fmt.Sprintf("meal count must be between 1 and %d", MaxMeals)}
	}
	if p.EatingMinutes < MinEating || p.EatingMinutes > MinutesPerDay-MinFasting {
		return &ValidationError{Field: "eating", Message: "eating time must be between 1h and 23h"}
	}
	if p.WindowStartMinutes < 0 || p.WindowStartMinutes >= MinutesPerDay {
		return &ValidationError{Field: "window_start", Message: "start time must be within the day"}
	}
	if p.GapMinutes < 0 {
		return &ValidationError{Field: "gap", Message: "gap cannot be negative"}
	}
	if p.Meals == 1 && p.GapMinutes != 0 {
		return &ValidationError{Field: "gap", Message: "a single meal cannot have a gap"}
	}
	if p.EatingMinutes/p.Meals < MinMealMinute {
		return &ValidationError{Field: "meals", Message: fmt.Sprintf("each meal must be at least %d minutes", MinMealMinute)}
	}
	if p.SpanMinutes() > MinutesPerDay-MinFasting {
		return &ValidationError{Field: "gap", Message: "the meal spread must be 23 hours or less"}
	}
	return nil
}

// FastingMinutes is the total non-eating time in a day.
func (p Plan) FastingMinutes() int {
	return MinutesPerDay - p.EatingMinutes
}

// SpanMinutes is the time from the first meal start to the last meal end.
func (p Plan) SpanMinutes() int {
	if p.Meals <= 0 {
		return 0
	}
	return p.EatingMinutes + (p.Meals-1)*p.GapMinutes
}

// LongFastMinutes is the time outside the whole meal spread.
func (p Plan) LongFastMinutes() int {
	n := MinutesPerDay - p.SpanMinutes()
	if n < 0 {
		return 0
	}
	return n
}

// MealRange is one eating slot relative to the start of the eating window.
type MealRange struct {
	Index           int
	StartMinutes    int
	EndMinutes      int
	DurationMinutes int
}

// MealRanges splits the eating time into equal slots, giving any leftover
// minute to the earliest slots.
func (p Plan) MealRanges() []MealRange {
	if p.Meals <= 0 || p.EatingMinutes < p.Meals {
		return nil
	}

	base := p.EatingMinutes / p.Meals
	remainder := p.EatingMinutes % p.Meals
	out := make([]MealRange, 0, p.Meals)
	offset := 0
	for i := 0; i < p.Meals; i++ {
		duration := base
		if i < remainder {
			duration++
		}
		out = append(out, MealRange{
			Index:           i + 1,
			StartMinutes:    offset,
			EndMinutes:      offset + duration,
			DurationMinutes: duration,
		})
		offset += duration + p.GapMinutes
	}
	return out
}

// Summary returns a short human-readable plan description.
func (p Plan) Summary() string {
	return fmt.Sprintf("%s fast · %s eat", minutesLabel(p.FastingMinutes()), minutesLabel(p.EatingMinutes))
}

func minutesLabel(minutes int) string {
	if minutes <= 0 {
		return "0h"
	}
	h := minutes / 60
	m := minutes % 60
	if m == 0 {
		return fmt.Sprintf("%dh", h)
	}
	if h == 0 {
		return fmt.Sprintf("%dm", m)
	}
	return fmt.Sprintf("%dh %02dm", h, m)
}

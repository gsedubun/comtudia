package plan

import (
	"fmt"
	"sort"
	"time"
)

// Phase is the current part of the eating cycle.
type Phase int

const (
	PhaseFasting Phase = iota
	PhaseEating
)

func (p Phase) String() string {
	switch p {
	case PhaseEating:
		return "eating"
	case PhaseFasting:
		return "fasting"
	default:
		return "unknown"
	}
}

// Slot is one meal on a particular cycle day.
type Slot struct {
	Index int
	Day   time.Time
	Start time.Time
	End   time.Time
}

// Duration returns the length of the meal.
func (s Slot) Duration() time.Duration {
	return s.End.Sub(s.Start)
}

// State is the computed schedule at a single instant.
type State struct {
	Now                  time.Time
	Phase                Phase
	PhaseStart           time.Time
	PhaseEnd             time.Time
	CurrentMeal          *Slot
	NextMeal             Slot
	NextEatingPhaseStart time.Time
	NextEatingPhaseMeal  int
	NextWindowStart      time.Time
	FirstMealIndex       int
	LastMealIndex        int
	MealCount            int
	Remaining            time.Duration
	Progress             float64
	FastingTotal         time.Duration
	LongFast             time.Duration
}

// PhaseKey identifies a contiguous phase. It changes exactly when
// notifications should fire.
func (s State) PhaseKey() string {
	return fmt.Sprintf("%d/%d", s.Phase, s.PhaseStart.UnixNano())
}

// SlotsForDate returns the meals belonging to the cycle that starts on day.
// The day is normalized to local midnight. Meal durations are preserved across
// DST transitions, so a later meal can shift relative to the wall clock on the
// day the clock changes.
func SlotsForDate(p Plan, day time.Time) []Slot {
	ranges := p.MealRanges()
	if len(ranges) == 0 {
		return nil
	}

	y, m, d := day.Date()
	dayStart := time.Date(y, m, d, 0, 0, 0, 0, day.Location())
	windowStart := wallTime(dayStart, p.WindowStartMinutes)
	slots := make([]Slot, 0, len(ranges))
	for _, r := range ranges {
		start := windowStart.Add(time.Duration(r.StartMinutes) * time.Minute)
		end := windowStart.Add(time.Duration(r.EndMinutes) * time.Minute)
		slots = append(slots, Slot{
			Index: r.Index,
			Day:   dayStart,
			Start: start,
			End:   end,
		})
	}
	return slots
}

// wallTime converts a wall-clock minute offset from local midnight into a
// time, normalizing into following days when necessary.
func wallTime(dayStart time.Time, minutes int) time.Time {
	days := minutes / MinutesPerDay
	rem := minutes % MinutesPerDay
	date := dayStart.AddDate(0, 0, days)
	return time.Date(date.Year(), date.Month(), date.Day(), rem/60, rem%60, 0, 0, dayStart.Location())
}

// Compute calculates the current phase and the next events around now.
func Compute(p Plan, now time.Time) (State, error) {
	if err := p.Validate(); err != nil {
		return State{Now: now}, err
	}

	loc := now.Location()
	day := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, loc)
	slots := make([]Slot, 0, p.Meals*3)
	for _, d := range []time.Time{day.AddDate(0, 0, -1), day, day.AddDate(0, 0, 1)} {
		slots = append(slots, SlotsForDate(p, d)...)
	}
	sort.SliceStable(slots, func(i, j int) bool {
		return slots[i].Start.Before(slots[j].Start)
	})

	runStart := make([]int, len(slots))
	runEnd := make([]int, len(slots))
	for i := range slots {
		if i > 0 && slots[i].Start.Equal(slots[i-1].End) {
			runStart[i] = runStart[i-1]
		} else {
			runStart[i] = i
		}
	}
	for i := len(slots) - 1; i >= 0; i-- {
		if i < len(slots)-1 && slots[i].End.Equal(slots[i+1].Start) {
			runEnd[i] = runEnd[i+1]
		} else {
			runEnd[i] = i
		}
	}

	st := State{
		Now:          now,
		MealCount:    p.Meals,
		FastingTotal: time.Duration(p.FastingMinutes()) * time.Minute,
		LongFast:     time.Duration(p.LongFastMinutes()) * time.Minute,
	}

	current := -1
	for i := range slots {
		if !now.Before(slots[i].Start) && now.Before(slots[i].End) {
			current = i
			break
		}
	}

	if current >= 0 {
		first := runStart[current]
		last := runEnd[current]
		meal := slots[current]
		st.Phase = PhaseEating
		st.CurrentMeal = &meal
		st.PhaseStart = slots[first].Start
		st.PhaseEnd = slots[last].End
		st.FirstMealIndex = slots[first].Index
		st.LastMealIndex = slots[last].Index
		if current+1 < len(slots) {
			st.NextMeal = slots[current+1]
		}
		if last+1 < len(slots) {
			st.NextEatingPhaseStart = slots[last+1].Start
			st.NextEatingPhaseMeal = slots[last+1].Index
		}
	} else {
		next := -1
		for i := range slots {
			if slots[i].Start.After(now) {
				next = i
				break
			}
		}
		if next < 0 {
			return State{Now: now}, fmt.Errorf("no upcoming meal found")
		}

		st.Phase = PhaseFasting
		st.NextMeal = slots[next]
		st.PhaseEnd = slots[next].Start
		st.FirstMealIndex = slots[next].Index
		st.LastMealIndex = slots[runEnd[next]].Index
		st.NextEatingPhaseStart = slots[next].Start
		st.NextEatingPhaseMeal = slots[next].Index

		previousEnd := time.Time{}
		for i := range slots {
			if !slots[i].End.After(now) {
				previousEnd = slots[i].End
			}
		}
		if previousEnd.IsZero() {
			previousEnd = st.PhaseEnd
		}
		st.PhaseStart = previousEnd
	}

	for i := range slots {
		if slots[i].Index != 1 {
			continue
		}
		if slots[i].Start.After(now) {
			st.NextWindowStart = slots[i].Start
			break
		}
	}

	remaining := st.PhaseEnd.Sub(now)
	if remaining < 0 {
		remaining = 0
	}
	st.Remaining = remaining

	if total := st.PhaseEnd.Sub(st.PhaseStart); total > 0 {
		progress := float64(now.Sub(st.PhaseStart)) / float64(total)
		switch {
		case progress < 0:
			progress = 0
		case progress > 1:
			progress = 1
		}
		st.Progress = progress
	}

	return st, nil
}

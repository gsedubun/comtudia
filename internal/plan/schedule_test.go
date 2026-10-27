package plan

import (
	"math"
	"testing"
	"time"
)

func mustCompute(t *testing.T, p Plan, now time.Time) State {
	t.Helper()
	st, err := Compute(p, now)
	if err != nil {
		t.Fatalf("Compute: %v", err)
	}
	return st
}

func almostEqual(a, b float64) bool {
	return math.Abs(a-b) < 1e-9
}

func TestComputeContiguousPlan(t *testing.T) {
	p := Default()
	day := time.Date(2026, 10, 26, 0, 0, 0, 0, time.UTC)

	t.Run("before window", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(10*time.Hour))
		if st.Phase != PhaseFasting {
			t.Fatalf("phase = %v, want fasting", st.Phase)
		}
		if !st.PhaseStart.Equal(day.AddDate(0, 0, -1).Add(18 * time.Hour)) {
			t.Fatalf("phase start = %v", st.PhaseStart)
		}
		if !st.PhaseEnd.Equal(day.Add(12 * time.Hour)) {
			t.Fatalf("phase end = %v", st.PhaseEnd)
		}
		if st.Remaining != 2*time.Hour {
			t.Fatalf("remaining = %v, want 2h", st.Remaining)
		}
		if !almostEqual(st.Progress, 16.0/18.0) {
			t.Fatalf("progress = %v, want %v", st.Progress, 16.0/18.0)
		}
		if !st.NextMeal.Start.Equal(day.Add(12 * time.Hour)) {
			t.Fatalf("next meal = %v", st.NextMeal.Start)
		}
		if !st.NextWindowStart.Equal(day.Add(12 * time.Hour)) {
			t.Fatalf("next window = %v", st.NextWindowStart)
		}
	})

	t.Run("window start", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(12*time.Hour))
		if st.Phase != PhaseEating {
			t.Fatalf("phase = %v, want eating", st.Phase)
		}
		if st.CurrentMeal == nil || st.CurrentMeal.Index != 1 {
			t.Fatalf("current meal = %+v, want meal 1", st.CurrentMeal)
		}
		if !st.PhaseStart.Equal(day.Add(12*time.Hour)) || !st.PhaseEnd.Equal(day.Add(18*time.Hour)) {
			t.Fatalf("phase = %v..%v", st.PhaseStart, st.PhaseEnd)
		}
		if st.FirstMealIndex != 1 || st.LastMealIndex != 3 {
			t.Fatalf("run = %d..%d, want 1..3", st.FirstMealIndex, st.LastMealIndex)
		}
		if st.Remaining != 6*time.Hour {
			t.Fatalf("remaining = %v, want 6h", st.Remaining)
		}
		if st.Progress != 0 {
			t.Fatalf("progress = %v, want 0", st.Progress)
		}
		if !st.NextMeal.Start.Equal(day.Add(14 * time.Hour)) {
			t.Fatalf("next meal = %v", st.NextMeal.Start)
		}
		if !st.NextEatingPhaseStart.Equal(day.AddDate(0, 0, 1).Add(12 * time.Hour)) {
			t.Fatalf("next eating phase = %v", st.NextEatingPhaseStart)
		}
		if !st.NextWindowStart.Equal(day.AddDate(0, 0, 1).Add(12 * time.Hour)) {
			t.Fatalf("next window = %v", st.NextWindowStart)
		}
	})

	t.Run("middle meal", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(14*time.Hour))
		if st.Phase != PhaseEating || st.CurrentMeal == nil || st.CurrentMeal.Index != 2 {
			t.Fatalf("phase = %v, meal = %+v", st.Phase, st.CurrentMeal)
		}
		if !almostEqual(st.Progress, 1.0/3.0) {
			t.Fatalf("progress = %v, want 1/3", st.Progress)
		}
	})

	t.Run("window end", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(18*time.Hour))
		if st.Phase != PhaseFasting {
			t.Fatalf("phase = %v, want fasting", st.Phase)
		}
		if !st.PhaseStart.Equal(day.Add(18 * time.Hour)) {
			t.Fatalf("phase start = %v", st.PhaseStart)
		}
		if !st.NextMeal.Start.Equal(day.AddDate(0, 0, 1).Add(12 * time.Hour)) {
			t.Fatalf("next meal = %v", st.NextMeal.Start)
		}
		if st.Progress != 0 {
			t.Fatalf("progress = %v, want 0", st.Progress)
		}
	})

	t.Run("one second before close", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(18*time.Hour-time.Second))
		if st.Phase != PhaseEating {
			t.Fatalf("phase = %v, want eating", st.Phase)
		}
		if st.Remaining != time.Second {
			t.Fatalf("remaining = %v, want 1s", st.Remaining)
		}
	})
}

func TestComputeWithGaps(t *testing.T) {
	p := Default()
	p.GapMinutes = 60
	day := time.Date(2026, 10, 26, 0, 0, 0, 0, time.UTC)

	t.Run("meal one", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(12*time.Hour))
		if st.Phase != PhaseEating || st.CurrentMeal == nil || st.CurrentMeal.Index != 1 {
			t.Fatalf("phase = %v, meal = %+v", st.Phase, st.CurrentMeal)
		}
		if st.FirstMealIndex != 1 || st.LastMealIndex != 1 {
			t.Fatalf("run = %d..%d", st.FirstMealIndex, st.LastMealIndex)
		}
		if !st.NextEatingPhaseStart.Equal(day.Add(15 * time.Hour)) {
			t.Fatalf("next eating phase = %v", st.NextEatingPhaseStart)
		}
	})

	t.Run("gap", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(14*time.Hour+30*time.Minute))
		if st.Phase != PhaseFasting {
			t.Fatalf("phase = %v, want fasting", st.Phase)
		}
		if !st.PhaseStart.Equal(day.Add(14*time.Hour)) || !st.PhaseEnd.Equal(day.Add(15*time.Hour)) {
			t.Fatalf("phase = %v..%v", st.PhaseStart, st.PhaseEnd)
		}
		if st.Remaining != 30*time.Minute {
			t.Fatalf("remaining = %v", st.Remaining)
		}
		if !almostEqual(st.Progress, 0.5) {
			t.Fatalf("progress = %v, want 0.5", st.Progress)
		}
		if !st.NextMeal.Start.Equal(day.Add(15 * time.Hour)) {
			t.Fatalf("next meal = %v", st.NextMeal.Start)
		}
		if st.FirstMealIndex != 2 || st.LastMealIndex != 2 {
			t.Fatalf("next run = %d..%d, want 2..2", st.FirstMealIndex, st.LastMealIndex)
		}
	})

	t.Run("meal two", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(15*time.Hour))
		if st.Phase != PhaseEating || st.CurrentMeal == nil || st.CurrentMeal.Index != 2 {
			t.Fatalf("phase = %v, meal = %+v", st.Phase, st.CurrentMeal)
		}
		if !st.NextEatingPhaseStart.Equal(day.Add(18 * time.Hour)) {
			t.Fatalf("next eating phase = %v", st.NextEatingPhaseStart)
		}
	})

	t.Run("after last meal", func(t *testing.T) {
		st := mustCompute(t, p, day.Add(20*time.Hour))
		if st.Phase != PhaseFasting {
			t.Fatalf("phase = %v", st.Phase)
		}
		if !st.PhaseStart.Equal(day.Add(20 * time.Hour)) {
			t.Fatalf("phase start = %v", st.PhaseStart)
		}
		if st.LongFast != 16*time.Hour {
			t.Fatalf("long fast = %v, want 16h", st.LongFast)
		}
		if st.FastingTotal != 18*time.Hour {
			t.Fatalf("fasting total = %v, want 18h", st.FastingTotal)
		}
	})
}

func TestComputeWindowCrossingMidnight(t *testing.T) {
	p := Default()
	p.WindowStartMinutes = 23 * 60
	day := time.Date(2026, 10, 26, 0, 0, 0, 0, time.UTC)

	t.Run("current meal from yesterday", func(t *testing.T) {
		st := mustCompute(t, p, day.AddDate(0, 0, 1).Add(30*time.Minute))
		if st.Phase != PhaseEating || st.CurrentMeal == nil {
			t.Fatalf("phase = %v, meal = %+v", st.Phase, st.CurrentMeal)
		}
		if st.CurrentMeal.Index != 1 {
			t.Fatalf("meal index = %d, want 1", st.CurrentMeal.Index)
		}
		if !st.CurrentMeal.Day.Equal(day) {
			t.Fatalf("meal day = %v, want %v", st.CurrentMeal.Day, day)
		}
		if !st.PhaseStart.Equal(day.Add(23 * time.Hour)) {
			t.Fatalf("phase start = %v", st.PhaseStart)
		}
		if !st.PhaseEnd.Equal(day.AddDate(0, 0, 1).Add(5 * time.Hour)) {
			t.Fatalf("phase end = %v", st.PhaseEnd)
		}
		if !st.NextWindowStart.Equal(day.AddDate(0, 0, 1).Add(23 * time.Hour)) {
			t.Fatalf("next window = %v", st.NextWindowStart)
		}
	})

	t.Run("morning fast", func(t *testing.T) {
		st := mustCompute(t, p, day.AddDate(0, 0, 1).Add(6*time.Hour))
		if st.Phase != PhaseFasting {
			t.Fatalf("phase = %v", st.Phase)
		}
		if !st.NextMeal.Start.Equal(day.AddDate(0, 0, 1).Add(23 * time.Hour)) {
			t.Fatalf("next meal = %v", st.NextMeal.Start)
		}
		if !st.NextWindowStart.Equal(day.AddDate(0, 0, 1).Add(23 * time.Hour)) {
			t.Fatalf("next window = %v", st.NextWindowStart)
		}
	})
}

func TestSlotsAcrossDST(t *testing.T) {
	loc, err := time.LoadLocation("America/New_York")
	if err != nil {
		t.Skip("tzdata not available")
	}

	p := Default()
	p.WindowStartMinutes = 23 * 60
	day := time.Date(2026, 3, 8, 12, 0, 0, 0, loc)
	slots := SlotsForDate(p, day)
	if len(slots) != 3 {
		t.Fatalf("got %d slots, want 3", len(slots))
	}
	wantStarts := []string{"23:00", "01:00", "03:00"}
	wantEnds := []string{"01:00", "03:00", "05:00"}
	for i, s := range slots {
		if got := s.Start.Format("15:04"); got != wantStarts[i] {
			t.Fatalf("slot %d start = %s, want %s", i, got, wantStarts[i])
		}
		if got := s.End.Format("15:04"); got != wantEnds[i] {
			t.Fatalf("slot %d end = %s, want %s", i, got, wantEnds[i])
		}
		if !s.End.After(s.Start) {
			t.Fatalf("slot %d is empty: %v..%v", i, s.Start, s.End)
		}
		if i > 0 && s.Start.Before(slots[i-1].End) {
			t.Fatalf("slot %d overlaps previous slot", i)
		}
	}
}

func TestSlotsAcrossSpringForward(t *testing.T) {
	loc, err := time.LoadLocation("America/New_York")
	if err != nil {
		t.Skip("tzdata not available")
	}

	p := Plan{
		EatingMinutes:      120,
		Meals:              1,
		WindowStartMinutes: 90, // 01:30
	}
	day := time.Date(2026, 3, 8, 12, 0, 0, 0, loc)
	slots := SlotsForDate(p, day)
	if len(slots) != 1 {
		t.Fatalf("got %d slots, want 1", len(slots))
	}
	s := slots[0]
	if got := s.Start.Format("15:04"); got != "01:30" {
		t.Fatalf("start = %s, want 01:30", got)
	}
	if got := s.End.Format("15:04"); got != "04:30" {
		t.Fatalf("end = %s, want 04:30", got)
	}
	if !s.End.After(s.Start) {
		t.Fatalf("meal is empty: %v..%v", s.Start, s.End)
	}
	if d := s.Duration(); d != 2*time.Hour {
		t.Fatalf("duration = %v, want 2h across spring forward", d)
	}
}

func TestComputeInvalidPlan(t *testing.T) {
	if _, err := Compute(Plan{}, time.Now()); err == nil {
		t.Fatal("expected validation error")
	}
}

func TestComputeAcrossSpringForward(t *testing.T) {
	loc, err := time.LoadLocation("America/New_York")
	if err != nil {
		t.Skip("tzdata not available")
	}

	p := Default()
	p.EatingMinutes = 120
	p.Meals = 1
	p.WindowStartMinutes = 90 // 01:30, before the spring-forward jump
	now := time.Date(2026, 3, 8, 3, 30, 0, 0, loc)
	st := mustCompute(t, p, now)
	if st.Phase != PhaseEating || st.CurrentMeal == nil || st.CurrentMeal.Index != 1 {
		t.Fatalf("phase = %v, meal = %+v", st.Phase, st.CurrentMeal)
	}
	if !st.PhaseStart.Equal(time.Date(2026, 3, 8, 1, 30, 0, 0, loc)) {
		t.Fatalf("phase start = %v", st.PhaseStart)
	}
	if !st.PhaseEnd.Equal(time.Date(2026, 3, 8, 4, 30, 0, 0, loc)) {
		t.Fatalf("phase end = %v", st.PhaseEnd)
	}
	if st.PhaseEnd.Sub(st.PhaseStart) != 2*time.Hour {
		t.Fatalf("phase duration = %v, want 2h", st.PhaseEnd.Sub(st.PhaseStart))
	}
	if st.Remaining != time.Hour {
		t.Fatalf("remaining = %v, want 1h", st.Remaining)
	}
	if !almostEqual(st.Progress, 0.5) {
		t.Fatalf("progress = %v, want 0.5", st.Progress)
	}
}

func TestSlotsWithGapAcrossSpringForward(t *testing.T) {
	loc, err := time.LoadLocation("America/New_York")
	if err != nil {
		t.Skip("tzdata not available")
	}

	p := Plan{
		EatingMinutes:      120,
		Meals:              2,
		GapMinutes:         30,
		WindowStartMinutes: 90, // 01:30
	}
	day := time.Date(2026, 3, 8, 12, 0, 0, 0, loc)
	slots := SlotsForDate(p, day)
	if len(slots) != 2 {
		t.Fatalf("got %d slots, want 2", len(slots))
	}
	for i, s := range slots {
		if d := s.Duration(); d != time.Hour {
			t.Fatalf("slot %d duration = %v, want 1h", i, d)
		}
	}
	if gap := slots[1].Start.Sub(slots[0].End); gap != 30*time.Minute {
		t.Fatalf("gap = %v, want 30m", gap)
	}
	if slots[1].Start.Before(slots[0].End) {
		t.Fatal("meals overlap across spring forward")
	}
}

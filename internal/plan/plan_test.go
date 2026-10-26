package plan

import "testing"

func TestDefaultValid(t *testing.T) {
	p := Default()
	if err := p.Validate(); err != nil {
		t.Fatalf("default plan should be valid: %v", err)
	}
	if p.FastingMinutes() != 18*60 {
		t.Fatalf("fasting = %d, want 1080", p.FastingMinutes())
	}
	if p.SpanMinutes() != 6*60 {
		t.Fatalf("span = %d, want 360", p.SpanMinutes())
	}
	if p.LongFastMinutes() != 18*60 {
		t.Fatalf("long fast = %d, want 1080", p.LongFastMinutes())
	}
}

func TestValidate(t *testing.T) {
	tests := []struct {
		name string
		plan Plan
		ok   bool
	}{
		{name: "default", plan: Default(), ok: true},
		{name: "no meals", plan: Plan{EatingMinutes: 360, Meals: 0, WindowStartMinutes: 720}, ok: false},
		{name: "too many meals", plan: Plan{EatingMinutes: 360, Meals: 9, WindowStartMinutes: 720}, ok: false},
		{name: "no eating", plan: Plan{EatingMinutes: 0, Meals: 1, WindowStartMinutes: 720}, ok: false},
		{name: "all day eating", plan: Plan{EatingMinutes: 1440, Meals: 3, WindowStartMinutes: 720}, ok: false},
		{name: "start before day", plan: Plan{EatingMinutes: 360, Meals: 3, WindowStartMinutes: -1}, ok: false},
		{name: "start after day", plan: Plan{EatingMinutes: 360, Meals: 3, WindowStartMinutes: 1440}, ok: false},
		{name: "negative gap", plan: Plan{EatingMinutes: 360, Meals: 3, GapMinutes: -1, WindowStartMinutes: 720}, ok: false},
		{name: "single meal gap", plan: Plan{EatingMinutes: 360, Meals: 1, GapMinutes: 60, WindowStartMinutes: 720}, ok: false},
		{name: "meal too short", plan: Plan{EatingMinutes: 60, Meals: 5, WindowStartMinutes: 720}, ok: false},
		{name: "gaps overflow day", plan: Plan{EatingMinutes: 360, Meals: 3, GapMinutes: 600, WindowStartMinutes: 720}, ok: false},
		{name: "gaps fill day", plan: Plan{EatingMinutes: 360, Meals: 3, GapMinutes: 540, WindowStartMinutes: 0}, ok: false},
		{name: "gaps leave too little outside time", plan: Plan{EatingMinutes: 1380, Meals: 2, GapMinutes: 1, WindowStartMinutes: 0}, ok: false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.plan.Validate()
			if tt.ok && err != nil {
				t.Fatalf("want valid, got %v", err)
			}
			if !tt.ok && err == nil {
				t.Fatal("want invalid, got nil")
			}
		})
	}
}

func TestMealRangesContiguous(t *testing.T) {
	p := Default()
	ranges := p.MealRanges()
	if len(ranges) != 3 {
		t.Fatalf("got %d ranges, want 3", len(ranges))
	}
	want := []MealRange{
		{Index: 1, StartMinutes: 0, EndMinutes: 120, DurationMinutes: 120},
		{Index: 2, StartMinutes: 120, EndMinutes: 240, DurationMinutes: 120},
		{Index: 3, StartMinutes: 240, EndMinutes: 360, DurationMinutes: 120},
	}
	for i := range want {
		if ranges[i] != want[i] {
			t.Fatalf("range %d = %+v, want %+v", i, ranges[i], want[i])
		}
	}
}

func TestMealRangesWithGap(t *testing.T) {
	p := Default()
	p.GapMinutes = 60
	ranges := p.MealRanges()
	want := []MealRange{
		{Index: 1, StartMinutes: 0, EndMinutes: 120, DurationMinutes: 120},
		{Index: 2, StartMinutes: 180, EndMinutes: 300, DurationMinutes: 120},
		{Index: 3, StartMinutes: 360, EndMinutes: 480, DurationMinutes: 120},
	}
	if len(ranges) != len(want) {
		t.Fatalf("got %d ranges, want %d", len(ranges), len(want))
	}
	for i := range want {
		if ranges[i] != want[i] {
			t.Fatalf("range %d = %+v, want %+v", i, ranges[i], want[i])
		}
	}
	if p.SpanMinutes() != 480 {
		t.Fatalf("span = %d, want 480", p.SpanMinutes())
	}
	if p.LongFastMinutes() != 16*60 {
		t.Fatalf("long fast = %d, want 960", p.LongFastMinutes())
	}
}

func TestMealRangesRemainder(t *testing.T) {
	p := Plan{EatingMinutes: 100, Meals: 3, WindowStartMinutes: 0}
	ranges := p.MealRanges()
	if len(ranges) != 3 {
		t.Fatalf("got %d ranges, want 3", len(ranges))
	}
	if ranges[0].DurationMinutes != 34 || ranges[1].DurationMinutes != 33 || ranges[2].DurationMinutes != 33 {
		t.Fatalf("durations = %d/%d/%d, want 34/33/33",
			ranges[0].DurationMinutes, ranges[1].DurationMinutes, ranges[2].DurationMinutes)
	}
	if ranges[2].EndMinutes != 100 {
		t.Fatalf("last end = %d, want 100", ranges[2].EndMinutes)
	}
}

func TestSummary(t *testing.T) {
	p := Default()
	if got := p.Summary(); got != "18h fast · 6h eat" {
		t.Fatalf("summary = %q", got)
	}
	p.GapMinutes = 60
	p.WindowStartMinutes = 8 * 60
	if got := p.Summary(); got != "18h fast · 6h eat" {
		t.Fatalf("summary = %q", got)
	}
}

package plan

import (
	"bytes"
	"encoding/json"
	"flag"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"testing"
	"time"

	"com.comtudia.app/internal/format"
)

// update regenerates the shared Android/desktop golden vectors:
//
//	go test ./internal/plan/ -run TestGoldenVectors -update
var update = flag.Bool("update", false, "update golden files")

// goldenPath is the canonical location of the cross-platform schedule vectors.
// The Android unit tests load the same file from their resources.
var goldenPath = filepath.Join(
	"..", "..", "android", "core", "src", "test", "resources", "schedule-golden.json",
)

type goldenPlan struct {
	Version            int  `json:"version"`
	EatingMinutes      int  `json:"eating_minutes"`
	Meals              int  `json:"meals"`
	GapMinutes         int  `json:"gap_minutes"`
	WindowStartMinutes int  `json:"window_start_minutes"`
	Notifications      bool `json:"notifications"`
	RunInTray          bool `json:"run_in_tray"`
}

type goldenSlot struct {
	Index int    `json:"index"`
	Start string `json:"start"`
	End   string `json:"end"`
}

type goldenState struct {
	Phase                string  `json:"phase"`
	PhaseStart           string  `json:"phaseStart"`
	PhaseEnd             string  `json:"phaseEnd"`
	CurrentMealIndex     *int    `json:"currentMealIndex"`
	NextMealIndex        *int    `json:"nextMealIndex"`
	FirstMealIndex       int     `json:"firstMealIndex"`
	LastMealIndex        int     `json:"lastMealIndex"`
	NextEatingPhaseStart *string `json:"nextEatingPhaseStart"`
	NextWindowStart      *string `json:"nextWindowStart"`
	RemainingSeconds     int64   `json:"remainingSeconds"`
	Progress             float64 `json:"progress"`
}

type goldenFormatted struct {
	Countdown     string `json:"countdown"`
	Duration      string `json:"duration"`
	ClockPhaseEnd string `json:"clockPhaseEnd"`
}

type goldenCase struct {
	Name      string          `json:"name"`
	Zone      string          `json:"zone"`
	Plan      goldenPlan      `json:"plan"`
	Now       string          `json:"now"`
	Slots     []goldenSlot    `json:"slots"`
	State     goldenState     `json:"state"`
	Formatted goldenFormatted `json:"formatted"`
}

type goldenFile struct {
	Cases []goldenCase `json:"cases"`
}

func TestGoldenVectors(t *testing.T) {
	want := buildGoldenVectors(t)

	if *update {
		data, err := marshalGolden(want)
		if err != nil {
			t.Fatalf("encode golden vectors: %v", err)
		}
		if err := os.MkdirAll(filepath.Dir(goldenPath), 0o755); err != nil {
			t.Fatalf("create golden directory: %v", err)
		}
		if err := os.WriteFile(goldenPath, data, 0o644); err != nil {
			t.Fatalf("write golden vectors: %v", err)
		}
		t.Logf("updated %s", goldenPath)
	}

	data, err := os.ReadFile(goldenPath)
	if err != nil {
		t.Fatalf("read golden vectors (run `go test ./internal/plan/ -run TestGoldenVectors -update`): %v", err)
	}

	var got goldenFile
	if err := json.Unmarshal(data, &got); err != nil {
		t.Fatalf("parse golden vectors: %v", err)
	}

	gotData, err := marshalGolden(got)
	if err != nil {
		t.Fatalf("encode golden vectors: %v", err)
	}
	wantData, err := marshalGolden(want)
	if err != nil {
		t.Fatalf("encode golden vectors: %v", err)
	}
	if !bytes.Equal(gotData, wantData) {
		t.Fatalf("golden vectors are stale; regenerate with:\n"+
			"  go test ./internal/plan/ -run TestGoldenVectors -update\n"+
			"%s", firstDifference(wantData, gotData))
	}
}

func marshalGolden(f goldenFile) ([]byte, error) {
	data, err := json.MarshalIndent(f, "", "  ")
	if err != nil {
		return nil, err
	}
	return append(data, '\n'), nil
}

func firstDifference(want, got []byte) string {
	wantLines := bytes.Split(want, []byte("\n"))
	gotLines := bytes.Split(got, []byte("\n"))
	for i := 0; i < len(wantLines) || i < len(gotLines); i++ {
		var w, g string
		if i < len(wantLines) {
			w = string(wantLines[i])
		}
		if i < len(gotLines) {
			g = string(gotLines[i])
		}
		if w != g {
			return "first difference on line " + strconv.Itoa(i+1) + ":\n" +
				"  want: " + w + "\n" +
				"  got:  " + g
		}
	}
	return "golden vectors differ"
}

func buildGoldenVectors(t *testing.T) goldenFile {
	t.Helper()

	berlin := loadZone(t, "Europe/Berlin")
	newYork := loadZone(t, "America/New_York")
	utc := time.UTC

	defaultPlan := Default()
	twoMeals := goldenPlanWith(8*60, 2, 0, 12*60)
	gapped := goldenPlanWith(4*60, 2, 30, 12*60)
	single := goldenPlanWith(60, 1, 0, 12*60)
	midnightWindow := goldenPlanWith(6*60, 3, 0, 22*60)
	midnightStart := goldenPlanWith(6*60, 3, 0, 0)
	shortWithGap := goldenPlanWith(60, 2, 15, 10*60)
	oneLongMeal := goldenPlanWith(120, 1, 0, 90)

	cases := []goldenCase{
		makeGoldenCase(t, "default-before-window", defaultPlan, time.Date(2026, 10, 26, 10, 0, 0, 0, utc)),
		makeGoldenCase(t, "default-window-start", defaultPlan, time.Date(2026, 10, 26, 12, 0, 0, 0, utc)),
		makeGoldenCase(t, "default-mid-meal", defaultPlan, time.Date(2026, 10, 26, 14, 0, 0, 0, utc)),
		makeGoldenCase(t, "default-window-end", defaultPlan, time.Date(2026, 10, 26, 18, 0, 0, 0, utc)),
		makeGoldenCase(t, "default-one-second-before-close", defaultPlan, time.Date(2026, 10, 26, 17, 59, 59, 0, utc)),
		makeGoldenCase(t, "sixteen-eight-two-meals", twoMeals, time.Date(2026, 10, 26, 15, 0, 0, 0, utc)),
		makeGoldenCase(t, "twenty-four-gap", gapped, time.Date(2026, 10, 26, 14, 15, 0, 0, utc)),
		makeGoldenCase(t, "twenty-four-second-meal", gapped, time.Date(2026, 10, 26, 15, 0, 0, 0, utc)),
		makeGoldenCase(t, "single-meal", single, time.Date(2026, 10, 26, 12, 30, 0, 0, utc)),
		makeGoldenCase(t, "single-meal-after", single, time.Date(2026, 10, 26, 20, 0, 0, 0, utc)),
		makeGoldenCase(t, "midnight-window-current-meal", midnightWindow, time.Date(2026, 10, 27, 0, 30, 0, 0, utc)),
		makeGoldenCase(t, "midnight-window-morning-fast", midnightWindow, time.Date(2026, 10, 27, 6, 0, 0, 0, utc)),
		makeGoldenCase(t, "window-at-midnight", midnightStart, time.Date(2026, 10, 26, 1, 0, 0, 0, utc)),
		makeGoldenCase(t, "short-eating-gap", shortWithGap, time.Date(2026, 10, 26, 10, 50, 0, 0, utc)),
		makeGoldenCase(t, "berlin-spring-forward-before", defaultPlan, time.Date(2026, 3, 29, 1, 30, 0, 0, berlin)),
		makeGoldenCase(t, "berlin-spring-forward-after", defaultPlan, time.Date(2026, 3, 29, 3, 30, 0, 0, berlin)),
		makeGoldenCase(t, "berlin-fall-back", defaultPlan, time.Date(2026, 10, 25, 2, 30, 0, 0, berlin)),
		makeGoldenCase(t, "berlin-fall-back-meal", oneLongMeal, time.Date(2026, 10, 25, 1, 45, 0, 0, berlin)),
		makeGoldenCase(t, "new-york-spring-forward", defaultPlan, time.Date(2026, 3, 8, 3, 30, 0, 0, newYork)),
		makeGoldenCase(t, "new-york-spring-forward-meal", oneLongMeal, time.Date(2026, 3, 8, 3, 30, 0, 0, newYork)),
		makeGoldenCase(t, "new-york-fall-back", defaultPlan, time.Date(2026, 11, 1, 1, 30, 0, 0, newYork)),
	}

	return goldenFile{Cases: cases}
}

func goldenPlanWith(eating, meals, gap, start int) Plan {
	return Plan{
		Version:            Version,
		EatingMinutes:      eating,
		Meals:              meals,
		GapMinutes:         gap,
		WindowStartMinutes: start,
		Notifications:      DefaultNotifications,
		RunInTray:          DefaultRunInTray,
	}
}

func loadZone(t *testing.T, name string) *time.Location {
	t.Helper()
	loc, err := time.LoadLocation(name)
	if err != nil {
		t.Fatalf("load %s: %v", name, err)
	}
	return loc
}

func makeGoldenCase(t *testing.T, name string, p Plan, now time.Time) goldenCase {
	t.Helper()

	st, err := Compute(p, now)
	if err != nil {
		t.Fatalf("%s: compute: %v", name, err)
	}

	loc := now.Location()
	day := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, loc)
	var raw []Slot
	for _, d := range []time.Time{day.AddDate(0, 0, -1), day, day.AddDate(0, 0, 1)} {
		raw = append(raw, SlotsForDate(p, d)...)
	}
	sort.SliceStable(raw, func(i, j int) bool { return raw[i].Start.Before(raw[j].Start) })

	slots := make([]goldenSlot, 0, len(raw))
	for _, s := range raw {
		slots = append(slots, goldenSlot{
			Index: s.Index,
			Start: s.Start.Format(time.RFC3339),
			End:   s.End.Format(time.RFC3339),
		})
	}

	var currentMeal *int
	if st.CurrentMeal != nil {
		index := st.CurrentMeal.Index
		currentMeal = &index
	}
	var nextMeal *int
	if !st.NextMeal.Start.IsZero() {
		index := st.NextMeal.Index
		nextMeal = &index
	}
	var nextEating *string
	if !st.NextEatingPhaseStart.IsZero() {
		value := st.NextEatingPhaseStart.Format(time.RFC3339)
		nextEating = &value
	}
	var nextWindow *string
	if !st.NextWindowStart.IsZero() {
		value := st.NextWindowStart.Format(time.RFC3339)
		nextWindow = &value
	}

	return goldenCase{
		Name:  name,
		Zone:  loc.String(),
		Plan:  toGoldenPlan(p),
		Now:   now.Format(time.RFC3339),
		Slots: slots,
		State: goldenState{
			Phase:                st.Phase.String(),
			PhaseStart:           st.PhaseStart.Format(time.RFC3339),
			PhaseEnd:             st.PhaseEnd.Format(time.RFC3339),
			CurrentMealIndex:     currentMeal,
			NextMealIndex:        nextMeal,
			FirstMealIndex:       st.FirstMealIndex,
			LastMealIndex:        st.LastMealIndex,
			NextEatingPhaseStart: nextEating,
			NextWindowStart:      nextWindow,
			RemainingSeconds:     int64(st.Remaining / time.Second),
			Progress:             st.Progress,
		},
		Formatted: goldenFormatted{
			Countdown:     format.Countdown(st.Remaining),
			Duration:      format.Duration(st.Remaining),
			ClockPhaseEnd: format.Clock(st.PhaseEnd),
		},
	}
}

func toGoldenPlan(p Plan) goldenPlan {
	return goldenPlan{
		Version:            p.Version,
		EatingMinutes:      p.EatingMinutes,
		Meals:              p.Meals,
		GapMinutes:         p.GapMinutes,
		WindowStartMinutes: p.WindowStartMinutes,
		Notifications:      p.Notifications,
		RunInTray:          p.RunInTray,
	}
}

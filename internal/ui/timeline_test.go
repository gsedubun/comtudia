package ui

import (
	"testing"
	"time"

	"com.comtudia.app/internal/plan"
)

func TestTimelineSegments(t *testing.T) {
	p := plan.Default()
	now := time.Date(2026, 10, 26, 10, 0, 0, 0, time.UTC)
	dayStart, dayEnd, segments := timelineSegments(p, now)
	if !dayStart.Equal(time.Date(2026, 10, 26, 0, 0, 0, 0, time.UTC)) {
		t.Fatalf("day start = %v", dayStart)
	}
	if !dayEnd.Equal(time.Date(2026, 10, 27, 0, 0, 0, 0, time.UTC)) {
		t.Fatalf("day end = %v", dayEnd)
	}
	if len(segments) != 3 {
		t.Fatalf("got %d segments, want 3", len(segments))
	}

	wantStart := []time.Time{
		time.Date(2026, 10, 26, 12, 0, 0, 0, time.UTC),
		time.Date(2026, 10, 26, 14, 0, 0, 0, time.UTC),
		time.Date(2026, 10, 26, 16, 0, 0, 0, time.UTC),
	}
	wantEnd := []time.Time{
		time.Date(2026, 10, 26, 14, 0, 0, 0, time.UTC),
		time.Date(2026, 10, 26, 16, 0, 0, 0, time.UTC),
		time.Date(2026, 10, 26, 18, 0, 0, 0, time.UTC),
	}
	for i := range segments {
		if !segments[i].start.Equal(wantStart[i]) || !segments[i].end.Equal(wantEnd[i]) {
			t.Fatalf("segment %d = %v..%v, want %v..%v", i, segments[i].start, segments[i].end, wantStart[i], wantEnd[i])
		}
	}
}

func TestTimelineSegmentsCrossingMidnight(t *testing.T) {
	p := plan.Default()
	p.WindowStartMinutes = 23 * 60
	now := time.Date(2026, 10, 27, 0, 30, 0, 0, time.UTC)
	dayStart, dayEnd, segments := timelineSegments(p, now)
	if !dayStart.Equal(time.Date(2026, 10, 27, 0, 0, 0, 0, time.UTC)) {
		t.Fatalf("day start = %v", dayStart)
	}
	if !dayEnd.Equal(time.Date(2026, 10, 28, 0, 0, 0, 0, time.UTC)) {
		t.Fatalf("day end = %v", dayEnd)
	}
	if len(segments) != 4 {
		t.Fatalf("got %d segments, want 4", len(segments))
	}
	if !segments[0].start.Equal(dayStart) {
		t.Fatalf("first segment starts at %v, want day start", segments[0].start)
	}
	if !segments[len(segments)-1].end.Equal(dayEnd) {
		t.Fatalf("last segment ends at %v, want day end", segments[len(segments)-1].end)
	}
	for i := 1; i < len(segments); i++ {
		if segments[i].start.Before(segments[i-1].end) {
			t.Fatalf("segment %d overlaps previous", i)
		}
	}
}

func TestTimelineSegmentsInvalidPlan(t *testing.T) {
	_, _, segments := timelineSegments(plan.Plan{}, time.Now())
	if len(segments) != 0 {
		t.Fatalf("invalid plan produced %d segments", len(segments))
	}
}

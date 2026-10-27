package ui

import (
	"math"
	"sort"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"

	"com.comtudia.app/internal/plan"
)

type timeline struct {
	widget.BaseWidget
	p   plan.Plan
	now time.Time
}

func newTimeline(p plan.Plan, now time.Time) *timeline {
	t := &timeline{p: p, now: now}
	t.ExtendBaseWidget(t)
	return t
}

func (t *timeline) SetPlan(p plan.Plan) {
	t.p = p
	if t.now.IsZero() {
		t.now = time.Now()
	}
	t.Refresh()
}

func (t *timeline) SetNow(now time.Time) {
	t.now = now
	t.Refresh()
}

func (t *timeline) CreateRenderer() fyne.WidgetRenderer {
	bg := canvas.NewRectangle(theme.Color(theme.ColorNameInputBackground))
	bg.CornerRadius = 10

	meals := make([]*canvas.Rectangle, plan.MaxMeals*2)
	for i := range meals {
		r := canvas.NewRectangle(theme.Color(theme.ColorNamePrimary))
		r.CornerRadius = 6
		r.Hide()
		meals[i] = r
	}

	nowLine := canvas.NewRectangle(theme.Color(theme.ColorNameForeground))
	nowLine.CornerRadius = 1

	labels := make([]*canvas.Text, 5)
	labelText := []string{"00", "06", "12", "18", "24"}
	for i := range labels {
		text := canvas.NewText(labelText[i], theme.Color(theme.ColorNameForeground))
		text.TextSize = 11
		switch i {
		case 0:
			text.Alignment = fyne.TextAlignLeading
		case len(labels) - 1:
			text.Alignment = fyne.TextAlignTrailing
		default:
			text.Alignment = fyne.TextAlignCenter
		}
		labels[i] = text
	}

	return &timelineRenderer{
		timeline: t,
		bg:       bg,
		meals:    meals,
		nowLine:  nowLine,
		labels:   labels,
	}
}

type timelineSegment struct {
	start time.Time
	end   time.Time
}

type timelineRenderer struct {
	timeline *timeline
	bg       *canvas.Rectangle
	meals    []*canvas.Rectangle
	nowLine  *canvas.Rectangle
	labels   []*canvas.Text

	dayStart time.Time
	dayEnd   time.Time
	segments []timelineSegment
}

func (r *timelineRenderer) Layout(size fyne.Size) {
	if size.Width <= 0 {
		return
	}

	barTop := float32(6)
	barHeight := float32(34)
	if size.Height < 62 {
		barHeight = float32(math.Max(18, float64(size.Height-24)))
	}

	r.bg.Move(fyne.NewPos(0, barTop))
	r.bg.Resize(fyne.NewSize(size.Width, barHeight))

	dayLength := r.dayEnd.Sub(r.dayStart)
	if dayLength <= 0 {
		return
	}
	ratio := func(t time.Time) float64 {
		v := float64(t.Sub(r.dayStart)) / float64(dayLength)
		switch {
		case v < 0:
			return 0
		case v > 1:
			return 1
		default:
			return v
		}
	}

	for i, rect := range r.meals {
		if i >= len(r.segments) {
			rect.Hide()
			continue
		}
		seg := r.segments[i]
		x0 := float32(ratio(seg.start) * float64(size.Width))
		x1 := float32(ratio(seg.end) * float64(size.Width))
		width := x1 - x0 - 2
		if width < 1 {
			width = 1
		}
		rect.Move(fyne.NewPos(x0+1, barTop+2))
		rect.Resize(fyne.NewSize(width, barHeight-4))
		rect.Show()
	}

	if !r.timeline.now.Before(r.dayStart) && r.timeline.now.Before(r.dayEnd) {
		x := float32(ratio(r.timeline.now) * float64(size.Width))
		r.nowLine.Move(fyne.NewPos(x-1, barTop-3))
		r.nowLine.Resize(fyne.NewSize(2, barHeight+6))
		r.nowLine.Show()
	} else {
		r.nowLine.Hide()
	}

	labelWidth := float32(34)
	labelY := barTop + barHeight + 5
	for i, label := range r.labels {
		height := label.MinSize().Height
		label.Resize(fyne.NewSize(labelWidth, height))
		x := float32(0)
		switch i {
		case 0:
			x = 0
		case len(r.labels) - 1:
			x = size.Width - labelWidth
		default:
			hour := i * 6
			at := time.Date(r.dayStart.Year(), r.dayStart.Month(), r.dayStart.Day(), hour, 0, 0, 0, r.dayStart.Location())
			if hour == 24 {
				at = r.dayEnd
			}
			x = float32(ratio(at)*float64(size.Width)) - labelWidth/2
		}
		label.Move(fyne.NewPos(x, labelY))
	}
}

func (r *timelineRenderer) MinSize() fyne.Size {
	return fyne.NewSize(240, 66)
}

func (r *timelineRenderer) Refresh() {
	variant := currentVariant()
	accent := accentColor(plan.PhaseEating, variant)
	foreground := theme.Color(theme.ColorNameForeground)

	r.dayStart, r.dayEnd, r.segments = timelineSegments(r.timeline.p, r.timeline.now)

	r.bg.FillColor = withAlpha(theme.Color(theme.ColorNameInputBackground), 0.8)
	r.bg.Refresh()

	for i, rect := range r.meals {
		if i >= len(r.segments) {
			rect.Hide()
			continue
		}
		rect.FillColor = accent
		rect.Refresh()
		rect.Show()
	}

	r.nowLine.FillColor = withAlpha(foreground, 0.8)
	r.nowLine.Refresh()

	for _, label := range r.labels {
		label.Color = withAlpha(foreground, 0.62)
		label.Refresh()
	}

	r.Layout(r.timeline.Size())
}

func (r *timelineRenderer) Objects() []fyne.CanvasObject {
	out := make([]fyne.CanvasObject, 0, 2+len(r.meals)+len(r.labels))
	out = append(out, r.bg)
	for _, meal := range r.meals {
		out = append(out, meal)
	}
	out = append(out, r.nowLine)
	for _, label := range r.labels {
		out = append(out, label)
	}
	return out
}

func (r *timelineRenderer) Destroy() {}

func timelineSegments(p plan.Plan, now time.Time) (time.Time, time.Time, []timelineSegment) {
	if now.IsZero() {
		now = time.Now()
	}
	y, m, d := now.Date()
	dayStart := time.Date(y, m, d, 0, 0, 0, 0, now.Location())
	dayEnd := dayStart.AddDate(0, 0, 1)

	var segments []timelineSegment
	for _, day := range []time.Time{dayStart.AddDate(0, 0, -1), dayStart} {
		for _, slot := range plan.SlotsForDate(p, day) {
			start := slot.Start
			end := slot.End
			if !end.After(dayStart) || !start.Before(dayEnd) {
				continue
			}
			if start.Before(dayStart) {
				start = dayStart
			}
			if end.After(dayEnd) {
				end = dayEnd
			}
			if start.Before(end) {
				segments = append(segments, timelineSegment{start: start, end: end})
			}
		}
	}

	sort.SliceStable(segments, func(i, j int) bool {
		return segments[i].start.Before(segments[j].start)
	})
	return dayStart, dayEnd, segments
}

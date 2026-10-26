package ui

import (
	"fmt"
	"image/color"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/layout"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"

	"com.comtudia.app/internal/format"
	"com.comtudia.app/internal/plan"
)

type mealList struct {
	box     *fyne.Container
	heading *widget.Label
	rows    []*mealRow
	p       plan.Plan
	day     time.Time
}

func newMealList() *mealList {
	heading := widget.NewLabelWithStyle("Schedule", fyne.TextAlignLeading, fyne.TextStyle{Bold: true})
	list := &mealList{
		box:     container.NewVBox(heading),
		heading: heading,
	}
	return list
}

func (m *mealList) SetData(p plan.Plan, day time.Time) {
	m.p = p
	m.day = normalizeDay(day)
	slots := plan.SlotsForDate(p, m.day)

	m.rows = m.rows[:0]
	objects := []fyne.CanvasObject{m.heading}
	if len(slots) == 0 {
		objects = append(objects, widget.NewLabel("No meals configured."))
	} else {
		for _, slot := range slots {
			row := newMealRow(slot)
			m.rows = append(m.rows, row)
			objects = append(objects, row.root)
		}
	}
	m.box.Objects = objects
	m.box.Refresh()
}

func (m *mealList) Update(st plan.State) {
	day := stateDay(st)
	if m.day.IsZero() || !format.SameDate(day, m.day) {
		m.SetData(m.p, day)
	}
	if len(m.rows) == 0 {
		return
	}

	variant := currentVariant()
	accent := accentColor(plan.PhaseEating, variant)
	baseFill := withAlpha(theme.Color(theme.ColorNameInputBackground), 0.65)
	activeFill := withAlpha(accent, 0.30)
	nextFill := withAlpha(accent, 0.14)

	current := 0
	if st.Phase == plan.PhaseEating && st.CurrentMeal != nil && format.SameDate(st.CurrentMeal.Day, m.day) {
		current = st.CurrentMeal.Index
	}
	next := 0
	if st.NextEatingPhaseMeal > 0 && !st.NextEatingPhaseStart.IsZero() && format.SameDate(st.NextEatingPhaseStart, m.day) {
		if st.Phase != plan.PhaseEating || st.NextEatingPhaseMeal != current {
			next = st.NextEatingPhaseMeal
		}
	}

	for _, row := range m.rows {
		switch {
		case row.index == current:
			row.setStatus("NOW", activeFill)
		case row.index == next:
			row.setStatus("NEXT", nextFill)
		default:
			row.setStatus("", baseFill)
		}
	}

	m.heading.SetText("Schedule · " + format.DayName(m.day, st.Now))
}

type mealRow struct {
	root  *fyne.Container
	bg    *canvas.Rectangle
	index int
	state *widget.Label
}

func newMealRow(slot plan.Slot) *mealRow {
	bg := canvas.NewRectangle(color.Transparent)
	bg.CornerRadius = 8

	index := widget.NewLabelWithStyle(fmt.Sprintf("%d", slot.Index), fyne.TextAlignLeading, fyne.TextStyle{Bold: true})
	times := widget.NewLabel(format.Range(slot.Start, slot.End))
	duration := widget.NewLabelWithStyle(format.DurationCompact(slot.Duration()), fyne.TextAlignTrailing, fyne.TextStyle{})
	state := widget.NewLabelWithStyle("", fyne.TextAlignTrailing, fyne.TextStyle{Bold: true})

	row := container.NewBorder(
		nil, nil,
		index,
		state,
		container.NewHBox(times, layout.NewSpacer(), duration),
	)
	stack := container.NewStack(bg, container.NewPadded(row))
	return &mealRow{
		root:  stack,
		bg:    bg,
		index: slot.Index,
		state: state,
	}
}

func (r *mealRow) setStatus(status string, fill color.Color) {
	r.state.SetText(status)
	r.bg.FillColor = fill
	r.bg.Refresh()
}

func stateDay(st plan.State) time.Time {
	if st.CurrentMeal != nil {
		return st.CurrentMeal.Day
	}
	if !st.NextMeal.Day.IsZero() {
		return st.NextMeal.Day
	}
	return normalizeDay(st.Now)
}

func normalizeDay(t time.Time) time.Time {
	if t.IsZero() {
		t = time.Now()
	}
	y, m, d := t.Date()
	return time.Date(y, m, d, 0, 0, 0, 0, t.Location())
}

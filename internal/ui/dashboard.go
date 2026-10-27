package ui

import (
	"fmt"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/layout"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"

	"com.comtudia.app/internal/format"
	"com.comtudia.app/internal/plan"
)

type dashboard struct {
	root     fyne.CanvasObject
	summary  *widget.Label
	notice   *widget.Label
	pill     *statusPill
	ring     *progressRing
	nextCard *statCard
	planCard *statCard
	timeline *timeline
	meals    *mealList
}

func newDashboard(p plan.Plan, loadErr error, onSettings func()) *dashboard {
	title := widget.NewLabelWithStyle("comtudia", fyne.TextAlignLeading, fyne.TextStyle{Bold: true})
	title.SizeName = theme.SizeNameHeadingText

	planButton := widget.NewButtonWithIcon("Plan", theme.SettingsIcon(), onSettings)
	header := container.NewHBox(title, layout.NewSpacer(), planButton)

	summary := widget.NewLabel(p.Summary())
	summary.SizeName = theme.SizeNameCaptionText

	notice := widget.NewLabel("")
	notice.Wrapping = fyne.TextWrapWord
	notice.Importance = widget.WarningImportance
	if loadErr != nil {
		notice.SetText("Could not load the saved plan — using defaults. " + loadErr.Error())
	} else {
		notice.Hide()
	}

	nextCard := newStatCard("Next eating")
	planCard := newStatCard("Your plan")
	now := time.Now()

	d := &dashboard{
		summary:  summary,
		notice:   notice,
		pill:     newStatusPill(),
		ring:     newProgressRing(),
		nextCard: nextCard,
		planCard: planCard,
		timeline: newTimeline(p, now),
		meals:    newMealList(),
	}

	body := container.NewVBox(
		header,
		summary,
		notice,
		container.NewCenter(d.pill),
		container.NewCenter(d.ring),
		container.NewGridWithColumns(2, nextCard.card, planCard.card),
		d.timeline,
		d.meals.box,
	)
	d.root = container.NewVScroll(container.NewPadded(body))
	d.SetPlan(p)
	return d
}

func (d *dashboard) SetPlan(p plan.Plan) {
	d.summary.SetText(p.Summary())
	d.planCard.Set("Your plan", p.Summary(), planDetail(p))
	d.timeline.SetPlan(p)
	d.meals.SetData(p, time.Now())
}

func (d *dashboard) Update(st plan.State) {
	variant := currentVariant()
	accent := accentColor(st.Phase, variant)
	onAccent := onAccentColor(st.Phase, variant)

	if st.Phase == plan.PhaseEating {
		d.pill.Set("EATING NOW", accent, onAccent)
		context := fmt.Sprintf("meals %d–%d · window ends %s",
			st.FirstMealIndex, st.LastMealIndex, format.Clock(st.PhaseEnd))
		cardTitle := "Window ends"
		if st.FirstMealIndex == st.LastMealIndex {
			context = fmt.Sprintf("meal %d of %d · ends %s",
				st.FirstMealIndex, st.MealCount, format.Clock(st.PhaseEnd))
			cardTitle = "Meal ends"
		}
		d.ring.Set(st.Progress, accent, format.Countdown(st.Remaining), context)
		d.nextCard.Set(cardTitle, format.Duration(st.Remaining),
			"next eating "+format.DayClock(st.NextEatingPhaseStart, st.Now))
	} else {
		d.pill.Set("FASTING", accent, onAccent)
		d.ring.Set(st.Progress, accent, format.Countdown(st.Remaining),
			"next eating "+format.When(st.NextEatingPhaseStart, st.Now))
		d.nextCard.Set("Next eating", format.Duration(st.Remaining),
			"opens "+format.DayClock(st.NextEatingPhaseStart, st.Now))
	}

	d.timeline.SetNow(st.Now)
	d.meals.Update(st)
}

func planDetail(p plan.Plan) string {
	mealDuration := ""
	if ranges := p.MealRanges(); len(ranges) > 0 {
		mealDuration = format.DurationCompact(time.Duration(ranges[0].DurationMinutes) * time.Minute)
	}
	detail := fmt.Sprintf("%d × %s · starts %s", p.Meals, mealDuration, format.ClockMinutes(p.WindowStartMinutes))
	if p.GapMinutes > 0 {
		detail += " · " + format.DurationCompact(time.Duration(p.GapMinutes)*time.Minute) + " gap"
	}
	return detail
}

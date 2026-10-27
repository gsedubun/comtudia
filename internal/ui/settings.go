package ui

import (
	"fmt"
	"strconv"
	"strings"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/data/validation"
	"fyne.io/fyne/v2/layout"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"

	"com.comtudia.app/internal/format"
	"com.comtudia.app/internal/plan"
)

type settingsView struct {
	root fyne.CanvasObject

	onSave   func(plan.Plan) error
	onCancel func()
	onTest   func()

	preset        *widget.Select
	eatHours      *widget.Select
	eatMinutes    *widget.Entry
	meals         *widget.Select
	gap           *widget.Entry
	startHour     *widget.Select
	startMinute   *widget.Entry
	notifications *widget.Check
	runInTray     *widget.Check
	eatTotal      *widget.Label
	preview       *widget.Label
	errorLabel    *widget.Label
	updating      bool
}

func newSettings(p plan.Plan, path string, onSave func(plan.Plan) error, onCancel, onTest func()) *settingsView {
	s := &settingsView{
		onSave:   onSave,
		onCancel: onCancel,
		onTest:   onTest,
	}

	s.preset = widget.NewSelect([]string{"16:8", "18:6", "20:4", "Custom"}, nil)
	s.eatHours = widget.NewSelect(hourOptions(1), nil)
	s.eatMinutes = newMinuteEntry()
	s.meals = widget.NewSelect(mealOptions(), nil)
	s.gap = widget.NewEntry()
	s.gap.SetPlaceHolder("0 = back-to-back")
	s.gap.Validator = validation.NewRegexp(`^\d{0,4}$`, "Use a number of minutes")
	s.startHour = widget.NewSelect(hourOptions(0), nil)
	s.startMinute = newMinuteEntry()

	s.notifications = widget.NewCheck("Desktop notifications", nil)
	s.runInTray = widget.NewCheck("Keep running in tray when closed", nil)

	s.eatTotal = widget.NewLabel("")
	s.eatTotal.SizeName = theme.SizeNameCaptionText

	s.preview = widget.NewLabelWithStyle("", fyne.TextAlignLeading, fyne.TextStyle{Monospace: true})
	s.preview.Wrapping = fyne.TextWrapWord
	s.preview.SizeName = theme.SizeNameCaptionText

	s.errorLabel = widget.NewLabelWithStyle("", fyne.TextAlignLeading, fyne.TextStyle{Bold: true})
	s.errorLabel.Importance = widget.DangerImportance
	s.errorLabel.Wrapping = fyne.TextWrapWord
	s.errorLabel.Hide()

	s.preset.OnChanged = s.presetChanged
	for _, w := range []*widget.Select{s.eatHours, s.meals, s.startHour} {
		w.OnChanged = func(string) { s.changed() }
	}
	for _, w := range []*widget.Entry{s.eatMinutes, s.startMinute, s.gap} {
		w.OnChanged = func(string) { s.changed() }
	}

	eatingTime := container.NewHBox(s.eatHours, widget.NewLabel("h"), s.eatMinutes, widget.NewLabel("min"))
	eatingTimeGroup := container.NewVBox(eatingTime, s.eatTotal)

	firstMeal := container.NewHBox(s.startHour, widget.NewLabel(":"), s.startMinute)
	form := widget.NewForm(
		widget.NewFormItem("Preset", s.preset),
		widget.NewFormItem("Eating time", eatingTimeGroup),
		widget.NewFormItem("Meals", s.meals),
		widget.NewFormItem("Gap (minutes)", s.gap),
		widget.NewFormItem("First meal", firstMeal),
		widget.NewFormItem("", s.notifications),
		widget.NewFormItem("", s.runInTray),
	)

	back := widget.NewButtonWithIcon("Back", theme.NavigateBackIcon(), s.cancel)
	title := widget.NewLabelWithStyle("Plan", fyne.TextAlignLeading, fyne.TextStyle{Bold: true})
	title.SizeName = theme.SizeNameHeadingText
	header := container.NewHBox(back, title, layout.NewSpacer())

	intro := widget.NewLabel("Your daily cycle is 24 hours. Eating time is the total across all meals; gaps count as fasting time.")
	intro.Wrapping = fyne.TextWrapWord
	intro.SizeName = theme.SizeNameCaptionText

	save := widget.NewButton("Save plan", s.save)
	save.Importance = widget.HighImportance
	reset := widget.NewButton("Reset", s.reset)
	cancel := widget.NewButton("Cancel", s.cancel)
	buttons := container.NewHBox(save, reset, cancel)

	test := widget.NewButton("Send test notification", func() {
		if s.onTest != nil {
			s.onTest()
		}
	})

	previewCard := widget.NewCard("Preview", "", s.preview)
	trayNote := widget.NewLabel("Tray support depends on your desktop; GNOME requires the AppIndicator extension.")
	trayNote.Wrapping = fyne.TextWrapWord
	trayNote.SizeName = theme.SizeNameCaptionText

	pathLabel := widget.NewLabel("Config: " + path)
	pathLabel.Wrapping = fyne.TextWrapWord
	pathLabel.SizeName = theme.SizeNameCaptionText

	body := container.NewVBox(
		header,
		intro,
		form,
		s.errorLabel,
		trayNote,
		buttons,
		previewCard,
		test,
		pathLabel,
	)
	s.root = container.NewVScroll(container.NewPadded(body))
	s.SetPlan(p)
	return s
}

func (s *settingsView) SetPlan(p plan.Plan) {
	s.updating = true
	s.eatHours.SetSelected(fmt.Sprintf("%02d", p.EatingMinutes/60))
	s.eatMinutes.SetText(fmt.Sprintf("%02d", p.EatingMinutes%60))
	s.meals.SetSelected(strconv.Itoa(p.Meals))
	s.gap.SetText(strconv.Itoa(p.GapMinutes))
	s.startHour.SetSelected(fmt.Sprintf("%02d", p.WindowStartMinutes/60))
	s.startMinute.SetText(fmt.Sprintf("%02d", p.WindowStartMinutes%60))
	s.notifications.SetChecked(p.Notifications)
	s.runInTray.SetChecked(p.RunInTray)
	s.updating = false

	s.detectPreset()
	s.updatePreview()
	s.hideError()
}

func (s *settingsView) currentPlan() (plan.Plan, error) {
	eatHours, err := atoiRange(s.eatHours.Selected, 1, 23, "eating time hours")
	if err != nil {
		return plan.Plan{}, err
	}
	eatMinutes, err := atoiRange(s.eatMinutes.Text, 0, 59, "eating time minutes")
	if err != nil {
		return plan.Plan{}, err
	}
	meals, err := atoiRange(s.meals.Selected, 1, plan.MaxMeals, "meal count")
	if err != nil {
		return plan.Plan{}, err
	}
	gap, err := atoiRange(s.gap.Text, 0, 24*60, "gap")
	if err != nil {
		return plan.Plan{}, err
	}
	startHour, err := atoiRange(s.startHour.Selected, 0, 23, "start hour")
	if err != nil {
		return plan.Plan{}, err
	}
	startMinute, err := atoiRange(s.startMinute.Text, 0, 59, "start minute")
	if err != nil {
		return plan.Plan{}, err
	}

	return plan.Plan{
		Version:            plan.Version,
		EatingMinutes:      eatHours*60 + eatMinutes,
		Meals:              meals,
		GapMinutes:         gap,
		WindowStartMinutes: startHour*60 + startMinute,
		Notifications:      s.notifications.Checked,
		RunInTray:          s.runInTray.Checked,
	}, nil
}

func (s *settingsView) changed() {
	if s.updating {
		return
	}
	s.detectPreset()
	s.updatePreview()
}

func (s *settingsView) presetChanged(name string) {
	if s.updating || name == "" || name == "Custom" {
		return
	}
	for _, preset := range plan.Presets {
		if preset.Name != name {
			continue
		}
		s.updating = true
		s.eatHours.SetSelected(fmt.Sprintf("%02d", preset.EatingMinutes/60))
		s.eatMinutes.SetText(fmt.Sprintf("%02d", preset.EatingMinutes%60))
		s.meals.SetSelected(strconv.Itoa(preset.Meals))
		s.gap.SetText(strconv.Itoa(preset.GapMinutes))
		s.updating = false
		s.updatePreview()
		return
	}
}

func (s *settingsView) detectPreset() {
	name := "Custom"
	if p, err := s.currentPlan(); err == nil {
		for _, preset := range plan.Presets {
			if p.EatingMinutes == preset.EatingMinutes && p.Meals == preset.Meals && p.GapMinutes == preset.GapMinutes {
				name = preset.Name
				break
			}
		}
	}
	s.updating = true
	s.preset.SetSelected(name)
	s.updating = false
}

func (s *settingsView) updatePreview() {
	s.updateEatingTotal()
	p, err := s.currentPlan()
	if err != nil {
		s.preview.SetText("Check the values above.")
		return
	}
	if err := p.Validate(); err != nil {
		s.preview.SetText("Preview unavailable\n" + err.Error())
		return
	}
	s.preview.SetText(previewText(p))
}

func (s *settingsView) updateEatingTotal() {
	hours, errHours := atoiRange(s.eatHours.Selected, 1, 23, "eating time hours")
	minutes, errMinutes := atoiRange(s.eatMinutes.Text, 0, 59, "eating time minutes")
	if errHours != nil || errMinutes != nil {
		s.eatTotal.SetText("= —")
		return
	}
	s.eatTotal.SetText("= " + format.Minutes(hours*60+minutes) + " total")
}

func (s *settingsView) save() {
	p, err := s.currentPlan()
	if err != nil {
		s.showError(err)
		return
	}
	if err := p.Validate(); err != nil {
		s.showError(err)
		return
	}
	if err := s.onSave(p); err != nil {
		s.showError(err)
		return
	}
	s.hideError()
}

func (s *settingsView) reset() {
	s.SetPlan(plan.Default())
}

func (s *settingsView) cancel() {
	if s.onCancel != nil {
		s.onCancel()
	}
}

func (s *settingsView) showError(err error) {
	s.errorLabel.SetText(err.Error())
	s.errorLabel.Show()
	s.errorLabel.Refresh()
}

func (s *settingsView) hideError() {
	s.errorLabel.SetText("")
	s.errorLabel.Hide()
}

func previewText(p plan.Plan) string {
	var b strings.Builder
	b.WriteString("Meals\n")
	for _, r := range p.MealRanges() {
		fmt.Fprintf(&b, "%d. %-20s %s\n",
			r.Index,
			format.RangeMinutes(p.WindowStartMinutes+r.StartMinutes, p.WindowStartMinutes+r.EndMinutes),
			format.DurationCompact(time.Duration(r.DurationMinutes)*time.Minute))
	}
	b.WriteString("\n")
	fmt.Fprintf(&b, "Eating %s · Fasting %s\n", format.Minutes(p.EatingMinutes), format.Minutes(p.FastingMinutes()))
	if p.GapMinutes > 0 {
		fmt.Fprintf(&b, "Long fast %s · meal gaps %s\n",
			format.Minutes(p.LongFastMinutes()),
			format.Minutes((p.Meals-1)*p.GapMinutes))
	} else {
		fmt.Fprintf(&b, "Long fast %s\n", format.Minutes(p.LongFastMinutes()))
	}
	return b.String()
}

func hourOptions(first int) []string {
	out := make([]string, 0, 24-first)
	for h := first; h < 24; h++ {
		out = append(out, fmt.Sprintf("%02d", h))
	}
	return out
}

func mealOptions() []string {
	out := make([]string, 0, plan.MaxMeals)
	for i := 1; i <= plan.MaxMeals; i++ {
		out = append(out, strconv.Itoa(i))
	}
	return out
}

func newMinuteEntry() *widget.Entry {
	e := widget.NewEntry()
	e.Validator = validation.NewRegexp(`^\d{0,2}$`, "Use 0–59")
	e.SetPlaceHolder("00")
	return e
}

func atoiRange(value string, min, max int, field string) (int, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		value = "0"
	}
	n, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("%s must be a number", field)
	}
	if n < min || n > max {
		return 0, fmt.Errorf("%s must be between %d and %d", field, min, max)
	}
	return n, nil
}

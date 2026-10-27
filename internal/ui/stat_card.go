package ui

import (
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"
)

type statCard struct {
	card  *widget.Card
	value *widget.Label
	sub   *widget.Label
}

func newStatCard(title string) *statCard {
	value := widget.NewLabelWithStyle("", fyne.TextAlignLeading, fyne.TextStyle{Bold: true})
	value.SizeName = theme.SizeNameSubHeadingText
	sub := widget.NewLabel("")
	sub.SizeName = theme.SizeNameCaptionText
	sub.Wrapping = fyne.TextWrapWord

	card := widget.NewCard(title, "", container.NewVBox(value, sub))
	return &statCard{card: card, value: value, sub: sub}
}

func (c *statCard) Set(title, value, sub string) {
	c.card.SetTitle(title)
	c.value.SetText(value)
	c.sub.SetText(sub)
	c.card.Refresh()
}

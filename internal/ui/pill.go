package ui

import (
	"image/color"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"
)

type statusPill struct {
	widget.BaseWidget
	text      string
	fill      color.Color
	textColor color.Color
}

func newStatusPill() *statusPill {
	p := &statusPill{
		fill:      color.Transparent,
		textColor: color.White,
	}
	p.ExtendBaseWidget(p)
	return p
}

func (p *statusPill) Set(text string, fill, textColor color.Color) {
	p.text = text
	p.fill = fill
	p.textColor = textColor
	p.Refresh()
}

func (p *statusPill) CreateRenderer() fyne.WidgetRenderer {
	bg := canvas.NewRectangle(color.Transparent)
	bg.CornerRadius = 18
	text := canvas.NewText("", color.White)
	text.Alignment = fyne.TextAlignCenter
	text.TextStyle = fyne.TextStyle{Bold: true}
	text.TextSize = theme.TextSize()
	return &statusPillRenderer{pill: p, bg: bg, text: text}
}

type statusPillRenderer struct {
	pill *statusPill
	bg   *canvas.Rectangle
	text *canvas.Text
}

func (r *statusPillRenderer) Layout(size fyne.Size) {
	r.bg.Move(fyne.NewPos(0, 0))
	r.bg.Resize(size)

	textSize := r.text.MinSize()
	r.text.Resize(fyne.NewSize(size.Width, textSize.Height))
	r.text.Move(fyne.NewPos(0, (size.Height-textSize.Height)/2))
}

func (r *statusPillRenderer) MinSize() fyne.Size {
	return fyne.NewSize(156, 34)
}

func (r *statusPillRenderer) Refresh() {
	r.bg.FillColor = r.pill.fill
	r.bg.Refresh()
	r.text.Text = r.pill.text
	r.text.Color = r.pill.textColor
	r.text.Refresh()
	r.Layout(r.pill.Size())
}

func (r *statusPillRenderer) Objects() []fyne.CanvasObject {
	return []fyne.CanvasObject{r.bg, r.text}
}

func (r *statusPillRenderer) Destroy() {}

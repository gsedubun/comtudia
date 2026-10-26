package ui

import (
	"image"
	"image/color"
	"math"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/canvas"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"
)

type progressRing struct {
	widget.BaseWidget
	progress float64
	accent   color.Color
	title    string
	subtitle string
}

func newProgressRing() *progressRing {
	r := &progressRing{accent: theme.Color(theme.ColorNamePrimary)}
	r.ExtendBaseWidget(r)
	return r
}

func (r *progressRing) Set(progress float64, accent color.Color, title, subtitle string) {
	switch {
	case progress < 0:
		progress = 0
	case progress > 1:
		progress = 1
	}
	r.progress = progress
	if accent != nil {
		r.accent = accent
	}
	r.title = title
	r.subtitle = subtitle
	r.Refresh()
}

func (r *progressRing) CreateRenderer() fyne.WidgetRenderer {
	raster := canvas.NewRaster(r.draw)
	title := canvas.NewText("", theme.Color(theme.ColorNameForeground))
	title.Alignment = fyne.TextAlignCenter
	title.TextStyle = fyne.TextStyle{Bold: true}
	subtitle := canvas.NewText("", theme.Color(theme.ColorNameForeground))
	subtitle.Alignment = fyne.TextAlignCenter
	return &ringRenderer{ring: r, raster: raster, title: title, subtitle: subtitle}
}

type ringRenderer struct {
	ring     *progressRing
	raster   *canvas.Raster
	title    *canvas.Text
	subtitle *canvas.Text
}

func (r *ringRenderer) Layout(size fyne.Size) {
	side := float32(math.Min(float64(size.Width), float64(size.Height)))
	if side <= 0 {
		return
	}
	r.raster.Resize(fyne.NewSize(side, side))
	r.raster.Move(fyne.NewPos((size.Width-side)/2, (size.Height-side)/2))

	titleSize := float32(math.Max(24, math.Min(54, float64(side)*0.15)))
	subtitleSize := float32(math.Max(11, math.Min(15, float64(side)*0.052)))
	r.title.TextSize = titleSize
	r.subtitle.TextSize = subtitleSize

	titleHeight := r.title.MinSize().Height
	subtitleHeight := r.subtitle.MinSize().Height
	gap := float32(6)
	blockHeight := titleHeight + gap + subtitleHeight
	top := (size.Height - blockHeight) / 2
	x := (size.Width - side) / 2

	r.title.Resize(fyne.NewSize(side, titleHeight))
	r.title.Move(fyne.NewPos(x, top))
	r.subtitle.Resize(fyne.NewSize(side, subtitleHeight))
	r.subtitle.Move(fyne.NewPos(x, top+titleHeight+gap))
}

func (r *ringRenderer) MinSize() fyne.Size {
	return fyne.NewSize(250, 250)
}

func (r *ringRenderer) Refresh() {
	r.raster.Refresh()
	r.title.Text = r.ring.title
	r.title.Color = theme.Color(theme.ColorNameForeground)
	r.title.Refresh()
	r.subtitle.Text = r.ring.subtitle
	r.subtitle.Color = withAlpha(theme.Color(theme.ColorNameForeground), 0.72)
	r.subtitle.Refresh()
	r.Layout(r.ring.Size())
}

func (r *ringRenderer) Objects() []fyne.CanvasObject {
	return []fyne.CanvasObject{r.raster, r.title, r.subtitle}
}

func (r *ringRenderer) Destroy() {}

func (r *progressRing) draw(width, height int) image.Image {
	img := image.NewNRGBA(image.Rect(0, 0, width, height))
	if width <= 0 || height <= 0 {
		return img
	}

	side := math.Min(float64(width), float64(height))
	if side < 8 {
		return img
	}
	cx := float64(width) / 2
	cy := float64(height) / 2
	outer := side/2 - 2
	thickness := math.Max(9, side*0.065)
	inner := outer - thickness
	edge := 0.8

	accent := colorToNRGBA(r.accent)
	background := colorToNRGBA(theme.Color(theme.ColorNameInputBackground))
	background.A = uint8(float64(background.A) * 0.55)

	progressAngle := r.progress * 2 * math.Pi

	for y := 0; y < height; y++ {
		dy := float64(y) + 0.5 - cy
		for x := 0; x < width; x++ {
			dx := float64(x) + 0.5 - cx
			distance := math.Hypot(dx, dy)
			if distance < inner-edge || distance > outer+edge {
				continue
			}

			coverage := 1.0
			if distance > outer-edge {
				coverage *= clamp01((outer + edge - distance) / (2 * edge))
			}
			if distance < inner+edge {
				coverage *= clamp01((distance - (inner - edge)) / (2 * edge))
			}
			if coverage <= 0 {
				continue
			}

			angle := math.Atan2(dx, -dy)
			if angle < 0 {
				angle += 2 * math.Pi
			}

			fill := background
			if r.progress > 0 && angle <= progressAngle {
				fill = accent
			}
			alpha := uint8(math.Round(float64(fill.A) * coverage))
			if alpha == 0 {
				continue
			}
			img.SetNRGBA(x, y, color.NRGBA{R: fill.R, G: fill.G, B: fill.B, A: alpha})
		}
	}
	return img
}

func colorToNRGBA(c color.Color) color.NRGBA {
	if c == nil {
		return color.NRGBA{}
	}
	return color.NRGBAModel.Convert(c).(color.NRGBA)
}

func withAlpha(c color.Color, factor float64) color.Color {
	n := colorToNRGBA(c)
	n.A = uint8(math.Round(float64(n.A) * clamp01(factor)))
	return n
}

func clamp01(v float64) float64 {
	switch {
	case v < 0:
		return 0
	case v > 1:
		return 1
	default:
		return v
	}
}

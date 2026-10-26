package ui

import (
	"image/color"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/theme"

	"com.comtudia.app/internal/plan"
)

type appTheme struct{}

var (
	eatingLight  = color.NRGBA{R: 0x1f, G: 0x9d, B: 0x6b, A: 0xff}
	eatingDark   = color.NRGBA{R: 0x3d, G: 0xd5, B: 0x97, A: 0xff}
	fastingLight = color.NRGBA{R: 0x4f, G: 0x6d, B: 0xe8, A: 0xff}
	fastingDark  = color.NRGBA{R: 0x8a, G: 0xa2, B: 0xff, A: 0xff}
)

func (appTheme) Color(name fyne.ThemeColorName, variant fyne.ThemeVariant) color.Color {
	switch name {
	case theme.ColorNamePrimary:
		return eatingAccent(variant)
	case theme.ColorNameForegroundOnPrimary:
		return onAccentColor(plan.PhaseEating, variant)
	}
	return theme.DefaultTheme().Color(name, variant)
}

func (appTheme) Font(style fyne.TextStyle) fyne.Resource {
	return theme.DefaultTheme().Font(style)
}

func (appTheme) Icon(name fyne.ThemeIconName) fyne.Resource {
	return theme.DefaultTheme().Icon(name)
}

func (appTheme) Size(name fyne.ThemeSizeName) float32 {
	return theme.DefaultTheme().Size(name)
}

func currentVariant() fyne.ThemeVariant {
	if app := fyne.CurrentApp(); app != nil {
		return app.Settings().ThemeVariant()
	}
	return theme.VariantLight
}

func eatingAccent(variant fyne.ThemeVariant) color.Color {
	if variant == theme.VariantDark {
		return eatingDark
	}
	return eatingLight
}

func fastingAccent(variant fyne.ThemeVariant) color.Color {
	if variant == theme.VariantDark {
		return fastingDark
	}
	return fastingLight
}

func accentColor(phase plan.Phase, variant fyne.ThemeVariant) color.Color {
	if phase == plan.PhaseEating {
		return eatingAccent(variant)
	}
	return fastingAccent(variant)
}

func onAccentColor(phase plan.Phase, variant fyne.ThemeVariant) color.Color {
	if variant != theme.VariantDark {
		return color.White
	}
	if phase == plan.PhaseEating {
		return color.NRGBA{R: 0x06, G: 0x20, B: 0x18, A: 0xff}
	}
	return color.NRGBA{R: 0x0b, G: 0x10, B: 0x20, A: 0xff}
}

package main

import (
	_ "embed"

	"fyne.io/fyne/v2"

	"com.comtudia.app/internal/ui"
)

//go:embed assets/icon.png
var appIconPNG []byte

//go:embed assets/tray.png
var trayIconPNG []byte

func main() {
	ui.Run(
		fyne.NewStaticResource("icon.png", appIconPNG),
		fyne.NewStaticResource("tray.png", trayIconPNG),
	)
}

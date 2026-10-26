APP := comtudia
BUILD := build
PREFIX ?= /usr/local
BUILDTYPE ?= release

.PHONY: all build run test check install clean distclean

all: build

build:
	@if [ ! -d $(BUILD) ]; then \
		meson setup $(BUILD) --prefix=$(PREFIX) --buildtype=$(BUILDTYPE); \
	else \
		meson setup --reconfigure $(BUILD) --prefix=$(PREFIX) --buildtype=$(BUILDTYPE); \
	fi
	meson compile -C $(BUILD)

run: build
	./$(BUILD)/$(APP)

test: build
	meson test -C $(BUILD) --print-errorlogs

check: test
	desktop-file-validate data/com.comtudia.app.desktop
	@if command -v appstreamcli >/dev/null 2>&1; then \
		appstreamcli validate data/com.comtudia.app.metainfo.xml; \
	else \
		echo "appstreamcli not installed; skipping metainfo validation"; \
	fi

install: build
	meson install -C $(BUILD)

clean:
	rm -rf $(BUILD)

distclean: clean

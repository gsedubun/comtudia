#!/usr/bin/env bash
#
# deploy-rpk.sh - install a Vela Quick App RPK on a Xiaomi band through the
# hidden "Third App Support" page in the international Mi Fitness app.
#
# Adapted from scripts/deploy.sh in
#   https://github.com/oryonatan/xiaomi-band-development
# MIT License, Copyright (c) 2025. The hidden-page launcher and UI automation
# are based on that project; see band/THIRD_PARTY_NOTICES.md for the notice.
#
# This script never downloads binaries. It compiles the small app_process
# launcher (LaunchFragment.java) with javac and d8 from the Android SDK, exactly
# like the upstream project. The phone must already have Mi Fitness installed
# and paired with the band.
#
# Usage:
#   tools/deploy-rpk.sh [--rpk <path>] [--package <name>] [--serial <serial>]
#                       [--uninstall | --uninstall-only]
#
# Options:
#   --rpk <path>        RPK to install (default: newest in band/comtudia/dist)
#   --package <name>    Band package name (default: read src/manifest.json)
#   --serial <serial>   Phone ADB serial (default: first USB device)
#   --uninstall         Uninstall the existing package before installing
#   --uninstall-only    Uninstall the package and stop
#   -h, --help          Show this help
#
# Environment:
#   ANDROID_HOME / ANDROID_SDK_ROOT   Android SDK (needed to build the launcher)
#   PHONE_SERIAL                      Phone ADB serial (overridden by --serial)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT/band/comtudia"
MANIFEST="$APP_DIR/src/manifest.json"

RPK=""
PKG=""
SERIAL="${PHONE_SERIAL:-}"
DO_UNINSTALL=false
UNINSTALL_ONLY=false
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"

usage() {
  sed -n '2,/^set -euo/{ /^set -euo/d; s/^# \?//; p }' "$0"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

warn() {
  echo "WARN: $*" >&2
}

info() {
  echo "==> $*"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --rpk) RPK="${2:-}"; shift 2 ;;
    --package) PKG="${2:-}"; shift 2 ;;
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --uninstall) DO_UNINSTALL=true; shift ;;
    --uninstall-only) UNINSTALL_ONLY=true; DO_UNINSTALL=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "unknown argument: $1 (use --help)" ;;
  esac
done

# --- Android SDK / JDK -----------------------------------------------------
# Prefer the toolchain provisioned by tools/android-env.sh when the user has
# not already exported a SDK or a JDK.
if [ -z "$ANDROID_HOME" ]; then
  for candidate in "$HOME/.local/android-sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    if [ -d "$candidate" ]; then ANDROID_HOME="$candidate"; break; fi
  done
fi
if [ -z "${JAVA_HOME:-}" ] && [ -x "$HOME/.local/jdk17/bin/javac" ] && ! command -v javac >/dev/null 2>&1; then
  JAVA_HOME="$HOME/.local/jdk17"
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi

# --- adb -------------------------------------------------------------------
ADB_BIN="${ADB:-}"
if [ -z "$ADB_BIN" ] && command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
fi
if [ -z "$ADB_BIN" ] && [ -n "$ANDROID_HOME" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
  ADB_BIN="$ANDROID_HOME/platform-tools/adb"
fi
if [ -z "$ADB_BIN" ]; then
  fail "adb is not installed. Run tools/android-env.sh, or install Android platform-tools and put adb on PATH."
fi

if [ -z "$SERIAL" ]; then
  SERIAL="$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }')"
  if [ -z "$SERIAL" ]; then
    fail "no phone found via ADB. Enable USB debugging, connect USB, and accept the RSA prompt. For wireless: adb pair <ip:port> <code>, then adb connect <ip:port>."
  fi
fi

ADB=("$ADB_BIN" -s "$SERIAL")
if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  fail "phone $SERIAL is not reachable. Check 'adb devices' and the USB debugging authorization."
fi
info "Phone: $SERIAL"

if ! command -v python3 >/dev/null 2>&1; then
  fail "python3 is required to parse the phone UI (uiautomator XML)."
fi

# --- RPK -------------------------------------------------------------------
if [ "$UNINSTALL_ONLY" = false ]; then
  if [ -z "$RPK" ]; then
    RPK="$(find "$APP_DIR/dist" -maxdepth 1 -type f -name '*.rpk' -printf '%T@ %p\n' 2>/dev/null \
      | sort -nr | head -1 | cut -d' ' -f2-)"
    [ -n "$RPK" ] || fail "no RPK found in band/comtudia/dist. Run tools/band-build.sh or pass --rpk <path>."
  fi
  [ -f "$RPK" ] || fail "RPK not found: $RPK"
  case "$RPK" in
    *.rpk) ;;
    *) fail "not an .rpk file: $RPK" ;;
  esac
  RPK="$(cd "$(dirname "$RPK")" && pwd)/$(basename "$RPK")"
fi

# --- package name ----------------------------------------------------------
if [ -z "$PKG" ]; then
  if [ -f "$MANIFEST" ]; then
    PKG="$(sed -n 's/.*"package"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$MANIFEST" | head -1)"
  fi
  PKG="${PKG:-com.comtudia.app}"
fi
if [[ ! "$PKG" =~ ^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$ ]]; then
  fail "invalid package name: $PKG"
fi

info "Package: $PKG"
if [ "$UNINSTALL_ONLY" = false ]; then
  info "RPK: $RPK"
fi

# --- Mi Fitness ------------------------------------------------------------
APK_PATH="$("${ADB[@]}" shell pm path com.xiaomi.wearable 2>/dev/null | head -1 | sed 's/^package://' | tr -d '\r')"
if [ -z "$APK_PATH" ]; then
  if "${ADB[@]}" shell pm path com.xiaomi.wearable.chn 2>/dev/null | grep -q package:; then
    fail "only the Chinese Mi Fitness (com.xiaomi.wearable.chn) is installed. This workflow targets the international app com.xiaomi.wearable."
  fi
  fail "Mi Fitness (com.xiaomi.wearable) is not installed on the phone."
fi
info "Mi Fitness: $APK_PATH"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/comtudia-deploy.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT
LAUNCH_DEX="/data/local/tmp/launch.dex"

build_launcher() {
  [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ] || fail "ANDROID_HOME is not set. Set it to your Android SDK, e.g. export ANDROID_HOME=\"\$HOME/Android/Sdk\" (Linux) or \"\$HOME/Library/Android/sdk\" (macOS)."
  command -v javac >/dev/null 2>&1 || fail "javac is not on PATH. Install a JDK (required to compile the debug-page launcher)."
  command -v java >/dev/null 2>&1 || fail "java is not on PATH. Install a JDK/JRE (required by d8)."

  local platform build_tools d8
  platform="$(ls -d "$ANDROID_HOME"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
  [ -n "$platform" ] || fail "no android.jar under $ANDROID_HOME/platforms/. Install an Android platform with sdkmanager."
  build_tools="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
  [ -n "$build_tools" ] || fail "no build-tools under $ANDROID_HOME/build-tools/. Install Android build-tools with sdkmanager."
  d8="$build_tools/d8"
  [ -x "$d8" ] || fail "d8 not found at $d8. Install Android build-tools."

  info "Building launcher dex (javac + d8)"
  cat > "$TMP_DIR/LaunchFragment.java" <<'JAVA'
import android.content.Intent;
import android.os.Looper;
import android.os.Parcelable;

public class LaunchFragment {
    public static void main(String[] args) {
        try {
            Looper.prepareMainLooper();
            String apkPath = args[0];
            dalvik.system.PathClassLoader cl = new dalvik.system.PathClassLoader(apkPath, ClassLoader.getSystemClassLoader());
            Class<?> builderClass = cl.loadClass("com.xiaomi.fitness.baseui.common.FragmentParams$b");
            Object builder = builderClass.newInstance();
            Class<?> fragmentClass = cl.loadClass("com.xiaomi.xms.wearable.ui.debug.ThirdAppDebugFragment");
            java.lang.reflect.Method setClass = builderClass.getMethod("e", Class.class);
            setClass.invoke(builder, fragmentClass);
            java.lang.reflect.Method build = builderClass.getMethod("b");
            Object fp = build.invoke(builder);
            Intent intent = new Intent();
            intent.setClassName("com.xiaomi.wearable", "com.xiaomi.fitness.baseui.common.CommonBaseActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("fragment_param", (Parcelable) fp);
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            java.lang.reflect.Method getService = amClass.getDeclaredMethod("getService");
            getService.setAccessible(true);
            Object am = getService.invoke(null);
            for (java.lang.reflect.Method m : am.getClass().getMethods()) {
                if (m.getName().equals("startActivity") && m.getParameterTypes().length == 10) {
                    Object[] callArgs = new Object[10];
                    Class<?>[] params = m.getParameterTypes();
                    for (int i = 0; i < 10; i++) {
                        if (params[i] == int.class) callArgs[i] = 0;
                        else if (params[i] == Intent.class) callArgs[i] = intent;
                        else if (params[i] == String.class && i == 1) callArgs[i] = "com.android.shell";
                        else callArgs[i] = null;
                    }
                    m.invoke(am, callArgs);
                    System.out.println("SUCCESS");
                    return;
                }
            }
            System.out.println("FAILED: no matching startActivity method");
        } catch (Throwable e) {
            System.err.println("ERROR: " + e);
            e.printStackTrace();
        }
    }
}
JAVA
  mkdir -p "$TMP_DIR/classes" "$TMP_DIR/dex"
  javac -source 8 -target 8 -bootclasspath "$platform" \
    "$TMP_DIR/LaunchFragment.java" -d "$TMP_DIR/classes"
  "$d8" "$TMP_DIR/classes/LaunchFragment.class" --output "$TMP_DIR/dex"
  "${ADB[@]}" push "$TMP_DIR/dex/classes.dex" "$LAUNCH_DEX" >/dev/null
}

if ! "${ADB[@]}" shell "test -s $LAUNCH_DEX" >/dev/null 2>&1; then
  build_launcher
  info "Launcher dex ready"
else
  info "Launcher dex already on phone"
fi

# --- push RPK --------------------------------------------------------------
if [ "$UNINSTALL_ONLY" = false ]; then
  DEVICE_RPK="/sdcard/Xiaomi-band/$(printf '%s' "$PKG" | tr '.' '_').rpk"
  info "Pushing RPK to $DEVICE_RPK"
  "${ADB[@]}" shell mkdir -p /sdcard/Xiaomi-band
  "${ADB[@]}" push "$RPK" "$DEVICE_RPK"
fi

# --- launch hidden debug page ---------------------------------------------
info "Launching Mi Fitness Third App Support page"
RESULT="$("${ADB[@]}" shell "CLASSPATH=$LAUNCH_DEX app_process / LaunchFragment $APK_PATH" 2>&1 || true)"
if ! printf '%s' "$RESULT" | grep -q 'SUCCESS'; then
  fail "could not launch the hidden debug page. Mi Fitness may have changed its internal classes. Output: $RESULT"
fi
sleep 4

# --- uiautomator helpers ---------------------------------------------------
dump_ui() {
  rm -f "$TMP_DIR/ui.xml"
  local attempt
  for attempt in 1 2 3; do
    "${ADB[@]}" shell uiautomator dump /sdcard/comtudia_ui.xml >/dev/null 2>&1 || true
    "${ADB[@]}" pull /sdcard/comtudia_ui.xml "$TMP_DIR/ui.xml" >/dev/null 2>&1 || true
    [ -s "$TMP_DIR/ui.xml" ] && break
    sleep 1
  done
  "${ADB[@]}" shell rm -f /sdcard/comtudia_ui.xml >/dev/null 2>&1 || true
}

find_center() {
  # $1 = attribute, $2 = value; prints "x y" or nothing.
  python3 - "$TMP_DIR/ui.xml" "$1" "$2" <<'PY'
import re
import sys

path, attr, value = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    xml = open(path, encoding="utf-8", errors="replace").read()
except OSError:
    sys.exit(0)
pat = attr + r'="' + re.escape(value) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
m = re.search(pat, xml)
if m:
    print((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2)
PY
}

tap_text() {
  local coords
  dump_ui
  coords="$(find_center text "$1")"
  [ -n "$coords" ] || return 1
  # shellcheck disable=SC2086
  "${ADB[@]}" shell input tap $coords
}

enter_package() {
  info "Entering package name"
  tap_text "click to input package name" || fail "package-name button not found on the debug page"
  sleep 2

  local coords
  dump_ui
  coords="$(find_center class "android.widget.EditText")"
  if [ -n "$coords" ]; then
    # shellcheck disable=SC2086
    "${ADB[@]}" shell input tap $coords
  fi
  sleep 1

  local first rest
  first="${PKG%%.*}"
  rest="${PKG#*.}"
  "${ADB[@]}" shell input text "$first"
  sleep 0.2
  "${ADB[@]}" shell input keyevent 56   # dot
  sleep 0.3
  "${ADB[@]}" shell input keyevent 67   # remove keyboard auto-space after dot
  sleep 0.1
  "${ADB[@]}" shell input text "$rest"
  sleep 0.5

  tap_text "OK" || fail "OK button not found after entering the package name"
  sleep 2
}

enter_package

if [ "$UNINSTALL_ONLY" = true ]; then
  info "Uninstalling $PKG from the band"
  tap_text "uninstall third app" || fail "uninstall button not found on the debug page"
  sleep 5
  echo ""
  echo "Uninstall request sent for $PKG. Check the band's app list."
  exit 0
fi

if [ "$DO_UNINSTALL" = true ]; then
  info "Uninstalling existing band package"
  tap_text "uninstall third app" || warn "uninstall button not found; continuing"
  sleep 5
fi

info "Tapping 'install third app'"
tap_text "install third app" || fail "install button not found on the debug page"
sleep 2

echo ""
echo "The phone file picker should now be open in /sdcard/Xiaomi-band."
echo "Tap this file to install it on the band:"
echo "  $(basename "$DEVICE_RPK")"
echo ""
if [ -t 0 ]; then
  read -r -p "Press Enter after the band reports the install result..." _
fi

#!/usr/bin/env bash
#
# band-build.sh - build the comtudia Vela Quick App into an .rpk
#
# Verified toolkit:
#   npm package: aiot-toolkit@2.0.5 (bin: aiot)
#   command:     aiot build
#   output:      band/comtudia/dist/<package>.debug.<versionName>.rpk
#
# The first run installs the toolkit into band/comtudia/node_modules when Node
# and npm are available. If Node is missing, use AIoT-IDE instead (see
# docs/band.md) and build the project at band/comtudia.
#
# Options:
#   --no-install   Do not run npm install; fail if the aiot CLI is missing
#   -h, --help     Show this help
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT/band/comtudia"
MANIFEST="$APP_DIR/src/manifest.json"
NO_INSTALL=false

usage() {
  sed -n '2,/^set -euo/{ /^set -euo/d; s/^# \?//; p }' "$0"
}

for arg in "$@"; do
  case "$arg" in
    --no-install) NO_INSTALL=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $arg (use --help)" >&2; exit 2 ;;
  esac
done

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

no_node_help() {
  cat >&2 <<'EOF'
ERROR: Node.js is not installed, so the AIoT toolkit CLI cannot run.

Build one of these ways instead:
  1. Install Node.js 14+ (tested with 20.18.0), then rerun this script.
  2. Open band/comtudia in AIoT-IDE and use Build:
       https://iot.mi.com/vela/quickapp/en/guide/start/use-ide.html
     The expected output is:
       band/comtudia/dist/*.rpk
EOF
}

[ -d "$APP_DIR" ] || fail "project not found: $APP_DIR"
[ -f "$MANIFEST" ] || fail "manifest not found: $MANIFEST"

AIOT_BIN="${AIOT_BIN:-}"
if [ -n "$AIOT_BIN" ] && [ ! -x "$AIOT_BIN" ]; then
  fail "AIOT_BIN is set but not executable: $AIOT_BIN"
fi
if [ -z "$AIOT_BIN" ] && [ -x "$APP_DIR/node_modules/.bin/aiot" ]; then
  AIOT_BIN="$APP_DIR/node_modules/.bin/aiot"
fi
if [ -z "$AIOT_BIN" ] && command -v aiot >/dev/null 2>&1; then
  AIOT_BIN="$(command -v aiot)"
fi

if [ -n "$AIOT_BIN" ] && ! "$AIOT_BIN" --version >/dev/null 2>&1; then
  if ! command -v node >/dev/null 2>&1; then
    no_node_help
    exit 1
  fi
  fail "AIoT CLI is present but not runnable: $AIOT_BIN"
fi

if [ -z "$AIOT_BIN" ]; then
  if ! command -v node >/dev/null 2>&1; then
    no_node_help
    exit 1
  fi
  if ! command -v npm >/dev/null 2>&1; then
    fail "npm is not installed. Install Node.js 14+ or build with AIoT-IDE (expected output: band/comtudia/dist/*.rpk)."
  fi
  if [ "$NO_INSTALL" = true ]; then
    fail "aiot CLI is not installed. Run (cd band/comtudia && npm install), or rerun without --no-install."
  fi

  NODE_MAJOR="$(node -p 'parseInt(process.versions.node, 10)')"
  if [ "$NODE_MAJOR" -lt 14 ]; then
    fail "Node.js 14+ is required (found $(node --version)). Use AIoT-IDE or upgrade Node."
  fi

  echo "==> Installing aiot-toolkit into band/comtudia/node_modules (first run only)"
  (cd "$APP_DIR" && npm install --no-audit --no-fund --no-package-lock) || {
    fail "npm install failed. Build with AIoT-IDE instead (expected output: band/comtudia/dist/*.rpk)."
  }
  AIOT_BIN="$APP_DIR/node_modules/.bin/aiot"
  [ -x "$AIOT_BIN" ] || fail "toolkit installed but $AIOT_BIN is missing"
fi

echo "==> Toolkit: $("$AIOT_BIN" --version 2>/dev/null || echo unknown)"
echo "==> Project: $APP_DIR"
echo "==> Building..."
(cd "$APP_DIR" && "$AIOT_BIN" build) || {
  fail "build failed. Check the compiler output above, or open band/comtudia in AIoT-IDE."
}

RPK="$(find "$APP_DIR/dist" -maxdepth 1 -type f -name '*.rpk' -printf '%T@ %p\n' 2>/dev/null \
  | sort -nr | head -1 | cut -d' ' -f2-)"
if [ -z "$RPK" ] || [ ! -f "$RPK" ]; then
  fail "build finished but no .rpk was produced in band/comtudia/dist/"
fi

VERSION_CODE="$(sed -n 's/.*"versionCode"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$MANIFEST" | head -1)"
echo ""
echo "RPK: $RPK"
echo "Size: $(wc -c < "$RPK") bytes"
echo "versionCode: ${VERSION_CODE:-unknown}"
echo ""
echo "Install with:"
echo "  tools/deploy-rpk.sh --rpk \"$RPK\" --package com.comtudia.app"

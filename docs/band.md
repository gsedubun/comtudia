# comtudia on the Xiaomi Smart Band 9 Pro

A standalone Vela JS Quick App version of comtudia for the Xiaomi Smart Band
9 Pro. It shows the current fasting/eating phase, a drift-free countdown, the
next phase boundary, the relevant meal schedule, and an on-band plan editor.
There is no phone sync yet; the plan is stored on the band with
`@system.storage`.

The dashboard's large countdown shows `H:MM` above one hour and `MM:SS` below
one hour. A full `HH:MM:SS` string at the requested 44–52 px size is wider than
the 168 design-width capsule; `format.countdown()` still returns the exact
desktop-style `HH:MM:SS` for tests and future layouts. The **Next** line is the
next phase boundary: the end of the current eating phase, or the next meal
start while fasting. The schedule page separately marks the next meal, and
labels its cycle day Today/Tomorrow/Yesterday so a window that crosses midnight
stays coherent.

> **Status:** the schedule math is tested with Node, and the RPK builds with no
> Vela compiler warnings. The deploy path has been executed end to end on a
> Samsung Galaxy S21 FE (Android 16) with Mi Fitness 3.59.2i: the hidden debug
> page opened, the RPK transferred, and **the app appeared in the band's app
> list**. Launching it on the test unit (global Smart Band 9 Pro) **crashed and
> rebooted the band**, so it was uninstalled again. Do not expect the RPK to run
> on global firmware; the phone notification path is the supported experience.
> See [What is verified](#what-is-verified).

On the test unit the install/launch sequence was:

1. `tools/deploy-rpk.sh` pushed the RPK and drove the debug page.
2. The band showed a `comtudia` entry in its app list.
3. Tapping the entry rebooted the band (the reported global-firmware failure).
4. `tools/deploy-rpk.sh --uninstall-only` removed it; Mi Fitness reported
   `uninstallApp success`.

## Why there is no app store

Xiaomi bands do not have an app store for third-party apps. A Vela JS Quick
App is packaged as an `.rpk` and installed through Mi Fitness's hidden
**Third App Support** page. On international firmware that page is not in the
normal menu. The community workaround launches the hidden
`ThirdAppDebugFragment` with a small `app_process` helper over ADB; see
[oryonatan/xiaomi-band-development](https://github.com/oryonatan/xiaomi-band-development)
(MIT). `tools/deploy-rpk.sh` is adapted from that project.

This path is unsupported by Xiaomi and can break after a Mi Fitness or band
firmware update.

## Verified target facts

| Fact | Value / behaviour | Source |
| --- | --- | --- |
| Screen | 336 x 480 AMOLED, capsule | oryonatan device table; target hardware notes |
| Design width | `168` | oryonatan device table (`336 / 2`) |
| Runtime | Vela JS Quick App, Vue-like `.ux` files | [Project structure](https://iot.mi.com/vela/quickapp/en/guide/framework/project-structure.html) |
| Manifest package | `com.comtudia.app`, same as the Android `applicationId` | `android/app/build.gradle`; [manifest docs](https://iot.mi.com/vela/quickapp/en/guide/framework/manifest.html) |
| Manifest required fields | `package`, `name`, `icon`, `versionCode`, `config`, `router` | manifest docs |
| Device type | `deviceTypeList: ["watch"]` | manifest docs; Songbird example |
| Storage | `@system.storage` with `get`/`set`/`delete`/`clear`; declare `system.storage` in `features` | [Data storage](https://iot.mi.com/vela/quickapp/en/features/data/storage.html) |
| Progress ring | `<progress type="arc">` with `percent`, `start-angle`, `total-angle`, `stroke-width`, `layer-color` | [progress](https://iot.mi.com/vela/quickapp/en/components/basic/progress.html) |
| Scrollable list | `<list>` + `<list-item>`; list needs an explicit height, item needs an explicit height | [list](https://iot.mi.com/vela/quickapp/en/components/container/list.html) |
| Page lifecycle | `onInit`, `onReady`, `onShow`, `onHide`, `onDestroy` | [Lifecycle](https://iot.mi.com/vela/quickapp/en/guide/framework/script/lifecycle.html) |
| Layout | Flexbox; default `flex-direction` is `row`, so pages set `column` | [General style](https://iot.mi.com/vela/quickapp/en/components/general/style.html) |
| Build CLI | `aiot-toolkit@2.0.5`, bin `aiot`, command `aiot build` | npm package, verified locally with Node 20.18.0 |
| Build output | `dist/com.comtudia.app.debug.1.0.0.rpk` | verified locally |

`minPlatformVersion: 1000` and the manifest shape follow the working Songbird
example from the upstream repository. `minAPILevel: 1` is declared explicitly.

## Repository layout

```
band/comtudia/
├── package.json                 # aiot-toolkit dev dependencies and build script
├── src/
│   ├── manifest.json            # package, designWidth 168, dark display
│   ├── app.ux                   # app lifecycle logging
│   ├── common/
│   │   ├── plan.js              # pure schedule math (Node-testable)
│   │   ├── format.js            # countdown/clock/duration helpers
│   │   ├── store.js             # @system.storage wrapper
│   │   └── icon.png             # 192 x 192 app icon
│   └── pages/
│       ├── index/index.ux       # dashboard ring + countdown
│       ├── schedule/schedule.ux # meal list with NOW/NEXT
│       └── settings/settings.ux # +/- plan editor
└── test/schedule-check.js       # Node assertion script, not packaged
```

`tools/band-build.sh` builds the app. `tools/deploy-rpk.sh` installs it on the
band through the phone. Neither script is part of the RPK.

## Prerequisites

- Xiaomi Smart Band 9 Pro paired and synced in **Mi Fitness** on the phone.
- Samsung Galaxy S21 FE (Android 14) with:
  - Developer options enabled (Settings -> About phone -> Software information
    -> tap **Build number** seven times).
  - **USB debugging** enabled.
  - The RSA authorization prompt accepted after connecting USB.
  - The phone unlocked while deploying, so `uiautomator` can read and tap the
    debug page.
- `adb` on the computer (Android platform-tools).
- For deployment only: a JDK (`javac`, `java`), an Android SDK platform
  (`platforms/android-*/android.jar`), Android build-tools with `d8`, and
  Python 3 for `uiautomator` XML parsing.
- For CLI builds: Node.js 14+ (tested with Node 20.18.0) and npm.
  Alternatively use AIoT-IDE, which bundles its own toolchain.

The repository's `tools/android-env.sh` can provision a JDK and Android SDK
under `~/.local`. `tools/deploy-rpk.sh` looks for `~/.local/android-sdk` and
`~/.local/jdk17` automatically, and also accepts `ANDROID_HOME`,
`ANDROID_SDK_ROOT`, and `JAVA_HOME`.

No root is required. The deploy path uses the phone's shell user and Mi
Fitness's own classes.

## Build

### Option A: AIoT-IDE

1. Install [AIoT-IDE](https://iot.mi.com/vela/quickapp/en/guide/start/use-ide.html).
2. Open the project directory `band/comtudia`.
3. Use the IDE's Build action.
4. The expected output is:

   ```
   band/comtudia/dist/*.rpk
   ```

AIoT-IDE also includes an emulator. This is the recommended path if Node is
not available.

### Option B: CLI (verified)

```bash
cd band/comtudia
npm install
npx aiot build
```

Or use the wrapper, which installs the toolkit on first use, builds, and prints
the newest RPK:

```bash
tools/band-build.sh
```

Verified output on Linux with Node 20.18.0:

```
dist/com.comtudia.app.debug.1.0.0.rpk
```

The `.debug.` name comes from the toolkit's development mode. The filename uses
`versionName`; the integer install version is `versionCode`.

If Node is missing, `tools/band-build.sh` exits with a message pointing at
AIoT-IDE and the expected `band/comtudia/dist/*.rpk` path. `npm install` only
installs development dependencies into the git-ignored `node_modules/`;
nothing extra is shipped inside the RPK.

The build uses the toolkit's built-in development signing certificate unless
`band/comtudia/sign/private.pem` and `sign/certificate.pem` exist. The
`sign/` directory is git-ignored. This matters for the future Interconnect
work (see below).

## Install

The phone must be connected by USB (or wireless debugging) and authorized.

```bash
adb devices
tools/deploy-rpk.sh \
  --rpk band/comtudia/dist/com.comtudia.app.debug.1.0.0.rpk \
  --package com.comtudia.app
```

`--serial <adb-serial>` selects a phone when more than one ADB device is
attached. If `--rpk` is omitted, the newest RPK in `band/comtudia/dist` is
used. If `--package` is omitted, it is read from `src/manifest.json`.

Before touching the phone, check that the script can see:

- `adb` on `PATH`;
- an authorized phone (`adb devices` shows `device`, not `unauthorized`);
- Mi Fitness `com.xiaomi.wearable` installed (not the Chinese
  `com.xiaomi.wearable.chn`);
- `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) pointing at an SDK with a platform
  `android.jar` and build-tools `d8`, if the launcher dex is not already
  cached at `/data/local/tmp/launch.dex`.

The script then:

1. Builds or reuses a tiny `LaunchFragment` dex with `javac` and `d8`.
2. Pushes the RPK to `/sdcard/Xiaomi-band/com_comtudia_app.rpk`.
3. Launches Mi Fitness's hidden `ThirdAppDebugFragment`.
4. Enters the package name (including the Samsung keyboard's automatic space
   after the first dot) and taps **install third app**.
5. Opens the phone's file picker.

**The one manual step:** tap the pushed `.rpk` in the file picker. Mi Fitness
then transfers and installs it on the band. The script prints the filename and
waits for you to confirm when run interactively.

If your firmware exposes the debug page in the normal UI, the documented path
is **Mi Fitness -> Me -> About -> Debug -> Third-party apps**. On many
international builds it is hidden, which is why the script exists.

## Updating and `versionCode`

The band caches packages. Every reinstall should bump the integer
`versionCode` in `band/comtudia/src/manifest.json`:

```json
"versionName": "1.0.1",
"versionCode": 2
```

Then rebuild and install again. `versionName` is display text and controls the
`.rpk` filename; `versionCode` is what the band compares. If the old version
still appears:

```bash
tools/deploy-rpk.sh --uninstall-only --package com.comtudia.app
```

then install the new RPK. `tools/deploy-rpk.sh --uninstall ...` uninstalls and
then installs in one run.

## Uninstall

```bash
tools/deploy-rpk.sh --uninstall-only --package com.comtudia.app
```

This launches the hidden page and taps **uninstall third app**. The same
button is on the debug page if you open it another way. Uninstalling removes
the on-band plan stored under the app's private storage.

## Risks, battery, and notifications

### Global/international firmware caveat

- Community reports describe crashes or reboots on global firmware after
  installing custom RPKs. This is **confirmed on the reference unit**: the app
  installed and was listed, then rebooted the band on launch. There is no
  official recovery path from Xiaomi.
- A Mi Fitness or band firmware update can remove or rename the hidden debug
  page and the internal classes the launcher depends on.
- Do not install this on a band whose health data you cannot afford to lose.
  Keep the band synced and know how to reset it before experimenting.
- The package name `com.comtudia.app` must not collide with another custom app.
- No warranty: this is an unsupported sideload.

### Battery

- The one-second timer runs only while the dashboard is visible. `onHide` and
  `onDestroy` clear it.
- The countdown is recomputed from the phase-end timestamp on every tick, not
  decremented, so it cannot drift.
- The UI is black (`#000000`) for AMOLED, with a single arc and text; there are
  no images, animations, network calls, or analytics.
- No background service. The app does not update while it is closed, and there
  are no notifications. Battery impact has not been measured on hardware.

### Notifications

The app does **not** send notifications or vibrate at phase changes. Your
band's normal notifications are unaffected. Adding phase-change alerts would
need Vela's notification/vibration APIs and a background-running declaration;
that is deliberately out of scope until the install path is proven.

## Future phone sync via Interconnect

The app is already named `com.comtudia.app`, matching the Android
`applicationId` in `android/app/build.gradle`. That is necessary but not
sufficient for Xiaomi's Interconnect path:

1. The RPK package name and the phone APK package name must match.
2. The RPK and the APK must be signed with the **same certificate** for the
   devices to trust each other. The current debug RPK is signed with the
   toolkit's development certificate, not the Android app's key.
3. A phone-side Interconnect service and the Vela `system.interconnect`
   feature still need to be implemented.

To prepare signing, generate/obtain the APK signing key pair, place it at
`band/comtudia/sign/private.pem` and `band/comtudia/sign/certificate.pem`, and
build in release mode with the toolkit. Do not commit private keys; `sign/` is
git-ignored. None of this has been tested.

## What is verified

Verified locally:

- `node --check` passes for all `.js` files.
- `band/comtudia/test/schedule-check.js` passes against the four required
  cases plus boundary, leftover-minute, validation, and corrupt-storage checks.
- The RPK builds with `aiot-toolkit@2.0.5` on Node 20.18.0, with no Vela
  compiler warnings.
- The built manifest contains `package: com.comtudia.app`, `designWidth: 168`,
  `versionCode: 1`, and the three routes.
- `tools/band-build.sh` and `tools/deploy-rpk.sh` pass `bash -n`; the
  no-Node/no-adb error paths were exercised.

Verified on hardware (2026-10-27):

- Phone: Samsung Galaxy S21 FE, Android 16, Mi Fitness `com.xiaomi.wearable`
  3.59.2i, band paired.
- `tools/deploy-rpk.sh` compiled the launcher, opened
  `ThirdAppSupport`, entered `com.comtudia.app`, pushed the RPK, and Mi Fitness
  reported `发送文件成功` (file sent).
- The band listed a `comtudia` app in its app list.
- Launching that app **crashed/rebooted the band**. Uninstalling with
  `--uninstall-only --package com.comtudia.app` returned `uninstallApp success`.
- Device artifacts (`/sdcard/Xiaomi-band/*.rpk`, `/data/local/tmp/launch.dex`)
  were removed afterwards, and the band no longer lists the app.

Not verified:

- Rendering on a band, because the app cannot be launched on global firmware.
- `@system.storage` behaviour on the band.
- Battery use.

The deploy script now types the package name and verifies the field text before
tapping OK: the earlier fixed sequence (`dot`, then `delete` to remove a
keyboard auto-space) dropped the first dot on Samsung's keyboard.

## Screenshot

Screenshot placeholder: `docs/band-dashboard.png` still needs to be captured
from the Vela emulator or a real band. Do not commit a mock-up. Capture the
dashboard at 336 x 480, ideally with the app in both fasting and eating phases
so the blue/green accent is visible.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `adb devices` shows `unauthorized` | Replug USB, accept the RSA prompt. If it never appears: Developer options -> Revoke USB debugging authorizations, then replug. |
| No phone found | Enable USB debugging, try another cable/port, or `adb pair` + `adb connect` for wireless debugging. |
| `Mi Fitness is not installed` | Install the international `com.xiaomi.wearable`, not the Chinese `com.xiaomi.wearable.chn`. |
| Launcher says `FAILED` / class not found | A Mi Fitness update likely changed the internal debug fragment. Check the upstream repository for an updated launcher. |
| Script cannot find a debug-page button | The UI automation looks for the English labels `click to input package name`, `install third app`, `uninstall third app`, and `OK`. Switch Mi Fitness/phone to English or tap the button manually once the page is open. |
| File picker does not show the RPK | Open the phone's Files app and browse to `Xiaomi-band/`; the RPK was pushed there. |
| Install succeeds but old version launches | Bump `versionCode`, rebuild, and uninstall the old package first. |
| Band reboots or app crashes | Uninstall immediately. This is the global-firmware risk above; do not keep retrying. |
| `d8`/`javac` missing | Install a JDK and Android build-tools, or set `ANDROID_HOME`. |
| `aiot` not found / Node missing | Install Node 14+ (tested 20.18.0) or build with AIoT-IDE. |

## Sources

- [Vela project structure](https://iot.mi.com/vela/quickapp/en/guide/framework/project-structure.html)
- [Vela project configuration / manifest](https://iot.mi.com/vela/quickapp/en/guide/framework/manifest.html)
- [Vela `.ux` files](https://iot.mi.com/vela/quickapp/en/guide/framework/ux.html)
- [Vela page lifecycle](https://iot.mi.com/vela/quickapp/en/guide/framework/script/lifecycle.html)
- [Vela data storage](https://iot.mi.com/vela/quickapp/en/features/data/storage.html)
- [Vela `progress` component](https://iot.mi.com/vela/quickapp/en/components/basic/progress.html)
- [Vela `list` component](https://iot.mi.com/vela/quickapp/en/components/container/list.html)
- [Vela `stack` component](https://iot.mi.com/vela/quickapp/en/components/container/stack.html)
- [Vela general styles](https://iot.mi.com/vela/quickapp/en/components/general/style.html)
- [Vela build parameters](https://iot.mi.com/vela/quickapp/en/guide/start/toolkit-params.html)
- [AIoT-IDE](https://iot.mi.com/vela/quickapp/en/guide/start/use-ide.html)
- [oryonatan/xiaomi-band-development](https://github.com/oryonatan/xiaomi-band-development)
- [Upstream deploy script](https://github.com/oryonatan/xiaomi-band-development/blob/develop/scripts/deploy.sh)
- [Upstream real-device deployment notes](https://github.com/oryonatan/xiaomi-band-development/blob/develop/docs/deployment-real-device.md)
- [aiot-toolkit on npm](https://www.npmjs.com/package/aiot-toolkit)

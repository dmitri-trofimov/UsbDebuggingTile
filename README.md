# USB Debugging Tile

An open-source Quick Settings tile and Material 3 Expressive companion app for Android that brings instant **1-tap USB Debugging toggling** to your status bar.

Designed to mirror the native Android **Wireless debugging** tile, it allows developers and power users to keep their physical USB port secured against unauthorized cable access and juice-jacking, unlocking it only when actively needed.

---

## Features

- ⚡ **1-Tap Quick Settings Tile**: Toggle USB Debugging (`adb`) directly from the notification shade without digging through nested Developer Options menus.
- 🔒 **Security First (Juice-Jacking Protection)**: Keep USB debugging disabled by default to protect your device at public charging kiosks or untrusted computers; enable with a single tap when developing.
- 🎨 **Material 3 Expressive Design**:
  - **One UI Hero Master Switch**: Tap the card or toggle slider for immediate state switching with haptic/ripple feedback.
  - **Flat In-Card Flow**: Clean, modern containment without nested card clutter ("container matryoshka").
  - **Adaptive & Themed Icons**: High-contrast icon assets with full support for Android 13+ / Samsung One UI monochrome color palette theming.
  - **Dynamic Contextual Disclosure**: Actionable setup commands tuck away automatically once prerequisites are satisfied.
- 🛡️ **Samsung One UI & Auto Blocker Awareness**: Built-in detection for Samsung's Auto Blocker (`com.samsung.android.rampart`) which silently blocks USB cable commands on One UI 6+.
- 📥 **1-Click PC Setup Batch Export**: Export an all-in-one `.bat` script directly to your phone's storage to automate all PC prerequisites with a single double-click.

---

## One-Time Setup (Prerequisites)

Because Android protects system-level developer toggles, the app requires the `WRITE_SECURE_SETTINGS` permission once via ADB:

### Option A: 1-Click Script (Recommended)
1. Open the **USB Debugging** app on your phone.
2. Tap **Export 1-Click Setup Script (.bat)**.
3. Connect your device to your PC via USB cable.
4. On your PC, navigate to your device's `Download` folder and double-click `setup_usb_tile.bat`.

### Option B: Manual ADB Commands
Open a terminal on your computer with your device connected and run:

```bash
# 1. Grant the tile permission to toggle USB debugging
adb shell pm grant com.antigravity.usbdebugtile android.permission.WRITE_SECURE_SETTINGS

# 2. (Samsung One UI only) If Auto Blocker is enabled and blocks cable commands:
adb shell pm disable-user --user 0 com.samsung.android.rampart
```

---

## Adding the Tile to Quick Settings

1. Swipe down twice from the top of your screen to open the full Quick Settings panel.
2. Tap the **Edit** (pencil) icon.
3. Locate **USB Debugging** in the available tiles list.
4. Drag it into your active tile grid.

---

## Building from Source

### Requirements
- **JDK 17** or higher
- **Android SDK** (API 34 / Android 14)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/dmitri-trofimov/UsbDebuggingTile.git
cd UsbDebuggingTile

# Build the debug APK
./gradlew assembleDebug

# Install directly to your connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Tech Stack & Architecture

- **Language**: Java 17
- **UI Framework**: Material Components for Android (`com.google.android.material:material:1.12.0`)
- **Target SDK**: Android 14 (API 34)
- **Minimum SDK**: Android 7.0 (API 24)
- **Core APIs**: `android.service.quicksettings.TileService`, `Settings.Global.ADB_ENABLED`

---

## License

This project is licensed under the MIT License.

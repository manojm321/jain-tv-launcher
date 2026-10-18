# tv-vibehack (Jain TV Launcher)

Apple TV-style leanback launcher and debloater for Android TV & Google TV, featuring an integrated offline Jain Tithi and Choghadiya engine.

## What it does

- **Clean tvOS Architecture**: Pitch-black background with dynamic atmospheric time-of-day sky gradient.
- **Hero Dock & Liquid Glass**: Pinned dock apps with spring-animated frosted glass highlights and reactive ambient color glows. Redundant app text titles have been removed for an uncluttered look.
- **Top Bar**: Minimalist greeting, large clock, date, and quick pills for Inputs (HDMI / Live TV), Wi-Fi SSID, and Settings.
- **Big Solar Cards**: Four large frosted glass cards displaying **Weather** (temp & condition), **Sunrise** (dawn), **Navakarsi** (sunrise + 48 min, highlighted in gold), and **Sunset** (dusk).
- **Next 7 Days Jain Tithi**: Astronomical lunar ephemeris (*Surya Udaya Tithi*) engine showing the upcoming 7 days of tithis with sacred parva highlights (Aathm, Agiyaras, Chaudas, Poonam/Amas). 100% offline.
- **Real-Time Choghadiya Timeline**: 8 canonical slots for the day (or night) with color-coded badges (🟢 Good / Best, 🟡 Neutral, 🔴 Avoid) and an illuminated real-time active slot highlight with `NOW` indicator.
- **Zero Ads & Bloat**: Bypasses all sponsored recommendations, banners, and manufacturer telemetry.

---

## How to Deploy

### 1. Prerequisites on the TV

1. **Connect to Wi-Fi**: Ensure your TV and your computer are on the same local Wi-Fi network.
2. **Enable Developer Options**:
   - Go to TV **Settings** → **Device Preferences** (or **System**) → **About**.
   - Scroll down to **Android TV OS build** (or **Build**) and click the OK/Select button **7 times** until you see *"You are now a developer!"*.
3. **Turn on USB / Network Debugging**:
   - Go back to **Settings** → **Device Preferences** → **Developer options**.
   - Enable **USB debugging** (and **Network debugging** if listed).
4. **Note TV IP Address**:
   - Go to **Settings** → **Network & Internet** → Select your connected Wi-Fi network to find the IP address (e.g., `192.168.8.157`).

---

### 2. Prerequisites on Your Computer

You need **Python 3**, **JDK 17**, and the **Android SDK**:

- **macOS** (via Homebrew):
  ```bash
  brew install openjdk@17 android-commandlinetools python3
  sdkmanager "platforms;android-34" "build-tools;34.0.0"
  ```
- **Linux (Ubuntu/Debian)**:
  ```bash
  sudo apt install openjdk-17-jdk python3 python3-venv
  ```
- **Windows**:
  Install Android Studio (bundled with JDK 17 & SDK) and Python from python.org.

---

### 3. Setup Python ADB Environment

Create the isolated virtual environment for the pure-Python ADB tools (this eliminates the need for system `adb` binaries and bypasses macOS local network permission blocks):

```bash
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
```

---

### 4. One-Command Build & Deploy

Run the automated build & install script with your TV's IP address:

```bash
TV_HOST=192.168.8.157 launcher/build-and-install.sh
```

*(On Windows PowerShell, run: `powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.8.157`)*

**What this script does:**
1. Compiles the native launcher APK with Gradle.
2. Prompts the TV to allow ADB debugging (accept the dialog on your TV screen if it appears).
3. Transfers the APK via `b64push.py` with MD5 checksum verification.
4. Installs the APK (`pm install -r`).
5. Sets `com.gathin.tvhome/.MainActivity` as the default home launcher.
6. Disables the ad-heavy stock launcher and presses Home.

---

### 5. Manual Step-by-Step Deployment (Alternative)

If you prefer full control over each step:

```bash
# 1. Compile the APK
cd launcher
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug
cd ..

# 2. Push APK to TV
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/b64push.py \
  launcher/app/build/outputs/apk/debug/app-debug.apk \
  /data/local/tmp/tvhome.apk

# 3. Install on TV
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py \
  'pm install -r -d -g /data/local/tmp/tvhome.apk'

# 4. Clean up temporary APK
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py \
  'rm -f /data/local/tmp/tvhome.apk'

# 5. Set as default Home launcher and launch
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py \
  'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity'
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py \
  'input keyevent KEYCODE_HOME'
```

---

### 6. Debloating & Restoring

To disable stock manufacturer telemetry and ad apps:
```bash
TV_HOST=192.168.8.157 tools/disable.sh examples/sony-bravia-bf1/debloat.txt
```

To restore all stock apps and the original manufacturer launcher at any time:
```bash
TV_HOST=192.168.8.157 tools/restore.sh examples/sony-bravia-bf1/debloat.txt
```

---

## Customization

### Configuring Dock Apps
Edit [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
```xml
<string-array name="dock_apps">
    <item>com.netflix.ninja</item>
    <item>com.amazon.amazonvideo.livingroom</item>
    <item>com.google.android.youtube.tv</item>
    <item>com.android.tv.settings</item>
</string-array>
```

---

## Jain Tithi & Choghadiya Engine

- **Surya Udaya Tithi**: Calculated via lunar elongation at local sunrise ($\lfloor(\lambda_{\text{Moon}} - \lambda_{\text{Sun}})/12^\circ\rfloor + 1$). Completely self-contained and accurate without any external network dependency.
- **Parva Tithi Badges**: Sacred days (Aathm 🪔, Agiyaras, Chaudas, Poonam, Amas, Pancham) are automatically highlighted with golden badges.
- **Choghadiya Timing**: Dynamically calculates the 8 canonical daytime slots between Sunrise and Sunset (or night slots between Sunset and Sunrise) for each day of the week:
  - 🟢 **Auspicious**: *Amrit*, *Shubh*, *Labh*
  - 🟡 **Neutral**: *Chal*
  - 🔴 **Inauspicious**: *Rog*, *Kal*, *Udveg*
- **Live Active Slot**: The currently active slot is illuminated with an inset frosted glow and real-time announcement in the section header.

---

## Useful ADB Commands

```bash
# Check installed 3rd-party streaming apps
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py 'pm list packages -3'

# Take a screenshot and pull it to docs/
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py 'screencap -p /data/local/tmp/s.png'
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py --pull /data/local/tmp/s.png docs/s.png
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py 'rm -f /data/local/tmp/s.png'
```

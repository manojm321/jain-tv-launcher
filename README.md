# tv-vibehack

Apple TV-style leanback launcher and debloater for Sony BRAVIA Android TV (BRAVIA BF1, Android 12).

## What it does

- **Clean tvOS Architecture**: Pitch-black background with dynamic atmospheric time-of-day sky gradient.
- **Hero Dock & Liquid Glass**: Pinned dock apps with spring-animated frosted glass highlights and reactive ambient color glows. Redundant app text titles have been removed for an uncluttered look.
- **Top Bar**: Minimalist greeting, large clock, date, and quick pills for Inputs (HDMI / Live TV), Wi-Fi SSID, and Settings.
- **Big Solar Cards**: Four large frosted glass cards displaying **Weather** (temp & condition), **Sunrise** (dawn), **Navakarsi** (sunrise + 48 min, highlighted in gold), and **Sunset** (dusk).
- **Next 7 Days Jain Tithi**: Astronomical lunar ephemeris (*Surya Udaya Tithi*) engine showing the upcoming 7 days of tithis with sacred parva highlights (Aathm, Agiyaras, Chaudas, Poonam/Amas). 100% offline.
- **Real-Time Choghadiya Timeline**: 8 canonical slots for the day (or night) with color-coded badges (🟢 Good / Best, 🟡 Neutral, 🔴 Avoid) and an illuminated real-time active slot highlight with `NOW` indicator.

## Project Structure

```
tv-vibehack/
├── launcher/               # Android TV Launcher app
│   ├── app/
│   │   ├── src/main/java/com/gathin/tvhome/
│   │   │   ├── MainActivity.java    # Home activity, liquid springs, focus routing
│   │   │   ├── AppAdapter.java      # App dock & grid adapter
│   │   │   ├── GlassView.java       # Custom tvOS liquid glass highlight
│   │   │   ├── WeatherManager.java  # Open-Meteo & NOAA astronomical solar calculations
│   │   │   └── PanchangManager.java # Astronomical Jain Tithi & canonical Choghadiya engine
│   │   └── src/main/res/            # Layouts, vector drawables, frosted shapes
│   └── build-and-install.sh         # Build, push and install script
└── tools/                           # Python ADB client tools (bypasses macOS local network blocks)
    ├── b64push.py                   # Base64 streaming file uploader with MD5 verification
    ├── tv.py                        # Pure-python ADB bridge wrapper
    └── requirements.txt             # Python dependencies (adb-shell)
```

## Jain Tithi & Choghadiya Engine

- **Surya Udaya Tithi**: Calculated via lunar elongation at local sunrise ($\lfloor(\lambda_{\text{Moon}} - \lambda_{\text{Sun}})/12^\circ\rfloor + 1$). Completely self-contained and accurate without any external network dependency.
- **Parva Tithi Badges**: Sacred days (Aathm 🪔, Agiyaras, Chaudas, Poonam, Amas, Pancham) are automatically highlighted with golden badges.
- **Choghadiya Timing**: Dynamically calculates the 8 canonical daytime slots between Sunrise and Sunset (or night slots between Sunset and Sunrise) for each day of the week:
  - 🟢 **Auspicious**: *Amrit*, *Shubh*, *Labh*
  - 🟡 **Neutral**: *Chal*
  - 🔴 **Inauspicious**: *Rog*, *Kal*, *Udveg*
- **Live Active Slot**: The currently active slot is illuminated with an inset frosted glow and real-time announcement in the section header.

## Customization

### Re-ordering or Changing Dock Apps
Edit [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml):
```xml
<string-array name="dock_apps">
    <item>com.netflix.ninja</item>
    <item>com.amazon.amazonvideo.livingroom</item>
    <item>com.google.android.youtube.tv</item>
    <item>com.android.tv.settings</item>
</string-array>
```

## Talking to the TV by hand

All ADB operations use the local Python venv to prevent macOS local network firewall interference:
```bash
# Connect and execute a shell command
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py 'pm list packages -3'

# Push an APK or file
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/b64push.py local.apk /data/local/tmp/remote.apk

# Capture a screenshot
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py 'screencap -p /data/local/tmp/s.png'
TV_HOST=192.168.8.157 tools/venv/bin/python -I tools/tv.py --pull /data/local/tmp/s.png docs/s.png
```

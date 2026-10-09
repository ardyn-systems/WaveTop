<p align="center">
  <img src="docs/images/wavetop-logo.png" alt="WaveTop" width="320" />
</p>

# WaveTop

**See what's on the air.**

WaveTop is an Android app that surveys the Wi-Fi networks and Bluetooth devices around you, pins them
on a map where they're heard loudest, and records **wardrives** you can share or send straight to
[NetSeer](https://github.com/ardyn-systems/NetSeer) to map them. It uses only Android's own scanning
APIs: no root, no monitor mode, no Google Play services. Nothing leaves your phone unless you send it.

<p align="center">
  <img src="docs/images/devices.png" alt="Devices" width="200" />
  <img src="docs/images/map-street.png" alt="Street map" width="200" />
  <img src="docs/images/drive-map.png" alt="A saved wardrive" width="200" />
  <img src="docs/images/settings-general.png" alt="Settings" width="200" />
</p>

## What it does

- **Devices** — every nearby access point and Bluetooth device, live: signal with a history
  sparkline, channel, security (open networks in red), vendor from the IEEE OUI registry. Filter by
  Wi-Fi or Bluetooth and sort by signal, name, security, channel, or last seen.
- **Map** — a street map with each device pinned where its signal peaked, a legend, zoom controls,
  and a card for whatever you tap (a cluster opens a list to pick from). Or the **radar**: distance
  from the centre is signal strength.
- **Channels** — access points per channel on 2.4, 5 and 6 GHz, to spot crowded channels.
- **Drives** — record a wardrive: every sighting with its time and GPS position. It keeps going with
  the screen off, saves itself when you stop, and opens later with its devices, its route on the map,
  and a time slider to replay it.
- **Send to NetSeer** — pair once, then push any drive to NetSeer with one tap (Wi-Fi and Bluetooth,
  with positions). Or share a drive as WiGLE CSV, Kismet netxml, or the raw recording.
- **Log** — what WaveTop noticed: new devices, radios switching, scan throttling, drives.
- **Settings** — three themes shared with NetSeer (**Terrain**, **Blueprint**, **Daylight**), 12/24-hour
  time, map labels, NetSeer pairing, backups, in-app updates, help, and about.

The [user guide](docs/user-guide.md) walks through every screen with screenshots.

## Install

1. Download `WaveTop-<version>.apk` from the [latest release](https://github.com/ardyn-systems/WaveTop/releases/latest)
   on your phone and open it. Android asks once to allow installs from your browser or file manager.
2. Open WaveTop and allow location, nearby devices and Bluetooth when asked (see below for why).

Needs Android 7.0 or newer. `SHA256SUMS.txt` on each release lists the APK's checksum.

### Why those permissions

| Permission | Why |
| --- | --- |
| Location (precise) | Android only gives apps Wi-Fi and Bluetooth scan results with location access, and it geotags drives. |
| Nearby devices / Bluetooth scan | Listing Wi-Fi networks (Android 13+) and Bluetooth devices. |
| Notifications | The notification that keeps a wardrive running with the screen off (optional). |
| Install apps | Only when you tap **Update** in Settings → Updates; Android asks the first time. |
| Internet | Map tiles, update checks, and sending drives to NetSeer — nothing else. |

## Updates

**Settings → Updates** works like NetSeer's and NineLives': **Check** lists the releases on GitHub,
**Update** downloads the APK, checks it against the release's `SHA256SUMS.txt`, and hands it to Android
to install (your drives stay). With **Check when WaveTop opens** on, WaveTop asks GitHub at most once a
day and puts a dot on the settings cog when a new version is out. Android can't install an older
version over a newer one, so rolling back means uninstalling first — back up your drives under
**Settings → Your data**.

## Sending drives to NetSeer

In NetSeer: **Settings → Integrations → Pair a device** shows a six-digit code. In WaveTop:
**Settings → NetSeer**, choose how to reach it, type the code, **Pair**.

- **USB cable** — phone plugged into the computer running NetSeer, USB debugging on, then on the computer:
  `adb reverse tcp:47331 tcp:47331`. NetSeer stays private to that computer.
- **Wi-Fi / network** — turn on **Allow devices on my network** in NetSeer's Integrations and type the
  address it shows.

Then open any drive and tap the NetSeer button. NetSeer opens the map by itself.

## Build from source

Android Studio (or JDK 17 + the Android SDK) and this repo:

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Debug builds install as **WaveTop (debug)** (`com.ardyn.wavetop.debug`) beside a release install.
How releases are signed and published is in [docs/releasing.md](docs/releasing.md); the app icons are
generated from the logos in [`branding/`](branding/) by `branding/make_icons.py`.

## Credits

Built with Jetpack Compose and [osmdroid](https://github.com/osmdroid/osmdroid) (Apache-2.0). Map data
© [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors. Vendor names from the IEEE OUI
registry.

© Ardyn Systems. All rights reserved.

# WaveTop

Wi-Fi and Bluetooth survey lives **inside the Astro Loop host app** (same process, same APK). It uses public Android scan APIs only — no aircrack-ng, injection, deauth, or monitor mode.

## Open / run

1. Open [`third_party/astro-loop`](third_party/astro-loop) in Android Studio (JDK 17, Android SDK 36).
2. Run the `app` configuration on a device or emulator with Play services / location.
3. Or from that directory:

```bash
./gradlew :app:installDebug
```

## Open WaveTop from Astro Loop

On the **logo/splash screen** (blue scout mark + “ASTRO LOOP” in Orbitron), press and **hold continuously for 10 seconds**. Release earlier (or wait ~3 seconds without touching) continues into the game.

Back from WaveTop returns to that splash; tap or wait to play.

## What the screens show

Both **Wi-Fi** and **Bluetooth** tabs list nearby results as HUD cards styled like Astro Loop (space background `#000011`, Exo 2 body, Orbitron titles, 2px panels). Each card stacks the same sections so more fields can be added later:

- **RSSI** — dBm, quality, bar (health-bar colors)
- **SSID and MAC address** — name/SSID + MAC (Bluetooth uses Name)
- **Encryption** — parsed from Wi-Fi `capabilities`; Bluetooth shows “Not advertised”
- **Channel and frequency** — channel, band, width/PHY when the platform provides them
- **Manufacturer** — IEEE MA-L OUI lookup; `Unknown` if no match or randomized MAC

Empty states cover permission denied, radio off, location off, and no results.

## Where it lives

| Piece | Path |
| --- | --- |
| Host app | `third_party/astro-loop/app` (Astro Loop 1.3) |
| Splash long-press | `third_party/astro-loop/app/src/main/java/com/astroloop/game/splash/` |
| WaveTop survey module | `third_party/astro-loop/wavetop/` |
| Survey activity | `com.ardyn.wavetop.survey.WaveTopSurveyActivity` |

Astro Loop is the host. The aircrack-ng tree is unused by this survey UI.

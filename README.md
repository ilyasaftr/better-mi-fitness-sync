# <img src="branding/app-icon.svg" alt="" width="40" height="40" align="absmiddle" /> Better Mi Fitness Sync

The official Mi Fitness app often does not sync all of your health data to Health Connect or Apple Health. **Better Mi Fitness Sync** is built to fix that. It pulls steps, heart rate, SpO₂, sleep, weight, workouts, and more from your Mi account so everything shows up where you already track health.

## Download latest

These buttons always point at the **newest published release**.

[![Download Android APK](https://img.shields.io/github/v/release/ilyasaftr/better-mi-fitness-sync?label=Download%20APK&logo=android&color=3DDC84)](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.apk)
[![Download iOS IPA](https://img.shields.io/github/v/release/ilyasaftr/better-mi-fitness-sync?label=Download%20IPA&logo=apple&color=000000)](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.ipa)
[![All releases](https://img.shields.io/github/v/release/ilyasaftr/better-mi-fitness-sync?label=All%20releases&logo=github)](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest)

| Platform | Direct download (latest) |
|----------|--------------------------|
| **Android** | [BetterMiFitnessSync.apk](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.apk) |
| **iOS** | [BetterMiFitnessSync.ipa](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.ipa) |

## Install on Android

1. Download **[BetterMiFitnessSync.apk](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.apk)** from the latest release.
2. Open the file on your phone.
3. If Android blocks it, allow **Install unknown apps** for your browser or Files app, then try again.
4. Open **Health Connect** and grant the permissions the app requests (or approve them when the app asks).
5. Sign in with your **Mi Account**, pick what to sync, then run a sync.

Optional (computer):

```bash
adb install -r BetterMiFitnessSync.apk
```

**Requirements:** Android with [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) available (or preinstalled on newer devices).

## Install on iOS

1. Download **[BetterMiFitnessSync.ipa](https://github.com/ilyasaftr/better-mi-fitness-sync/releases/latest/download/BetterMiFitnessSync.ipa)** from the latest release.
2. Re-sign and install on your iPhone (for example [Sideloadly](https://sideloadly.io/), or open `iosApp` in Xcode and run on a device).
3. On first launch, allow **Health** (HealthKit) access when prompted.
4. Sign in with your **Mi Account**, pick what to sync, then run a sync.

**Notes:** The release IPA is **unsigned**. You must re-sign it with a certificate that includes the **HealthKit** entitlement (`com.apple.developer.healthkit`). A **free Apple ID** is **not enough** — use a **paid Apple Developer Program** team (or equivalent profile with HealthKit). Physical iPhone required for real HealthKit use.

## What it does

- Sign in to Mi Fitness (email, password, or browser verification); auto-detects your Mi health region from cloud data
- Sync daily metrics: steps, distance, active calories, heart rate, resting heart rate, SpO₂, sleep (with stages), weight / body fat, blood pressure, temperature, VO₂ max
- Sync workouts with richer detail when Mi provides it:
  - GPS routes (outdoor activities)
  - In-workout heart rate (and recover HR when available)
  - Pace / speed, elevation, and summary notes (cadence, stride, training load, HR zones, …)
  - **Android (Health Connect):** steps cadence (run/walk), cycling pedaling cadence (rides), speed, power, elevation gained
  - **iOS (HealthKit):** running speed & stride, walking speed & step length, cycling cadence/speed/power; form metrics like power / GCT / vertical oscillation only when Mi sends them (no public running-cadence type on HealthKit)
- Writes into **Health Connect** (Android) or **Apple Health / HealthKit** (iOS)
- Optional auto-sync on a schedule
- Credentials and tokens stay **on your device** — a sync bridge, not a cloud health store

## Privacy

- Login credentials and session tokens are stored locally on the device.
- Health samples go from **Mi Fitness** to **your phone’s health store**.
- We don’t run a cloud server that stores your health history — nothing is uploaded to us for safekeeping or analysis.

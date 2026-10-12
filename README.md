# Gas Tracker

A personal Android app for logging fill-ups and seeing what fuel really costs: spend per month, price per litre, and tank-to-tank fuel economy. Jetpack Compose + Room, OLED-black and gold UI, three home-screen widgets.

## Features

- **Log**: fill-ups grouped by month with month totals, swipe to delete (with undo), and a hero card for this month's spend.
- **Fast entry**: the price field inserts the decimal for you (`1799` → `$1.799/L`). Comma or dot decimals both work. Litres are worked out from total ÷ price, and the odometer field shows the distance since your last reading and warns if a reading goes backwards.
- **Partial fills**: turn off "Filled to the top" for a top-up. Economy is measured from one full tank to the next, so a partial fill's fuel is added to the next full fill's reading (shown with `~` and a hollow chart dot). The app also flags fills that *look* partial: a reading far below your median (that fill wasn't full) or far above it (the previous fill wasn't).
- **Stats**: this month vs last month, this year, average price by volume, lifetime economy, fuel cost per 100 km, monthly spend bars, price and economy charts with 3M/6M/1Y/All ranges, and year/month history.
- **Data**: export CSV (save to a folder or share), import CSV with a preview (merge skips rows already in the log, or replace all). A snapshot of the log is kept before every import. The app also appears in the share sheet for CSV files, so another app's export can go straight in.
- **Widgets**: this month's spend in 1×1 and 2×1, plus a 2×1 with lifetime L/100km.

## Moving from v1

v2 uses a new package id (`com.tuttoposto.gastracker`) because v1 was signed with a key that no longer exists, so it can't be updated in place. Both apps can be installed at the same time:

1. Install v2.
2. In v1, tap **Export CSV** and pick **Import into Gas Tracker** in the share sheet.
3. Check the preview and tap **Import**. v1's CSV format is read as is.
4. Once v2 looks right, uninstall v1.

## Bugs fixed from v1

| v1 behaviour | Fix |
| --- | --- |
| Price per litre was stored in whole cents, so `$1.799` was saved as `$1.80`, which skewed litres and economy | Stored in mills (1/1000 $) |
| On comma-decimal locales the CSV export wrote `1,799` inside a comma-separated file | Machine formats are locale-independent |
| Typing `45,50` saved **$4550** (the comma was dropped) | Comma accepted as decimal separator |
| Date picker pre-selected the previous day in time zones east of UTC | Uses UTC consistently, as the Material picker expects |
| After a save, opening another entry could show the previous entry's data or close straight away (shared edit state) | One ViewModel per edit screen, loaded before display |
| Double-tapping Save could insert a duplicate | Save is guarded while in flight |
| Edit form didn't scroll above the keyboard | Scrolls, with IME padding |
| Partial fills could only be marked by deleting the odometer reading, and nothing caught a fill that was mislabelled | Explicit full/partial flag plus automatic suspect detection |
| "This month" went stale if the app stayed open past midnight | Recomputed when the date changes |
| No import, and the signing key was lost | CSV import with preview and safety snapshots; release key kept outside the repo |

## Building

Requirements: JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew testDebugUnitTest           # JVM tests: stats, partial fills, CSV, parsing
./gradlew connectedDebugAndroidTest   # device tests: Room, import, UI flows
./gradlew assembleRelease             # signed if the properties below exist
```

Release signing is read from `~/.gradle/gradle.properties` (never from the repo):

```properties
gasTrackerKeystore=C:/Users/<you>/.android/gas-tracker-release.jks
gasTrackerKeystorePassword=...
gasTrackerKeyAlias=gastracker
```

**Back up that keystore.** Android only installs an update over an APK signed with the same key.

The debug build uses the `.debug` package suffix, so debug installs and instrumented test runs (which uninstall the app afterwards) never touch the data in the release install.

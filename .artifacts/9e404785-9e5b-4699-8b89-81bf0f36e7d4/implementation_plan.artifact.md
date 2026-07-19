# Implementation Plan - Mega Feature Suite

This major update implements all 6 proposed features to transform the app into a professional-grade battery utility.

## Proposed Changes

### 1. Color Segments (Level-Based Colors)
- **Settings:** Add thresholds for "Low", "Medium", and "High" levels with customizable colors.
- **Logic:** `BarDrawable` and `RingDrawable` will select the color dynamically based on current percentage.

### 2. App Blacklist (Auto-Hide)
- **New Screen:** `AppBlacklistScreen` to list installed apps with toggles.
- **Persistence:** Save blocked package names in `SettingsRepository` using `stringSetPreferencesKey`.
- **Detection:** Use `UsageStatsManager` in `BatteryForegroundService` to hide the overlay when a blacklisted app is in the foreground.
- **Permission:** Guide user to enable "Usage Access" permission.

### 3. Percentage Text
- **Settings:** "Show percentage text" toggle and "Font size" slider.
- **Drawing:** Use `canvas.drawText` in Drawables to render the level (e.g., "75%") centered or at the end of the bar.

### 4. Advanced Charging Animations
- **Types:** `Scanner` (current), `Pulsing`, `Wave` (sine wave), `Bubbles` (rising particles).
- **Settings:** Selection menu in the Bar Style screen.
- **Logic:** Implement math-based paths for Wave and random particle physics for Bubbles.

### 5. Battery History Chart
- **Database:** Create `BatteryDatabase` with Room to store `BatteryEntry(timestamp, level)`.
- **Logging:** Log battery level every 30 minutes in the service.
- **UI:** A custom `BatteryHistoryChart` component in the `DashboardScreen` using Compose `Canvas` to draw a sleek line graph.

### 6. Advanced Notch/Punch-Hole Support
- **Detection:** Use `DisplayCutout` APIs to detect camera position.
- **Settings:** "Cutout Offset" sliders for manual calibration (X, Y, Padding).
- **Profiles:** Presets for common cutout styles (Left hole, Center hole, Wide notch).

---

## Technical Details

### [MODIFY] [SettingsRepository.kt](file:///C:/Users/ES/Downloads/Batterieleiste/app/src/main/java/com/example/batterieleiste/data/SettingsRepository.kt)
- Add keys for: `BLOCKED_APPS` (Set), `ANIMATION_TYPE` (Int), `SHOW_PERCENTAGE` (Bool), `SEGMENT_COLORS` (JSON/String).

### [NEW] [BatteryDatabase.kt](file:///C:/Users/ES/Downloads/Batterieleiste/app/src/main/java/com/example/batterieleiste/data/BatteryDatabase.kt)
- Room entities and DAO for history tracking.

### [MODIFY] [BatteryForegroundService.kt](file:///C:/Users/ES/Downloads/Batterieleiste/app/src/main/java/com/example/batterieleiste/BatteryForegroundService.kt)
- Add `UsageStats` monitoring loop (every 2-3s).
- Implement new animation drawing logic.
- Log battery level to Room.

### [MODIFY] [SettingsScreen.kt](file:///C:/Users/ES/Downloads/Batterieleiste/app/src/main/java/com/example/batterieleiste/ui/SettingsScreen.kt)
- Add `AppBlacklistScreen`.
- Add `BatteryHistoryChart` to `DashboardScreen`.
- Expand `BarStyleScreen` with animation selection and notch offsets.

---

## Verification Plan

### Automated
- Room DB unit tests for history logging.

### Manual
- **Blacklist:** Block "YouTube" and verify the bar disappears when YouTube is opened.
- **Animations:** Switch between all 4 styles and verify fluidity.
- **Chart:** Let the app run for a few hours and verify the chart shows the data points.
- **Text:** Toggle percentage text and verify alignment in both Bar and Ring modes.

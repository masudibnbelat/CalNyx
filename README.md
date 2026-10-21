# CalNyx - Modern Offline Calculator App

**Kotlin + Jetpack Compose + Room**

A modern, offline-first calculator app with multiple calculator types and a unique Notepad-style calculator with worksheets.

## Features

- ✅ Normal Calculator
- ✅ Scientific Calculator (placeholder - ready to implement)
- ✅ **Notepad Calculator** with multiple Worksheets (fully working + Room persistence)
- ✅ Financial Calculator (placeholder)
- ✅ Matrix Calculator (placeholder)
- ✅ Business Math Calculator (placeholder)
- ✅ Calculation History (Room)
- ✅ Dark / Light theme
- ✅ Fully Offline
- ✅ Data persists after app close (Room Database)

## How to Run

### Option 1: Android Studio (Recommended)
1. Open Android Studio
2. Open the `CalNyx` folder
3. Wait for Gradle sync
4. Run on Emulator or real device

### Option 2: VS Code + Command Line
1. Install [Android SDK](https://developer.android.com/studio) and set `ANDROID_HOME`
2. Open terminal in `CalNyx` folder
3. Run:
   ```bash
   ./gradlew installDebug
   ```
   or
   ```bash
   ./gradlew assembleDebug
   ```
   APK will be in `app/build/outputs/apk/debug/`

## Project Structure

```
app/src/main/java/com/calnyx/app/
├── data/           → Room entities, DAO, Database, Repository
├── ui/
│   ├── theme/      → Material 3 colors & typography
│   ├── screens/    → All calculator screens
│   └── components/ → Reusable UI components
├── viewmodel/      → ViewModels
├── navigation/     → Navigation graph
├── MainActivity.kt
└── CalNyxApp.kt
```

## Customize

- Normal Calculator → already working
- Notepad + Worksheets → fully working with auto-save
- Other calculators → open the respective Screen file and implement logic
- Theme colors → `ui/theme/Color.kt`

## Tech Stack
- Kotlin
- Jetpack Compose + Material 3
- Room Database
- Navigation Compose
- ViewModel + StateFlow
- exp4j (math expression evaluator)

---

Made for offline use. All history and worksheets are stored locally on the device.

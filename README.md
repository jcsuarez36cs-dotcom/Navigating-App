# Navigating-App

Android app (APK) that lets you:
- upload a PDF map from device storage,
- preview the first page on-screen,
- use local phone GPS to show live coordinates while navigating.

## What this MVP does
- Loads PDF files through Android's file picker.
- Renders the first page of the selected PDF.
- Requests GPS permission and displays latitude/longitude + accuracy.

> Note: this MVP reads and displays a georeferenced PDF file as a map document, but it does **not yet** extract geospatial metadata to place the GPS marker directly on-map.

## Build APK

### Prerequisites
- Android SDK installed
- `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) configured
- JDK 17

### Commands
From the repository root:

```bash
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Run on device
1. Enable Developer Options + USB debugging on your Android device.
2. Install debug APK:
   ```bash
   ./gradlew installDebug
   ```
3. Open **Navigating App**.
4. Tap **Upload georeferenced PDF** and pick a PDF file.
5. Tap **Enable local GPS** and grant location permission.

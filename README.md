# 📱 Slagalica

A mobile applications project

### Running from Terminal

#### Build the app
```bash
./gradlew build
```

#### Install on connected device/emulator
```bash
./gradlew installDebug
```

#### Run the app (build + install)
```bash
./gradlew installDebug && adb shell am start -n <PACKAGE_NAME>/.<MAIN_ACTIVITY>
```

Or simply use the Run command:
```bash
./gradlew run
```

### Running from Android Studio

1. **Sync Gradle** - Android Studio will prompt you with a "Sync Now" button when opening the project
2. **Run the app** - Click the Run button (▶) or press `Shift + F10`
3. **Choose target**:
   - Android Emulator (API 24+)
   - Physical device (via WiFi/USB)

## 📋 Requirements
- Minimum SDK: API 24 (Android 7.0)
- Compile SDK: API 34+
- Gradle: Version 7.0+

## 📖 Development Notes
- Ensure your emulator or device is running before clicking Run
- For physical device debugging, enable Developer Mode and USB Debugging
- Gradle will automatically download dependencies on first build

---

**Team**: tim23
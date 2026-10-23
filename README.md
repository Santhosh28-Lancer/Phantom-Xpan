# Phantom Xpan

**Phantom Xpan** is an experimental native Android camera app prototype built with Kotlin and the Android Camera2 API, focused on still photography with a 65:24 panoramic composition workflow.

## Current version
- App version: **1.3.1**
- Package / namespace: `com.santhosh.x200tcamera`
- Language: Kotlin
- Android build: Gradle Kotlin DSL
- Compile SDK: 35; minimum SDK: 23; target SDK: 35

## Features in this prototype
- Photo-only camera workflow
- 65:24 panoramic framing guide and JPEG crop workflow
- Supported photo-resolution selection
- Flash and composition-grid controls
- Selectable colour looks
- Camera settings and public photo saving
- Phantom Xpan launcher branding

## Requirements
- Android Studio with Android SDK 35 installed
- JDK 17
- Gradle wrapper included in this repository
- An Android device with Camera2 support for hardware validation

## Build
Open this folder in Android Studio and allow Gradle sync to complete. Or, from the project root:

```bash
# Windows
.\\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

The debug APK, if the build succeeds, is generated under `app/build/outputs/apk/debug/`.

## Validation note
This is a development prototype, not a verified production release. The source package was not built or validated on a physical Vivo X200T as part of this GitHub upload. Test camera permissions, front/rear camera selection, supported resolution choices, flash, saved JPEG dimensions, colour looks, and public-storage behaviour on the target device before release.

## Repository contents
This repository contains Android Studio project source, resources, Gradle build scripts, and the Gradle wrapper. Local SDK paths, IDE state, caches, and generated build output are excluded.
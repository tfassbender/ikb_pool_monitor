# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`ikb_pool_monitor` is an Android app (single `app` module) scaffolded from the Android Studio Empty Compose Activity template. Package `com.tfassbender.ikbpool`, applicationId matches. As of this writing the app contains only `MainActivity` showing a placeholder "Hello Android" Composable — there is no domain logic yet, so most work here is greenfield.

## Toolchain

- Kotlin + Jetpack Compose (Material3), built with Gradle Kotlin DSL.
- `compileSdk = 36` (with `minorApiLevel = 1`), `minSdk = 26`, `targetSdk = 36`. Requires a Gradle/AGP that supports the new `compileSdk { version = release(...) }` block — older AGP versions will not parse `app/build.gradle.kts`.
- Java 11 source/target.
- Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog). Add new libraries there rather than hardcoding versions in `build.gradle.kts`.

## Commands

Run from the repo root using the wrapper (`gradlew.bat` on Windows / `./gradlew` on Unix):

- Build debug APK: `gradlew.bat :app:assembleDebug`
- Install on a connected device/emulator: `gradlew.bat :app:installDebug`
- Unit tests (JVM): `gradlew.bat :app:testDebugUnitTest`
- Single unit test: `gradlew.bat :app:testDebugUnitTest --tests "com.tfassbender.ikbpool.SomeTest.methodName"`
- Instrumented/Compose UI tests (needs a running device/emulator): `gradlew.bat :app:connectedDebugAndroidTest`
- Lint: `gradlew.bat :app:lintDebug` (report at `app/build/reports/lint-results-debug.html`)
- Clean: `gradlew.bat clean`

`local.properties` holds the local `sdk.dir` and is not portable across machines — do not commit changes to it.

## Source layout

- `app/src/main/java/com/tfassbender/ikbpool/` — app code. `MainActivity` is the single entry point; it calls `enableEdgeToEdge()` and hosts Compose content inside `IKBPoolMonitorTheme { Scaffold { ... } }`.
- `app/src/main/java/com/tfassbender/ikbpool/ui/theme/` — Compose theme (`Color.kt`, `Theme.kt`, `Type.kt`). New screens should be wrapped in `IKBPoolMonitorTheme` so previews and the real app render consistently.
- `app/src/test/` — JVM unit tests (JUnit4).
- `app/src/androidTest/` — instrumented + Compose UI tests (`androidx.compose.ui.test.junit4`, Espresso).

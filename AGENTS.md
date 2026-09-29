# Agent Instructions — MedQB

## Project

Kotlin Multiplatform (Android + Desktop) medical quiz app using Compose Multiplatform.

- **Root project name**: `MedQB`
- **Modules**:
  - `:androidApp` (Android application launcher shell)
  - `:desktopApp` (Desktop JVM application launcher and packaging)
  - `:shared` (shared KMP library module — where all UI, business logic, Room DB, and tests live)
- **Entrypoints**:
  - Android: `androidApp/src/main/java/com/medqb/app/MainActivity.kt`
  - Desktop: `desktopApp/src/main/kotlin/com/medqb/app/desktop/Main.kt`
- **Java 21** required for Gradle daemon; **JVM target 17** for Kotlin compilation
- **Kotlin 2.4.20**, **AGP 9.4.0**, **Compose Multiplatform 1.13.0-alpha01**

## Build & Test Commands

```bash
# Desktop tests & compilation check (primary verification command, fast ~4s)
./gradlew :shared:desktopTest --stacktrace

# Generate code coverage report via Jacoco (XML + HTML)
./gradlew :shared:jacocoDesktopTestReport --stacktrace

# Quick desktop compilation check without running tests
./gradlew :shared:compileKotlinDesktop --stacktrace

# Quick desktop launcher module compilation check
./gradlew :desktopApp:compileKotlin --stacktrace

# Lint (Android)
./gradlew lint --stacktrace

# Full Android release build (CI only)
./gradlew assembleRelease --stacktrace

# Desktop release package (slow ~7m: performs full whole-program ProGuard release optimization)
./gradlew :desktopApp:packageReleaseDistributionForCurrentOS --stacktrace
```

No separate typecheck or formatter commands — compilation is the typecheck. No ktlint/detekt configured.

See [`docs/testing.md`](docs/testing.md) for the complete testing strategy, test inventory, fakes architecture, and coverage report locations.

**Important**: Android builds cannot be run locally — there is no Android SDK on this system. Android APKs are built exclusively via GitHub Actions CI. Only desktop targets can be run locally: use `desktopTest` for rapid (~4s) verification and test passes; `packageReleaseDistributionForCurrentOS` runs whole-program ProGuard optimization (~7m) and should only be used when validating final distribution packaging.

## Architecture

- **Shared module**: `shared/src/commonMain/kotlin/com/medqb/app/shared/`
  - `ui/` — Compose screens, components, dialogs, rich text subsystem, theme
  - `data/` — repositories, database, models, cache
  - `domain/` — use cases, intent dispatcher, snackbar dispatcher
  - `viewmodel/` — ViewModels (one per screen)
  - `orchestration/` — workflow, navigation persistence, media navigation coordinators
  - `navigation/` — Navigation 3 routes (sealed interface `MedQBRoutes`)
  - `di/` — Metro DI graph (`AppGraph` interface, `AppScope`, platform-specific `@DependencyGraph`)
  - `platform/` — expect/actual platform implementations (Logger, StorageProvider, FileSystemHelper)
- **Launcher modules**:
  - `androidApp/` — Android application launcher shell with runtime storage permissions
  - `desktopApp/` — Desktop application launcher with Compose Desktop windowing and native distribution packaging
- **Metro DI** (`dev.zacsweers.metro`) — compile-time dependency injection via `@DependencyGraph`
- **Navigation 3** (`androidx.navigation3`) — not traditional Navigation Compose
- **SQLite bundled** (`androidx.sqlite:sqlite-bundled`) and **Room 3** (`androidx.room3`) for local databases
- **Coil 3** for image loading, **Ksoup** for HTML parsing

## Conventions

- Version catalog at `gradle/libs.versions.toml` — all dependencies versioned there
- Material 3 dynamic colors on Android 12+; fallback `expressiveLightColorScheme()` on older/desktop
- UI color reference: `docs/ui-colors.md`
- Desktop release uses ProGuard (`desktopApp/proguard-desktop.pro`) with `com.guardsquare:proguard-gradle:7.10.0` in root `buildscript` and `version.set("7.10.0")` in `desktopApp/build.gradle.kts`; release builds enable obfuscation=false. (Version 7.10.0 is required to support Kotlin 2.4+ metadata).
- ABI splits enabled for Android release — only `arm64-v8a` by default
- KMP Android target uses `com.android.kotlin.multiplatform.library` with `withHostTest {}` enabled

## Gotchas

- **Tests exist in `commonTest` and `desktopTest` inside `:shared`** — run `./gradlew :shared:desktopTest --stacktrace` to execute KMP unit, Room SQLite, and Desktop Compose UI tests
- **Do not run Android tests** — `testDebugUnitTest` is excluded from agent workflows
- CI runs Android tests, lint, and desktop tests in parallel — all must pass
- `org.gradle.configuration-cache=true` is enabled — build scripts must be configuration-cache compatible
- `-Xexpect-actual-classes` compiler arg is required (set top-level in `kotlin.compilerOptions` in `shared/build.gradle.kts`)
- Desktop main class: `com.medqb.app.desktop.MainKt`
- Both `:androidApp` and `:desktopApp` depend on `:shared` (`implementation(project(":shared"))`)

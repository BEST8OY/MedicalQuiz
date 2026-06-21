# Agent Instructions — MedQB

## Project

Kotlin Multiplatform (Android + Desktop) medical quiz app using Compose Multiplatform.

- **Root project name**: `MedQB`
- **Modules**: `:app` (Android shell), `:composeApp` (shared KMP module — where all UI and logic lives)
- **Entrypoints**: `app/...MainActivity.kt` (Android), `composeApp/src/desktopMain/kotlin/main.kt` (Desktop)
- **Java 21** required for Gradle daemon; **JVM target 17** for Kotlin compilation
- **Kotlin 2.4.0**, **AGP 9.2.1**, **Compose Multiplatform 1.12.0-alpha01**

## Build & Test Commands

```bash
# Desktop tests
./gradlew :composeApp:desktopTest --stacktrace

# Lint (Android)
./gradlew lint --stacktrace

# Full Android release build
./gradlew assembleRelease --stacktrace

# Desktop release package
./gradlew :composeApp:packageReleaseDistributionForCurrentOS --stacktrace
```

No separate typecheck or formatter commands — compilation is the typecheck. No ktlint/detekt configured.

**Important**: Android builds cannot be run locally — there is no Android SDK on this system. Android APKs are built exclusively via GitHub Actions CI. Only desktop builds (`desktopTest`, `packageReleaseDistributionForCurrentOS`) can be run locally.

## Architecture

- **Shared module**: `composeApp/src/commonMain/kotlin/com/medqb/app/shared/`
  - `ui/` — Compose screens, components, dialogs, rich text subsystem, theme
  - `data/` — repositories, database, models, cache
  - `domain/` — use cases, intent dispatcher, snackbar dispatcher
  - `viewmodel/` — ViewModels (one per screen)
  - `orchestration/` — workflow, navigation persistence, media navigation coordinators
  - `navigation/` — Navigation 3 routes (sealed interface `MedQBRoutes`)
  - `di/` — Metro DI graph (`AppGraph` interface, `AppScope`, platform-specific `@DependencyGraph`)
  - `platform/` — expect/actual platform implementations (Logger, StorageProvider, FileSystemHelper)
- **Platform code**: `androidMain/` and `desktopMain/` — expect/actual implementations
- **Metro DI** (`dev.zacsweers.metro`) — compile-time dependency injection via `@DependencyGraph`
- **Navigation 3** (`androidx.navigation3`) — not traditional Navigation Compose
- **SQLite bundled** (`androidx.sqlite:sqlite-bundled`) for local databases
- **Coil 3** for image loading, **Ksoup** for HTML parsing

## Conventions

- Version catalog at `gradle/libs.versions.toml` — all dependencies versioned there
- Material 3 dynamic colors on Android 12+; fallback `expressiveLightColorScheme()` on older/desktop
- UI color reference: `docs/ui-colors.md`
- Desktop release uses ProGuard (`proguard-desktop.pro`); release builds enable obfuscation=false
- ABI splits enabled for Android release — only `arm64-v8a` by default

## Gotchas

- **No test files exist yet** — `commonTest` has dependencies but no test classes
- **Do not run Android tests** — `testDebugUnitTest` is excluded from agent workflows
- CI runs Android tests, lint, and desktop tests in parallel — all must pass
- `org.gradle.configuration-cache=true` is enabled — build scripts must be configuration-cache compatible
- `-Xexpect-actual-classes` compiler arg is required (set in `composeApp/build.gradle.kts`)
- Desktop main class: `com.medqb.app.shared.MainKt`
- The `:app` module depends on `:composeApp` (`implementation(project(":composeApp"))`)

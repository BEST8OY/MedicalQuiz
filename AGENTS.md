# AGENTS.md

## Project overview

Kotlin Multiplatform medical quiz app targeting **Android** and **Desktop** (JVM). Two Gradle modules:
- `:composeApp` — shared KMP module (Android + Desktop targets). All shared code lives here.
- `:app` — Android-only shell (permissions, Activity entry point). Depends on `:composeApp`.

## Build and run

### Android

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (minified, split by ABI)
./gradlew testDebugUnitTest      # unit tests
./gradlew lint                   # Android lint
```

### Desktop

```bash
./gradlew :composeApp:run                                # run desktop app
./gradlew :composeApp:packageReleaseDistributionForCurrentOS  # package release
./gradlew :composeApp:desktopTest                        # desktop tests (Linux only, needs xvfb)
```

Desktop tests on Linux CI require `xvfb-run -a` and packages `libgl1 libxrender1 libxrandr2`.

### Full CI (what GitHub runs)

CI runs Android build + lint + desktop build in parallel. See `.github/workflows/ci.yml`.
- Android unit tests: `./gradlew testDebugUnitTest`
- Desktop tests: `./gradlew :composeApp:desktopTest` (via xvfb on Linux)
- Lint: `./gradlew lint`

No KSP is used — `ORG_GRADLE_PROJECT_ksp_incremental: false` is set in CI but irrelevant.

## Architecture

### Entry points

- **Desktop**: `composeApp/src/desktopMain/kotlin/main.kt` → `com.medicalquiz.app.shared.MainKt` → calls `App()` composable
- **Android**: `app/src/main/java/com/medicalquiz/app/MainActivity.kt` → requests storage permission → calls `App()` composable
- **Shared `App()`**: `composeApp/src/commonMain/kotlin/App.kt` — the single composable root. Sets up DI, navigation, theme.

### Dependency injection

Manual DI via `AppDependencyContainer` at `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/di/AppDependencyContainer.kt`. No Hilt/Dagger/Koin. ViewModels created via `viewModelFactory { initializer { ... } }` in `App.kt`.

### Navigation

Uses **Jetpack Navigation 3** (NOT Navigation Compose). Routes defined in `NavigationRoutes.kt`. Back stack managed via `SnapshotStateList<MedicalQuizRoutes>`. Navigation persistence across restarts via `NavigationStateRepository`.

### Key packages (all under `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/`)

| Package | Purpose |
|---------|---------|
| `data/` | Database (SQLite bundled), repositories, models |
| `domain/` | Use cases, intents, snackbar dispatcher |
| `viewmodel/` | ViewModels for each screen |
| `ui/screens/` | Screen composables (quiz, filter, settings, media) |
| `ui/richtext/` | HTML-to-Compose rich text rendering |
| `navigation/` | Routes, navigator, persistence |
| `orchestration/` | App-level workflow coordination |
| `platform/` | `expect`/`actual` declarations for platform differences |
| `di/` | Composition root |

### Platform-specific code

- `expect`/`actual` pattern with `-Xexpect-actual-classes` compiler flag
- Key platform splits: `StorageProvider`, `FileSystemHelper`, `PlatformInfo`, `Logger`, `TextIntentLauncher`, `VideoPlayer`, `AudioPlayer`, `ClipboardCompat`, `Theme`
- Android uses `Media3`/ExoPlayer for playback; Desktop uses `vlcj`

### Toolchain versions

- Kotlin **2.4.0**, JVM toolchain **21**, Java compilation target **17**
- AGP **9.2.1**, Compose Plugin **1.12.0-alpha01** (JetBrains)
- Compose BOM **2026.05.01** (AndroidX)
- Android compileSdk **37**, minSdk **31**, targetSdk **37**

## Conventions

- Version catalog at `gradle/libs.versions.toml` — all deps referenced via `libs.*`
- No KSP, no annotation processing
- Configuration cache enabled (`org.gradle.configuration-cache=true`)
- Android release builds: `isMinifyEnabled = true`, `isShrinkResources = true`, split APKs by ABI (arm64-v8a only)
- Desktop release: ProGuard enabled (optimize on, obfuscate off), config at `composeApp/proguard-desktop.pro`
- Desktop has native Wayland support via JVM args
- All shared code goes in `:composeApp` — `:app` is a thin Android wrapper only

## Things to watch out for

- **No existing test files** in the codebase despite test dependencies in build files. CI references `testDebugUnitTest` and `desktopTest` but no test source files exist yet.
- **Android storage permission**: App requires `MANAGE_EXTERNAL_STORAGE` for reading quiz databases from external storage. `MainActivity` gates the entire UI behind this permission.
- **SQLite bundled**: Uses `androidx.sqlite:sqlite-bundled` (bundled native libs) for cross-platform DB.
- **Media playback**: Android = Media3/ExoPlayer, Desktop = vlcj. Both have platform-specific `VideoPlayer` and `AudioPlayer` implementations.
- **Desktop Linux CI**: Needs `xvfb` + X11 libs to run headless. Release packaging produces `.deb`, `.msi`, `.dmg` depending on OS.

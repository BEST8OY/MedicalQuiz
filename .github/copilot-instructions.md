# Copilot instructions for QuizApp

## Architecture at a glance
- Kotlin Multiplatform app with two modules:
  - `:composeApp` = shared UI/business logic (`commonMain`) + platform actuals (`androidMain`, `desktopMain`)
  - `:app` = Android host shell (permission gating + activity/app wiring)
- App entry flow:
  - Shared root is `App()` in `composeApp/src/commonMain/kotlin/App.kt`
  - Android starts in `app/src/main/java/com/medicalquiz/app/MainActivity.kt` and renders `App()` only after storage permission checks
  - Desktop starts in `composeApp/src/desktopMain/kotlin/main.kt` and directly renders `App()`

## Data + state flow (important)
- `QuizViewModel` (`.../shared/viewmodel/QuizViewModel.kt`) is the single state owner (`StateFlow<QuizUiState>`) and emits cross-screen actions via `UiEvent`.
- `App.kt` wires repositories + `DatabaseManager` into `QuizViewModel`, and persists navigation stack changes through `NavigationStateRepository`.
- `DatabaseManager` (`.../shared/data/DatabaseManager.kt`) is the only DB implementation (`DatabaseProvider`) and performs all DB I/O on `Dispatchers.IO` guarded by `Mutex`.
- Persisted JSON state files in app storage root:
  - `quiz_session.json` and `quiz_session_history.json` (`QuizSessionRepository`)
  - `navigation_state.json` (`NavigationStateRepository`)
  - `settings.json` (`SettingsRepository`)
- Navigation 3 uses typed routes (`MedicalQuizRoutes`); transient overlays (`MediaViewer`, `HtmlViewer`) are intentionally filtered out during restore.

## Platform boundaries (expect/actual)
- Keep domain/UI logic in `commonMain`; keep filesystem/logging/media player/back-handler implementation in platform actuals.
- Key expect/actual seams to extend instead of bypassing:
  - `shared/platform/{StorageProvider,FileSystemHelper,Logger}`
  - `shared/ui/screens/media/{AudioPlayer,VideoPlayer}`
  - `PlatformBackHandler` declared in `MediaViewerScreen.kt`
- Storage roots are platform-specific:
  - Android: legacy `ExternalStorage/MedicalQuiz` preferred, fallback to app external/files dir
  - Desktop: `~/.medicalquiz`
- DB/media path conventions are not identical by platform:
  - Android databases are under `<root>/databases/*.db`
  - Desktop databases are expected directly in `<root>/*.db`
  - Media files are under `<root>/media/*`

## Media + content conventions
- Media link resolution is centralized in `MediaHandler` (`.../shared/ui/media/MediaHandler.kt`); route new media-like links through it.
- `App.kt` prefilters unavailable media on `Dispatchers.IO` before pushing `MedicalQuizRoutes.MediaViewer`.
- Rich HTML rendering is custom (`.../shared/ui/richtext/parser/RichTextParser.kt`, Ksoup-backed); reuse this pipeline before introducing alternative parsers.
- Media metadata is loaded from `composeResources/files/media_descriptions.json` through `MediaDescriptionRepository`.
- Platform playback split:
  - Android uses Media3/ExoPlayer (`VideoPlayer.android.kt`, `AudioPlayer.android.kt`)
  - Desktop uses VLCJ and discovery helper `VlcDiscovery` (`VideoPlayer.desktop.kt`, `AudioPlayer.desktop.kt`)

## Build/run workflows
- Prerequisite: JDK installed (Gradle fails with “No Java compiler found” otherwise).
- Toolchain targets from build files:
  - JVM toolchain 21 in `composeApp/build.gradle.kts`
  - Java/Kotlin target 17 in Android modules
- Common commands from repo root:
  - `./gradlew :composeApp:run` (desktop)
  - `./gradlew :app:assembleDebug` and `./gradlew :app:installDebug` (Android)
  - `./gradlew clean`
- No tests are currently present in repo; verify changes manually on both Android and desktop paths when touching shared code.

## Change guidelines for this repo
- Prefer extending `QuizViewModel` + repository flows over adding new global state holders.
- When adding/changing routes, update both route definitions (`NavigationRoutes.kt`) and restore sanitization/persistence behavior.
- Keep heavy DB/filesystem/media checks on `Dispatchers.IO` (match existing `DatabaseManager` and media prefilter patterns).
- Avoid hardcoded absolute paths; always go through `StorageProvider`/`FileSystemHelper`.
- Fast debugging checks:
  - Databases not showing: inspect platform-specific `FileSystemHelper.listDatabases()` behavior.
  - Session restore oddities: inspect JSON files above and `MedicalQuizRoutes.isTransient` filtering.
  - Desktop media playback failures: verify VLC availability via `VlcDiscovery`.

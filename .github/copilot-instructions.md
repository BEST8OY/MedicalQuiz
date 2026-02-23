# Project Guidelines

## Code Style
- Kotlin Multiplatform app: keep shared logic in `commonMain`; use `expect/actual` seams instead of platform `if` branches in shared code (see `shared/platform/StorageProvider.kt`, `FileSystemHelper.kt`).
- Follow coroutine-first patterns: DB/filesystem/persistence work stays on `Dispatchers.IO` and uses synchronization where already established (see `DatabaseManager.kt`, `UserDataManager.kt`).
- Keep state in `StateFlow` + immutable UI state updates, matching `QuizViewModel` (`composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/viewmodel/QuizViewModel.kt`).

## Architecture
- Two-module split: `:composeApp` contains shared features + desktop target; `:app` is Android host shell (`settings.gradle.kts`).
- Entry flow: Android permission gate in `app/src/main/java/com/medicalquiz/app/MainActivity.kt` before rendering `App()`; desktop launches directly from `composeApp/src/desktopMain/kotlin/main.kt`.
- `App.kt` is a thinner composition boundary; keep startup + persistence orchestration delegated to `shared/orchestration/*Coordinator.kt`, while `QuizViewModel` remains the central state owner.
- Navigation uses typed `MedicalQuizRoutes`; overlays `MediaViewer`/`HtmlViewer` are transient and excluded from restore (`sanitizeRestoredBackStack` in `NavigationRoutes.kt`).

## Build and Test
- Toolchains/targets: JVM toolchain 21 in `composeApp/build.gradle.kts`; Android SDK levels are from `gradle/libs.versions.toml` (`compileSdk/targetSdk = 36`, `minSdk = 31`); Kotlin/Java bytecode target is 17.
- Core commands from repo root:
  - `./gradlew :composeApp:run`
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:installDebug`
  - `./gradlew clean`
- Current tests are narrow: shared tests in `composeApp/src/commonTest` and Android instrumentation smoke tests in `app/src/androidTest` (for example `MainActivityLifecycleSmokeTest.kt`).
- If Gradle sync fails, check plugin/version catalog first (`agp = 9.0.1` in `gradle/libs.versions.toml`) before assuming code regressions.

## Project Conventions
- Persisted app/session/nav state is file-backed under storage root (`quiz_session.json`, `quiz_session_history.json`, `navigation_state.json`, `settings.json`) via repositories in `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/data`.
- Keep app-level orchestration out of composables when possible: add/extend coordinators under `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/orchestration` (`AppStartupCoordinator`, `AppNavigationPersistenceCoordinator`) for startup and persistence-heavy flows.
- Route media-like links through `MediaHandler` and open rich content through existing parser/rendering pipeline (`ui/media/MediaHandler.kt`, `ui/richtext/parser/RichTextParser.kt`).
- Keep route changes paired with persistence updates: modify `NavigationRoutes.kt` and `NavigationStateRepository.kt` together.
- Never hardcode absolute paths; always resolve with `StorageProvider`/`FileSystemHelper`.
- Respect platform storage layout differences: Android DBs in `<root>/databases/*.db`; desktop DBs in `<root>/*.db`; media in `<root>/media/*`.

## Integration Points
- Navigation + lifecycle integration: `navigation3-ui` and `lifecycle-viewmodel-navigation3` (`composeApp/build.gradle.kts`, `gradle/libs.versions.toml`).
- Media stack split by platform: Android uses Media3 (`AudioPlayer.android.kt`, `VideoPlayer.android.kt`); desktop uses VLCJ + discovery guard (`VlcDiscovery.kt`).
- Rich text/media assets: Ksoup parser + compose resources (`composeApp/src/commonMain/composeResources/files/media_descriptions.json`).
- SQLite integration uses `androidx.sqlite:sqlite-bundled` through `DatabaseManager` (`composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/data/DatabaseManager.kt`).

## Security
- Android currently requests broad storage access (`MANAGE_EXTERNAL_STORAGE`) in `app/src/main/AndroidManifest.xml`; avoid widening scope unless absolutely required.
- Backup rules currently include all app files (`app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`); treat new persisted data as potentially backup-synced.
- Keep filename/path inputs constrained at media/html boundaries (see `LocalContentRepository.kt` + `MediaHandler.kt`) and avoid introducing unsanitized file route inputs.

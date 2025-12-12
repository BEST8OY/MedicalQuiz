# Copilot Instructions for MedicalQuiz

## Project Overview

MedicalQuiz is a **Kotlin Multiplatform (KMP)** quiz application targeting **Android** and **Desktop (JVM)** platforms using **Compose Multiplatform**. It loads medical quiz questions from SQLite databases and supports media viewing, answer logging, and performance filtering.

## Architecture

### Module Structure
- **`composeApp/`** - Main KMP module containing shared UI and business logic
  - `commonMain/` - Platform-agnostic code (Compose UI, ViewModels, data layer)
  - `androidMain/` - Android-specific implementations
  - `desktopMain/` - Desktop-specific implementations
- **`app/`** - Legacy Android-only module (minimal usage, wraps `composeApp`)

### Key Architectural Patterns

**Expect/Actual Pattern** - Platform abstractions in `platform/` directories:
```kotlin
// commonMain: expect object FileSystemHelper { fun getDatabasePath(dbName: String): String }
// androidMain/desktopMain: actual object FileSystemHelper { ... }
```
Platform-specific implementations: `FileSystemHelper`, `StorageProvider`, `PlatformBackHandler`, `getPlatformColorScheme`

**Data Flow:**
1. `DatabaseSelectionScreen` → User picks `.db` file from `StorageProvider.getAppStorageDirectory()/databases/`
2. `DatabaseManager` opens SQLite via `BundledSQLiteDriver` (coroutine-safe with `Mutex`)
3. `QuizViewModel` exposes `StateFlow<QuizState>` to Compose UI
4. UI collects state via `collectAsStateWithLifecycle()`

**State Management:**
- Single `QuizState` data class holds all quiz state (question, answers, filters, selections)
- `QuizViewModel` manages state via `MutableStateFlow` with `update {}` pattern
- Settings persisted via `SettingsRepository` to JSON file

## Storage Paths

| Platform | Root Directory |
|----------|----------------|
| Desktop | `~/.medicalquiz/` |
| Android | `/sdcard/MedicalQuiz/` (legacy) or app-specific storage |

Subdirectories: `databases/`, `media/`, `image_cache/`, `settings.json`

## Build Commands

- No command to build the project

## Code Conventions

### Compose UI Patterns
- Screens in `ui/` directory, one file per major screen (e.g., `QuizScreen.kt`, `QuizRoot.kt`)
- Use `Material3` components exclusively
- Access font size via `LocalFontSize.current` (CompositionLocal)
- Navigation uses `ModalNavigationDrawer` with `drawerState`

### ViewModel Patterns
- Use `Dispatchers.IO` for database/file operations
- Emit toasts via `SharedFlow<UiEvent>`:
  ```kotlin
  private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4)
  ```

### Database Operations
- All DB access through `DatabaseProvider` interface
- Use `withContext(Dispatchers.IO)` and `mutex.withLock {}` for thread safety
- Question data model: `Question`, `Answer`, `Subject`, `System`

### HTML Rendering
- Rich text rendering via custom `RichText` composable in `ui/richtext/`
- Parses HTML from question/explanation fields
- Supports images, tables, lists, code blocks

## Dependencies (Key Libraries)

- **Compose**: `1.10.0-rc01` (Multiplatform)
- **Kotlin**: `2.2.21`
- **SQLite**: `androidx.sqlite:sqlite-bundled:2.6.2`
- **Image Loading**: Coil 3 (`coil-compose`, `coil-svg`, `coil-network-ktor`)
- **HTML Parsing**: Ksoup (`com.mohamedrejeb.ksoup:ksoup-html`)
- **Networking**: Ktor (OkHttp on Android, CIO on Desktop)

Version catalog: [gradle/libs.versions.toml](gradle/libs.versions.toml)

## Common Patterns

### Adding Platform-Specific Code
1. Define `expect` declaration in `commonMain/kotlin/.../platform/`
2. Implement `actual` in both `androidMain` and `desktopMain`
3. Example files: `FileSystemHelper.kt`, `StorageProvider.kt`

### Adding a New Screen
1. Create composable in `composeApp/src/commonMain/kotlin/.../ui/`
2. Add navigation entry in `QuizRoot.kt` (drawer menu or dialog)
3. Add state fields to `QuizState` if needed

### Database Schema
Questions table: `id`, `question`, `explanation`, `corrAns`, `mediaName`, `subId`, `sysId`
Logs table: `qid`, `selectedAnswer`, `corrAnswer`, `time`, `testId`

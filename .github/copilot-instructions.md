# MedicalQuiz AI Coding Instructions

## Architecture Overview

This is a **Kotlin Multiplatform (KMP)** medical quiz application targeting Android and Desktop (JVM). The codebase uses a shared-first approach:

- **`:composeApp`** - The shared multiplatform module containing 95% of logic and UI (Compose Multiplatform)
- **`:app`** - Thin Android wrapper that embeds `:composeApp`

### Source Set Structure
```
composeApp/src/
├── commonMain/    # Shared code (UI, ViewModel, data layer)
├── androidMain/   # Android-specific implementations (ExoPlayer, platform helpers)
└── desktopMain/   # Desktop-specific implementations (VLC, file system)
```

**Expect/Actual Pattern**: Platform abstractions in `commonMain/platform/` use `expect` declarations with `actual` implementations in each platform source set. Key examples:
- `FileSystemHelper` - File operations (database paths differ: Android uses `databases/` subfolder, Desktop uses root)
- `StorageProvider` - Platform-specific storage directories
- `VideoPlayer`, `AudioPlayer` - Media playback (ExoPlayer on Android, VLC on Desktop)

## Key Patterns

### State Management
Single `QuizViewModel` manages all quiz state via `QuizState` data class. Uses:
- `StateFlow` for reactive UI updates
- `collectAsStateWithLifecycle()` in Compose
- `Resource<T>` sealed class for async loading states (`Loading`, `Success`, `Error`)

### Database Access
- Direct SQLite via `BundledSQLiteDriver` (no Room/SQLDelight ORM)
- `DatabaseProvider` interface abstracts queries
- `DatabaseManager` handles raw SQL with `Mutex` for thread safety
- Databases are `.db` files placed in platform-specific storage directories

### Database Schema (inferred from SQL)
The app expects these tables/columns (see `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/data/DatabaseManager.kt`):

- `Questions`: `id`, `question`, `explanation`, `corrAns`, `title`, `mediaName`, `otherMedias`, `pplTaken`, `corrTaken`, `subId`, `sysId`
	- `subId`/`sysId` can be either integer IDs or comma-separated text IDs; code checks `pragma_table_info('Questions')` for `subId` type.
- `Answers`: `id`, `answerId` (nullable), `answerText`, `correctPercentage` (nullable), `qId`
	- When `answerId` is null, code falls back to `id`.
- `Subjects`: `id`, `name`, `count`
- `Systems`: `id`, `name`, `count`
- `SubjectsSystems`: `subId`, `sysId` (used to map selected subjects → systems)
- `logs`: `qid`, `selectedAnswer`, `corrAnswer`, `time`, `answerDate`, `testId`
	- Performance filters join against a grouped `logs` summary; question performance aggregates attempts/correct/incorrect.

### Composable Structure
```
App() → QuizRoot() → QuizScreen() / SelectionMenuComposable / MediaViewerScreen
```
- `QuizRoot` handles navigation drawer, dialogs, and top-level state
- `RichText` custom component for rendering HTML-formatted question content
- `MediaHandler` callback interface for opening media/HTML files

## Conventions

- **Kotlin 2.2+** with experimental features enabled (`-Xcontext-parameters`)
- **Java 17** target for Android, **Java 21** toolchain for KMP
- Use `Dispatchers.IO` for database/file operations in ViewModel
- Prefer `rememberSaveable` over `remember` for configuration change survival
- Media files stored in `{storageRoot}/media/`, databases in platform-specific paths

## Dependencies (from `libs.versions.toml`)

| Purpose | Library |
|---------|---------|
| UI | Compose Multiplatform + Material3 |
| Images | Coil 3 (compose + SVG + ktor network) |
| Serialization | kotlinx-serialization-json |
| Database | androidx.sqlite:sqlite-bundled |
| HTML Parsing | ksoup |
| Android Video | Media3/ExoPlayer |
| Desktop Video | VLCJ |

## Adding New Features

1. **New UI component**: Add to `composeApp/src/commonMain/.../ui/`
2. **Platform-specific code**: Create `expect` in `commonMain/platform/`, add `actual` in both `androidMain` and `desktopMain`
3. **New data model**: Add to `data/models/`, update `DatabaseManager` SQL queries
4. **New ViewModel action**: Add function to `QuizViewModel`, emit `UiEvent` for side effects

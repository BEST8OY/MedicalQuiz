# MedicalQuiz AI Agent Instructions

This is a Kotlin Multiplatform (KMP) medical quiz application targeting Android and Desktop (JVM).

## Project Structure

- **`:composeApp`** - Shared multiplatform module (95% of logic, UI, data layer)
- **`:app`** - Thin Android wrapper that embeds `:composeApp`

Source sets:
```
composeApp/src/
├── commonMain/    # Shared code (UI, ViewModel, data models)
├── androidMain/   # Android-specific (ExoPlayer, platform helpers)
└── desktopMain/   # Desktop-specific (VLC, file system)
```

## Build Commands

**Note: We never build on this system. These commands are for reference only - builds happen elsewhere.**

```bash
# Build all targets
./gradlew build

# Clean build
./gradlew clean

# Android build
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease

# Desktop build
./gradlew :composeApp:package
./gradlew :composeApp:run

# Tests (single test class)
./gradlew test --tests "ClassName"
./gradlew test --tests "com.medicalquiz.app.ClassName"

# Run all tests
./gradlew test

# Lint (Android only - no ktlint/detekt configured)
./gradlew lint

# Full check (includes lint)
./gradlew check
```

## Code Style Guidelines

### Kotlin Style
- **Kotlin 2.3.0** with Java 17 (Android) / Java 21 (KMP) toolchain
- 4 spaces indentation, no tabs
- Max line length: 120 characters
- Use trailing commas in multi-line lists
- Prefer expression bodies for single-expression functions: `fun foo() = bar()`

### Imports
- Group: stdlib → kotlinx → androidx → third-party → project
- No wildcard imports (except for Compose: `import androidx.compose.ui.*`)
- Order alphabetically within groups
- Example:
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medicalquiz.app.shared.data.models.Question
```

### Naming Conventions
- **Packages**: `com.medicalquiz.app.shared.*`
- **Classes**: PascalCase (`QuizViewModel`, `QuestionPerformance`)
- **Functions**: camelCase (`loadQuestion()`, `submitAnswer()`)
- **Variables**: camelCase (`selectedAnswerId`, `isLoggingEnabled`)
- **Constants**: UPPER_SNAKE_CASE (top-level or companion object)
- **Private properties**: Leading underscore only for backing fields (`_state`, `_uiEvents`)
- **Composable functions**: PascalCase starting with noun (`QuizScreen()`, `AnswerComposable()`)

### Types & Nullability
- Prefer non-nullable types; use `?` only when null is meaningful
- Use `Result<T>` or `Resource<T>` sealed class for async operations:
```kotlin
sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
}
```
- Avoid platform types; explicitly specify types for public APIs
- Use `kotlinx.datetime` for dates, not java.util.Date

### Error Handling
- Use `try/catch` with specific exceptions, not `Exception`
- Log errors with context: `println("Error loading question $id: ${e.message}")`
- Emit user-facing errors via `UiEvent.ShowToast`
- Never crash; handle all edge cases gracefully
- Database operations use `Dispatchers.IO` + `Mutex` for thread safety

### Architecture Patterns
- **ViewModel**: Single `QuizViewModel` manages all quiz state
- **State Management**: `StateFlow` in ViewModel, `collectAsStateWithLifecycle()` in UI
- **Repository Pattern**: `DatabaseProvider`, `SettingsRepository`, `TextHighlightsRepository`
- **Expect/Actual**: Platform abstractions in `commonMain/platform/`:
  - `FileSystemHelper` - File operations
  - `StorageProvider` - Storage directories
  - `VideoPlayer`, `AudioPlayer` - Media playback
- **Navigation 3**: Type-safe routes with `MedicalQuizRoutes` sealed class
- **UiEvent**: One-time events via `Channel<UiEvent>` for side effects

### Compose UI Guidelines
- Split UI into small, focused composables in `ui/` package
- Use `rememberSaveable` for configuration change survival
- Prefer `MaterialTheme` values over hardcoded colors
- State hoisting: pass state and callbacks down, events up
- Custom components: `RichText` for HTML, `SelectableHighlightText` for highlights

### Database Access
- Direct SQLite via `BundledSQLiteDriver` (no ORM)
- Use `Mutex` for thread safety in `DatabaseManager`
- Placeholder parameters for all user input (never string concat SQL)
- Close databases in `onCleared()` with `runBlocking`

### Dependencies (from libs.versions.toml)
| Purpose | Library |
|---------|---------|
| UI | Compose Multiplatform + Material3 |
| Images | Coil 3 (compose + SVG + ktor network) |
| Serialization | kotlinx-serialization-json |
| Database | androidx.sqlite:sqlite-bundled |
| HTML Parsing | ksoup |
| Android Video | Media3/ExoPlayer |
| Desktop Video | VLCJ |
| Navigation | Navigation 3 (1.0.0-alpha03) |

## Development Skills

The following specialized skills are available for this Android KMP codebase:

**Navigation & Architecture:**
- `android-navigation3` - Navigation 3 library with type-safe routes
- `android-ui-architecture` - State management, ViewModel, architectural patterns
- `android-theme-anatomy` - Jetpack Compose theme internals

**UI Components:**
- `android-material3` - Material Design 3 implementation
- `android-app-layout` - Layouts, modifiers, responsive UIs
- `android-custom-design-system` - Custom design systems
- `android-components` - Master skill for all Material 3 components
- `android-components-buttons`, `android-components-input`, `android-components-navigation`, `android-components-feedback`, `android-components-containers`, `android-components-overlays`, `android-components-selection`, `android-components-resources` - Component-specific guidance

Invoke skills when working on related features to get detailed implementation guidance.

## Working with This Codebase

### Adding New Features
1. **UI component**: Add to `composeApp/src/commonMain/kotlin/.../ui/`
2. **Platform-specific**: `expect` in `commonMain/platform/`, `actual` in `androidMain`/`desktopMain`
3. **Data model**: Add to `data/models/`, update `DatabaseManager` SQL
4. **ViewModel action**: Add function to `QuizViewModel`, emit `UiEvent` for side effects
5. **Navigation route**: Add to `MedicalQuizRoutes` sealed class for new screens

### Testing
- No existing tests; add unit tests in `src/commonTest/kotlin/`
- Use `kotlinx-coroutines-test` for coroutine testing
- Mock `DatabaseProvider` interface for ViewModel tests
- CI runs: `testDebugUnitTest` (Android), `desktopTest` (Desktop)

### Key Files Reference
- `QuizViewModel.kt` - Main state management (560+ lines)
- `DatabaseManager.kt` - SQL queries and database operations
- `QuizRoot.kt` - Top-level UI orchestration with Navigation 3
- `QuizState.kt` - State data class with copy methods
- `Resource.kt` - Async loading state sealed class
- `MedicalQuizRoutes.kt` - Navigation routes (type-safe)
- `UiEvent.kt` - Side effect events (toasts, navigation, media)

### CI/CD
- GitHub Actions workflows in `.github/workflows/`
- `ci.yml` - Main CI (build, test, lint, desktop packaging)
- `android-ci.yml` - Android release builds
- ProGuard configs: `app/proguard-rules.pro` (Android), `composeApp/proguard-desktop.pro`

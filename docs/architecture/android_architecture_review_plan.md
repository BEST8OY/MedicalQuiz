# Android Architecture Review Plan

This document outlines a detailed plan to review the QuizApp architecture based on the official [Android Architecture Guidelines](https://developer.android.com/topic/architecture) and [Recommendations](https://developer.android.com/topic/architecture/recommendations).

## 1. Layered Architecture Review
The recommended architecture favors separation of concerns, driving UI from data models, and following Unidirectional Data Flow (UDF).

### 1.1. UI Layer
- **Objective**: Ensure the UI layer only displays application data and serves as the primary point of user interaction.
- **Action Items**:
  - Verify that UI components (Composables in `composeApp`) do not interact directly with data sources (e.g., `DatabaseManager`, `StorageProvider`).
  - Ensure all data is exposed to the UI via the `QuizViewModel` using `StateFlow`.
  - Check that Jetpack Compose is used effectively for adaptive layouts across different form factors (Android, Desktop).

### 1.2. Data Layer
- **Objective**: Ensure the data layer contains the business logic and exposes application data.
- **Action Items**:
  - Review existing repositories (`QuizSessionRepository`, `NavigationStateRepository`, `SettingsRepository`, `MediaDescriptionRepository`) to ensure they abstract data sources properly.
  - Verify that `DatabaseManager` and JSON file operations are strictly confined to the data layer and execute on `Dispatchers.IO`.
  - Ensure repositories expose data using Kotlin Coroutines and Flows.

### 1.3. Domain Layer (Optional)
- **Objective**: Encapsulate complex business logic or logic reused by multiple ViewModels.
- **Action Items**:
  - Evaluate if the current `QuizViewModel` is becoming too complex.
  - Identify potential Use Cases (e.g., `GetMediaDescriptionUseCase`, `SaveQuizSessionUseCase`) that could be extracted into a domain layer to simplify the ViewModel.

## 2. UI Layer & ViewModel Best Practices
ViewModels are responsible for providing the UI state and access to the data layer.

### 2.1. Unidirectional Data Flow (UDF)
- **Objective**: State flows down, events flow up.
- **Action Items**:
  - Confirm that `QuizViewModel` exposes UI state using the observer pattern (`StateFlow<QuizUiState>`).
  - Ensure the UI sends actions/events to the ViewModel through method calls (e.g., `UiEvent`).
  - Verify that the ViewModel processes events immediately and updates the state, rather than sending events back to the UI.

### 2.2. ViewModel Lifecycle & Dependencies
- **Objective**: ViewModels should be agnostic of the Android lifecycle.
- **Action Items**:
  - Audit `QuizViewModel` to ensure it does not hold references to `Activity`, `Fragment`, `Context`, or `Resources`.
  - Ensure `AndroidViewModel` is not used; use plain `ViewModel` instead.
  - Verify that UI state is exposed via a single property (or multiple related properties) using `StateFlow` and `stateIn` with `WhileSubscribed`.

### 2.3. Lifecycle-Aware Collection
- **Objective**: Collect UI state safely.
- **Action Items**:
  - Ensure Compose UI collects state using lifecycle-aware operators (e.g., `collectAsStateWithLifecycle()` on Android).

## 3. Data Management & Single Source of Truth (SSOT)
- **Objective**: Assign a single source of truth for application data.
- **Action Items**:
  - Verify that `QuizViewModel` is the single state owner for the UI.
  - Ensure `DatabaseManager` is the SSOT for database operations and uses `Mutex` to guard I/O operations.
  - Check that data models are independent of UI elements and lifecycle components.

## 4. Dependency Management
- **Objective**: Manage dependencies cleanly to scale the app and facilitate testing.
- **Action Items**:
  - Review how dependencies (Repositories, `DatabaseManager`, `StorageProvider`) are injected into `QuizViewModel` and `App.kt`.
  - Evaluate the current manual dependency injection approach.
  - Consider adopting a DI framework (like Koin, since this is a Kotlin Multiplatform project) if the dependency graph becomes too complex.

## 5. Testing Strategy
- **Objective**: Ensure the architecture is testable in isolation.
- **Action Items**:
  - **ViewModels**: Plan unit tests for `QuizViewModel`, including testing `StateFlow` emissions and event handling.
  - **Data Layer**: Plan unit tests for repositories and data sources (`DatabaseManager`).
  - **Test Doubles**: Use fakes instead of mocks for testing repositories and platform-specific actuals (e.g., `FakeStorageProvider`).

## 6. Naming Conventions
- **Objective**: Maintain a consistent and readable codebase.
- **Action Items**:
  - **Methods**: Ensure methods are verb phrases (e.g., `loadQuiz()`, `saveSession()`).
  - **Properties**: Ensure properties are noun phrases (e.g., `quizUiState`).
  - **Streams**: Ensure streams of data are named appropriately (e.g., `getQuizSessionStream()`).
  - **Implementations**: Ensure interface implementations have meaningful names (e.g., `OfflineQuizRepository` instead of `DefaultQuizRepository`).

## Next Steps
1. Conduct a code walkthrough of `QuizViewModel.kt` and `App.kt` against section 2.
2. Audit the `shared/data` package against section 1.2.
3. Document any architectural violations and create refactoring tasks.
4. Introduce a basic unit testing setup for the shared module.

---

## Execution Report (2026-02-20)

This section captures the completed walkthrough and audit results.

### 1) Layered Architecture Review

#### 1.1 UI Layer
- ✅ UI primarily consumes `StateFlow<QuizUiState>` from `QuizViewModel`.
- ✅ Lifecycle-aware collection is used (`collectAsStateWithLifecycle`) in root and screen composables.
- ⚠️ **Violation:** Some UI composables directly access filesystem/platform data sources, bypassing ViewModel/data-layer boundaries:
  - `DatabaseSelectionScreen` calls `FileSystemHelper.listDatabases()` directly.
  - `HtmlViewerScreen` builds storage paths and calls `FileSystemHelper.readText/exists()` directly.
  - `MediaViewerScreen` builds storage paths and calls `FileSystemHelper.exists()` directly.

#### 1.2 Data Layer
- ✅ Repositories (`QuizSessionRepository`, `NavigationStateRepository`, `SettingsRepository`, `TextHighlightsRepository`) encapsulate persistence concerns.
- ✅ `DatabaseManager` keeps DB I/O in `Dispatchers.IO` and serializes access with `Mutex`.
- ⚠️ **Gap:** Repository APIs are largely synchronous for session/navigation persistence; this limits composability and testability for async flows.

#### 1.3 Domain Layer (Optional)
- ⚠️ **Gap:** `QuizViewModel` is large and multi-responsibility (state restore, filtering, loading, logging, navigation events, highlights coordination).
- Recommendation: introduce use cases for session restore, filter application, and question loading orchestration.

### 2) UI Layer & ViewModel Best Practices

#### 2.1 UDF
- ✅ State-down/events-up pattern is mostly respected.
- ✅ UI actions are passed via ViewModel method calls.
- ℹ️ `UiEvent` is used for one-off navigation/toast side effects; acceptable, but keep event ownership centralized (currently done in `App`).

#### 2.2 ViewModel Lifecycle & Dependencies
- ✅ `QuizViewModel` is a plain `ViewModel` (not `AndroidViewModel`).
- ✅ No `Activity`/`Fragment`/`Context`/`Resources` references were found in `QuizViewModel`.
- ⚠️ **Deviation from guideline:** `toolbarTitle` uses `SharingStarted.Eagerly`; plan guideline targets `stateIn` with `WhileSubscribed` for lifecycle efficiency.

#### 2.3 Lifecycle-aware Collection
- ✅ Lifecycle-aware collectors are used on Android-facing UI paths.

### 3) SSOT
- ✅ `QuizViewModel` remains the primary UI state owner.
- ✅ `DatabaseManager` remains SSOT for DB operations and mutex-guards all DB access.
- ✅ Durable state is persisted in repositories (`quiz_session.json`, `quiz_session_history.json`, `navigation_state.json`, `settings.json`).

### 4) Dependency Management
- ✅ Manual DI in `App.kt` works and is explicit.
- ⚠️ **Scalability concern:** Composition root currently wires many concrete dependencies and runtime restore rules; complexity is increasing.
- Recommendation: evaluate KMP-friendly DI (e.g., Koin) if graph grows further.

### 5) Testing Strategy
- ✅ Implemented: basic shared-module unit test setup (see below).
- ✅ Added: first `commonTest` unit tests to validate shared state logic and establish test entry point.

### 6) Naming Conventions
- ✅ Method/property naming is generally consistent and readable.
- ℹ️ Streams are short-named (`state`, `uiEvents`) and consistent within current style.

## Prioritized Refactoring Backlog

### P0 (Architecture boundary correctness)
1. ✅ Move database listing out of `DatabaseSelectionScreen` into ViewModel/repository boundary.
2. ✅ Move HTML/media file resolution and filesystem checks out of composables into ViewModel + repository/service abstractions.
3. ✅ Keep UI composables render-only for content state and callbacks.

### P1 (ViewModel simplification)
1. ✅ Extracted `RestoreSessionUseCase` (DB init → session restore → question load fallback sequencing).
2. ✅ Extracted `ApplyFiltersUseCase` (subject/system pruning + preview count + ID fetch orchestration).
3. ✅ Extracted `LoadQuestionUseCase` (question + answers + performance + highlights synchronization).
4. ✅ Extracted `QuizSessionBoundaryUseCase` (session restore/save/clear boundary between ViewModel and repository).
5. ✅ Extracted `UiEventDispatcher` (toast/navigation/media event emission boundary for ViewModel side effects).

### P2 (Flow/lifecycle efficiency)
1. ✅ Migrated `toolbarTitle` sharing strategy to `SharingStarted.WhileSubscribed(...)`.
2. ✅ Converted selected repository reads/writes to suspend/flow APIs for composability and tests.
  - ✅ Added suspend IO APIs to `NavigationStateRepository` and migrated `App.kt` nav-state save path to async (`saveNavigationStateAsync`).
  - ✅ Migrated `App.kt` startup navigation restore path to async (`restoreNavigationStateAsync`) with bootstrap gating.
  - ✅ Added suspend IO APIs to `QuizSessionRepository` and migrated `App.kt` history read/write/restore/clear hot paths to async.
  - ✅ Migrated `QuizSessionBoundaryUseCase` and `QuizViewModel` session restore/save/clear flow to async end-to-end APIs.
  - ✅ Added reactive history flow (`historyEntries`) in `QuizSessionRepository` and migrated `App.kt` to lifecycle-aware collection instead of manual pull refresh.
  - ✅ Added explicit suspend settings APIs (`refreshSettingsAsync`/`saveSettingsAsync`) and routed settings initialization/writes through async IO dispatch.

### P3 (DI evolution)
1. Keep manual DI for now.
2. Trigger Koin evaluation once dependency graph or test fixture setup becomes costly.

## Unit Testing Setup Added

- Added `commonTest` source-set dependencies in `composeApp/build.gradle.kts`:
  - `kotlin("test")`
  - `kotlinx-coroutines-test`
- Added initial shared unit tests under:
  - `composeApp/src/commonTest/kotlin/com/medicalquiz/app/shared/ui/state/QuizUiStateTest.kt`

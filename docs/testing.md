# Testing Strategy — MedQB

This document describes the testing architecture, testing conventions, and verification workflows for the **MedQB** Kotlin Multiplatform application.

---

## 1. Overview & Verification Targets

MedQB targets **Android** and **Desktop (JVM)** with shared UI and business logic residing in `:shared`.

- **Primary Verification Target**: Desktop JVM target (`jvm("desktop")` inside `:shared`).
  - Unit tests, database tests, and Compose UI tests execute directly on Desktop JVM.
  - Fast execution (~4s–7s) without Android SDK or emulator overhead.
- **CI Verification Target**: GitHub Actions runs Android unit tests and APK compilation in parallel.

---

## 2. Test Commands

```bash
# Run all unit tests, database tests, and Compose UI tests (fast ~4s–7s)
./gradlew :shared:desktopTest --stacktrace

# Generate code coverage report (XML + HTML) via Jacoco
./gradlew :shared:jacocoDesktopTestReport --stacktrace

# Quick compilation verification without running tests
./gradlew :shared:compileTestKotlinDesktop --stacktrace

# Compile desktop application launcher module
./gradlew :desktopApp:compileKotlin --stacktrace
```

### Coverage Reports
After running `jacocoDesktopTestReport`, coverage outputs are generated at:
- **HTML report**: `shared/build/reports/jacoco/jacocoDesktopTestReport/html/index.html`
- **XML report**: `shared/build/reports/jacoco/jacocoDesktopTestReport/jacocoDesktopTestReport.xml`

---

## 3. Architecture & Test Patterns

### A. Dependency Injection & Test Doubles
- Production code uses compile-time **Metro DI** (`dev.zacsweers.metro`).
- Tests use **pure constructor injection** with zero mocking frameworks (no Mockito or MockK).
- Test doubles are implemented as high-fidelity, thread-safe in-memory fakes located in [`Fakes.kt`](../shared/src/commonTest/kotlin/com/medqb/app/shared/viewmodel/Fakes.kt):
  - `FakeDatabaseProvider`: In-memory SQLite quiz bank stub.
  - `FakeUserDataManager`: In-memory `UserDatabase` Room wrapper.
  - `FakeQuizSessionRepository`: In-memory session history repository.
  - `FakeSettingsRepository`: In-memory settings state flows.
  - `FakeTextHighlightsRepository`: In-memory text highlight storage.
  - `FakeAppStartupCoordinator`: In-memory database discovery coordinator.
  - `FakeSnackbarSink`: Collects emitted snackbar messages for assertion.

### B. In-Memory SQLite Database Testing (Room 3)
- Room 3 tests (`DefaultTextHighlightsRepositoryTest`, `DefaultQuizSessionRepositoryTest`, `RoomLogDaoFilterTest`, `RoomSessionHistoryDaoTest`, `RoomTextHighlightDaoTest`) use real in-memory SQLite instances powered by `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`).
- Pattern:
  ```kotlin
  val db = Room.inMemoryDatabaseBuilder<UserDatabase> { UserDatabaseConstructor.initialize() }
      .setDriver(BundledSQLiteDriver())
      .build()
  ```
- This ensures 100% fidelity with SQLite table constraints, foreign keys, cascading deletions, and SQL queries natively across platforms without Robolectric.

### C. Compose Multiplatform UI Testing (Desktop)
- Compose UI behavior tests live in `shared/src/desktopTest/kotlin/` using `androidx.compose.ui.test.v2.runComposeUiTest`.
- Tests verify:
  - Component rendering and state changes ([`EmptyStateMessageUiTest`](../shared/src/desktopTest/kotlin/com/medqb/app/shared/ui/components/EmptyStateMessageUiTest.kt)).
  - User interactions, button clicks, and dialog navigation ([`JumpToDialogUiTest`](../shared/src/desktopTest/kotlin/com/medqb/app/shared/ui/dialogs/JumpToDialogUiTest.kt)).

---

## 4. Test Suite Inventory

| Category | Suite | Description |
| :--- | :--- | :--- |
| **Domain** | `ApplyFiltersUseCaseTest` | System pruning, normalization, question count estimation, difficulty counts. |
| | `LoadQuestionUseCaseTest` | Concurrent question and highlight loading, graceful failure handling. |
| | `DifficultyIndexTest` | Empirical correct rate math and 5-tier classification. |
| **Data & Repositories** | `DefaultTextHighlightsRepositoryTest` | Room 3 in-memory highlight overlap merging, color updates, deletions. |
| | `DefaultQuizSessionRepositoryTest` | Room 3 in-memory session persistence, sorting, filtering, orphan cleanup. |
| | `RoomLogDaoFilterTest` | SQLite LogDao queries, filtering by subjects, systems, performance. |
| | `RoomSessionHistoryDaoTest` | SQLite SessionHistoryDao upserts, cascade deletions. |
| | `RoomTextHighlightDaoTest` | SQLite TextHighlightDao CRUD operations. |
| **ViewModels & Workflow** | `DatabaseSelectionViewModelTest` | Asynchronous DB listing, loading state, pull-to-refresh, error states. |
| | `AppWorkflowCoordinatorTest` | State machine transitions, DB selection, filter resets. |
| | `QuizViewModelTest` | Quiz state machine, submission, grading, highlight sync, history appending. |
| | `FilterHubViewModelTest` | Inclusion/exclusion filtering, live count previews. |
| | `SettingsViewModelTest` | Preference toggles, submission modes, font scale presets. |
| **UI & Rich Text** | `EmptyStateMessageUiTest` | Compose UI layout and typography assertions. |
| | `JumpToDialogUiTest` | Compose UI stepper interactions, slider, jumping and dismiss actions. |
| | `RichTextTableTest` | HTML table parsing, CSS borders, cell spanning. |
| | `RichTextStreamliningTest` | HTML entities, whitespace compaction, styling markers. |
| **Navigation** | `AppNavigatorTest` | Navigation 3 destination backstacks, transitions, routing. |

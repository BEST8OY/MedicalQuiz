# Lifecycle/ViewModel Inventory

## 1) ViewModel inventory

### `QuizViewModel`
- Location: `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/viewmodel/QuizViewModel.kt`
- Base type: `androidx.lifecycle.ViewModel`
- Exposed state:
  - `state: StateFlow<QuizUiState>`
  - `toolbarTitle: StateFlow<String>` derived from `state`
  - `uiEvents: SharedFlow<UiEvent>`
- Lifecycle resources managed inside VM:
  - `DatabaseProvider` reference
  - `SettingsRepository` observation `Job`
  - session/history persistence via `QuizSessionRepository`
  - in-memory LRU scroll cache
- Cleanup path:
  - `onCleared()` cancels settings observation and closes DB with `runBlocking`.

## 2) ViewModel creation and wiring

### Creation site
- `App()` creates VM with `viewModel { QuizViewModel() }` in Compose root.

### Dependency wiring
- VM dependencies are injected post-construction using setter methods inside `LaunchedEffect(Unit)`:
  - `setSettingsRepository(settingsRepository)`
  - `setTextHighlightsRepository(textHighlightsRepository)`
  - `setCacheManager(cacheManager)`
  - `setSessionRepository(sessionRepository)`
- Database manager is created and set from another effect keyed by `selectedDatabase`.

## 3) Lifecycle + Compose touchpoints

### Lifecycle-aware collection
- `collectAsStateWithLifecycle()` used in major screen nodes:
  - Filter entry
  - Quiz root/screen
  - Media viewer
  - highlights flows in rich text

### Lifecycle-aware side effects
- `LaunchedEffect(...)` for initialization, navigation state persistence, data prefetch, and event collection.
- Android media lifecycle:
  - `LifecycleResumeEffect` for resume/pause behavior
  - `DisposableEffect` to release `ExoPlayer`
  - page activation effect pauses inactive player pages.

## 4) Persistence/state containers

### Process-durable files
- `SettingsRepository` -> `settings.json`
- `QuizSessionRepository` -> `quiz_session.json` and `quiz_session_history.json`
- `NavigationStateRepository` -> `navigation_state.json`

### In-memory/compose state
- `App.kt`: back stack in `remember { mutableStateListOf(...) }`
- route-level dialogs and UI flags in `rememberSaveable`
- VM UI model in `MutableStateFlow`
- one-shot events in `MutableSharedFlow`

## 5) Missing architecture elements from Android guidance
- No explicit `ViewModelProvider.Factory` / factory object.
- No `SavedStateHandle` in `QuizViewModel` constructor.
- No assisted creation path for runtime args through CreationExtras/Factory.

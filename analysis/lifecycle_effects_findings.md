# Lifecycle Effects Findings

## Positive findings
1. **Lifecycle-aware state collection is broadly correct**
   - `collectAsStateWithLifecycle()` is used for VM and settings/highlight flows in Compose surfaces.
2. **Media playback has explicit lifecycle hooks on Android**
   - Resume/pause behavior is tied to lifecycle via `LifecycleResumeEffect`.
   - Player release is tied to composition disposal via `DisposableEffect`.
3. **Resource teardown is present in ViewModel**
   - DB close path exists in explicit `closeDatabase()` and `onCleared()`.

## Findings requiring improvement

### F1 — Setter-based dependency injection in ViewModel
- VM is created with empty constructor and then mutated via multiple setters in a `LaunchedEffect(Unit)`.
- Risk: temporary partially-initialized VM states, harder test setup, and lifecycle timing coupling.

### F2 — No SavedStateHandle usage
- VM restoration relies on file repositories and route tokens rather than `SavedStateHandle`.
- Risk: process-death restoration semantics are custom and brittle compared to Android standard APIs.

### F3 — Duplicate event collectors on shared `uiEvents`
- `App.kt` and `QuizRoot.kt` both collect `viewModel.uiEvents` concurrently.
- Although events are partitioned by `when` branches, this pattern is fragile because shared-flow consumers can evolve and accidentally consume/ignore overlapping events.

### F4 — Potential main-thread block in `onCleared()`
- `onCleared()` uses `runBlocking` around DB close.
- Risk: synchronous blocking during lifecycle teardown can increase jank/ANR risk if close operation stalls.

### F5 — State restoration complexity spread across multiple layers
- Navigation restoration, selected DB saveable state, and session restore token handling are spread across effects.
- Risk: ordering bugs and future maintenance cost.

## Severity assessment
- F1: Medium
- F2: High
- F3: Medium
- F4: Medium
- F5: Medium

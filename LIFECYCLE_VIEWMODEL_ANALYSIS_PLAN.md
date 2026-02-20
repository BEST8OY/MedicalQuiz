# Lifecycle & ViewModel Analysis Plan (MedicalQuiz)

## Scope and inputs
This plan is based on Android architecture guidance for:
- ViewModel factories
- ViewModel APIs and scoping
- SavedState integration for ViewModel
- ViewModel API cheatsheet
- LiveData guidance
- Save UI state guidance
- Lifecycle-aware components
- Lifecycle + Compose integration

It is tailored to this repository's Kotlin Multiplatform + Compose structure (`app/` + `composeApp/`).

---

## 1) Analysis goals
1. Verify ViewModel lifecycle correctness across Android and desktop entry points.
2. Validate state ownership boundaries (UI state vs business/session state vs persisted state).
3. Ensure state survival matches user expectations for:
   - recomposition
   - configuration change
   - process death
   - explicit app restart
4. Confirm lifecycle-aware side effects in Compose and platform-specific media code.
5. Produce prioritized remediation tasks with effort and risk.

---

## 2) Codebase mapping (what to inspect first)

### Primary targets
- `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/viewmodel/QuizViewModel.kt`
- `composeApp/src/commonMain/kotlin/App.kt`
- `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/ui/screens/**`
- `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/navigation/**`
- `app/src/main/java/com/medicalquiz/app/MainActivity.kt`
- `composeApp/src/androidMain/kotlin/com/medicalquiz/app/shared/ui/screens/media/PlayerLifecycle.android.kt`

### Dependency/config targets
- `composeApp/build.gradle.kts`
- `gradle/libs.versions.toml`

### Secondary targets (state/data persistence)
- `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/data/SettingsRepository.kt`
- `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/data/QuizSessionRepository.kt`
- `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/navigation/NavigationStateRepository.kt`

---

## 3) Standards matrix (derived from docs)
Use this matrix as the review rubric.

### A. ViewModel creation & dependencies
- ViewModels should not manually construct heavyweight dependencies inside the class body.
- Creation should be centralized (factory/DI-aware pattern) and testable.
- If runtime params are needed (IDs, route args), verify factory/extras usage is explicit.

### B. ViewModel scoping
- Confirm each ViewModel is scoped to the correct owner (activity/nav destination/root).
- Detect accidental re-creation due to unstable owners or misplaced initialization.
- Verify shared state ViewModels are intentionally shared and not global by accident.

### C. Saved state handling
- Distinguish state layers:
  - ephemeral UI (`remember`)
  - recomposition + config (`rememberSaveable` / SavedStateHandle)
  - process-death durable (`SavedStateHandle` + persistent storage)
- Validate restoration order and conflict policy (saved state vs repository restore).

### D. Lifecycle-aware observation/effects
- Ensure flows are collected with lifecycle awareness in Compose.
- Confirm long-running work is bound to lifecycle (`viewModelScope`, lifecycle observers).
- Validate media/player resources are started/stopped/released with lifecycle events.

### E. LiveData/Flow interoperability
- Prefer Flow/StateFlow consistency unless LiveData is required.
- If LiveData is present, verify main-thread set/update patterns and lifecycle owner correctness.

### F. Compose integration
- UI should consume immutable UI state and send events upward.
- Side effects should use Compose effect APIs with stable keys.
- Prevent stale captures in lambdas/callbacks that outlive composition.

### G. State-saving policy
- For each important screen state, classify expected survival:
  - rotate survives?
  - process death survives?
  - app relaunch survives?
- Verify implementation matches the declared policy.

---

## 4) Execution phases

### Phase 1 — Inventory and architecture snapshot
1. Build a symbol inventory of ViewModel classes, state holders, and lifecycle observers.
2. Build call graph snippets for ViewModel creation and repository wiring.
3. Capture current state containers (`MutableStateFlow`, `remember`, repositories, caches).

Suggested commands:
```bash
rg "class .*ViewModel|ViewModel\(|viewModelScope|SavedStateHandle|Factory|viewModel\(" composeApp app
rg "remember\(|rememberSaveable\(|LaunchedEffect|DisposableEffect|collectAsState|collectAsStateWithLifecycle" composeApp
rg "Lifecycle|LifecycleOwner|LifecycleObserver|repeatOnLifecycle|LiveData|observe\(" composeApp app
```

Output artifact: `analysis/lifecycle_viewmodel_inventory.md`.

### Phase 2 — Owner/scope verification
1. For each ViewModel, identify owner and creation site.
2. Verify owner stability across recompositions/navigation.
3. Mark any scope mismatch (too broad / too narrow).

Output artifact: `analysis/viewmodel_scope_table.md` with columns:
- ViewModel
- Owner
- Creation API
- Intended lifetime
- Actual lifetime
- Risk

### Phase 3 — State survival audit
1. Pick representative user journeys:
   - choose DB -> filter -> quiz -> media -> back
   - process interruption and return
2. For each journey and state field, map storage mechanism and expected survival.
3. Simulate configuration change and process death on Android.

Output artifact: `analysis/state_survival_matrix.md`.

### Phase 4 — Lifecycle effect audit
1. Inspect Compose effects and screen entry/exit side effects.
2. Validate start/stop/release behavior for media + database resources.
3. Check cancellation behavior for long-running coroutines/jobs.

Output artifact: `analysis/lifecycle_effects_findings.md`.

### Phase 5 — Gap analysis against doc standards
1. Compare findings to standards matrix section-by-section.
2. Label each gap as:
   - correctness bug
   - resilience bug
   - maintainability issue
3. Assign severity (High/Medium/Low) and confidence.

Output artifact: `analysis/lifecycle_viewmodel_gap_report.md`.

### Phase 6 — Remediation roadmap
1. Convert each gap to a concrete code change task.
2. Group by sprint and dependency order.
3. Define verification test cases per task.

Output artifact: `analysis/lifecycle_viewmodel_remediation_plan.md`.

---

## 5) Validation strategy

### Static checks
- Kotlin compile + lint.
- Optional Detekt custom rule set for lifecycle anti-patterns.

### Behavioral checks
- Manual QA scenarios for rotate/background/foreground/process death.
- Instrumented tests (Android):
  - activity recreation
  - saved-state restoration
  - navigation back stack ViewModel retention

### Suggested commands
```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :app:assembleDebug
./gradlew lint
```

---

## 6) Deliverables definition ("complete" means all of these)
1. Inventory document with all ViewModel/lifecycle touchpoints.
2. Scope table for every ViewModel.
3. State survival matrix for key user journeys.
4. Gap report mapped to Android guidance topics.
5. Prioritized remediation backlog with acceptance criteria.
6. Final executive summary (top risks + recommended first fixes).

---

## 7) Prioritized hotspots for this repo
1. `QuizViewModel` initialization, dependency injection, and restoration paths.
2. Session persistence vs UI state restoration interaction.
3. Compose screen collection patterns (`collectAsState*`, effect usage).
4. Android activity + Compose root ownership boundaries.
5. Media player lifecycle handlers and resource cleanup timing.

---

## 8) Definition of done
Analysis is done when:
- Every ViewModel has an explicit owner, lifetime, and creation strategy documented.
- Every user-visible state element has a declared survival policy and matching implementation.
- All lifecycle-driven resources (DB, media, observers, coroutines) have verified teardown paths.
- A ranked remediation plan exists with concrete code-level next steps.

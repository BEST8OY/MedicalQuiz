# Lifecycle/ViewModel Remediation Plan

## Implementation progress
- ✅ R1 implemented: constructor injection and explicit factory-backed ViewModel creation in `App.kt`.
- 🟡 R2 partially implemented: `SavedStateHandle` added to `QuizViewModel` with key state snapshot restore/persist; follow-up needed to validate process-death scenarios on Android.
- ✅ R4 implemented: blocking `runBlocking` removed from `onCleared()`.
- ✅ R3 implemented: UI event handling now has a single collector boundary in `App.kt` for navigation/media events, removing overlapping collection in `QuizRoot`.
- 🟡 R5 partially implemented: database initialization + session restoration now run through one ordered `LaunchedEffect` path in `App.kt`; remaining work is to move this orchestration into a dedicated coordinator/use-case for easier testing.
- 🟡 R6 partially implemented: added Android instrumentation smoke coverage for activity recreation (`MainActivityLifecycleSmokeTest`); full scenario matrix (quiz index/filter/process-death/media pause-resume) remains pending device-level implementation.
- ✅ R7 implemented: added state ownership policy and lifecycle/ViewModel PR checklist under `docs/architecture/`.

## Sprint 1 (High-priority correctness/resilience)

### R1 — Introduce explicit ViewModel factory and constructor injection
- Replace setter-based initialization with constructor parameters (repositories/managers that can be stable singletons).
- Provide a `ViewModelProvider.Factory` (or equivalent Compose factory binding) for Android owner integration.
- Acceptance criteria:
  - No required dependency setter calls after VM construction.
  - Unit tests can instantiate VM with fakes directly.

### R2 — Add `SavedStateHandle` to `QuizViewModel`
- Persist minimal critical keys:
  - selected DB name (if VM-owned)
  - current question index
  - selected filter ids
  - active route/session marker
- Merge restore strategy: `SavedStateHandle` first, then repository fallback for durable history/session.
- Acceptance criteria:
  - Process death recreation recovers current index/filter without relying solely on file reads.

### R3 — Normalize UI event delivery
- Replace broad shared-flow consumption by introducing:
  - destination-specific event channels, or
  - sealed event routing with explicit consumer boundaries.
- Acceptance criteria:
  - No two unrelated composables collecting the same event stream for overlapping responsibilities.

## Sprint 2 (Lifecycle quality + maintainability)

### R4 — Remove blocking work from `onCleared()`
- Replace `runBlocking` close with non-blocking/safe shutdown strategy (idempotent close + background close where supported).
- Acceptance criteria:
  - `onCleared()` completes without synchronous heavy I/O.

### R5 — Consolidate restoration orchestration
- Create one restoration coordinator (single effect or use-case) that sequences:
  1. nav restore
  2. DB init
  3. session restore
  4. fallback navigation
- Acceptance criteria:
  - One documented flow controls restoration order.
  - Fewer cross-cutting flags/tokens in `App.kt`.

## Sprint 3 (Hardening + tests)

### R6 — Add lifecycle instrumentation tests (Android)
- Cases:
  1. rotate on Quiz screen keeps index/filter
  2. process death restore from recents
  3. history launch restores expected DB/session
  4. media screen pause/resume behavior

### R7 — Add architecture docs and guardrails
- Document state ownership policy (remember/rememberSaveable/VM/repository).
- Add lint/detekt checks or review checklist for lifecycle anti-patterns.

## Suggested implementation order
1. R1
2. R2
3. R5
4. R3
5. R4
6. R6
7. R7

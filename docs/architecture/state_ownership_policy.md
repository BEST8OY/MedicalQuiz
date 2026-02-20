# State Ownership Policy (Lifecycle/ViewModel)

This document defines where state should live in MedicalQuiz to avoid lifecycle bugs and restore-order issues.

## Ownership order

Use the **lowest-cost owner that still satisfies survival requirements**:

1. **Composable local state (`remember`)**
   - Use for ephemeral UI-only state.
   - Examples: dialog visibility, temporary text input, in-progress gestures.
   - Must not be required after config change/process death.

2. **Saveable UI state (`rememberSaveable`)**
   - Use for UI state that should survive config change and transient recreation.
   - Examples: currently selected tab, non-critical draft fields.

3. **Screen/business state (`ViewModel`)**
   - Use for screen-level app state, loading/error state, and operations.
   - Must be the source of truth for quiz state (index/filter/current question metadata).
   - Avoid post-construction setter wiring for required dependencies.

4. **Durable state (Repository / file / DB)**
   - Use for long-lived data and session/history that must survive process death/app restarts.
   - `SavedStateHandle` is for minimal restore-critical snapshot keys, not large payloads.

## Required rules

- Keep a **single collector boundary** for cross-screen navigation events.
- `onCleared()` must not run blocking I/O on the main thread.
- Add explicit `SavedStateHandle` keys for restore-critical state.
- Restore ordering must be deterministic:
  1. DB init
  2. session restore
  3. question load
  4. fallback navigation

## Anti-patterns to reject in review

- Required dependency injection via runtime setter after VM creation.
- Multiple unrelated collectors consuming the same global event stream.
- `runBlocking`/heavy synchronous I/O in `onCleared()`.
- Duplicated restore orchestrations spread across screens.


# Lifecycle / ViewModel PR Review Checklist

Use this checklist on every PR touching Compose state, ViewModels, navigation, or persistence.

## ViewModel construction

- [ ] Required dependencies are constructor-injected.
- [ ] Factory/initializer path is explicit where `SavedStateHandle` is required.
- [ ] No required dependency is set via post-construction setter.

## State ownership

- [ ] `remember` only for ephemeral UI state.
- [ ] `rememberSaveable` only for small saveable UI state.
- [ ] Business state lives in ViewModel.
- [ ] Durable state lives in repository/DB/file, not in Composable locals.

## Restore and lifecycle

- [ ] Restore ordering is deterministic and centralized.
- [ ] `SavedStateHandle` keys are minimal and restore-critical.
- [ ] Process-death path has repository fallback for durable data.
- [ ] No blocking I/O in `onCleared()`.

## Eventing and effects

- [ ] No duplicate collectors with overlapping responsibilities.
- [ ] Cross-screen events have a clear owner/collector boundary.
- [ ] `LaunchedEffect` keys are intentional and stable.

## Testing

- [ ] Unit tests cover ViewModel restore/persist behavior.
- [ ] Android instrumentation tests cover recreation/regression path for touched flows.
- [ ] Manual validation notes include rotation/process-death expectations.


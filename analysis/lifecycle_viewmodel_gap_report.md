# Lifecycle/ViewModel Gap Report

## Gap matrix vs Android architecture guidance

| Area | Current state | Gap | Severity |
|---|---|---|---|
| ViewModel factories | VM built via `viewModel { QuizViewModel() }` and configured later through setters | No explicit factory/assisted creation for dependencies and runtime args | Medium |
| ViewModel APIs/scoping | One root-scoped VM shared across flow | Scope is broad; acceptable now but risks over-sharing as app grows | Low-Medium |
| ViewModel + SavedState | No `SavedStateHandle` | Process-death recovery bypasses standard `SavedStateHandle` patterns | High |
| Lifecycle + Compose | Most flows use lifecycle-aware collection | Good baseline; still risk from complex multi-effect orchestration | Low |
| Lifecycle resources | Media player uses lifecycle/dispose effects | DB close in VM `onCleared()` uses blocking close | Medium |
| LiveData guidance | Project uses Flow/StateFlow consistently | No major LiveData interoperability issue identified | Low |
| Save UI state guidance | Mixed use of rememberSaveable + file persistence + VM | Restoration logic is powerful but fragmented and harder to reason about | Medium |

## Top 5 architecture risks
1. **Lack of `SavedStateHandle` for VM-owned critical state**.
2. **Post-construction setter injection of core dependencies**.
3. **Concurrent collectors on a single shared UI-event stream**.
4. **Blocking teardown (`runBlocking`) in `onCleared()`**.
5. **Complex restoration order coupling between nav/session/DB init effects**.

## Confidence
- Code-reading confidence: High.
- Runtime-behavior confidence: Medium (no emulator/device lifecycle simulation executed in this run).

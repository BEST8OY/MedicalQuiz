# ViewModel Scope Table

| ViewModel | Owner | Creation API | Intended lifetime | Actual lifetime (current) | Risk |
|---|---|---|---|---|---|
| `QuizViewModel` | Compose root in `App()` + NavDisplay with `rememberViewModelStoreNavEntryDecorator()` present | `viewModel { QuizViewModel() }` | Shared app-session VM across Filter/Quiz/Settings/Media screens; reset when process dies or owner is destroyed | Effectively activity/root-compose scoped because created once at `App()` root, then passed down to entries | **Medium**: broad scope is intentional, but risks accidental coupling and stale state bleed between routes if future screens assume per-destination VM |

## Notes
- Current scope is coherent with a single shared quiz flow state model.
- If route-specific state grows (e.g., Settings-only asynchronous work), split into destination-scoped VMs to reduce cross-screen coupling.

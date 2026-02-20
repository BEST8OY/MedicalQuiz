# State Survival Matrix

## Survival policy legend
- ✅ Expected to survive
- ⚠️ Partially survives / conditional
- ❌ Not expected to survive

| State | Storage mechanism | Recomposition | Config change | Process death | App relaunch | Notes |
|---|---|---:|---:|---:|---:|---|
| Current screen back stack | File persistence via `NavigationStateRepository` + in-memory `backStack` (`remember`) | ✅ | ✅ (activity recreates + file restore path) | ✅ | ✅ | Custom file-based restore instead of bundle saveable back stack. |
| Selected database | `rememberSaveable` + nav file `selectedDatabase` | ✅ | ✅ | ✅ | ✅ | Dual-source restore; behavior depends on nav file validity. |
| Quiz filter selections (`selectedSubjectIds`, `selectedSystemIds`, `performanceFilter`) | VM `StateFlow` + `QuizSessionRepository` persisted session | ✅ | ✅ (same VM instance path) | ✅ | ✅ | Restored only if DB matches current selection context. |
| Current question index | VM `StateFlow` + session file | ✅ | ✅ | ✅ | ✅ | Guarded by session restoration and DB compatibility check. |
| Dialog visibility in Filter (`showSubjectDialog`, etc.) | `rememberSaveable` | ✅ | ✅ | ⚠️ | ❌ | Process death survival depends on Android saved instance state availability. |
| Media viewer UI flags (`showUI`, zoom state, overlays) | mostly `rememberSaveable` | ✅ | ✅ | ⚠️ | ❌ | Expected ephemeral UI state; does not persist to repository. |
| Settings (logging, metadata, font scale) | `SettingsRepository` JSON file | ✅ | ✅ | ✅ | ✅ | Durable by design. |
| Scroll position cache per question | VM in-memory LRU map | ✅ | ✅ | ❌ | ❌ | Explicitly ephemeral (memory-only). |

## Key restoration order observations
1. App restores navigation from `NavigationStateRepository` first.
2. Database manager initialization follows selected DB restoration.
3. Quiz session restore happens when launching quiz/history path and when quiz is present but IDs missing.
4. If session unavailable/mismatch, stack is popped to database selection.

## Risk notes
- State is split across `rememberSaveable`, VM state, and file repositories. This works, but can create race conditions if restore ordering changes.
- No `SavedStateHandle` means process-death restoration for VM-owned state is fully custom and file-driven.

---
name: animation
description: Android/Jetpack Compose animation playbook for choosing the right API, avoiding jank, and implementing smooth UI transitions (AnimatedVisibility, AnimatedContent, updateTransition, animate*AsState, shared elements).
---

# Android Animation Skill (Compose-first)

Use this skill when tasks mention animation, transition, jank, jumpy motion, morphing UI, shared elements, visibility changes, or state-driven motion.

## Keywords / Triggers
- animation, transition, morph, smooth, jank, jumpy
- fade, slide, scale, expand, shrink
- `AnimatedVisibility`, `AnimatedContent`, `updateTransition`, `animate*AsState`, `animateContentSize`
- shared element, shared bounds, `SharedTransitionLayout`

## Core decision guide
1. **Single value changes** (alpha, dp, color, offset, size): use `animate*AsState`.
2. **Multiple properties tied to one state**: use `updateTransition`.
3. **Show/hide content**: use `AnimatedVisibility`.
4. **Swap content/layout by state**: use `AnimatedContent`.
5. **Only size changes** of a composable: use `Modifier.animateContentSize()`.
6. **Screen-to-screen matched elements**: use shared transitions (`SharedTransitionLayout`, `sharedElement`, `sharedBounds`).

## Best practices
- Prefer **one state machine** (`enum class`) for related animations to keep timing synchronized.
- For `AnimatedContent`, always render from `targetState` lambda param, not external mutable vars.
- For `animateContentSize`, put it **before** size modifiers (`width`, `height`, `size`) when possible.
- Separate concerns:
  - `None ↔ Visible`: vertical enter/exit (`AnimatedVisibility`)
  - `Visible ↔ Visible layout morph`: `AnimatedContent` or `updateTransition` child animations
- Hoist `MaterialTheme.motionScheme...` values out of non-composable lambdas (`transitionSpec`).
- Avoid stacking too many effects (e.g., slide + scale + width + content swap) unless required.

## Compose API mapping (quick)
- **Calm/default**: `fadeIn() + fadeOut()`
- **Toolbar/container enter/exit**: `slideInVertically` / `slideOutVertically`
- **Adjacent button morph**: `expandHorizontally` / `shrinkHorizontally` (+ subtle fade)
- **Value-driven width**: `transition.animateDp` or `animateDpAsState`

## Repo-specific guidance (QuizApp)
- For media/filter action bars, prefer a typed state (`None`, `SingleA`, `SingleB`, `Dual`) and drive width + content from one transition.
- Avoid hard-coding unrelated animation clocks in separate branches if they should feel unified.
- Keep heavy work (DB/file/media checks) off main thread; animation composables should not block.

## Example pattern (state-driven controls)
```kotlin
enum class ControlsLayout { None, One, Two }

val transition = updateTransition(targetState = layout, label = "controls")
val width by transition.animateDp(label = "width") { state ->
	when (state) {
		ControlsLayout.Two -> 280.dp
		ControlsLayout.One -> 140.dp
		ControlsLayout.None -> 140.dp
	}
}

AnimatedVisibility(visible = showUi && layout != ControlsLayout.None) {
	Box(Modifier.width(width)) {
		transition.AnimatedContent(
			transitionSpec = {
				if (initialState == ControlsLayout.None || targetState == ControlsLayout.None) {
					fadeIn() togetherWith fadeOut()
				} else {
					(fadeIn() + expandHorizontally()) togetherWith
						(fadeOut() + shrinkHorizontally())
				}
			},
			label = "controls-content"
		) { target ->
			// render target layout
		}
	}
}
```

## Anti-patterns to avoid
- Running composable API calls inside non-composable lambdas (`transitionSpec` with `MaterialTheme...`).
- Using `AnimatedContent` for pure visibility toggles where `AnimatedVisibility` is enough.
- Mixing `0↔N` visibility transitions with `1↔2` morph transitions without explicit state separation.
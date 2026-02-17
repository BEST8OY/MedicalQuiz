# Color System Audit

## Scope
- `composeApp/src/commonMain`
- `composeApp/src/androidMain`
- `composeApp/src/desktopMain`

## Method
- Reviewed Material 3 theme definition and role mapping.
- Searched for hardcoded colors and places where semantic content/container color propagation can break.
- Reviewed rich text and media flows where content is rendered inside parent slots (for example `ListItem`).

## What is working well
1. **Theme role coverage is strong**
   - Light and dark schemes define full Material 3 semantic roles, including surface container tiers (`surfaceContainerLow`..`Highest`).
   - This is a good foundation for consistent contrast and hierarchy.

2. **Recent RichText fallback hierarchy is directionally correct**
   - Rich text now supports inherited content color fallback (`explicit -> style -> LocalContentColor`) in `InteractiveText`.
   - This fixes parent slot color propagation for contexts like `ListItem` headline color.

3. **Surface usage is common across screens**
   - Many screens already place content in `Surface` containers, which is the right pattern for container/content relationships.

## Risks and inconsistencies found

### 1) Hardcoded literal colors in runtime UI (high priority)
Literal colors bypass semantic theming and can break dark mode / dynamic color consistency.

Hotspots:
- `desktopMain/.../VideoPlayer.desktop.kt` (remaining intentional `Color.Black` media canvas background only)

**Recommendation**
- Keep only true media-black requirements as explicit literals (video canvas/background), and document them inline.
- For overlays/icons/text, migrate to semantic tokens (`scrim`, `onSurface`, `onSurfaceVariant`, or `contentColorFor(container)`).

### 2) Mixed strategy for text color ownership (medium priority)
Some components rely on inherited `LocalContentColor`, while others directly set `MaterialTheme.colorScheme.on*` in leaf nodes.

**Risk**
- Makes it hard to reason about precedence and creates accidental overrides when components are reused in different containers.

**Recommendation**
- Adopt a rule:
  - If component is a **leaf reusable text block** used in slots: prefer inherited color + optional override param.
  - If component owns a **container surface**: set container color and let content color derive from `contentColorFor` (or `Surface` defaults), only overriding for intentional emphasis.

### 3) Rich text subsystem still has role-specific color islands (medium priority)
`TableCellStyles` and some toolbar/media caption paths still use direct `onSurface`/`onSurfaceVariant` assignments.

**Risk**
- May be correct semantically, but should be explicit design intent. Otherwise it can conflict with parent-provided content color.

**Recommendation**
- Keep direct `onSurface*` where semantic role is intentional (e.g., table headers/body distinction).
- For slot/embedded contexts, switch to inherited color or provide a parameterized palette to avoid hidden overrides.

## Proposed standard (repo-wide)

### Color precedence contract
1. Explicit component parameter color (if provided)
2. `TextStyle.color` (if specified)
3. `LocalContentColor.current`
4. `contentColorFor(containerColor)` when component owns a container

### Allowed hardcoded color exceptions
- True media rendering surfaces (e.g., video black canvas)
- Temporary debug visuals (must be tagged with `TODO` and issue)

### Checklist for new composables
- [ ] Does this composable own its container color?
- [ ] If not, does it inherit content color correctly?
- [ ] Are `on*` roles selected based on the actual container role?
- [ ] Any literal color? If yes, is it an approved exception?

## Prioritized remediation backlog
1. Introduce a small shared guideline helper doc for color precedence in Compose components.
2. Audit `richtext` subpackage remaining explicit `onSurface*` usages and classify each as intentional vs. inherited.
3. Add a lightweight static check script in CI to flag new literal color usages (`Color.White/Black/...`) outside approved paths.


## Remediation status updates
- Replaced non-essential `Color.White` usage in desktop media error/overlay text and icon controls with semantic/inherited content colors.
- Replaced media viewer non-UI background fallback from literal black to `surfaceDim` for theme consistency.
- Remaining literal color usage is now limited to the desktop video rendering canvas (`SwingPanel` background), treated as an allowed media exception.

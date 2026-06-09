# Coloring Method & System

This document describes the theming and coloring architecture of the MedicalQuiz app, a Kotlin Multiplatform (KMP) + Compose Multiplatform application targeting Android and Desktop.

---

## 1. Architecture Overview

The coloring system is built on **Material 3 (M3) Expressive** theming and follows a layered architecture:

```
Brand Seed Colors (Color.kt)
       │
       ▼
BrandedColorScheme / expressiveLightColorScheme (Theme.kt)
       │
       ├── Extended Palettes via Custom CompositionLocal (LocalHighlightColorScheme)
       │
       ▼
Platform Hook (expect/actual)
       │
       ├── Android → Material You dynamic colors (API 31+)
       └── Desktop → null (fallback to branded or expressive scheme with dark mode hook)
       │
       ▼
AppTheme composable → MaterialExpressiveTheme + LocalHighlightColorScheme.Provider
       │
       ├── MotionScheme.expressive() (default app-wide)
       └── Subtree overrides → MotionScheme.standard() (clinical answer feedback)
       │
       ▼
UI Components (via MaterialTheme.colorScheme & HighlightTheme.colors)
```

Two separate, yet harmonized coloring subsystems coexist:

- **M3 Theme Colors** — Structural UI colors (surfaces, containers, typography, buttons, cards).
- **Text Highlight Colors** — User-created text annotations driven by a decoupled semantic database model and dynamic, theme-aware theme extensions.

---

## 2. Theme Color Definitions

**File:** `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/ui/theme/Color.kt`

Defines three brand seed colors plus standard light/dark variants.

### Brand Seeds

```kotlin
internal val PrimarySeed   = Color(0xFF6750A4)  // Vibrant Purple/Indigo
internal val SecondarySeed = Color(0xFF00696F)  // Muted Teal (medical/health feel)
internal val TertiarySeed  = Color(0xFF984062)  // Bright Coral (highlights)
```

### Palette Strategy

| Role | Light Theme | Dark Theme | Intent / Clinical Rationale |
| --- | --- | --- | --- |
| **Primary** | `#6750A4` (Purple) | `#D0BCFF` (Lavender) | Main interactive elements, structural links |
| **Secondary** | `#00696F` (Teal) | `#4FD9E6` (Cyan) | Health association, interactive auxiliary paths |
| **Tertiary** | `#984062` (Coral) | `#FFB1C8` (Pink) | Special emphasis, callouts, important states |
| **Error** | `#BA1A1A` (Red) | `#FFB4AB` (Salmon) | Errors, validation failures, destructive actions |

### Surface Scale

Uses the **M3 Expressive** surface container system (6 levels):

| Token | Light | Dark |
| --- | --- | --- |
| `SurfaceContainerLowest` | `#FFFFFF` | `#0F0D13` |
| `SurfaceContainerLow` | `#F7F2FA` | `#1D1B20` |
| `SurfaceContainer` | `#F3EDF7` | `#211F26` |
| `SurfaceContainerHigh` | `#ECE6F0` | `#2B2930` |
| `SurfaceContainerHighest` | `#E6E0E9` | `#36343B` |
| `Surface` (base) | `#FEF7FF` | `#141218` |

---

## 3. Color Scheme Assembly & Custom Extensions

**File:** `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/ui/theme/Theme.kt`

The application initializes the default Material color definitions alongside a custom extended color system to handle dynamic, semantic user highlights without breaking architectural layers.

### HighlightColorScheme

```kotlin
@Immutable
data class HighlightColorScheme(
    val yellowContainer: Color, val onYellowContainer: Color,
    val greenContainer: Color,  val onGreenContainer: Color,
    val blueContainer: Color,   val onBlueContainer: Color,
    val pinkContainer: Color,   val onPinkContainer: Color,
    val orangeContainer: Color, val onOrangeContainer: Color
)

val LocalHighlightColorScheme = staticCompositionLocalOf { ... }

object HighlightTheme {
    val colors: HighlightColorScheme
        @Composable get() = LocalHighlightColorScheme.current
}
```

### Fallback Paths

| Path | Description |
| --- | --- |
| **Branded** (default) | `BrandedLightColorScheme` / `BrandedDarkColorScheme` — hand-crafted from Color.kt constants via `lightColorScheme()` / `darkColorScheme()` |
| **Expressive fallback** | `expressiveLightColorScheme()` / `darkColorScheme()` — Google's research-backed expressive defaults (enable via `useExpressiveFallback = true`) |

### The `AppTheme` Composable

1. Evaluates platform scheme overrides (e.g., Android Material You dynamic coloring).
2. Generates the runtime `HighlightColorScheme` matching light or dark mode properties to guarantee accessible contrast ratios.
3. Chains providers inside `MaterialExpressiveTheme`.

---

## 4. Platform-Specific Overrides

### Android (`Theme.android.kt`)

On API 31+ (Android 12+), uses `dynamicDarkColorScheme(context)` / `dynamicLightColorScheme(context)` to generate colors from the user's wallpaper (Material You). Falls back to `null` on older versions.

### Desktop (`Theme.desktop.kt`)

Returns `null` for the platform color scheme (deferring to the common code fallback). Provides `isDesktopDarkTheme()` utility that detects OS-level dark mode preference via `gsettings` (Linux) or `defaults` (macOS), enabling automatic theme toggling.

---

## 5. Application of Surface & Container Tokens

In compliance with M3 guidelines, **background color shifts are declared explicitly using surface container tokens.** Tonal elevation (`tonalElevation`) is reserved strictly for drawing shadows, structural z-indexing, or interactive lifting—not for driving implicit surface background color mutations.

```kotlin
// Correct: Explicit color role via M3 Expressive Tokens
Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 2.dp
) { ... }
```

### Component Implementation Reference

| Core Component Context | Applied Token Mapping |
| --- | --- |
| **Card Backgrounds / Items** | `color = surfaceContainerLow` |
| **Metadata Tags (QuizScreen)** | `color = surfaceContainerHigh` |
| **Dialog Containers** | `color = surfaceContainerHigh` |
| **Top App Bar** | `surfaceContainer` via `TopAppBarDefaults` |
| **Active Navigation / Filter Toggles** | `primaryContainer` / `onPrimaryContainer` |
| **Inactive Navigation / Filter Toggles** | `surfaceContainerLow` / `onSurfaceVariant` |
| **Correct Quiz Feedback State** | `tertiaryContainer` / `onTertiaryContainer` |
| **Incorrect Quiz Feedback State** | `errorContainer` / `onErrorContainer` |

### Animated Color Transitions

Interactive elements use `animateColorAsState` from the M3 motion scheme. Answer feedback transitions (correct/incorrect) use a **standard `tween` easing** (`FastOutSlowInEasing`, 350ms) instead of the expressive spring, keeping medical feedback clinical rather than playful.

---

## 6. RichText Palette (Dynamic Theme-Aware)

**File:** `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/ui/richtext/RichText.kt`

The `rememberRichTextPalette()` function generates structurally isolated content pairings mapped dynamically out of structural roles:

| RichText Composition Role | Assigned Color Token |
| --- | --- |
| **Important Background Callout** | `tertiaryContainer` |
| **Important Text Callout** | `onTertiaryContainer` |
| **Selected Rich Text Background** | `primaryContainer` |
| **Selected Rich Text Foreground** | `onPrimaryContainer` |
| **Hyperlink Elements** | `primary` |
| **Dictionary Definition Tooltips** | `secondary` |
| **Abstracts / Explanatory Subtext** | `onSurfaceVariant` |

Note: dictionary text uses `secondary` (teal) to visually distinguish it from hyperlinks (`primary`, purple).

---

## 7. Text Highlight Color System

**File:** `composeApp/src/commonMain/kotlin/com/medicalquiz/app/shared/data/models/TextHighlight.kt`

User annotation models are decoupled from visual representations. Persistence layers store only raw enum string signatures (`"YELLOW"`, `"GREEN"`), allowing structural theme mutations without modifying SQLite schemas.

### The Decoupled Data Model

```kotlin
@Serializable
enum class HighlightColor(val displayName: String) {
    YELLOW("Yellow"),
    GREEN("Green"),
    BLUE("Blue"),
    PINK("Pink"),
    ORANGE("Orange");
}
```

### Theme Layer UI Resolution Bridge

To extract active themes smoothly during component drawing cycles, explicit extension mappings translate model identities into runtime configuration states:

```kotlin
@Composable
fun HighlightColor.toContainerColors(): Pair<Color, Color> {
    val theme = HighlightTheme.colors
    return when (this) {
        HighlightColor.YELLOW -> theme.yellowContainer to theme.onYellowContainer
        HighlightColor.GREEN  -> theme.greenContainer to theme.onGreenContainer
        HighlightColor.BLUE   -> theme.blueContainer to theme.onBlueContainer
        HighlightColor.PINK   -> theme.pinkContainer to theme.onPinkContainer
        HighlightColor.ORANGE -> theme.orangeContainer to theme.onOrangeContainer
    }
}
```

### Text Span Rendering

In `SelectableHighlightStyling.kt`, highlight backgrounds are resolved in composable space via `resolveHighlightBackground()` and passed as a pre-computed `Map<Long, Color>` to the non-composable `applyHighlightsToText()`. This keeps the span-building utility function pure while still using theme-aware colors.

### Highlight Toolbar Chips

When `SelectionToolbar` or `HighlightEditPopup` UI layers invoke background operations, chips use `Surface` with resolved container/onContainer colors:

```kotlin
@Composable
private fun HighlightColorChip(color: HighlightColor, ...) {
    val (containerColor, onContainerColor) = color.toContainerColors()
    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = onContainerColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.size(32.dp)
    ) {
        Text(
            text = color.displayName.take(1),
            color = onContainerColor,
            ...
        )
    }
}
```

---

## 8. Motion Scheme Strategy

The platform maintains a highly intentional, layered approach toward app motion dynamics:

| Scope / Context Layer | Active Motion Scheme Profile | Rationale |
| --- | --- | --- |
| **Standard UI Context (Default)** | `MotionScheme.expressive()` (via `MaterialExpressiveTheme`) | Spring-physics engine for natural, elastic interactions. |
| **Answer Feedback Transitions** | `tween(durationMillis = 350, easing = FastOutSlowInEasing)` | Strict time-based curves for correct/incorrect reveals. Prevents elastic bounces to preserve professional clinical context. |

---

## 9. Android Resource Layer (Minimal)

**Files:** `app/src/main/res/values/themes.xml`, `values-night/themes.xml`

- Parent theme: `Theme.Material3.DayNight.NoActionBar`
- No colors defined in `colors.xml` — all theming is Compose-driven
- Serves only as the Android Activity theme bridge

---

## File Reference Map

| File | Purpose |
| --- | --- |
| `.../ui/theme/Color.kt` | Raw core base vectors and hardcoded brand colors. |
| `.../ui/theme/Theme.kt` | Custom theme extensions (`HighlightColorScheme`), standard wrapper integrations, and fallbacks. |
| `.../ui/theme/Theme.android.kt` | Android Material You dynamic colors (expect/actual). |
| `.../ui/theme/Theme.desktop.kt` | Desktop lifecycle tracking and active dark-mode sync routines. |
| `.../ui/theme/Type.kt` | Typography definitions. |
| `.../ui/theme/Shape.kt` | Shape definitions (`Shapes` + `AppShapes`). |
| `.../data/models/TextHighlight.kt` | Pure semantic data representations (SQLite persistent enums). |
| `.../ui/richtext/RichText.kt` | `rememberRichTextPalette()` — dynamic theme palette. |
| `.../ui/richtext/toolbar/SelectableHighlightToolbar.kt` | Highlight color chips + `toContainerColors()` resolution bridge. |
| `.../ui/richtext/styling/SelectableHighlightStyling.kt` | Resolves active `CompositionLocal` highlight selections to paint text spans cleanly. |
| `.../ui/richtext/SelectableHighlightText.kt` | Selectable text composable with theme-aware highlighting. |
| `.../ui/screens/quiz/AnswerListItems.kt` | Answer card with standard-easing feedback transitions. |

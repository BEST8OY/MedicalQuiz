# UI Color Reference

## Color Architecture

The app uses **Material 3 (M3)** dynamic colors throughout. No hardcoded UI colors exist except for text highlight colors and a seed color for palette generation.

- **Android 12+**: Uses `dynamicLightColorScheme()` / `dynamicDarkColorScheme()` (Monet — adapts to wallpaper)
- **Pre-API 31 Android & Desktop**: Uses `expressiveLightColorScheme()` / `darkColorScheme()` with seed `0xFF6750A4`

---

## Highlight Colors (User-Applied Text Highlights)

Defined in `TextHighlight.kt` — applied at **40% alpha** as `SpanStyle(background = ...)`.

| Name   | Hex       |
|--------|-----------|
| Yellow | `#FFEB3B` |
| Green  | `#4CAF50` |
| Blue   | `#2196F3` |
| Pink   | `#E91E63` |
| Orange | `#FF9800` |

---

## UI Elements by Screen

All tokens below are `MaterialTheme.colorScheme.*` unless noted.

---

### App Scaffold (`App.kt`)

| Element | Token |
|---------|-------|
| Snackbar container | `inverseSurface` |
| Snackbar content | `inverseOnSurface` |

---

### Database Selection Screen

| Element | Token |
|---------|-------|
| TopBar container | `surfaceContainer` |
| TopBar title | `onSurface` |
| TopBar action icons | `onSurfaceVariant` |
| DatabaseItemCard container | `surfaceContainer` |
| DatabaseItemCard icon | `onSurfaceVariant` |
| DatabaseItemCard text | `onSurface` |
| EmptyState title | `onSurface` |
| EmptyState subtitle | `onSurfaceVariant` |

---

### Filter Screen (`FilterScreen.kt`)

| Element | Token |
|---------|-------|
| Root Surface | `surface` |

**DatabaseHeaderCard**
| Property | Token |
|----------|-------|
| Container | `secondaryContainer` |
| Icon container | `secondary` / icon tint `onSecondary` |
| Labels | `onSecondaryContainer` |

**FilterPreviewCard**
| State | Container | Text |
|-------|-----------|------|
| Has preview | `primaryContainer` | `onPrimaryContainer` |
| No preview | `surfaceContainerLow` | `onSurfaceVariant` |

**FilterSelectionCard (per filter row)**
| State | Container | Icon container | Icon tint | Title | Subtitle |
|-------|-----------|----------------|-----------|-------|----------|
| Active | `primaryContainer` | `primary` | `onPrimary` | `onPrimaryContainer` | `onPrimaryContainer` |
| Inactive | `surfaceContainerLow` | `surfaceContainerHighest` | `onSurfaceVariant` | `onSurface` | `onSurfaceVariant` |

**LoggingToggleCard**
| State | Container | Icon container | Icon tint | Title | Subtitle |
|-------|-----------|----------------|-----------|-------|----------|
| Checked | `secondaryContainer` | `secondary` | `onSecondary` | `onSecondaryContainer` | `onSecondaryContainer` |
| Unchecked | `surfaceContainerLow` | `surfaceContainerHighest` | `onSurfaceVariant` | `onSurface` | `onSurfaceVariant` |

---

### Quiz Screen (`QuizScreen.kt`)

| Element | Token |
|---------|-------|
| Root Surface | `surfaceContainerLowest` |
| Loading/empty placeholder | `onSurfaceVariant` |
| Question ElevatedCard container | `surfaceContainer` (elevation 1dp) |

**HintSection**
| State | Container | Content |
|-------|-----------|---------|
| Visible | `tertiaryContainer` | `onTertiaryContainer` |
| Hidden (togglable) | `surfaceContainer` | `onSurfaceVariant` |

**Explanation ElevatedCard** — container: `surfaceContainer`, title: `onSurface`

**Metadata OutlinedCard** — container: `surfaceContainerLow`, border: `outlineVariant`
| Element | Token |
|---------|-------|
| Label text | `onSurfaceVariant` |
| Tag container | `surfaceContainerHigh` |
| Tag text | `onSurface` |

**PerformanceCard** — container: `surfaceContainer`, border: `outlineVariant`
| Element | Token |
|---------|-------|
| Stat label | `onSurfaceVariant` |
| Stat value | `onSurface` |
| Last result (correct) | `tertiary` |
| Last result (incorrect) | `error` |

---

### Answer List Items (`AnswerListItems.kt`)

**AnswerListItem container & text**
| State | Container | Text |
|-------|-----------|------|
| Correct (result shown) | `secondaryContainer` | `onSecondaryContainer` |
| Wrong selection (result shown) | `errorContainer` | `onErrorContainer` |
| Selected (before submit) | `primaryContainer` | `onPrimaryContainer` |
| Default | `surfaceContainerLow` | `onSurface` |

**Answer label badge (leading indicator)**
| State | Container | Text | Shape |
|-------|-----------|------|-------|
| Correct (result shown) | `secondary` | `onSecondary` | `Gem` |
| Wrong selection (result shown) | `error` | `onError` | `Pill` |
| Selected (before submit) | `primary` | `onPrimary` | `Sunny` |
| Default | `surfaceVariant` | `onSurfaceVariant` | `Pill` |

**Percentage badge (trailing)** — plain text, no container background.
| State | Color |
|-------|-------|
| Correct | `onSecondaryContainer` |
| Incorrect | `onErrorContainer` |

Only renders after submission, so only these two states are reachable.

---

### QuizTopBar / QuizBottomBar

| Element | Token |
|---------|-------|
| QuizTopBar container | `surfaceContainer` |
| QuizTopBar title | `onSurface` |
| QuizTopBar icons | `onSurfaceVariant` |
| Question counter pill | `secondaryContainer` / `onSecondaryContainer` |

---

### Settings Screen (`SettingsScreen.kt`)

| Element | Token |
|---------|-------|
| TopAppBar container | `surfaceContainer` |
| TopAppBar title/nav | `onSurface` |
| Section header | `primary` |
| Card containers | `surfaceContainerLow` |
| Info notice card | `surfaceContainer` |
| Card title | `onSurface` |
| Card subtitle | `onSurfaceVariant` |
| FormatSize icons | `onSurfaceVariant` |
| Scale label | `primary` |

**LivePreviewCard**
| Element | Token |
|---------|-------|
| Container | `surfaceContainerHighest` |
| Border | `outlineVariant` |
| "LIVE PREVIEW" label | `secondary` |
| Question body | `onSurface` |
| Choice label container | `secondaryContainer` |
| Choice label text | `onSecondaryContainer` |
| Choice text | `onSurfaceVariant` |

---

### JumpToDialog

| Element | Token |
|---------|-------|
| TextField focused border | `primary` |
| TextField unfocused border | `outline` |
| Slider thumb / active track | `primary` |
| Slider min/max labels | `onSurfaceVariant` |

---

### Dialog Components (`DialogComponents.kt`)

| Element | Token |
|---------|-------|
| DialogShell Surface | `surfaceContainerHigh` |
| DialogHeader title | `onSurface` |
| DialogHeader subtitle | `onSurfaceVariant` |
| DialogHeader close icon | `onSurfaceVariant` |
| Destructive button container | `error` |
| Destructive button content | `onError` |

---

### Selection Dialog (Subject/System filters)

| Element | Token |
|---------|-------|
| Loading text | `onSurfaceVariant` |
| Error icon container | `errorContainer` |
| Warning icon tint | `error` |
| Error message | `error` |
| Search field unfocused border | `outline` |
| Search icon | `onSurfaceVariant` |
| Divider | `outlineVariant` |
| "No matches" text | `onSurfaceVariant` |
| SelectionItem checked bg | `secondaryContainer` |
| Checkbox checked | `primary` |

---

### Performance Filter Dialog

| Element | Token |
|---------|-------|
| Selected item bg | `primaryContainer` |
| Selected item title/subtitle | `onPrimaryContainer` |
| Unselected item title | `onSurface` |
| Unselected item subtitle | `onSurfaceVariant` |
| RadioButton selected | `primary` |

---

### History Pane (`HistoryPane.kt`)

| Element | Token |
|---------|-------|
| Swipe Dismiss (delete) bg | `errorContainer` |
| Swipe Dismiss (rename) bg | `tertiaryContainer` |
| Delete icon | `onErrorContainer` |
| Rename icon | `onTertiaryContainer` |

**HistoryItemCard**
| State | Container | Icon | Name | DB name | Q# | Timestamp |
|-------|-----------|------|------|---------|----|-----------|
| Selected | `primaryContainer` | `onPrimaryContainer` | `onPrimaryContainer` | `onPrimaryContainer` | `onPrimaryContainer` | `onPrimaryContainer` |
| Not selected | `surfaceContainer` | `onSurfaceVariant` | `onSurface` | `onSurfaceVariant` | `onSurface` | `onSurfaceVariant` |

---

### Media Viewer Screen (`MediaViewerScreen.kt`)

| Element | Token |
|---------|-------|
| Root (UI visible) | `surface` |
| Root (UI hidden) | `surfaceDim` |
| Back FilledIconButton container | `surfaceVariant` |
| Back FilledIconButton content | `onSurfaceVariant` |
| Page counter container | `surfaceVariant` |
| Page counter text | `onSurfaceVariant` |
| Drag handle | `outlineVariant` |
| BottomSheet container | `surfaceContainer` |
| BottomSheet scrim | `scrim` (alpha 0.32) |
| BottomSheet title | `onSurface` |
| Divider | `outlineVariant` |
| Unsupported icon circle | `surfaceVariant` |
| Unsupported warning icon | `onSurfaceVariant` |
| Unsupported title | `onSurface` |
| Unsupported filename | `onSurfaceVariant` |

---

### HTML Viewer Screen (`HtmlViewerScreen.kt`)

| Element | Token |
|---------|-------|
| Root Surface | `surfaceContainerLowest` |
| TopAppBar container | `surfaceContainerLow` |
| Loading/empty card | `surfaceContainerHigh` |
| Loading text | `onSurface` |
| Content Surface | `surfaceContainer` |
| "Interactive HTML" text | `onSurfaceVariant` |

---

### Audio Player

| Platform | Element | Token |
|----------|---------|-------|
| Android | Time text | `onSurfaceVariant` |
| Desktop | Card container | `surfaceVariant` |
| Desktop | Time text | `onSurfaceVariant` |

---

### Video Player (Desktop)

| Element | Token |
|---------|-------|
| Control overlay | `scrim` (alpha 0.7) |
| Control overlay content | `inverseOnSurface` |

---

### Rich Text Subsystem

**Semantic palette (`rememberRichTextPalette()`)**

| Semantic Token | Theme Token |
|----------------|-------------|
| `importantBackground` | `tertiaryContainer` |
| `importantText` | `onTertiaryContainer` |
| `selectedBackground` | `primaryContainer` |
| `selectedText` | `onPrimaryContainer` |
| `linkText` | `primary` |
| `dictionaryText` | `primary` |
| `abstractText` | `onSurfaceVariant` |

**Table cells**

| Cell | Container | Text |
|------|-----------|------|
| Header row | `surfaceContainerHighest` | `onSurface` |
| Abstract class row | `surfaceVariant` | `onSurfaceVariant` |
| Default row (even) | `surface` | `onSurface` |
| Default row (odd, zebra) | `surfaceContainerLow` | `onSurface` |
| "selected" class | `secondaryContainer` | `onSecondaryContainer` |
| "wichtig" class | `tertiaryContainer` | `onTertiaryContainer` |

**Other rich text elements**

| Element | Token |
|---------|-------|
| Code block bg | `surfaceVariant` |
| Abstract card bg | `surfaceVariant` |
| Abstract card border | `outlineVariant` |
| Table border | `outlineVariant` |
| Table row dividers | `outlineVariant` |
| Selection toolbar bg | `surfaceContainerHighest` |

**Toolbar action button**

| State | Container | Content |
|-------|-----------|---------|
| Enabled | `surfaceContainerHighest` | `onSurface` |
| Disabled | `surfaceContainer` | `onSurfaceVariant` |

**Highlight edit popup delete button** — container: `errorContainer`, content: `onErrorContainer`

**Highlight color chip border**

| State | Token |
|-------|-------|
| Selected | `primary` (2.5dp) |
| Unselected | `outlineVariant` (1dp) |

**Selection highlight color** (text selection drag) — `secondaryContainer`

---

## All M3 Tokens Used

| Category | Tokens |
|----------|--------|
| **Surface** | `surface`, `surfaceDim`, `surfaceContainer`, `surfaceContainerLow`, `surfaceContainerLowest`, `surfaceContainerHigh`, `surfaceContainerHighest`, `surfaceVariant`, `inverseSurface` |
| **On-Surface** | `onSurface`, `onSurfaceVariant`, `inverseOnSurface` |
| **Primary** | `primary`, `primaryContainer`, `onPrimary`, `onPrimaryContainer` |
| **Secondary** | `secondary`, `secondaryContainer`, `onSecondary`, `onSecondaryContainer` |
| **Tertiary** | `tertiary`, `tertiaryContainer`, `onTertiaryContainer` |
| **Error** | `error`, `errorContainer`, `onError`, `onErrorContainer` |
| **Outline** | `outline`, `outlineVariant` |
| **Scrim** | `scrim` |
| **Scrim (inherited)** | `inverseOnSurface` |

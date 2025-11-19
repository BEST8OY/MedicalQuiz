# Compose HTML Scope

This document inventories the HTML, CSS, and behavior surface area currently handled by our WebView pipeline so we can finish the Compose-only renderer with confidence.

## 1. Where HTML currently renders

- `com.medicalquiz.app.QuizActivity` ➜ `QuizScreen` uses `WebViewComposable` (`WebViewRenderer` + `WebViewController`). HTML is produced dynamically in `QuestionHtmlBuilder` (question stem, hints, answers, explanation, quiz JS) and styled by the asset CSS.
- `com.medicalquiz.app.ui.MediaViewerScreen` renders standalone `.html` files inside a raw `WebView` inside `HtmlContent`. These files expect the same CSS bundle and JS image bridge.
- `RichText` (Compose) already renders explanation dialogs sourced from `media_descriptions.json`, but it currently supports only a subset of the HTML features listed below.

Any Compose replacement needs to cover all three entry points (quiz stem, media HTML files, description dialogs) so we can delete `WebViewRenderer`, `WebViewController`, `WebViewComposable`, and related bridge code.

## 2. CSS asset inventory

All HTML injected into the WebView applies the CSS bundle from `app/src/main/assets/styles`:

| File | Purpose | Notable selectors/classes |
| --- | --- | --- |
| `content.css` | Base typography, quiz layout helpers | `.medical-content`, `h3/h4/h5`, `p`, `.selected`, `.wichtig`, `.nowrap`, `.scientific-name`, `.dictionary`, `hr`, `pre`, `code`, `button`, `.abstract` (block + span), `#hintdiv`, `.modal-overflow-scroll`, `.quiz-block`, `.question-body`, `.answers`, `.answer-button` states (`.correct/.incorrect/.locked`), `.answer-label`, `.answer-text`, `.answer-feedback`, `.explanation`, `.hidden`, `.empty-state`, `.answer-percentage`, `.hint-visible`, `.answer-revealed` |
| `lists.css` | Ordered/unordered list spacing, nested list tweaks, table-cell lists | `ul`, `ol`, nested selectors, `td ul/ol` |
| `tables.css` | Data table layout, sticky headers, zebra striping | `table`, `.table-wrapper`, `thead`, `tbody`, `td`, `.table-default-style`, `.table-header-footer-style`, `thead.abstract`, `tbody tr:first-child`, etc. |
| `images.css` | Responsive images | `.medical-content img`, overrides when `width` attr present |
| `utilities.css` | Currently only defines `.answer-choice` + `.answer-choice.correct/.incorrect` blocks (border accent + background fills). These styles are unused today and can be replicated directly in Compose when/if answer cards return.

These selectors imply the structural elements Compose must reproduce (e.g., `.abstract` cards, `.answer-button` visual states, table wrappers, hint banners, clickable media).

## 3. HTML structure & custom classes

Compiled from `QuestionHtmlBuilder`, CSS selectors, and `HtmlUtils` sanitization:

- **Block tags**: `article`, `section`, `div`, `p`, `h1`-`h6`, `ul/ol/li`, `table/thead/tbody/tr/th/td`, `hr`, `pre`, `code`.
- **Inline tags**: `strong/b`, `em/i`, `u`, `sup`, `sub`, `span`, `a`, `img`.
- **Special containers**:
  - `.quiz-block` with `.question-body`, `.answers`, `.answer-button` children, `.answer-feedback`, `.explanation` (hidden until reveal), `.answer-percentage` badges.
  - `.abstract` divs/spans for vitals tables, patient info, etc.
  - `#hintdiv` plus body classes `hint-visible`, `hint-auto`, `answer-revealed` toggled from JS.
  - `.modal-overflow-scroll` wrappers and `.table-wrapper` for horizontal scrolling.
- **Utility classes** from data content: `.selected`, `.wichtig`, `.scientific-name`, `.nowrap`, `.dictionary`, `.abstract` (both div & span), `.hidden`, `.empty-state`.
- **Inline styles**: `HtmlUtils` strips inline `style` attributes but preserves `class` and structural tags. Some `td` padding/align attributes remain (selectors target `[style*="padding-left: 1em"]`, etc.). Compose renderer must emulate these spacing rules explicitly.

## 4. JavaScript & bridge-driven behavior

Two scripts currently run inside WebView:

1. `QuestionHtmlBuilder` inlines quiz logic:
   - Binds `.answer-button` clicks to `AndroidBridge.onAnswerSelected()` and visually locks/marks answers.
   - Manages hint visibility by toggling classes on `<body>` and `#hintdiv`.
   - Defines globals consumed from Kotlin: `applyAnswerState(correctId, selectedId)`, `markAnswerRevealed()`, `revealExplanation()`, `setAnswerFeedback(text)`.
2. `WebViewRenderer.HTML_TEMPLATE` injects `makeImagesClickable()` to forward `<img>` taps to `AndroidBridge.openMedia()` (after rewriting `src` to `file:///media/<name>` via `HtmlUtils.sanitizeForWebView`).

When we delete WebView we must recreate these behaviors in Compose:

- Compose answer list must emit callbacks to ViewModel and reflect correct/incorrect states, answer percentages, locking, and explanation visibility.
- Hint banner toggle (and auto-show on reveal) must become Compose state backed by ViewModel events.
- Inline images/links referencing local media must open via existing `MediaHandler`. `HtmlUtils.collectMediaFiles()` already enumerates file names for preloading; Compose renderer should reuse the same helper and expose click targets.

## 5. RichText renderer coverage vs. gaps

Current Compose renderer (`ui/richtext/RichTextRenderer.kt`):

- ✅ Supports paragraphs, headings (h1–h6), unordered/ordered lists, horizontal rules, `<pre>/<code>` blocks, inline formatting (bold/italic/underline/monospace/sup/sub) and anchor tags.
- ❌ Missing features required for parity:
  - Tables (`table`, `thead`, `tbody`, row/column styling, sticky header behavior, nested paragraphs/lists, `thead.abstract`).
  - `.abstract` cards (block + span versions) with their nested headings, lists, and tables.
  - Quiz-only structures: `.quiz-block`, `.answer-button`, `.answer-percentage`, `.answer-feedback`, `.explanation`, `.hint-visible`/`#hintdiv` logic.
  - Utility classes (`.wichtig`, `.selected`, `.scientific-name`, `.dictionary`, `.nowrap`) and `body.answer-revealed` state-driven styling.
  - Scroll helpers like `.modal-overflow-scroll` / `.table-wrapper`.
  - Image rendering with overlay/click handling and MediaHandler integration (currently only WebView + `makeImagesClickable`).
  - Any behavior that previously relied on CSS animations (e.g., `button:hover`, state colors) must become explicit Compose styling driven by Material color tokens.
  - Anchor rewriting for media links (`file:///media/<name>`) so Compose can open the same assets.

## 6. Additional observations

- `styles/utilities.css` only included the `.answer-choice` helpers above; no further action unless we revive that UI in Compose.
- `HtmlContent` inside `MediaViewerScreen` currently feeds the *raw* HTML file into `WebViewRenderer`. We need a file loader that pipes this HTML through the Compose renderer (likely via the same jsoup pipeline) so media HTML benefits from the same component set.
- `HtmlUtils` already normalizes `<img>` and `<a>` sources pointing to local media. Compose renderer should reuse those helpers rather than duplicating regex logic.

With this inventory we have the list of HTML elements, CSS classes, and interactive hooks our Compose implementation must support to fully remove WebView.

package com.medqb.app.shared.ui.richtext.parser

import com.medqb.app.shared.ui.richtext.matchesAnyMarker
import com.medqb.app.shared.ui.richtext.normalizedMarkers

/**
 * Configuration and constants for the RichTextParser.
 */
internal object RichTextParserConfig {

    // Parsing limits
    const val MAX_RECURSION_DEPTH = 100
    const val MAX_TABLE_ROWS = 1000
    const val MAX_TABLE_COLUMNS = 50

    // Heuristic thresholds
    const val MAX_TITLE_LENGTH = 200
    const val BOLD_FONT_WEIGHT_THRESHOLD = 600
    const val BOLD_CHECK_MAX_DEPTH = 4
    const val ALIGNMENT_DESCENT_MAX_DEPTH = 3

    /**
     * CSS class name markers for identifying header rows.
     */
    val headerRowClassMarkers = normalizedMarkers(
        "header",
        "table-header",
        "table_header",
        "tableheader",
        "table-header-row",
        "tableheaderrow",
        "thead",
        "tablehead",
        "column-header-row",
        "columnheaderrow",
        "ueberschrift",
        "titelzeile",
        "section-header",
        "subheader",
        "table-heading",
        "tableheading"
    )

    /**
     * CSS class name markers for identifying title rows.
     */
    val titleRowClassMarkers = normalizedMarkers(
        "table-title",
        "tabletitle",
        "table-caption",
        "tablecaption",
        "caption-row",
        "captionrow",
        "legend-row",
        "legendrow",
        "data-table-title",
        "datatabletitle"
    )

    /**
     * CSS class name markers for identifying header cells.
     */
    val headerCellClassMarkers = normalizedMarkers(
        "header",
        "table-header",
        "tableheader",
        "column-header",
        "columnheader",
        "row-header",
        "rowheader",
        "table-head",
        "tablehead",
        "col-header",
        "colheader",
        "ueberschrift",
        "title-cell",
        "titlecell",
        "label-cell",
        "labelcell"
    )

    /**
     * CSS class name markers for bold text.
     */
    val boldClassMarkers = normalizedMarkers(
        "bold",
        "text-bold",
        "fw-bold",
        "fwbold",
        "font-weight-bold",
        "fontweightbold",
        "strong",
        "important"
    )

    /**
     * CSS class name markers for center alignment.
     */
    val centerAlignmentClassMarkers = normalizedMarkers(
        "text-center",
        "text-centre",
        "align-center",
        "centered",
        "centre-text",
        "ta-center",
        "tacentre",
        "center-text"
    )

    /**
     * CSS class name markers for end/right alignment.
     */
    val endAlignmentClassMarkers = normalizedMarkers(
        "text-right",
        "text-end",
        "align-right",
        "align-end",
        "ta-right",
        "taright",
        "text-right-align",
        "textright"
    )

    /**
     * Attribute values that indicate a header element.
     */
    val headerAttributeValues = setOf(
        "header",
        "heading",
        "title",
        "label",
        "legend",
        "summary",
        "caption",
        "topic",
        "thead"
    )

    /**
     * Attribute names to check for header row markers.
     */
    val headerRowAttributeNames = setOf(
        "data-row-type",
        "data-type",
        "role",
        "data-role",
        "aria-role",
        "data-section",
        "data-header",
        "data-caption",
        "data-title",
        "data-heading"
    )

    /**
     * Attribute names to check for header cell markers.
     */
    val headerCellAttributeNames = setOf(
        "data-cell-type",
        "data-type",
        "role",
        "data-role",
        "data-header",
        "data-heading",
        "headers",
        "scope"
    )

    /**
     * HTML tags that are considered block-level elements.
     * These trigger a paragraph break when encountered inside inline content.
     */
    val blockLevelChildTags = setOf(
        "div",
        "section",
        "article",
        "table",
        "ul",
        "ol",
        "dl",
        "figure",
        "figcaption",
        "blockquote",
        "pre",
        "form",
        "header",
        "footer",
        "nav",
        "aside",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "hr",
        "svg",
        "canvas",
        "iframe",
        "object"
    )

    /**
     * HTML tags to ignore during parsing.
     */
    val ignoredTagNames = setOf("style", "script", "head", "meta", "link", "title")
}

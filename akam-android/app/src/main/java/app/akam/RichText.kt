package app.akam

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import uniffi.akam.RichSpan

object RichStyles {
    const val BOLD = "bold"
    const val ITALIC = "italic"
    const val UNDERLINE = "underline"
    const val STRIKE = "strike"
    const val CODE = "code"           // inline monospace
    const val CODEBLOCK = "codeblock" // full-line monospace block
    // heading levels; "heading" stays H1 so notes saved before H2/H3 still render
    const val H1 = "heading"
    const val H2 = "heading2"
    const val H3 = "heading3"
    // a link span packs its url after the prefix: "link:https://example.com"
    const val LINK_PREFIX = "link:"
}

private val HEADINGS = listOf(RichStyles.H1, RichStyles.H2, RichStyles.H3)
fun isHeading(style: String) = style in HEADINGS

/** Heading cycle: none → H1 → H2 → H3 → none. */
fun nextHeading(current: String?): String? = when (current) {
    null -> RichStyles.H1
    RichStyles.H3 -> null
    else -> HEADINGS[HEADINGS.indexOf(current) + 1]
}

// list markers stored as literal line prefixes, like bullets/checkboxes
internal val NUM_RE = Regex("""^\d+\. """)

/** The list marker a line begins with (bullet / checkbox / number), or null. */
fun listMarker(line: String): String? = when {
    line.startsWith("• ") -> "• "
    line.startsWith("☐ ") -> "☐ "
    line.startsWith("☑ ") -> "☑ "
    else -> NUM_RE.find(line)?.value
}

/** Marker the next item gets: numbers increment, a checked box resets to empty. */
private fun nextMarker(marker: String): String = when (marker) {
    "☑ " -> "☐ "
    else -> marker.removeSuffix(". ").toIntOrNull()?.let { "${it + 1}. " } ?: marker
}

/** The number a fresh "N. " line takes, from the numbered line directly above it. */
fun numberFor(text: String, lineStart: Int): Int {
    if (lineStart == 0) return 1
    val prevStart = text.lastIndexOf('\n', lineStart - 2) + 1
    val prevLine = text.substring(prevStart, lineStart - 1)
    return (NUM_RE.find(prevLine)?.value?.removeSuffix(". ")?.toIntOrNull() ?: 0) + 1
}

/**
 * A newline was just inserted at [cursor] (so text[cursor-1] == '\n'). If the
 * line it split is a list item, continue the list with the next marker — or, when
 * that item was empty, exit the list (drop the marker and the newline). Returns
 * the new (text, cursor), or null to keep the newline exactly as typed.
 *
 * ponytail: increments from the previous line, correct for append-typing; a
 * deleted middle item doesn't renumber the rest. Add a renumber pass if it bites.
 */
fun continueList(text: String, cursor: Int): Pair<String, Int>? {
    val nl = cursor - 1
    if (nl < 0 || text.getOrNull(nl) != '\n') return null
    val lineStart = text.lastIndexOf('\n', nl - 1) + 1
    val prevLine = text.substring(lineStart, nl)
    val marker = listMarker(prevLine) ?: return null
    return if (prevLine.length == marker.length) {
        text.removeRange(lineStart, cursor) to lineStart // empty item: exit the list
    } else {
        val nm = nextMarker(marker)
        (text.substring(0, cursor) + nm + text.substring(cursor)) to (cursor + nm.length)
    }
}

/** Char ranges [start, end] of each line beginning with "> ", for the quote bar. */
fun quoteLineRanges(text: String): List<IntRange> = buildList {
    var offset = 0
    for (line in text.split("\n")) {
        if (line.startsWith("> ")) add(offset..(offset + line.length))
        offset += line.length + 1
    }
}

/**
 * Re-anchor spans after the text changed. The edit is located as the single
 * contiguous region outside the common prefix/suffix (IME edits, paste, and
 * toolbar inserts are all contiguous). End offsets bias right so typing at the
 * end of a styled run extends it.
 */
fun remapSpans(spans: List<RichSpan>, old: String, new: String): List<RichSpan> {
    if (old == new || spans.isEmpty()) return spans
    var prefix = 0
    val minLen = minOf(old.length, new.length)
    while (prefix < minLen && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < minLen - prefix &&
        old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
    ) suffix++
    val oldEnd = old.length - suffix
    val newEnd = new.length - suffix
    val delta = newEnd - oldEnd
    fun start(p: Int) = if (p >= oldEnd) p + delta else if (p <= prefix) p else prefix
    fun end(p: Int) = if (p >= oldEnd) p + delta else if (p <= prefix) p else newEnd
    return spans.mapNotNull { sp ->
        val s = start(sp.start).coerceIn(0, new.length)
        val e = end(sp.end).coerceIn(0, new.length)
        if (s < e) RichSpan(s, e, sp.style) else null
    }
}

/** Toggle `style` over [s, e): remove if the range is fully styled, else apply. */
fun toggleSpan(spans: List<RichSpan>, style: String, s: Int, e: Int): List<RichSpan> {
    if (s >= e) return spans
    val same = spans.filter { it.style == style }
    val others = spans.filter { it.style != style }
    var covered = false
    var p = s
    for (sp in same.sortedBy { it.start }) {
        if (sp.start > p) break
        if (sp.end > p) p = sp.end
        if (p >= e) {
            covered = true
            break
        }
    }
    return if (covered) {
        others + same.flatMap { sp ->
            when {
                sp.end <= s || sp.start >= e -> listOf(sp)
                else -> listOfNotNull(
                    if (sp.start < s) sp.copy(end = s) else null,
                    if (sp.end > e) sp.copy(start = e) else null
                )
            }
        }
    } else {
        others + same + RichSpan(s, e, style)
    }
}

// two forms, tried in order: multi-word `#some words#` (closing hash directly
// after a word char), then single-word `#tag` / `#nested/tag`
internal val TAG_RE =
    Regex("""(?<=^|\s)#(?:[\p{L}\p{N}_-](?:[\p{L}\p{N}_ /-]*[\p{L}\p{N}_/-])?#|[\p{L}\p{N}_-][\p{L}\p{N}_/-]*)""")
private val URL_RE = Regex("""https?://\S+""")

@Composable
fun rememberRichTransformation(spans: List<RichSpan>): VisualTransformation {
    val cs = MaterialTheme.colorScheme
    return remember(spans, cs) {
        VisualTransformation { text ->
            TransformedText(richAnnotated(text.text, spans, cs), OffsetMapping.Identity)
        }
    }
}

internal fun richAnnotated(text: String, spans: List<RichSpan>, cs: ColorScheme): AnnotatedString {
    val builder = AnnotatedString.Builder(text)

    // stored character styles
    for (sp in spans) {
        val s = sp.start.coerceIn(0, text.length)
        val e = sp.end.coerceIn(0, text.length)
        if (s >= e) continue
        val style = when (sp.style) {
            RichStyles.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            RichStyles.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            RichStyles.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            RichStyles.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            RichStyles.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = cs.surfaceVariant)
            // codeblock: monospace here; the full-width tint is drawn behind the field
            RichStyles.CODEBLOCK -> SpanStyle(fontFamily = FontFamily.Monospace)
            RichStyles.H1 -> SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
            RichStyles.H2 -> SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            RichStyles.H3 -> SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
            else -> when {
                sp.style.startsWith(RichStyles.LINK_PREFIX) ->
                    SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline)
                sp.style.startsWith("image:") ->
                    SpanStyle(fontSize = 180.sp, color = Color.Transparent)
                else -> continue
            }
        }
        builder.addStyle(style, s, e)
    }

    // dynamic decorations derived from the text itself
    var offset = 0
    text.split("\n").forEachIndexed { index, line ->
        fun style(s: SpanStyle, from: Int, to: Int) = builder.addStyle(s, offset + from, offset + to)
        if (index == 0 && line.isNotEmpty()) {
            // the first line is the note title
            style(SpanStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold), 0, line.length)
        }
        when {
            line.startsWith("☑ ") -> {
                style(SpanStyle(color = cs.primary), 0, 1)
                style(
                    SpanStyle(color = cs.onSurfaceVariant, textDecoration = TextDecoration.LineThrough),
                    2, line.length
                )
            }
            line.startsWith("☐ ") || line.startsWith("• ") ->
                style(SpanStyle(color = cs.primary), 0, 1)
            // quote line: muted italic; the accent bar is drawn behind the field
            line.startsWith("> ") ->
                style(SpanStyle(color = cs.onSurfaceVariant, fontStyle = FontStyle.Italic), 0, line.length)
            else -> NUM_RE.find(line)?.let {
                style(SpanStyle(color = cs.primary, fontWeight = FontWeight.Medium), 0, it.value.length)
            }
        }
        // chip-style hashtags: tonal secondaryContainer highlight
        for (m in TAG_RE.findAll(line)) {
            style(
                SpanStyle(color = cs.onSecondaryContainer, background = cs.secondaryContainer),
                m.range.first, m.range.last + 1
            )
        }
        for (m in URL_RE.findAll(line)) {
            style(
                SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline),
                m.range.first, m.range.last + 1
            )
        }
        offset += line.length + 1
    }
    return builder.toAnnotatedString()
}

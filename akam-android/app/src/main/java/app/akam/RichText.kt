package app.akam

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import uniffi.akam.RichSpan

object RichStyles {
    const val BOLD = "bold"
    const val ITALIC = "italic"
    const val UNDERLINE = "underline"
    const val STRIKE = "strike"
    const val HEADING = "heading"
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
            RichStyles.HEADING -> SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            else -> continue
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

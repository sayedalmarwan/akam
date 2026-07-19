package app.akam

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.akam.RichSpan

class RichSpanTest {
    private fun sp(s: Int, e: Int, style: String = "bold") = RichSpan(s, e, style)

    @Test
    fun insertionInsideSpanExtendsIt() {
        // "abcdef" with bold "cd" [2,4); insert "ZZ" at 3 -> "abcZZdef"
        val out = remapSpans(listOf(sp(2, 4)), "abcdef", "abcZZdef")
        assertEquals(listOf(sp(2, 6)), out)
    }

    @Test
    fun typingAtSpanEndExtendsIt() {
        // bold "ab" [0,2); append "c" -> bold continues
        val out = remapSpans(listOf(sp(0, 2)), "ab", "abc")
        assertEquals(listOf(sp(0, 3)), out)
    }

    @Test
    fun deletingSpannedTextDropsSpan() {
        // bold "cd" [2,4); delete it -> "abef"
        val out = remapSpans(listOf(sp(2, 4)), "abcdef", "abef")
        assertEquals(emptyList<RichSpan>(), out)
    }

    @Test
    fun editAfterSpanLeavesItAlone() {
        val out = remapSpans(listOf(sp(0, 2)), "abcdef", "abcXdef")
        assertEquals(listOf(sp(0, 2)), out)
    }

    @Test
    fun deletionInsideLargeSpanShrinksIt() {
        // span [0,10) over "0123456789"; delete [3,6) -> length 7
        val out = remapSpans(listOf(sp(0, 10)), "0123456789", "0126789")
        assertEquals(listOf(sp(0, 7)), out)
    }

    @Test
    fun toggleAddsThenRemoves() {
        val added = toggleSpan(emptyList(), "bold", 1, 3)
        assertEquals(listOf(sp(1, 3)), added)
        val removed = toggleSpan(added, "bold", 1, 3)
        assertEquals(emptyList<RichSpan>(), removed)
    }

    @Test
    fun toggleRemovalSplitsSurroundingSpan() {
        // bold [0,10), un-bold [3,6) -> [0,3) and [6,10)
        val out = toggleSpan(listOf(sp(0, 10)), "bold", 3, 6)
        assertEquals(listOf(sp(0, 3), sp(6, 10)), out)
    }

    @Test
    fun togglePartiallyStyledRangeAppliesStyle() {
        // bold [0,4), toggle [2,6): not fully covered -> style is added
        val out = toggleSpan(listOf(sp(0, 4)), "bold", 2, 6)
        assertEquals(setOf(sp(0, 4), sp(2, 6)), out.toSet())
    }

    @Test
    fun otherStylesUntouchedByToggle() {
        val italic = sp(0, 5, "italic")
        val out = toggleSpan(listOf(italic), "bold", 0, 5)
        assertEquals(setOf(italic, sp(0, 5)), out.toSet())
    }

    // ---- list continuation on Enter ----

    /** Enter after a non-empty bullet inserts the next bullet. */
    @Test
    fun enterContinuesBullet() {
        // "• milk\n" with caret after the \n (pos 7)
        val out = continueList("• milk\n", 7)
        assertEquals("• milk\n• " to 9, out)
    }

    /** Enter on an empty bullet drops the marker and the newline (exit list). */
    @Test
    fun enterOnEmptyItemExitsList() {
        // "• milk\n• \n" — caret after the trailing \n (pos 10); the "• " line is empty
        val out = continueList("• milk\n• \n", 10)
        assertEquals("• milk\n" to 7, out)
    }

    /** Numbered items increment; a checked box continues as unchecked. */
    @Test
    fun enterIncrementsNumberAndResetsCheckbox() {
        assertEquals("1. a\n2. " to 8, continueList("1. a\n", 5))
        assertEquals("☑ done\n☐ " to 9, continueList("☑ done\n", 7))
    }

    /** A plain line yields no continuation. */
    @Test
    fun enterOnPlainLineDoesNothing() {
        assertEquals(null, continueList("hello\n", 6))
    }

    @Test
    fun numberForFollowsPreviousNumberedLine() {
        val text = "1. a\n2. b\n"
        assertEquals(3, numberFor(text, text.length)) // next line after "2. b"
        assertEquals(1, numberFor("plain\n", 6))       // no number above -> starts at 1
    }

    @Test
    fun headingCyclesThroughLevelsAndBack() {
        assertEquals("heading", nextHeading(null))
        assertEquals("heading2", nextHeading("heading"))
        assertEquals("heading3", nextHeading("heading2"))
        assertEquals(null, nextHeading("heading3"))
    }
}

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
}

package uns.ac.rs.team23.slagalica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TextNormalizationTest {

    private fun match(a: String, b: String) = normalizeAnswer(a) == normalizeAnswer(b)

    @Test
    fun caseInsensitive() {
        assertEquals(normalizeAnswer("Operation"), normalizeAnswer("OPERATION"))
        assertEquals(normalizeAnswer("football"), normalizeAnswer("FootBall"))
    }

    @Test
    fun trimsAndCollapsesWhitespace() {
        assertEquals(normalizeAnswer("  table  "), normalizeAnswer("table"))
        assertEquals(normalizeAnswer("moj  broj"), normalizeAnswer("moj broj"))
    }

    @Test
    fun stripsLatinDiacritics() {
        assertEquals(normalizeAnswer("žaba"), normalizeAnswer("zaba"))
        assertEquals(normalizeAnswer("ČAČAK"), normalizeAnswer("cacak"))
        assertEquals(normalizeAnswer("đak"), normalizeAnswer("djak"))
    }

    @Test
    fun cyrillicMatchesLatin() {
        // Cyrillic stored answer, Latin typed guess.
        assertEquals(normalizeAnswer("ЖАБА"), normalizeAnswer("zaba"))
        assertEquals(normalizeAnswer("Београд"), normalizeAnswer("Beograd"))
        assertEquals(normalizeAnswer("фудбал"), normalizeAnswer("fudbal"))
    }

    @Test
    fun differentAnswersDoNotMatch() {
        assertNotEquals(normalizeAnswer("table"), normalizeAnswer("chair"))
        assertNotEquals(normalizeAnswer("sport"), normalizeAnswer("art"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", normalizeAnswer(""))
        assertEquals("", normalizeAnswer("   "))
    }
}

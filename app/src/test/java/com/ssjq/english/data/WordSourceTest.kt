package com.ssjq.english.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordSourceTest {

    @Test
    fun canonicalDbRemovesLegacySuffix() {
        assertEquals("CET4_1", WordSource.canonicalDb("CET4_1.db"))
        assertEquals("CET4_1", WordSource.canonicalDb("CET4_1"))
        assertEquals("", WordSource.canonicalDb(null))
    }

    @Test
    fun sourceIdUsesCanonicalDb() {
        assertEquals("CET4_1#42", WordSource.id("CET4_1.db", "42"))
        assertEquals("CET4_1#42", WordSource.id("CET4_1", "42"))
    }

    @Test
    fun parseIdRejectsLegacyWordOnlyAndCanonicalizesDb() {
        assertEquals("CET4_1" to "42", WordSource.parseId("CET4_1.db#42"))
        assertNull(WordSource.parseId("42"))
        assertNull(WordSource.parseId("CET4_1#"))
    }

    @Test
    fun canonicalSourceIdAcceptsLegacyWordOnly() {
        assertEquals("CET4_1#42", WordSource.canonicalSourceId("CET4_1.db#42"))
        assertEquals("42", WordSource.canonicalSourceId("42"))
    }

    @Test
    fun wordEntrySharesSourceIdWithQuizState() {
        val entry = WordEntry(
            wordId = "42",
            headWord = "abandon",
            dbName = "CET4_1.db",
        )
        assertEquals(WordSource.id("CET4_1", "42"), entry.sourceId)
        assertEquals("CET4_1", entry.toWordDetail().dbName)
    }
}

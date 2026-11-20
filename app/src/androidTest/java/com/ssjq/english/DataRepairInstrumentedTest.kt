package com.ssjq.english

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ssjq.english.data.ExportContentType
import com.ssjq.english.data.ExportData
import com.ssjq.english.data.ExportScope
import com.ssjq.english.data.ImportConflictStrategy
import com.ssjq.english.data.LibraryFileManager
import com.ssjq.english.data.LibraryRepository
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.WordEntry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataRepairInstrumentedTest {

    private fun resetLibrary(context: Context) {
        val prefs = context.getSharedPreferences("user_library", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        prefs.edit()
            .putString(UserLibrary.KEY_WRONG, "[]")
            .putString(UserLibrary.KEY_FAVORITE, "[]")
            .commit()
        UserLibrary.init(context)
        UserLibrary.repairStoredLists()
    }

    @Test
    fun corruptWordListIsRepaired() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resetLibrary(context)
        val prefs = context.getSharedPreferences("user_library", Context.MODE_PRIVATE)
        val raw = """
            [
              {"wordId":"1","dbName":"CET4_1.db","headWord":"alpha"},
              {"bad":1},
              {"wordId":"1","dbName":"CET4_1","headWord":"alpha"},
              {"wordId":"2","dbName":"CET4_2","headWord":"beta"}
            ]
        """.trimIndent()
        prefs.edit().putString(UserLibrary.KEY_WRONG, raw).commit()
        UserLibrary.repairStoredLists()

        val entries = UserLibrary.allWrongWords()
        assertEquals(2, entries.size)
        assertTrue(entries.all { !it.dbName.endsWith(".db") })
        resetLibrary(context)
    }

    @Test
    fun legacyExportGetsDefaultFields() {
        val json = JSONObject().apply {
            put("version", 1)
            put("wrongWords", JSONArray().put(JSONObject().apply {
                put("wordId", "1")
                put("headWord", "alpha")
                put("dbName", "CET4_1.db")
            }))
            put("favorites", JSONArray())
        }.toString()
        LibraryFileManager.validateJson(json)
        val data = LibraryFileManager.deserializeFromJson(json)
        assertEquals(LibraryFileManager.SCHEMA_VERSION, data.version)
        assertEquals(ExportScope.ALL, data.scope)
        assertEquals(ExportContentType.BOTH, data.contentType)
        assertEquals(1, data.wrongWords.size)
        assertEquals("CET4_1", data.wrongWords[0].dbName)
    }

    @Test
    fun overwriteImportDeduplicatesEntries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        resetLibrary(context)
        val entry = WordEntry(
            wordId = "1",
            headWord = "alpha",
            dbName = "CET4_1.db",
            addedAt = 1L,
        )
        val data = ExportData(
            wrongWords = listOf(entry, entry.copy(addedAt = 2L)),
            favorites = emptyList(),
        )
        runBlocking {
            LibraryRepository.importData(data, ImportConflictStrategy.OVERWRITE)
        }
        assertEquals(1, UserLibrary.allWrongWords().size)
        resetLibrary(context)
    }

    @Test
    fun mainActivityLaunchesWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1500)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}

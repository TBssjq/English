package com.ssjq.english.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ExportScope {
    ALL,
    GROUP,
    SINGLE,
}

enum class ExportContentType {
    BOTH,
    WRONG_ONLY,
    FAVORITE_ONLY,
}

data class ExportData(
    val version: Int = 1,
    val exportTime: Long = System.currentTimeMillis(),
    val scope: ExportScope = ExportScope.ALL,
    val scopeName: String = "",
    val contentType: ExportContentType = ExportContentType.BOTH,
    val wrongWords: List<WordEntry> = emptyList(),
    val favorites: List<WordEntry> = emptyList(),
)

enum class ImportConflictStrategy {
    OVERWRITE,
    MERGE_DUPLICATE,
    SKIP_EXISTING,
}

data class ImportResult(
    val success: Boolean,
    val message: String,
    val addedWrongCount: Int = 0,
    val addedFavoriteCount: Int = 0,
    val skippedCount: Int = 0,
)

object LibraryRepository {

    private val mutex = Mutex()

    suspend fun getAllEntries(): Pair<List<WordEntry>, List<WordEntry>> = mutex.withLock {
        getAllEntriesUnsafe()
    }

    private fun getAllEntriesUnsafe(): Pair<List<WordEntry>, List<WordEntry>> {
        val wrong = UserLibrary.readList(UserLibrary.KEY_WRONG)
        val fav = UserLibrary.readList(UserLibrary.KEY_FAVORITE)
        return wrong to fav
    }

    suspend fun importData(
        data: ExportData,
        strategy: ImportConflictStrategy,
    ): ImportResult = mutex.withLock {
        val backupWrong = UserLibrary.readList(UserLibrary.KEY_WRONG)
        val backupFav = UserLibrary.readList(UserLibrary.KEY_FAVORITE)

        return try {
            var addedWrong = 0
            var addedFav = 0
            var skipped = 0

            when (strategy) {
                ImportConflictStrategy.OVERWRITE -> {
                    if (data.wrongWords.isNotEmpty()) {
                        UserLibrary.writeList(UserLibrary.KEY_WRONG, data.wrongWords)
                        addedWrong = data.wrongWords.size
                    }
                    if (data.favorites.isNotEmpty()) {
                        UserLibrary.writeList(UserLibrary.KEY_FAVORITE, data.favorites)
                        addedFav = data.favorites.size
                    }
                }

                ImportConflictStrategy.MERGE_DUPLICATE -> {
                    val existingWrong = backupWrong.toMutableList()
                    val existingWrongKeys = existingWrong.map { "${it.wordId}_${it.dbName}" }.toSet()
                    data.wrongWords.forEach { entry ->
                        val key = "${entry.wordId}_${entry.dbName}"
                        if (!existingWrongKeys.contains(key)) {
                            existingWrong.add(entry)
                            addedWrong++
                        }
                    }
                    UserLibrary.writeList(UserLibrary.KEY_WRONG, existingWrong)

                    val existingFav = backupFav.toMutableList()
                    val existingFavKeys = existingFav.map { "${it.wordId}_${it.dbName}" }.toSet()
                    data.favorites.forEach { entry ->
                        val key = "${entry.wordId}_${entry.dbName}"
                        if (!existingFavKeys.contains(key)) {
                            existingFav.add(entry)
                            addedFav++
                        } else {
                            skipped++
                        }
                    }
                    UserLibrary.writeList(UserLibrary.KEY_FAVORITE, existingFav)
                }

                ImportConflictStrategy.SKIP_EXISTING -> {
                    val existingWrongKeys = backupWrong.map { "${it.wordId}_${it.dbName}" }.toSet()
                    val newWrong = data.wrongWords.filter {
                        !existingWrongKeys.contains("${it.wordId}_${it.dbName}")
                    }
                    addedWrong = newWrong.size
                    if (newWrong.isNotEmpty()) {
                        UserLibrary.writeList(UserLibrary.KEY_WRONG, backupWrong + newWrong)
                    }

                    val existingFavKeys = backupFav.map { "${it.wordId}_${it.dbName}" }.toSet()
                    val newFav = data.favorites.filter {
                        !existingFavKeys.contains("${it.wordId}_${it.dbName}")
                    }
                    addedFav = newFav.size
                    skipped = data.favorites.size - newFav.size
                    if (newFav.isNotEmpty()) {
                        UserLibrary.writeList(UserLibrary.KEY_FAVORITE, backupFav + newFav)
                    }
                }
            }

            ImportResult(
                success = true,
                message = "导入成功：错题+$addedWrong，收藏+$addedFav，跳过$skipped",
                addedWrongCount = addedWrong,
                addedFavoriteCount = addedFav,
                skippedCount = skipped,
            )
        } catch (e: Exception) {
            UserLibrary.writeList(UserLibrary.KEY_WRONG, backupWrong)
            UserLibrary.writeList(UserLibrary.KEY_FAVORITE, backupFav)
            ImportResult(
                success = false,
                message = "导入失败：${e.message ?: "未知错误"}",
            )
        }
    }

    suspend fun exportAll(contentType: ExportContentType = ExportContentType.BOTH): ExportData = mutex.withLock {
        val (wrong, fav) = getAllEntriesUnsafe()
        val filteredWrong = if (contentType == ExportContentType.FAVORITE_ONLY) emptyList() else wrong
        val filteredFav = if (contentType == ExportContentType.WRONG_ONLY) emptyList() else fav
        ExportData(
            version = 1,
            exportTime = System.currentTimeMillis(),
            scope = ExportScope.ALL,
            scopeName = "全部词库",
            contentType = contentType,
            wrongWords = filteredWrong,
            favorites = filteredFav,
        )
    }

    suspend fun exportByGroup(groupName: String, contentType: ExportContentType = ExportContentType.BOTH): ExportData = mutex.withLock {
        val (wrong, fav) = getAllEntriesUnsafe()
        val dbNames = getDbNamesForGroup(groupName)
        val filteredWrong = if (contentType == ExportContentType.FAVORITE_ONLY) emptyList() else wrong.filter { it.dbName in dbNames }
        val filteredFav = if (contentType == ExportContentType.WRONG_ONLY) emptyList() else fav.filter { it.dbName in dbNames }
        ExportData(
            version = 1,
            exportTime = System.currentTimeMillis(),
            scope = ExportScope.GROUP,
            scopeName = groupName,
            contentType = contentType,
            wrongWords = filteredWrong,
            favorites = filteredFav,
        )
    }

    suspend fun exportByDbName(dbName: String, contentType: ExportContentType = ExportContentType.BOTH): ExportData = mutex.withLock {
        val (wrong, fav) = getAllEntriesUnsafe()
        val filteredWrong = if (contentType == ExportContentType.FAVORITE_ONLY) emptyList() else wrong.filter { it.dbName == dbName }
        val filteredFav = if (contentType == ExportContentType.WRONG_ONLY) emptyList() else fav.filter { it.dbName == dbName }
        ExportData(
            version = 1,
            exportTime = System.currentTimeMillis(),
            scope = ExportScope.SINGLE,
            scopeName = dbName,
            contentType = contentType,
            wrongWords = filteredWrong,
            favorites = filteredFav,
        )
    }

    private fun getDbNamesForGroup(groupName: String): Set<String> {
        return LibraryCatalog.categories
            .find { it.name == groupName }
            ?.subcategories
            ?.flatMap { it.dbFiles }
            ?.toSet()
            ?: emptySet()
    }

    fun init(context: Context) {
        UserLibrary.init(context)
    }
}
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
    val version: Int = 2,
    val exportTime: Long = System.currentTimeMillis(),
    val scope: ExportScope = ExportScope.ALL,
    val scopeName: String = "",
    val contentType: ExportContentType = ExportContentType.BOTH,
    val wrongWords: List<WordEntry> = emptyList(),
    val favorites: List<WordEntry> = emptyList(),
    val totalDays: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalWordsLearned: Int = 0,
    val totalWordsMastered: Int = 0,
    val totalMinutes: Int = 0,
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

    private fun entryKey(entry: WordEntry): String = entry.sourceId

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
        val importedWrong = data.wrongWords.distinctBy(::entryKey)
        val importedFav = data.favorites.distinctBy(::entryKey)

        return try {
            var addedWrong = 0
            var addedFav = 0
            var skipped = 0

            when (strategy) {
                ImportConflictStrategy.OVERWRITE -> {
                    // 即使导入文件里列表为空，也要写入空列表以清空旧数据
                    UserLibrary.writeList(UserLibrary.KEY_WRONG, importedWrong)
                    addedWrong = importedWrong.size
                    UserLibrary.writeList(UserLibrary.KEY_FAVORITE, importedFav)
                    addedFav = importedFav.size
                }

                ImportConflictStrategy.MERGE_DUPLICATE -> {
                    val existingWrong = backupWrong.toMutableList()
                    val existingWrongKeys = existingWrong.map(::entryKey).toMutableSet()
                    importedWrong.forEach { entry ->
                        if (existingWrongKeys.add(entryKey(entry))) {
                            existingWrong.add(entry)
                            addedWrong++
                        } else {
                            skipped++
                        }
                    }
                    UserLibrary.writeList(UserLibrary.KEY_WRONG, existingWrong)

                    val existingFav = backupFav.toMutableList()
                    val existingFavKeys = existingFav.map(::entryKey).toMutableSet()
                    importedFav.forEach { entry ->
                        if (existingFavKeys.add(entryKey(entry))) {
                            existingFav.add(entry)
                            addedFav++
                        } else {
                            skipped++
                        }
                    }
                    UserLibrary.writeList(UserLibrary.KEY_FAVORITE, existingFav)
                }

                ImportConflictStrategy.SKIP_EXISTING -> {
                    val existingWrongKeys = backupWrong.map(::entryKey).toSet()
                    val newWrong = importedWrong.filterNot { existingWrongKeys.contains(entryKey(it)) }
                    addedWrong = newWrong.size
                    skipped += importedWrong.size - newWrong.size
                    if (newWrong.isNotEmpty()) {
                        UserLibrary.writeList(UserLibrary.KEY_WRONG, backupWrong + newWrong)
                    }

                    val existingFavKeys = backupFav.map(::entryKey).toSet()
                    val newFav = importedFav.filterNot { existingFavKeys.contains(entryKey(it)) }
                    addedFav = newFav.size
                    skipped += importedFav.size - newFav.size
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
            throw e
        }
    }

    suspend fun exportAll(contentType: ExportContentType = ExportContentType.BOTH): ExportData = mutex.withLock {
        val (wrong, fav) = getAllEntriesUnsafe()
        val filteredWrong = if (contentType == ExportContentType.FAVORITE_ONLY) emptyList() else wrong
        val filteredFav = if (contentType == ExportContentType.WRONG_ONLY) emptyList() else fav
        val stats = CheckInManager.stats()
        ExportData(
            version = LibraryFileManager.SCHEMA_VERSION,
            exportTime = System.currentTimeMillis(),
            scope = ExportScope.ALL,
            scopeName = "全部词库",
            contentType = contentType,
            wrongWords = filteredWrong,
            favorites = filteredFav,
            totalDays = stats.totalDays,
            currentStreak = stats.currentStreak,
            longestStreak = stats.longestStreak,
            totalWordsLearned = stats.totalWordsLearned,
            totalWordsMastered = stats.totalWordsMastered,
            totalMinutes = stats.totalMinutes,
        )
    }

    suspend fun exportByGroup(groupName: String, contentType: ExportContentType = ExportContentType.BOTH): ExportData = mutex.withLock {
        val (wrong, fav) = getAllEntriesUnsafe()
        val dbNames = getDbNamesForGroup(groupName)
        val filteredWrong = if (contentType == ExportContentType.FAVORITE_ONLY) emptyList() else wrong.filter { it.dbName in dbNames }
        val filteredFav = if (contentType == ExportContentType.WRONG_ONLY) emptyList() else fav.filter { it.dbName in dbNames }
        ExportData(
            version = LibraryFileManager.SCHEMA_VERSION,
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
            version = LibraryFileManager.SCHEMA_VERSION,
            exportTime = System.currentTimeMillis(),
            scope = ExportScope.SINGLE,
            scopeName = dbName,
            contentType = contentType,
            wrongWords = filteredWrong,
            favorites = filteredFav,
        )
    }

    private fun getDbNamesForGroup(groupName: String): Set<String> {
        val category = LibraryCatalog.categories.find { it.name == groupName } ?: return emptySet()
        return category.subcategories.flatMap { it.dbFiles }.toSet()
    }
}
package com.ssjq.english.data

/**
 * 词库分类目录：按考试/学段组织词库数据库文件。
 * 两级结构：分类(Category) → 子分类(Subcategory) → 多个 .db 文件。
 */
data class LibraryCategory(
    val name: String,
    val subcategories: List<LibrarySubcategory>,
)

data class LibrarySubcategory(
    val name: String,
    val dbFiles: List<String>,
) {
    val totalWords: Int get() = dbFiles.size
}

/**
 * 词库的中文显示信息，供界面分行排版使用。
 *
 * 旧实现只给出一整串「高中英语 · 北师大版高中 10」，主界面用大字号整段渲染时，
 * 窄屏会把序号折到第二行、只剩「高中 10」一行超大文字，视觉极不平衡。
 * 拆成分类 / 册名两段后，界面可以分别控制字号与行数。
 *
 * @param category    分类中文名，去掉括号内的英文缩写，如「高中英语」
 * @param subcategory 子分类中文名，如「北师大版高中」
 * @param bookName    带序号的册名，如「北师大版高中 10」；未收录时退化为原始文件名
 * @param rawName     原始 db 文件名（不含 .db），仅内部排查用，不直接展示
 * @param mapped      是否已在分类目录中登记（false 表示未知词库，只能显示文件名）
 */
data class BookDisplay(
    val category: String,
    val subcategory: String,
    val bookName: String,
    val rawName: String,
    val mapped: Boolean,
)

object LibraryCatalog {

    /**
     * 界面显示用的词库名：中文分类 + 子分类 + 序号。
     * 只用于展示，文件读写与 SharedPreferences 的键仍使用原始 dbName。
     */
    fun displayName(dbName: String): String {
        val parts = displayParts(dbName)
        return if (parts.mapped) "${parts.category} · ${parts.bookName}" else parts.bookName
    }

    /**
     * 把 db 文件名解析成中文显示信息（分类 / 册名分开），供需要分行排版的界面使用。
     */
    fun displayParts(dbName: String): BookDisplay {
        val name = dbName.removeSuffix(".db")
        for (category in categories) {
            for (sub in category.subcategories) {
                val index = sub.dbFiles.indexOf(name)
                if (index >= 0) {
                    return BookDisplay(
                        category = category.name.substringBefore("（").trim(),
                        subcategory = sub.name,
                        bookName = "${sub.name} ${index + 1}",
                        rawName = name,
                        mapped = true,
                    )
                }
            }
        }
        // 未登记的词库：直接显示文件名，界面据此提示「未登记」而不是假装有中文名
        return BookDisplay(
            category = "其他词库",
            subcategory = "",
            bookName = name,
            rawName = name,
            mapped = false,
        )
    }

    /**
     * 按关键词匹配词库：中文分类名 / 子分类名 / 中文显示名 / 原始文件名都能命中。
     * 旧实现只匹配原始文件名（如 BeiShiGaoZhong_1），输入「北师大」搜不到任何词库。
     */
    fun matchesQuery(dbName: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val raw = dbName.removeSuffix(".db")
        if (raw.contains(q, ignoreCase = true)) return true
        // 中文比对时忽略空格：「高中10」也能命中「北师大版高中 10」
        val compactQ = q.replace(" ", "")
        val parts = displayParts(dbName)
        if (parts.bookName.replace(" ", "").contains(compactQ, ignoreCase = true)) return true
        if (parts.subcategory.replace(" ", "").contains(compactQ, ignoreCase = true)) return true
        // 分类名同时按全名（含英文缩写，如「大学英语四级（CET4）」）与短名匹配
        val fullCategory = categories.firstOrNull { cat ->
            cat.subcategories.any { sub -> sub.dbFiles.any { it == raw } }
        }?.name
        if (fullCategory != null) {
            if (fullCategory.contains(q, ignoreCase = true)) return true
            if (fullCategory.substringBefore("（").replace(" ", "").contains(compactQ, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * 逐层扩大的查找顺序：当前词库 → 同子分类 → 同分类 → 其他词库。
     * 用于查词时先就近命中，避免每次都扫全部词库。
     *
     * @param current 当前词库名，可带 .db 后缀
     * @param available assets 中实际存在的词库名，不含 .db 后缀
     */
    fun searchOrder(current: String, available: List<String>): List<String> {
        val name = current.removeSuffix(".db")
        val availableSet = available.toSet()
        var sameSub = emptyList<String>()
        var sameCat = emptyList<String>()
        for (category in categories) {
            for (sub in category.subcategories) {
                if (name in sub.dbFiles) {
                    sameSub = sub.dbFiles
                    sameCat = category.subcategories.flatMap { it.dbFiles }
                }
            }
        }
        // 只返回 assets 中实际存在的词库：分类目录里声明但未打包的条目会导致打开失败
        return buildList {
            if (name in availableSet) add(name)
            addAll(sameSub.filter { it != name && it in availableSet })
            addAll(sameCat.filter { it != name && it !in sameSub && it in availableSet })
            addAll(available.filter { it != name && it !in sameCat })
        }.distinct()
    }

    /**
     * 完整的词库分类目录。
     * db 文件名必须与 assets/databases/ 下的实际文件名一致（不含 .db 后缀也可以，显示用）。
     */
    val categories: List<LibraryCategory> = listOf(
        // ========== 大学英语四级 ==========
        LibraryCategory(
            name = "大学英语四级（CET4）",
            subcategories = listOf(
                LibrarySubcategory("正序", listOf("CET4_1", "CET4_2", "CET4_3")),
                LibrarySubcategory("乱序", listOf("CET4luan_1", "CET4luan_2")),
            ),
        ),
        // ========== 大学英语六级 ==========
        LibraryCategory(
            name = "大学英语六级（CET6）",
            subcategories = listOf(
                LibrarySubcategory("正序", listOf("CET6_1", "CET6_2", "CET6_3")),
                LibrarySubcategory("乱序", listOf("CET6luan_1")),
            ),
        ),
        // ========== 考研英语 ==========
        LibraryCategory(
            name = "考研英语",
            subcategories = listOf(
                LibrarySubcategory("正序", listOf("KaoYan_1", "KaoYan_2", "KaoYan_3")),
                LibrarySubcategory("乱序", listOf("KaoYanluan_1")),
            ),
        ),
        // ========== 专业英语四级 ==========
        LibraryCategory(
            name = "专业英语四级（Level4）",
            subcategories = listOf(
                LibrarySubcategory("正序", listOf("Level4_1", "Level4_2")),
                LibrarySubcategory("乱序", listOf("Level4luan_1", "Level4luan_2")),
            ),
        ),
        // ========== 专业英语八级 ==========
        LibraryCategory(
            name = "专业英语八级（Level8）",
            subcategories = listOf(
                LibrarySubcategory("正序", listOf("Level8_1", "Level8_2")),
                LibrarySubcategory("乱序", listOf("Level8luan_2")),
            ),
        ),
        // ========== 商务英语 ==========
        LibraryCategory(
            name = "商务英语（BEC）",
            subcategories = listOf(
                LibrarySubcategory("商务英语词汇", listOf("BEC_2", "BEC_3")),
            ),
        ),
        // ========== 雅思 ==========
        LibraryCategory(
            name = "雅思（IELTS）",
            subcategories = listOf(
                LibrarySubcategory("正序", listOf("IELTS_2", "IELTS_3")),
                LibrarySubcategory("乱序", listOf("IELTSluan_2")),
            ),
        ),
        // ========== 托福 ==========
        LibraryCategory(
            name = "托福（TOEFL）",
            subcategories = listOf(
                LibrarySubcategory("托福词汇", listOf("TOEFL_2", "TOEFL_3")),
            ),
        ),
        // ========== GRE ==========
        LibraryCategory(
            name = "美国研究生入学考试（GRE）",
            subcategories = listOf(
                LibrarySubcategory("研究生入学词汇", listOf("GRE_2", "GRE_3")),
            ),
        ),
        // ========== GMAT ==========
        LibraryCategory(
            name = "管理类研究生入学考试（GMAT）",
            subcategories = listOf(
                LibrarySubcategory("正序", listOf("GMAT_2", "GMAT_3")),
                LibrarySubcategory("乱序", listOf("GMATluan_2")),
            ),
        ),
        // ========== SAT ==========
        LibraryCategory(
            name = "美国大学入学考试（SAT）",
            subcategories = listOf(
                LibrarySubcategory("本科入学词汇", listOf("SAT_2", "SAT_3")),
            ),
        ),
        // ========== 高中英语 ==========
        LibraryCategory(
            name = "高中英语",
            subcategories = listOf(
                LibrarySubcategory("通用高中", listOf("GaoZhong_2", "GaoZhong_3", "GaoZhongluan_2")),
                LibrarySubcategory(
                    "人教版高中",
                    List(11) { "PEPGaoZhong_${it + 1}" },
                ),
                LibrarySubcategory(
                    "北师大版高中",
                    List(11) { "BeiShiGaoZhong_${it + 1}" },
                ),
            ),
        ),
        // ========== 初中英语 ==========
        LibraryCategory(
            name = "初中英语",
            subcategories = listOf(
                LibrarySubcategory("通用初中", listOf("ChuZhong_2", "ChuZhong_3", "ChuZhongluan_2")),
                LibrarySubcategory(
                    "人教版初中",
                    listOf(
                        "PEPChuZhong7_1", "PEPChuZhong7_2",
                        "PEPChuZhong8_1", "PEPChuZhong8_2",
                        "PEPChuZhong9_1",
                    ),
                ),
                LibrarySubcategory(
                    "外研社初中",
                    List(6) { "WaiYanSheChuZhong_${it + 1}" },
                ),
            ),
        ),
        // ========== 小学英语 ==========
        LibraryCategory(
            name = "小学英语",
            subcategories = listOf(
                LibrarySubcategory(
                    "人教版小学",
                    listOf(
                        "PEPXiaoXue3_1", "PEPXiaoXue3_2",
                        "PEPXiaoXue4_1", "PEPXiaoXue4_2",
                        "PEPXiaoXue5_1", "PEPXiaoXue5_2",
                        "PEPXiaoXue6_1", "PEPXiaoXue6_2",
                    ),
                ),
            ),
        ),
    )

    /** 所有可用的 db 文件名（从 catalog 中汇总，用于与 assets 比对） */
    val allDbNames: Set<String> by lazy {
        categories.flatMap { cat ->
            cat.subcategories.flatMap { sub -> sub.dbFiles }
        }.toSet()
    }

    /**
     * 根据 assets 中实际存在的文件，过滤并生成显示用的分类目录。
     * 这样如果某个 db 文件不存在，不会显示出来。
     */
    fun buildCatalogForAvailableDbs(availableDbs: List<String>): List<LibraryCategory> {
        val availableNames = availableDbs.map { it.removeSuffix(".db") }.toSet()
        return categories.mapNotNull { cat ->
            val subs = cat.subcategories.mapNotNull { sub ->
                val existing = sub.dbFiles.filter { it in availableNames }
                if (existing.isEmpty()) null else LibrarySubcategory(sub.name, existing)
            }
            if (subs.isEmpty()) null else LibraryCategory(cat.name, subs)
        }
    }

    /**
     * 在分类目录中搜索匹配的子分类/词库，返回扁平化的结果列表（用于搜索过滤）。
     * 返回：子分类全限定名 + 对应 db 文件列表。
     */
    fun search(query: String, availableDbs: List<String>): List<Pair<String, List<String>>> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val lowerQ = q.lowercase()
        val availableNames = availableDbs.map { it.removeSuffix(".db") }.toSet()
        val result = mutableListOf<Pair<String, List<String>>>()
        for (cat in categories) {
            for (sub in cat.subcategories) {
                // 用 matchesQuery 逐本匹配：中文分类名 / 册名 / 序号都能命中，
                // 不再是「只能按 BeiShiGaoZhong 这类英文文件名搜索」
                val matchedDbs = sub.dbFiles
                    .filter { it in availableNames && matchesQuery(it, q) }
                val catMatch = cat.name.lowercase().contains(lowerQ)
                val subMatch = sub.name.lowercase().contains(lowerQ)
                if (matchedDbs.isNotEmpty() || catMatch || subMatch) {
                    val dbs = if (matchedDbs.isNotEmpty()) matchedDbs
                    else sub.dbFiles.filter { it in availableNames }
                    if (dbs.isNotEmpty()) {
                        // 标题只用中文分类名，去掉「（CET4）」这类英文括号注释
                        val catLabel = cat.name.substringBefore("（").trim()
                        result += ("$catLabel · ${sub.name}" to dbs)
                    }
                }
            }
        }
        return result
    }
}

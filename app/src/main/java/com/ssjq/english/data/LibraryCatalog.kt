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

object LibraryCatalog {

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
                LibrarySubcategory("BEC", listOf("BEC_2", "BEC_3")),
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
                LibrarySubcategory("TOEFL", listOf("TOEFL_2", "TOEFL_3")),
            ),
        ),
        // ========== GRE ==========
        LibraryCategory(
            name = "GRE",
            subcategories = listOf(
                LibrarySubcategory("GRE", listOf("GRE_2", "GRE_3")),
            ),
        ),
        // ========== GMAT ==========
        LibraryCategory(
            name = "GMAT",
            subcategories = listOf(
                LibrarySubcategory("正序", listOf("GMAT_2", "GMAT_3")),
                LibrarySubcategory("乱序", listOf("GMATluan_2")),
            ),
        ),
        // ========== SAT ==========
        LibraryCategory(
            name = "SAT",
            subcategories = listOf(
                LibrarySubcategory("SAT", listOf("SAT_2", "SAT_3")),
            ),
        ),
        // ========== 高中英语 ==========
        LibraryCategory(
            name = "高中英语",
            subcategories = listOf(
                LibrarySubcategory("通用高中", listOf("GaoZhong_2", "GaoZhong_3", "GaoZhongluan_2")),
                LibrarySubcategory(
                    "人教版高中（PEP）",
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
                    "人教版初中（PEP）",
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
            name = "小学英语（人教版PEP）",
            subcategories = listOf(
                LibrarySubcategory(
                    "人教版 PEP",
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
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val availableNames = availableDbs.map { it.removeSuffix(".db") }.toSet()
        val result = mutableListOf<Pair<String, List<String>>>()
        for (cat in categories) {
            for (sub in cat.subcategories) {
                val matchedDbs = sub.dbFiles
                    .filter { it in availableNames && it.lowercase().contains(q) }
                val catMatch = cat.name.lowercase().contains(q)
                val subMatch = sub.name.lowercase().contains(q)
                if (matchedDbs.isNotEmpty() || catMatch || subMatch) {
                    val dbs = if (matchedDbs.isNotEmpty()) matchedDbs
                    else sub.dbFiles.filter { it in availableNames }
                    if (dbs.isNotEmpty()) {
                        result += ("${cat.name} · ${sub.name}" to dbs)
                    }
                }
            }
        }
        return result
    }
}

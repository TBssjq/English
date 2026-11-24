package com.ssjq.english.ui.nav

/**
 * Navigation 3 类型安全路由。
 * 每个路由是一个对象/数据类，直接作为 back stack 中的 key。
 */

/** 首页 / 仪表盘：选择词库与快速入口 */
object Home

/** 词汇列表页：某词库的单词列表，按 List 分组 */
data class WordListRoute(val dbName: String)

/** 单词详情 / 学习卡片页 */
data class WordDetailRoute(val dbName: String, val wordId: String)

/** 背诵会话页：默认遍历整本词库；reviewQueue 非空时只复习指定到期单词 */
data class WordStudyRoute(
    val dbName: String,
    val startWordId: String? = null,
    val reviewQueue: List<String> = emptyList(),
)

/** 快速测验页：mode=null 时 4 种模式随机切换；非 null 时固定该模式 */
data class QuizRoute(
    val dbName: String,
    val count: Int = 20,
    val mode: String? = null,
    val sourceWords: List<com.ssjq.english.data.WordDetail> = emptyList(),
    /** 疯狂刷题的题源标识，用于退出后按同一批词恢复进度 */
    val sourceKey: String? = null,
    /** 随机模式下允许出现的题型 label（空 = 全部题型），疯狂刷题选题型后带入 */
    val allowedModes: List<String> = emptyList(),
)

/** 搜索页 */
data class SearchRoute(val dbName: String)

/** 用户词单类型：错题本 / 收藏夹 */
enum class LibraryType { WRONG, FAVORITE }

/**
 * 用户词单页：错题本 / 收藏夹。
 * showAll 为真时进入后直接展示全部词库的条目 —— 跨词库测验的错题分散在各库，
 * 只看当前词库会让人以为没有记录。
 */
data class LibraryRoute(
    val dbName: String,
    val type: LibraryType,
    val showAll: Boolean = false,
)

/** 疯狂刷题题源类型 */
enum class CrazyQuizSource { WRONG, FAVORITE, LIBRARY }

/** 疯狂刷题路由：题源与词库由页面内部选择 */
object CrazyQuizRoute

/** 每日打卡页：学习统计 / 连续天数 / 贡献热力图（即「成就」页） */
object CheckInRoute

/** 关于我页面 */
object AboutRoute

/**
 * 底部导航栏的三个顶层页签。
 * 二级页面（词库列表、单词详情等）不属于顶层，进入后底栏会隐藏。
 */
enum class MainTab {
    /** 学习：词库与学习入口 */
    STUDY,

    /** 成就：打卡统计、连续天数、成就 */
    ACHIEVEMENT,

    /** 关于作者 */
    ABOUT,
}

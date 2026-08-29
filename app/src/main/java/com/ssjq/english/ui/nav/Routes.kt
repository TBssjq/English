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

/** 背诵会话页：遍历整个词库的全部单词（可指定起始单词） */
data class WordStudyRoute(val dbName: String, val startWordId: String? = null)

/** 快速测验页：mode=null 时 4 种模式随机切换；非 null 时固定该模式 */
data class QuizRoute(val dbName: String, val count: Int = 20, val mode: String? = null, val sourceWords: List<com.ssjq.english.data.WordDetail> = emptyList())

/** 搜索页 */
data class SearchRoute(val dbName: String)

/** 用户词单类型：错题本 / 收藏夹 */
enum class LibraryType { WRONG, FAVORITE }

/** 用户词单页：错题本 / 收藏夹 */
data class LibraryRoute(val dbName: String, val type: LibraryType)

/** 疯狂刷题题源类型 */
enum class CrazyQuizSource { WRONG, FAVORITE, LIBRARY }

/** 疯狂刷题路由 */
data class CrazyQuizRoute(val dbName: String, val source: CrazyQuizSource, val count: Int)

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

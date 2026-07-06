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

/** 背诵会话页：遍历整个词库的全部单词 */
data class WordStudyRoute(val dbName: String)

/** 快速测验页：mode=null 时 4 种模式随机切换；非 null 时固定该模式 */
data class QuizRoute(val dbName: String, val count: Int = 20, val mode: String? = null)

/** 搜索页 */
data class SearchRoute(val dbName: String)

/** 用户词单类型：错题本 / 收藏夹 */
enum class LibraryType { WRONG, FAVORITE }

/** 用户词单页：错题本 / 收藏夹 */
data class LibraryRoute(val dbName: String, val type: LibraryType)

/** 每日打卡页：学习统计 / 连续天数 / 贡献热力图 */
object CheckInRoute

/** 关于我页面 */
object AboutRoute

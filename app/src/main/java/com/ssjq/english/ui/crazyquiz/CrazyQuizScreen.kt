@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.crazyquiz

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ssjq.english.ui.glass.GlassShapes
import com.ssjq.english.ui.glass.LocalAppBackdrop
import com.ssjq.english.ui.glass.LocalGlassPalette
import com.ssjq.english.ui.glass.glassTopBar
import com.ssjq.english.ui.glass.glassTopBarColors
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.UserManager
import com.ssjq.english.data.WordDetail
import com.ssjq.english.data.WordSource
import com.ssjq.english.data.WordEntry
import com.ssjq.english.ui.common.LiquidGlassCard
import com.ssjq.english.ui.common.LiquidGlassDialog
import com.ssjq.english.ui.common.LiquidGlassListItem
import com.ssjq.english.ui.nav.CrazyQuizSource
import com.ssjq.english.quiz.QuizMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 题源标识不含题量题量另外存 requestedCount，换题量时不会误接旧进度 */
private fun crazyQuizSourceKey(source: CrazyQuizSource, dbName: String?): String = when (source) {
    CrazyQuizSource.WRONG -> "crazy|wrong"
    CrazyQuizSource.FAVORITE -> "crazy|favorite"
    CrazyQuizSource.LIBRARY -> "crazy|library|${WordSource.canonicalDb(dbName)}"
}

/** 词库名与单词编号共同标识题源，避免不同词库相同 word_id 互相覆盖 */
private fun wordSourceId(dbName: String, wordId: String): String =
    WordSource.id(dbName, wordId)

/** 读取同一题源、同一题量上次的题序，退出后按原词序恢复 */
private fun savedQuizSourceIds(sourceKey: String, count: Int): List<String> {
    val raw = UserManager.getQuizProgress() ?: return emptyList()
    val obj = try {
        org.json.JSONObject(raw)
    } catch (e: Exception) {
        return emptyList()
    }
    if (obj.optString("dbName") != "crazy_quiz") return emptyList()
    if (obj.optString("sourceKey") != sourceKey) return emptyList()
    if (obj.optInt("requestedCount", 0) != count) return emptyList()
    val array = obj.optJSONArray("sourceIds") ?: return emptyList()
    return (0 until array.length())
        .map { array.optString(it) }
        .filter { it.isNotBlank() }
        .map(WordSource::canonicalSourceId)
}

@Composable
fun CrazyQuizScreen(
    onBack: () -> Unit,
    onStartQuiz: (
        dbName: String,
        sourceWords: List<WordDetail>,
        sourceKey: String,
        count: Int,
        /** 本轮允许出现的题型 label，空 = 全部 */
        modes: List<String>,
    ) -> Unit,
) {
    val context = LocalContext.current
    var selectedSource by remember { mutableStateOf<CrazyQuizSource?>(null) }
    var selectedDb by remember { mutableStateOf<String?>(null) }
    // 当前学习词库与疯狂刷题共用同一个题库来源；旧版本只有 lastStudyDb 时由 UserManager 兼容
    val lastStudyDb = remember { UserManager.getCurrentBook() }
    var allDbs by remember { mutableStateOf<List<String>>(emptyList()) }
    var wrongCount by remember { mutableStateOf(0) }
    var favoriteCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        allDbs = withContext(Dispatchers.IO) { DatabaseManager.listAssetDatabases(context) }
    }

    /** 统计错题/收藏数量：必须在 IO 线程，避免主线程解析大量 JSON 卡顿 */
    suspend fun calculateStats() {
        // 旧实现在主线程对 N 个词库各解析 2 次完整 JSON（共 2N 次），
        // 几十个词库时明显卡顿。改为 IO 线程一次解析出各词库计数。
        // IO 线程只负责取值，状态回到主线程赋值
        val (w, f) = withContext(Dispatchers.IO) {
            UserLibrary.allWrongWords().size to UserLibrary.allFavorites().size
        }
        wrongCount = w
        favoriteCount = f
    }

    LaunchedEffect(allDbs) {
        calculateStats()
    }

    var startQuizTrigger by remember { mutableStateOf<CrazyQuizSource?>(null) }
    // 每次开始疯狂刷题前都先选题量
    var showCountDialog by remember { mutableStateOf(false) }
    var quizCount by remember { mutableStateOf(20) }
    // 疯狂刷题题型可多选：默认全选，允许用户只练某几种题型
    var selectedModes by remember { mutableStateOf(QuizMode.all.toSet()) }
    var savedCounts by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 全应用共享的背景采样层与玻璃配色
    val liquidBackdrop = LocalAppBackdrop.current
    val glassSurface = LocalGlassPalette.current.surfaceTint

    LaunchedEffect(showCountDialog, selectedSource, selectedDb) {
        if (!showCountDialog) return@LaunchedEffect
        val source = selectedSource ?: return@LaunchedEffect
        val key = crazyQuizSourceKey(source, selectedDb)
        savedCounts = withContext(Dispatchers.IO) {
            listOf(10, 20, 30, 50, 100)
                .filter { savedQuizSourceIds(key, it).isNotEmpty() }
                .toSet()
        }
    }

    LaunchedEffect(startQuizTrigger) {
        val source = startQuizTrigger ?: return@LaunchedEffect
        // 整段搬到 IO 线程：旧实现在主线程循环调用 getWordDetail（每条一次线程
        // 切换 + 一次开库 + 6 张关联表查询），100 条就是 100 次往返，错题本/
        // 收藏夹较大时点击「开始刷题」会明显卡住界面
        val key = crazyQuizSourceKey(source, selectedDb)
        val savedIds = withContext(Dispatchers.IO) { savedQuizSourceIds(key, quizCount) }
        val words = withContext(Dispatchers.IO) {
            when (source) {
                CrazyQuizSource.WRONG -> {
                    val all = UserLibrary.allWrongWords()
                    val byId = all.associateBy { wordSourceId(it.dbName, it.wordId) }
                    val byWordId = all.associateBy { it.wordId }
                    if (savedIds.isNotEmpty()) {
                        savedIds.mapNotNull { byId[it] ?: byWordId[it] }.take(quizCount)
                            .mapNotNull { loadEntryDetail(context, it) }
                    } else {
                        all.shuffled().take(quizCount)
                            .mapNotNull { loadEntryDetail(context, it) }
                    }
                }
                CrazyQuizSource.FAVORITE -> {
                    val all = UserLibrary.allFavorites()
                    val byId = all.associateBy { wordSourceId(it.dbName, it.wordId) }
                    val byWordId = all.associateBy { it.wordId }
                    if (savedIds.isNotEmpty()) {
                        savedIds.mapNotNull { byId[it] ?: byWordId[it] }.take(quizCount)
                            .mapNotNull { loadEntryDetail(context, it) }
                    } else {
                        all.shuffled().take(quizCount)
                            .mapNotNull { loadEntryDetail(context, it) }
                    }
                }
                CrazyQuizSource.LIBRARY -> {
                    val raw = selectedDb ?: return@withContext emptyList<WordDetail>()
                    // openDatabase 需要带 .db 后缀的资产文件名，缺失时补上，
                    // 避免 FileNotFoundException
                    val dbName = if (raw.endsWith(".db")) raw else "$raw.db"
                    val canonicalDb = WordSource.canonicalDb(dbName)
                    val db = DatabaseManager.openDatabase(context, dbName)
                    try {
                        if (savedIds.isNotEmpty()) {
                            savedIds.take(quizCount).mapNotNull { saved ->
                                val parsed = WordSource.parseId(saved)
                                when {
                                    parsed != null && parsed.first == canonicalDb ->
                                        DatabaseManager.getWordDetail(db, parsed.second, dbName)
                                    parsed == null ->
                                        DatabaseManager.getWordDetail(db, saved, dbName)
                                    else -> null
                                }
                            }
                        } else {
                            DatabaseManager.getWordList(db)
                                .shuffled()
                                .take(quizCount)
                                .mapNotNull { DatabaseManager.getWordDetail(db, it.wordId, dbName) }
                        }
                    } finally {
                        DatabaseManager.release(db)
                    }
                }
            }
        }
        if (words.isNotEmpty()) {
            onStartQuiz("crazy_quiz", words, key, quizCount, selectedModes.map { it.label })
        }
        startQuizTrigger = null
    }

    fun onSourceSelected(source: CrazyQuizSource) {
        // 先校验数量，再写状态：旧实现先赋值再 return，会在数量为 0 时
        // 把 selectedSource 置为无效值却不做任何事，造成状态不同步
        if (source == CrazyQuizSource.WRONG && wrongCount == 0) {
            return
        }
        if (source == CrazyQuizSource.FAVORITE && favoriteCount == 0) {
            return
        }
        selectedSource = source
        if (source == CrazyQuizSource.LIBRARY) {
            selectedDb = null
        }
        // 题量由用户决定，选定后才真正加载题源
        showCountDialog = true
    }

    fun startSelectedQuiz(count: Int) {
        quizCount = count
        showCountDialog = false
        startQuizTrigger = selectedSource
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.glassTopBar(liquidBackdrop),
                colors = glassTopBarColors(),
                title = { Text("疯狂刷题", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
            )
        },
    ) { padding ->
        if (selectedSource == CrazyQuizSource.LIBRARY && selectedDb == null) {
            SelectDbScreen(
                dbs = allDbs,
                onSelect = { db ->
                    selectedDb = db
                    showCountDialog = true
                },
                onBack = { selectedSource = null },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                item {
                    LiquidGlassCard(
                        backdrop = liquidBackdrop,
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassShapes.Card,
                        blurRadius = 10.dp,
                        lensHeight = 14.dp,
                        lensAmount = 24.dp,
                        surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shadow = glassShadow(20.dp, 0.2f),
                        highlight = Highlight.Default.copy(alpha = 0.8f),
                        innerShadow = glassInnerShadow(8.dp, 0.06f),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(Brush.linearGradient(listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                )))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Star,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "疯狂刷题",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "挑战你的词汇极限，看看你能得多少分！",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                item {
                    Text("选择题源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                // 上次学习的词库快捷入口
                if (lastStudyDb != null) {
                    item {
                        LiquidGlassCard(
                            backdrop = liquidBackdrop,
                            onClick = {
                                selectedDb = lastStudyDb
                                selectedSource = CrazyQuizSource.LIBRARY
                                showCountDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = GlassShapes.Item,
                            blurRadius = 6.dp,
                            lensHeight = 10.dp,
                            lensAmount = 18.dp,
                            surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shadow = glassShadow(14.dp, 0.15f),
                            highlight = Highlight.Default.copy(alpha = 0.6f),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.History, null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "继续学习",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        // 原来直接显示 db 文件名（如 BeiShiGaoZhong_10），改为中文册名
                                        LibraryCatalog.displayName(lastStudyDb),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    LiquidGlassCard(
                        backdrop = liquidBackdrop,
                        onClick = { onSourceSelected(CrazyQuizSource.WRONG) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassShapes.Item,
                        blurRadius = 6.dp,
                        lensHeight = 10.dp,
                        lensAmount = 18.dp,
                        surfaceColor = glassSurface,
                        shadow = glassShadow(14.dp, 0.12f),
                        highlight = Highlight.Default.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.BugReport, null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("错题本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("复习做错的单词，巩固薄弱环节", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (wrongCount == 0) {
                                Text("暂无错题记录", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("$wrongCount 题", style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                item {
                    LiquidGlassCard(
                        backdrop = liquidBackdrop,
                        onClick = { onSourceSelected(CrazyQuizSource.FAVORITE) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassShapes.Item,
                        blurRadius = 6.dp,
                        lensHeight = 10.dp,
                        lensAmount = 18.dp,
                        surfaceColor = glassSurface,
                        shadow = glassShadow(14.dp, 0.12f),
                        highlight = Highlight.Default.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Favorite, null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("收藏夹", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("复习收藏的单词，强化重点词汇", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (favoriteCount == 0) {
                                Text("暂无收藏单词", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("$favoriteCount 题", style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }

                item {
                    LiquidGlassCard(
                        backdrop = liquidBackdrop,
                        onClick = { onSourceSelected(CrazyQuizSource.LIBRARY) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassShapes.Item,
                        blurRadius = 6.dp,
                        lensHeight = 10.dp,
                        lensAmount = 18.dp,
                        surfaceColor = glassSurface,
                        shadow = glassShadow(14.dp, 0.12f),
                        highlight = Highlight.Default.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("词库题目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("从词库中随机抽取题目练习", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("选择词库", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (showCountDialog) {
        val countOptions = listOf(10, 20, 30, 50, 100)
        val palette = LocalGlassPalette.current
        // 面板用高不透明度底色：否则下层的「错题本 / 收藏夹 / 词库题目」卡片会透过玻璃，
        // 和弹窗里的题型、题量看成一整块内容（竖屏下尤其像同一个列表）
        val panelSurface = if (palette.isDark) {
            Color(0xFF17191D).copy(alpha = 0.97f)
        } else {
            Color(0xFFF6F7F9).copy(alpha = 0.97f)
        }
        // 竖屏高度有限：面板最多占屏幕 78%，内部题型区可滚动，按钮固定贴在面板底部
        val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp
        // 弹窗内显式声明本轮题源，和下层题源卡片区分开
        val sourceSummary = when (selectedSource) {
            CrazyQuizSource.WRONG -> "错题本 · 共 $wrongCount 题"
            CrazyQuizSource.FAVORITE -> "收藏夹 · 共 $favoriteCount 题"
            CrazyQuizSource.LIBRARY -> "词库题目 · ${
                selectedDb?.let { LibraryCatalog.displayName(it) } ?: "未选词库"
            }"
            null -> "未选择题源"
        }
        LiquidGlassDialog(
            backdrop = liquidBackdrop,
            onDismissRequest = { showCountDialog = false },
            surfaceColor = panelSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPanelHeight),
            ) {
                // ① 标题 + 题源（只读摘要，不可在弹窗内改动）
                Text("刷题设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.Item)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "题源",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        sourceSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(Modifier.height(12.dp))

                // ② 中部可滚动区：题型 → 题量，两块各自带标题与说明
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "题型",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "可多选 · 已选 ${selectedModes.size} 种，共 ${QuizMode.all.size} 种",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuizMode.all.forEach { mode ->
                            val checked = selectedModes.contains(mode)
                            LiquidGlassListItem(
                                backdrop = liquidBackdrop,
                                onClick = {
                                    // 至少保留一种题型：勾掉的最后一项不允许取消
                                    if (checked && selectedModes.size <= 1) {
                                        Toast.makeText(context, "至少保留一种题型", Toast.LENGTH_SHORT).show()
                                    } else {
                                        selectedModes = if (checked) selectedModes - mode else selectedModes + mode
                                    }
                                },
                                selected = checked,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            mode.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = if (checked) "已选" else "未选",
                                        tint = if (checked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        },
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "题量",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "本轮题目数，点选后按底部按钮开始",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        countOptions.forEach { count ->
                            val isCurrent = count == quizCount
                            val saved = savedCounts.contains(count)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(GlassShapes.Item)
                                        .background(
                                            if (isCurrent) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            } else {
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                                            }
                                        )
                                        .border(
                                            width = if (isCurrent) 2.dp else 1.dp,
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                            },
                                            shape = GlassShapes.Item,
                                        )
                                        .clickable { quizCount = count },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (saved) "可继续" else "题",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (saved) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // ③ 固定底部操作区：始终可见，不随中部内容滚动
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showCountDialog = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                    Button(
                        onClick = { startSelectedQuiz(quizCount) },
                        modifier = Modifier
                            .weight(2f)
                            .height(46.dp),
                        shape = GlassShapes.PanelBlock(),
                    ) {
                        Text(
                            "开始刷题 · $quizCount 题",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SelectDbScreen(
    dbs: List<String>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                Text("选择词库", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        items(dbs) { dbName ->
            Card(
                onClick = { onSelect(dbName) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = GlassShapes.Item,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Bookmark, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(LibraryCatalog.displayName(dbName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 取单词详情（同步，调用方已切到 IO 线程）。
 * 旧实现每次查完都不释放连接，疯狂刷题加载 100 词会堆积 100 个 SQLite 连接。
 */
private fun loadEntryDetail(context: android.content.Context, entry: WordEntry): WordDetail? {
    val dbFileName = if (entry.dbName.endsWith(".db")) entry.dbName else "${entry.dbName}.db"
    val db = DatabaseManager.openDatabase(context, dbFileName)
    return try {
        DatabaseManager.getWordDetail(db, entry.wordId)
            ?.copy(dbName = dbFileName.removeSuffix(".db"))
    } finally {
        DatabaseManager.release(db)
    }
}

fun getScoreGrade(correctCount: Int, totalCount: Int): String {
    if (totalCount == 0) return "F"
    val percent = correctCount.toFloat() / totalCount
    return when {
        percent >= 0.95 -> "S"
        percent >= 0.90 -> "A"
        percent >= 0.80 -> "B"
        percent >= 0.70 -> "C"
        percent >= 0.60 -> "D"
        else -> "F"
    }
}

fun getScoreColor(grade: String): Color {
    return when (grade) {
        "S" -> Color(0xFFD4AF37)
        "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF2196F3)
        "C" -> Color(0xFFFF9800)
        "D" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
}

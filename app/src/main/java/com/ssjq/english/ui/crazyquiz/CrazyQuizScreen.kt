@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.crazyquiz

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.UserManager
import com.ssjq.english.data.WordDetail
import com.ssjq.english.data.WordEntry
import com.ssjq.english.ui.common.LiquidGlassCard
import com.ssjq.english.ui.common.LiquidGlassListItem
import com.ssjq.english.ui.nav.CrazyQuizSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CrazyQuizScreen(
    onBack: () -> Unit,
    onStartQuiz: (dbName: String, sourceWords: List<WordDetail>) -> Unit,
) {
    val context = LocalContext.current
    var selectedSource by remember { mutableStateOf<CrazyQuizSource?>(null) }
    var selectedDb by remember { mutableStateOf<String?>(null) }
    val lastStudyDb = remember { UserManager.getLastStudyDb() }
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
        withContext(Dispatchers.IO) {
            val wMap = UserLibrary.wrongCountByDb()
            val fMap = UserLibrary.favoriteCountByDb()
            wrongCount = allDbs.sumOf { wMap[it.removeSuffix(".db")] ?: 0 }
            favoriteCount = allDbs.sumOf { fMap[it.removeSuffix(".db")] ?: 0 }
        }
    }

    LaunchedEffect(allDbs) {
        calculateStats()
    }

    var startQuizTrigger by remember { mutableStateOf<CrazyQuizSource?>(null) }
    // 液态玻璃背景采样源
    val liquidBackdrop = rememberLayerBackdrop()

    LaunchedEffect(startQuizTrigger) {
        val source = startQuizTrigger ?: return@LaunchedEffect
        // 整段搬到 IO 线程：旧实现在主线程循环调用 getWordDetail（每条一次线程
        // 切换 + 一次开库 + 6 张关联表查询），100 条就是 100 次往返，错题本/
        // 收藏夹较大时点击「开始刷题」会明显卡住界面
        val words = withContext(Dispatchers.IO) {
            when (source) {
                CrazyQuizSource.WRONG -> {
                    UserLibrary.allWrongWords()
                        .shuffled()
                        .take(100)
                        .mapNotNull { loadEntryDetail(context, it) }
                }
                CrazyQuizSource.FAVORITE -> {
                    UserLibrary.allFavorites()
                        .shuffled()
                        .take(100)
                        .mapNotNull { loadEntryDetail(context, it) }
                }
                CrazyQuizSource.LIBRARY -> {
                    val raw = selectedDb ?: return@withContext emptyList<WordDetail>()
                    // openDatabase 需要带 .db 后缀的资产文件名，缺失时补上，
                    // 避免 FileNotFoundException
                    val dbName = if (raw.endsWith(".db")) raw else "$raw.db"
                    try {
                        val db = DatabaseManager.openDatabase(context, dbName)
                        try {
                            DatabaseManager.getWordList(db)
                                .shuffled()
                                .take(100)
                                .mapNotNull { DatabaseManager.getWordDetail(db, it.wordId) }
                        } finally {
                            DatabaseManager.release(db)
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
        }
        if (words.isNotEmpty()) {
            onStartQuiz("crazy_quiz", words)
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
        } else {
            startQuizTrigger = source
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层：渐变 + 彩色光斑
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(liquidBackdrop),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
            Box(
                Modifier
                    .size(180.dp)
                    .offset(x = (-40).dp, y = 100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            )
            Box(
                Modifier
                    .size(140.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = 200.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
            )
            Box(
                Modifier
                    .size(120.dp)
                    .offset(x = 30.dp, y = 450.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
            )
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
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
                    startQuizTrigger = CrazyQuizSource.LIBRARY
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
                        shape = RoundedCornerShape(24.dp),
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
                                startQuizTrigger = CrazyQuizSource.LIBRARY
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
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
                                        lastStudyDb.removeSuffix(".db"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
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
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 6.dp,
                        lensHeight = 10.dp,
                        lensAmount = 18.dp,
                        surfaceColor = Color.White.copy(alpha = 0.18f),
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Close, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("暂无错题", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 6.dp,
                        lensHeight = 10.dp,
                        lensAmount = 18.dp,
                        surfaceColor = Color.White.copy(alpha = 0.18f),
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Close, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("暂无收藏", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 6.dp,
                        lensHeight = 10.dp,
                        lensAmount = 18.dp,
                        surfaceColor = Color.White.copy(alpha = 0.18f),
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
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
                    Text(dbName.removeSuffix(".db"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    return try {
        val db = DatabaseManager.openDatabase(context, entry.dbName + ".db")
        try {
            DatabaseManager.getWordDetail(db, entry.wordId)
        } finally {
            DatabaseManager.release(db)
        }
    } catch (_: Exception) {
        null
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

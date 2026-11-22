@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.worddetail

import android.widget.Toast
import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ssjq.english.R
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.WordDetail
import com.ssjq.english.data.WordEntry
import com.ssjq.english.ui.common.LiquidGlassBottomBar
import com.ssjq.english.ui.common.LiquidGlassCard
import com.ssjq.english.ui.common.LiquidGlassDialog
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.ReviewScheduler
import com.ssjq.english.ui.common.LiquidGlassListItem
import com.ssjq.english.ui.glass.glassTopBar
import com.ssjq.english.ui.glass.glassTopBarColors
import com.ssjq.english.ui.common.ShimmerBox
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ssjq.english.ui.glass.GlassShapes
import com.ssjq.english.ui.glass.LocalAppBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.service.PronunciationPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WordSearchResult(val dbName: String, val word: WordDetail)

/** 查词结果上限：覆盖多本词库，同时避免一次点击把全部词库都扫一遍 */
private const val MAX_SEARCH_RESULTS = 20

/** 单次查词最多扫描的词库数：每本词库首次打开都要整库复制，扫全部 81 本会长时间无反馈 */
private const val MAX_SCAN_DBS = 8

/** 其中允许触发整库复制的词库数。未使用过的词库单本可达数十 MB，必须严格限量 */
private const val MAX_UNCACHED_SCAN = 2

/** 查词跳转的返回层级上限，避免长会话里无限累积 */
private const val MAX_WORD_HISTORY = 20

/** 查词结果行：单词、释义与所属词库；当前词库用主色标签区分 */
@Composable
private fun SearchResultRow(
    backdrop: Backdrop,
    result: WordSearchResult,
    currentDb: String,
    onClick: () -> Unit,
) {
    val word = result.word
    val isCurrent = result.dbName == currentDb
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    word.headWord,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                word.trans.firstOrNull()?.let {
                    Text(
                        listOfNotNull(it.pos, it.tranCn).joinToString("  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = GlassShapes.Tag,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ) {
                Text(
                    LibraryCatalog.displayName(result.dbName),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun WordDetailScreen(
    dbName: String,
    wordId: String,
    onBack: () -> Unit,
    wordQueue: List<String>? = null,
    startIndex: Int = 0,
    onNavigateToWord: (dbName: String, wordId: String) -> Unit = { _, _ -> },
    /** 是否记录背诵进度。复习队列与随手看词不应覆盖正式进度 */
    trackProgress: Boolean = false,
) {
    val context = LocalContext.current
    val reviewScope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<WordDetail?>(null) }
    var loading by remember { mutableStateOf(true) }

    // 背诵会话模式：wordQueue 非空时，按队列顺序遍历单词
    val isStudyMode = wordQueue != null
    // 非背诵模式（从列表进入详情）时，加载整本词库作为滑动队列，同样支持左右滑动浏览
    var browseQueue by remember(dbName) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(dbName) {
        if (wordQueue == null) {
            browseQueue = withContext(Dispatchers.IO) {
                val db = DatabaseManager.openDatabase(context, dbName)
                try {
                    DatabaseManager.getWordList(db).map { it.wordId }
                } finally {
                    DatabaseManager.release(db)
                }
            }
        }
    }
    // 实际用于滑动的队列：背诵模式用传入的队列，否则用整本词库
    val queue = wordQueue ?: browseQueue
    val canSwipe = queue != null && queue.size > 1
    val lastIndex = (queue?.size?.minus(1))?.coerceAtLeast(0) ?: 0
    var currentIndex by remember(queue) {
        mutableStateOf(
            if (isStudyMode) startIndex.coerceIn(0, lastIndex)
            else (queue?.indexOf(wordId)?.takeIf { it >= 0 } ?: 0).coerceIn(0, lastIndex)
        )
    }
    // 查词跳转过的单词位置。返回键先在这里逐级回退，退完才退出背诵页
    val wordHistory = remember(queue) { mutableStateListOf<Int>() }
    BackHandler(enabled = wordHistory.isNotEmpty()) {
        currentIndex = wordHistory.removeAt(wordHistory.lastIndex)
    }
    val currentWordId = queue?.getOrNull(currentIndex) ?: wordId
    // 翻面状态随单词重置：评分直接前进后，下一张卡片必须回到未翻面状态
    var revealed by remember(currentWordId) { mutableStateOf(false) }
    // 普通背词模式的自动发音，与测验共用同一用户偏好
    var autoSpeak by remember { mutableStateOf(UserLibrary.autoPlayAudioEnabled()) }
    // 同一单词只计入一次学习量，反复翻面不重复累加
    var learnedMarked by remember(currentWordId) { mutableStateOf(false) }
    // 点「模糊」后先翻面，等用户在认识/不认识之间做最终判断
    var fuzzyPending by remember(currentIndex) { mutableStateOf(false) }
    val total = queue?.size ?: 1
    // 只有正式背诵会话才写进度：复习队列与"只看一眼"的详情页下标语义不同，
    // 无条件写入会把正常背诵进度挪到无关位置
    LaunchedEffect(currentIndex, isStudyMode, trackProgress) {
        if (isStudyMode && trackProgress) {
            withContext(Dispatchers.IO) { UserLibrary.saveStudyIndex(dbName, currentIndex) }
        }
    }

    // 学习时长追踪：进入页面开始计时，离开时按分钟累加到打卡记录
    val sessionStart = remember { System.currentTimeMillis() }
    DisposableEffect(Unit) {
        onDispose {
            val minutes = ((System.currentTimeMillis() - sessionStart) / 60000L).toInt().coerceAtLeast(0)
            if (minutes > 0) {
                reviewScope.launch(Dispatchers.IO) {
                    CheckInManager.accumulate(addMinutes = minutes)
                }
            }
        }
    }

    // 收藏/错题状态：随单词切换重新读取。
    // 放到 IO 线程读取，避免组合期同步解析完整 JSON（每翻一个词一次）阻塞主线程
    var isFavorite by remember(currentWordId) { mutableStateOf(false) }
    var isWrong by remember(currentWordId) { mutableStateOf(false) }
    LaunchedEffect(currentWordId) {
        val (fav, wr) = withContext(Dispatchers.IO) {
            UserLibrary.isFavorite(dbName, currentWordId) to
                UserLibrary.isWrong(dbName, currentWordId)
        }
        isFavorite = fav
        isWrong = wr
    }
    // 全应用共享的背景采样层
    val liquidBackdrop = LocalAppBackdrop.current

    // 搜索弹窗
    var showWordSearch by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<WordSearchResult>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchDone by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // 手势交互状态
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var showExamplePanel by remember { mutableStateOf(false) }
    var showSpellPanel by remember { mutableStateOf(false) }
    var spellInput by remember { mutableStateOf("") }
    var spellResult by remember { mutableStateOf<Boolean?>(null) }

    val panelScrollConnection = remember(canSwipe, revealed) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (canSwipe && revealed && source == NestedScrollSource.UserInput) {
                    val delta = consumed.y + available.y
                    if (delta < -24f) {
                        showSpellPanel = false
                        showExamplePanel = true
                    } else if (delta > 24f) {
                        showExamplePanel = false
                        showSpellPanel = true
                        spellInput = ""
                        spellResult = null
                    }
                }
                return Offset.Zero
            }
        }
    }

    // 获取所有词库列表
    var allDbs by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        allDbs = withContext(Dispatchers.IO) { DatabaseManager.listAssetDatabases(context) }
    }

    /** 在指定词库中查找单词：先轻量探测命中词，只为命中词取完整词条 */
    suspend fun findWordInDb(name: String, query: String): WordDetail? =
        withContext(Dispatchers.IO) {
            val db = DatabaseManager.openDatabase(context, name)
            try {
                val hit = DatabaseManager.findHeadWordId(db, query) ?: return@withContext null
                // 探测到词头却取不到词条说明该库数据不完整，跳过这本词库继续找，
                // 不在此处抛异常：协程里的异常会让弹窗永远停在加载态
                DatabaseManager.getWordDetail(db, hit.first, name)
            } finally {
                DatabaseManager.release(db)
            }
        }

    /** 学习量与掌握量都要改写整段打卡 JSON，统一放到 IO 线程 */
    fun markLearnedOnce() {
        reviewScope.launch {
            withContext(Dispatchers.IO) {
                CheckInManager.accumulate(addWordsLearned = 1)
            }
        }
    }

    fun markMasteredOnce() {
        reviewScope.launch {
            withContext(Dispatchers.IO) {
                CheckInManager.accumulate(addWordsMastered = 1)
            }
        }
    }

    fun closeWordSearch() {
        showWordSearch = false
        searchResults = emptyList()
        searchDone = false
        searchError = null
        searchLoading = false
    }

    fun openWordSearch(word: String) {
        showExamplePanel = false
        showSpellPanel = false
        searchKeyword = word
        // 清掉上一轮结果，避免新一次查找先闪一下旧内容
        searchResults = emptyList()
        searchDone = false
        searchError = null
        searchLoading = true
        showWordSearch = true
    }

    LaunchedEffect(showWordSearch, searchKeyword) {
        if (!showWordSearch || searchKeyword.isEmpty()) return@LaunchedEffect
        val query = searchKeyword.trim()
        if (query.isEmpty()) {
            searchLoading = false
            searchDone = true
            return@LaunchedEffect
        }
        try {
            val currentName = dbName.removeSuffix(".db")
            // 第一层：当前词库命中就直接进入，不再弹窗
            val currentHit = findWordInDb(currentName, query)
            if (currentHit != null) {
                showWordSearch = false
                searchLoading = false
                val idx = wordQueue?.indexOf(currentHit.wordId) ?: -1
                if (idx >= 0) {
                    if (idx == currentIndex) {
                        Toast.makeText(context, "当前正在学习该单词", Toast.LENGTH_SHORT).show()
                    } else {
                        wordHistory.add(currentIndex)
                        if (wordHistory.size > MAX_WORD_HISTORY) wordHistory.removeAt(0)
                        currentIndex = idx
                    }
                } else {
                    onNavigateToWord(currentName, currentHit.wordId)
                }
                return@LaunchedEffect
            }
            // allDbs 由另一个 LaunchedEffect 异步加载，启动后立刻查词时可能仍是空
            val available = if (allDbs.isEmpty()) {
                withContext(Dispatchers.IO) { DatabaseManager.listAssetDatabases(context) }
            } else {
                allDbs
            }
            // 后续层：同子分类 → 同分类 → 其他词库，逐层扩大范围。
            // 已复制的词库排在前面，并限制总扫描数以控制首次复制开销。
            val baseOrder = LibraryCatalog.searchOrder(
                currentName,
                available.map { it.removeSuffix(".db") },
            )
            val order = withContext(Dispatchers.IO) {
                val (copied, uncached) = baseOrder.partition { DatabaseManager.isCopied(context, it) }
                (copied + uncached.take(MAX_UNCACHED_SCAN)).take(MAX_SCAN_DBS)
            }
            val found = mutableListOf<WordSearchResult>()
            for (name in order) {
                if (name == currentName) continue
                if (found.size >= MAX_SEARCH_RESULTS) break
                val hit = findWordInDb(name, query)
                if (hit != null) found.add(WordSearchResult(name, hit))
            }
            searchResults = found
            searchDone = true
            searchLoading = false
        } catch (e: Exception) {
            // 词库损坏或 assets 缺失时给出明确提示，弹窗不能一直停在转圈状态
            searchResults = emptyList()
            searchError = e.message ?: "查找失败"
            searchDone = true
            searchLoading = false
        }
    }

    fun toggleFavorite() {
        val word = detail ?: return
        val current = isFavorite
        val entry = WordEntry(
            wordId = word.wordId,
            headWord = word.headWord,
            dbName = dbName,
            tranCn = word.trans.firstOrNull()?.tranCn,
        )
        reviewScope.launch {
            val next = withContext(Dispatchers.IO) {
                if (current) {
                    UserLibrary.removeFavorite(dbName, word.wordId)
                    false
                } else {
                    UserLibrary.addFavorite(entry)
                    true
                }
            }
            isFavorite = next
        }
    }

    fun markWrong() {
        val word = detail ?: return
        if (!isWrong) {
            val currentFavorite = isFavorite
            val entry = WordEntry(
                wordId = word.wordId,
                headWord = word.headWord,
                dbName = dbName,
                tranCn = word.trans.firstOrNull()?.tranCn,
            )
            reviewScope.launch {
                val (wrong, favorite) = withContext(Dispatchers.IO) {
                    UserLibrary.addWrong(entry)
                    val autoFavorite = UserLibrary.autoFavoriteEnabled(dbName)
                    if (autoFavorite && !currentFavorite) UserLibrary.addFavorite(entry)
                    true to (currentFavorite || autoFavorite)
                }
                isWrong = wrong
                isFavorite = favorite
            }
        }
    }

    fun markKnown() {
        // 「认识」后从错题本移除
        if (isWrong) {
            reviewScope.launch(Dispatchers.IO) {
                UserLibrary.removeWrong(dbName, currentWordId)
                withContext(Dispatchers.Main) { isWrong = false }
            }
        }
    }

    // 有道词典发音 API：https://dict.youdao.com/dictvoice?audio={word}&type={type}
    // type=1 英音，type=2 美音。
    //
    val pronunciationPlayer = remember { PronunciationPlayer() }
    DisposableEffect(Unit) {
        onDispose { pronunciationPlayer.release() }
    }
    val speak: (String, Int) -> Unit = { word, type ->
        pronunciationPlayer.speak(word, type)
    }
    // 普通模式自动发音：切到新词时播放，开关状态与测验共用。
    // detail 参与 key：首次进入时它还在加载，不带上就永远不会触发第一次发音
    LaunchedEffect(currentWordId, detail?.wordId, autoSpeak) {
        if (autoSpeak) detail?.headWord?.let { speak(it, 2) }
    }

    LaunchedEffect(dbName, currentWordId) {
        loading = true
        revealed = false
        // 加载期间必须清空旧词：否则收藏/标记会作用在上一个单词上
        detail = null
        detail = withContext(Dispatchers.IO) {
            val db = DatabaseManager.openDatabase(context, dbName)
            try {
                DatabaseManager.getWordDetail(db, currentWordId, dbName)
            } finally {
                DatabaseManager.release(db)
            }
        }
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.glassTopBar(liquidBackdrop),
                colors = glassTopBarColors(),
                title = {
                    Column {
                        Text(detail?.headWord ?: "",
                            fontWeight = FontWeight.SemiBold)
                        if (isStudyMode) {
                            Text(
                                "进度 ${currentIndex + 1} / $total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            autoSpeak = !autoSpeak
                            UserLibrary.setAutoPlayAudio(autoSpeak)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = if (autoSpeak) "关闭自动发音" else "开启自动发音",
                            tint = if (autoSpeak) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { toggleFavorite() }) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                },
            )
        }
    ) { padding ->
        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                MaterialTheme.colorScheme.background,
            )
        )
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            // 词条不存在时（复习队列残留、词库换版本后 word_id 变更）给出可返回的提示，
            // 否则整页只有顶栏与评分按钮，点什么都没反应
            if (!loading && detail == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "该单词在当前词库中不存在",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                        Text("返回")
                    }
                }
            }
            if (loading) {
                // 骨架屏：模拟 Hero 区 + 释义区布局，比转圈更有"内容正在浮现"感
                DetailSkeleton()
            } else if (detail != null) {
                val word = detail!!
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                        .pointerInput(canSwipe) {
                            if (!canSwipe) return@pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    // 每次拖拽从零起算；否则会叠加在上一轮的残留偏移上
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset = Offset(
                                        x = (dragOffset.x + amount.x).coerceIn(-360f, 360f),
                                        // 允许负向位移，上滑才能触发例句面板
                                        y = (dragOffset.y + amount.y).coerceIn(-360f, 360f),
                                    )
                                },
                                onDragEnd = {
                                    val dx = dragOffset.x
                                    val dy = dragOffset.y
                                    when {
                                        // 左右滑动：切换到上一个 / 下一个单词
                                        dx < -140 -> {
                                            if (currentIndex < total - 1) {
                                                currentIndex++
                                                revealed = false
                                            } else if (isStudyMode) {
                                                // 背诵模式滑到最后一个，结束本次背诵
                                                onBack()
                                            }
                                        }
                                        dx > 140 -> {
                                            if (currentIndex > 0) {
                                                currentIndex--
                                                revealed = false
                                            }
                                        }
                                        // 上下滑动：辅助面板（需先翻面看释义）
                                        dy < -160 && revealed -> {
                                            closeWordSearch()
                                            showSpellPanel = false
                                            showExamplePanel = true
                                        }
                                        dy > 160 && revealed -> {
                                            closeWordSearch()
                                            showExamplePanel = false
                                            showSpellPanel = true
                                            spellInput = ""
                                            spellResult = null
                                        }
                                    }
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    // 手势被取消时不会走 onDragEnd：
                                    // ① 卡片背面的纵向滚动抢走手势；
                                    // ② canSwipe 翻转导致 pointerInput 重启。
                                    // 缺失此回调会让卡片永久停在偏移位置（最多 ±360px）。
                                    dragOffset = Offset.Zero
                                },
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 学习卡片：三层视觉层次 + 3D 翻转动画
                    val flipRotation by animateFloatAsState(
                        targetValue = if (revealed) 180f else 0f,
                        animationSpec = tween(450),
                        label = "flipRot",
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .offset { IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
                            .graphicsLayer {
                                rotationY = flipRotation
                                rotationZ = dragOffset.x * 0.03f
                                cameraDistance = 12 * density
                                alpha = 0.96f
                            },
                        shape = GlassShapes.Panel,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        ),
                        onClick = {
                            // 卡片翻面与评分按钮翻面走同一套计数，避免学习量漏记
                            if (revealed) {
                                revealed = false
                            } else {
                                revealed = true
                                if (!learnedMarked) {
                                    learnedMarked = true
                                    markLearnedOnce()
                                }
                            }
                        },
                    ) {
                        AnimatedContent(
                            targetState = revealed,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "flip",
                            modifier = Modifier.graphicsLayer {
                                // 卡片旋转过半后，内容反向旋转 180° 抵消镜像
                                rotationY = if (flipRotation > 90f) 180f else 0f
                            },
                        ) { show ->
                            if (!show) {
                                // 正面：克制排版，字号层级清楚
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        word.headWord,
                                        style = MaterialTheme.typography.displayLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 音标：小一号、克制
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        word.ukPhone?.takeIf { it.isNotBlank() }?.let {
                                            Text("英 /$it/", style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        word.usPhone?.takeIf { it.isNotBlank() }?.let {
                                            Text("美 /$it/", style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                    // 考频星级 + 发音，合并为一行，更紧凑
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (word.star > 0) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                repeat(word.star.coerceAtMost(5)) {
                                                    Icon(Icons.Filled.Star, null,
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            CompactPronounceButton(label = "英", onClick = { speak(word.headWord, 1) })
                                            CompactPronounceButton(label = "美", onClick = { speak(word.headWord, 2) })
                                            IconButton(
                                                onClick = {
                                                    autoSpeak = !autoSpeak
                                                    UserLibrary.setAutoPlayAudio(autoSpeak)
                                                },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Headphones,
                                                    contentDescription = if (autoSpeak) "关闭自动发音" else "开启自动发音",
                                                    tint = if (autoSpeak) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(32.dp))
                                    // 呼吸式引导提示
                                    val breath = rememberInfiniteTransition(label = "breath")
                                    val breathAlpha by breath.animateFloat(
                                        initialValue = 0.35f,
                                        targetValue = 0.85f,
                                        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                                        label = "a",
                                    )
                                    Text(
                                        "轻触查看释义",
                                        modifier = Modifier.alpha(breathAlpha),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                // 背面：完整释义 / 短语 / 例句 / 记忆法 / 同反义词 / 派生词
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(20.dp)
                                        .nestedScroll(panelScrollConnection)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    // 标题行：单词 + 难度星级
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(word.headWord, style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        if (word.star > 0) {
                                            Spacer(Modifier.width(8.dp))
                                            repeat(word.star) {
                                                Icon(Icons.Filled.Star, null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    // 释义：第一条加粗（考义），其余正常
                                    word.trans.forEachIndexed { idx, t ->
                                        Text(
                                            listOfNotNull(t.pos, t.tranCn).joinToString("  "),
                                            style = if (idx == 0) MaterialTheme.typography.titleMedium
                                                else MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    // 短语：液态玻璃小卡片
                                    if (word.phrases.isNotEmpty()) {
                                        SectionHeader("短语")
                                        word.phrases.forEach {
                                            Spacer(Modifier.height(8.dp))
                                            GlassMiniCard(backdrop = liquidBackdrop) {
                                                ClickableWordText(it.content ?: "", onWordClick = { openWordSearch(it) })
                                                Text(it.cn ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    // 例句：液态玻璃小卡片
                                    if (word.sentences.isNotEmpty()) {
                                        SectionHeader("例句")
                                        word.sentences.forEach { s ->
                                            Spacer(Modifier.height(8.dp))
                                            GlassMiniCard(backdrop = liquidBackdrop) {
                                                val enText = s.content ?: ""
                                                val cnText = s.cn ?: ""
                                                ClickableWordText(enText, onWordClick = { openWordSearch(it) })
                                                if (cnText.isNotBlank()) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(cnText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                    // 记忆法
                                    word.remMethod?.takeIf { it.isNotBlank() }?.let {
                                        SectionHeader("记忆法")
                                        Text(it, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    // 同义词
                                    if (word.synos.isNotEmpty()) {
                                        SectionHeader("同义词")
                                        word.synos.forEach { s ->
                                            Text(
                                                listOfNotNull(s.pos, s.tran).joinToString("  "),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    // 反义词
                                    if (word.antos.isNotEmpty()) {
                                        SectionHeader("反义词")
                                        Text(word.antos.filter { it.isNotBlank() }.joinToString("、"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    // 派生相关词
                                    if (word.relWords.isNotEmpty()) {
                                        SectionHeader("派生词")
                                        word.relWords.forEach { r ->
                                            Text(
                                                listOfNotNull(r.hwd, r.pos, r.tran).joinToString("  "),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 只有正式背诵会话才显示评分按钮；词库搜索跳转只是看词条
                    if (isStudyMode) {
                        val onGrade: (grade: Int) -> Unit = { grade ->
                            // grade: 0=不认识, 2=认识。模糊不再作为最终评分，只用于进入翻面判定。
                            if (!learnedMarked) {
                                learnedMarked = true
                                markLearnedOnce()
                            }
                            if (grade == 0) markWrong()
                            if (grade == 2) {
                                markKnown()
                                markMasteredOnce()
                            }
                            // 记录复习结果并安排下一次到期日；读写独立 SP，放到 IO 线程
                            reviewScope.launch {
                                withContext(Dispatchers.IO) {
                                    ReviewScheduler.record(dbName, currentWordId, grade)
                                }
                            }
                            if (currentIndex < total - 1) {
                                currentIndex++
                            } else {
                                Toast.makeText(
                                    context,
                                    "本轮背诵完成",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onBack()
                            }
                        }
                        // 底部悬浮操作栏：液态玻璃底板 + 按钮色彩层级化
                        LiquidGlassBottomBar(
                            backdrop = liquidBackdrop,
                            shape = GlassShapes.Panel,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onGrade(0) },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = GlassShapes.Item,
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp, MaterialTheme.colorScheme.error,
                                    ),
                                ) {
                                    Text(
                                        if (isWrong) "不认识 ✓" else if (fuzzyPending) "还不认识" else "不认识",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                // 模糊只负责翻面，随后让用户明确判断认识还是不认识
                                if (!fuzzyPending) {
                                    androidx.compose.material3.FilledTonalButton(
                                        onClick = {
                                            revealed = true
                                            fuzzyPending = true
                                            if (!learnedMarked) {
                                                learnedMarked = true
                                                markLearnedOnce()
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(52.dp),
                                        shape = GlassShapes.Item,
                                    ) {
                                        Text("模糊", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                                Button(
                                    onClick = { onGrade(2) },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = GlassShapes.Item,
                                ) {
                                    Text(
                                        if (fuzzyPending) "认识了" else "认识",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWordSearch) {
        LiquidGlassDialog(
            backdrop = liquidBackdrop,
            onDismissRequest = { closeWordSearch() },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (searchDone && searchResults.isNotEmpty()) {
                        "查找 \"$searchKeyword\"（${searchResults.size}）"
                    } else {
                        "查找 \"$searchKeyword\""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { closeWordSearch() }) {
                    Icon(Icons.Filled.Close, "关闭")
                }
            }
            Spacer(Modifier.height(12.dp))

            if (searchLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "正在查找可用的词库…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = searchResults,
                        key = { "${it.dbName}-${it.word.wordId}" },
                    ) { result ->
                        SearchResultRow(
                            backdrop = liquidBackdrop,
                            result = result,
                            currentDb = dbName,
                            onClick = {
                                closeWordSearch()
                                if (result.dbName == dbName && wordQueue != null) {
                                    val idx = wordQueue.indexOf(result.word.wordId)
                                    if (idx >= 0) {
                                        wordHistory.add(currentIndex)
                                        if (wordHistory.size > MAX_WORD_HISTORY) {
                                            wordHistory.removeAt(0)
                                        }
                                        currentIndex = idx
                                    } else {
                                        onNavigateToWord(dbName, result.word.wordId)
                                    }
                                } else {
                                    onNavigateToWord(result.dbName, result.word.wordId)
                                }
                            },
                        )
                    }
                }
            } else if (searchDone) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.SearchOff,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        searchError?.let { "查找失败：$it" }
                            ?: "未找到单词 \"$searchKeyword\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 例句面板：上滑触发
    if (showExamplePanel && detail != null) {
        val word = detail!!
        LiquidGlassDialog(
            backdrop = liquidBackdrop,
            onDismissRequest = { showExamplePanel = false },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "${word.headWord} 的例句",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showExamplePanel = false }) {
                    Icon(Icons.Filled.Close, "关闭")
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                word.sentences.forEach { s ->
                    Spacer(Modifier.height(8.dp))
                    GlassMiniCard(backdrop = liquidBackdrop) {
                        ClickableWordText(s.content ?: "", onWordClick = { openWordSearch(it) })
                        if (!s.cn.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(s.cn, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // 拼写面板：下滑触发
    if (showSpellPanel && detail != null) {
        val word = detail!!
        LiquidGlassDialog(
            backdrop = liquidBackdrop,
            onDismissRequest = { showSpellPanel = false },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "拼写 ${word.headWord}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showSpellPanel = false }) {
                    Icon(Icons.Filled.Close, "关闭")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                word.trans.firstOrNull()?.tranCn ?: "请根据释义拼写单词",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = spellInput,
                onValueChange = { spellInput = it; spellResult = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入单词") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    spellResult = spellInput.trim().equals(word.headWord, ignoreCase = true)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("提交")
            }
            if (spellResult != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (spellResult == true) "✓ 正确" else "✗ 正确答案是 ${word.headWord}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (spellResult == true) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    }
}

/** 背面分节标题：统一主色 + 半粗体 + 上边距 */
@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary)
}

/**
 * 圆形发音按钮：主色背景 + 按压 scale 动画
 */
@Composable
private fun PronounceButton(
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(120),
        label = "pronounceScale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.phonograph),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 详情页骨架屏：模拟 Hero 区 + 释义区布局
 */
@Composable
private fun DetailSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = GlassShapes.Card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .width(200.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(16.dp))
                ShimmerBox(
                    modifier = Modifier
                        .width(160.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShimmerBox(modifier = Modifier.size(56.dp).clip(CircleShape))
                    ShimmerBox(modifier = Modifier.size(56.dp).clip(CircleShape))
                }
            }
        }
        // 底部按钮骨架
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBox(modifier = Modifier.weight(1f).height(52.dp).clip(GlassShapes.Item))
            ShimmerBox(modifier = Modifier.weight(1f).height(52.dp).clip(GlassShapes.Item))
            ShimmerBox(modifier = Modifier.weight(1f).height(52.dp).clip(GlassShapes.Item))
        }
    }
}

/**
 * 背诵会话包装器：从数据库加载全部 wordId，再以队列模式
 * 委托给 [WordDetailScreen] 顺序背诵。点击「开始背诵」即进入。
 */
@Composable
fun WordStudyScreen(
    dbName: String,
    startWordId: String? = null,
    reviewQueue: List<String> = emptyList(),
    onBack: () -> Unit,
    onNavigateToWord: (dbName: String, wordId: String) -> Unit,
) {
    val context = LocalContext.current
    var wordIds by remember { mutableStateOf<List<String>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    // 队列内容参与 key：两次复习数量相同但到期单词不同时也要重新加载
    LaunchedEffect(dbName, reviewQueue) {
        wordIds = null
        loadFailed = false
        val ids = if (reviewQueue.isNotEmpty()) {
            reviewQueue
        } else {
            try {
                withContext(Dispatchers.IO) {
                    val db = DatabaseManager.openDatabase(context, dbName)
                    try {
                        DatabaseManager.getWordList(db).map { it.wordId }
                    } finally {
                        DatabaseManager.release(db)
                    }
                }
            } catch (e: Exception) {
                // 词库文件缺失或损坏：走失败分支给出可返回的提示，不让异常打断协程
                loadFailed = true
                return@LaunchedEffect
            }
        }
        if (ids.isEmpty()) loadFailed = true else wordIds = ids
    }

    /** 上次背到末尾时回到开头，避免每次进入都停在最后一个单词 */
    fun savedStartIndex(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        val saved = UserLibrary.studyIndex(dbName)
        return if (saved >= ids.lastIndex) 0 else saved.coerceIn(0, ids.lastIndex)
    }

    when {
        wordIds != null -> {
            val ids = checkNotNull(wordIds) { "词库列表尚未加载完成" }
            // 若指定了起始单词，则从该单词开始；否则读取上次进度。
            // 起始下标必须夹到合法范围：
            // ① 跨词库跳转时 indexOf 返回 -1；
            // ② 回退到的 studyIndex 可能已超出当前词库长度（词库变小 / 切换过词库）。
            // 任何一种都会让下面的 ids[startIdx] 直接越界崩溃。
            val startIdx = if (reviewQueue.isNotEmpty()) {
                0
            } else if (startWordId != null) {
                val found = ids.indexOf(startWordId)
                if (found >= 0) found else savedStartIndex(ids)
            } else {
                savedStartIndex(ids)
            }
            WordDetailScreen(
                dbName = dbName,
                wordId = ids[startIdx],
                onBack = onBack,
                wordQueue = ids,
                startIndex = startIdx,
                onNavigateToWord = onNavigateToWord,
                // 复习队列的下标与整本背诵进度无关，不能写回去
                trackProgress = reviewQueue.isEmpty(),
            )
        }
        loadFailed -> Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "词库为空或加载失败",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("返回")
                }
            }
        }
        else -> DetailSkeleton()
    }
}

/**
 * 液态玻璃小卡片：用于短语、例句等辅助信息。
 */
@Composable
private fun GlassMiniCard(
    backdrop: Backdrop,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // 短语与例句数量随词条增长，使用静态磨砂避免每项创建多套离屏缓冲
    LiquidGlassListItem(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            content = content,
        )
    }
}

/**
 * 紧凑发音按钮：正面使用，不抢单词风头。
 */
@Composable
private fun CompactPronounceButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(120),
        label = "compactPronounceScale",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 将文本中的英文单词转换为可点击的链接
 */
@Composable
private fun ClickableWordText(
    text: String,
    onWordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wordPattern = remember { Regex("[a-zA-Z]+") }
    val matches = remember(text, wordPattern) { wordPattern.findAll(text).toList() }
    
    if (matches.isEmpty()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        for (match in matches) {
            if (match.range.start > lastIndex) {
                append(text.substring(lastIndex, match.range.start))
            }
            val word = match.value
            addStringAnnotation(
                tag = "WORD",
                annotation = word,
                start = length,
                end = length + word.length,
            )
            append(word)
            lastIndex = match.range.endInclusive + 1
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }

    val textLayoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    androidx.compose.foundation.text.BasicText(
        text = annotatedString,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offsetPosition ->
                val layoutResult = textLayoutResult.value ?: return@detectTapGestures
                val offset = layoutResult.getOffsetForPosition(offsetPosition)
                val words = annotatedString.getStringAnnotations("WORD", 0, annotatedString.length)
                words.firstOrNull { offset in it.start..it.end }
                    ?: words.minByOrNull {
                        minOf(kotlin.math.abs(offset - it.start), kotlin.math.abs(offset - it.end))
                    }
                    ?.let { onWordClick(it.item) }
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { textLayoutResult.value = it },
    )
}

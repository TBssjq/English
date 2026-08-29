@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.worddetail

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

import android.media.AudioAttributes
import android.media.MediaPlayer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.ssjq.english.ui.common.LiquidGlassCard
import com.ssjq.english.ui.common.ShimmerBox
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class WordSearchResult(val dbName: String, val word: WordDetail)

@Composable
fun WordDetailScreen(
    dbName: String,
    wordId: String,
    onBack: () -> Unit,
    wordQueue: List<String>? = null,
    startIndex: Int = 0,
    onNavigateToWord: (dbName: String, wordId: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<WordDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var revealed by remember { mutableStateOf(false) }

    // 背诵会话模式：wordQueue 非空时，按队列顺序遍历单词
    val isStudyMode = wordQueue != null
    // 非背诵模式（从列表进入详情）时，加载整本词库作为滑动队列，同样支持左右滑动浏览
    var browseQueue by remember(dbName) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(dbName) {
        if (wordQueue == null) {
            browseQueue = withContext(Dispatchers.IO) {
                val db = DatabaseManager.openDatabase(context, dbName)
                DatabaseManager.getWordList(db).map { it.wordId }
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
    val currentWordId = queue?.getOrNull(currentIndex) ?: wordId
    val total = queue?.size ?: 1
    // 进入背诵模式时立即把当前进度持久化（下一次继续从这里）
    LaunchedEffect(currentIndex, isStudyMode) {
        if (isStudyMode) UserLibrary.saveStudyIndex(dbName, currentIndex)
    }

    // 学习时长追踪：进入页面开始计时，离开时按分钟累加到打卡记录
    val sessionStart = remember { System.currentTimeMillis() }
    DisposableEffect(Unit) {
        onDispose {
            val minutes = ((System.currentTimeMillis() - sessionStart) / 60000L).toInt().coerceAtLeast(0)
            if (minutes > 0) CheckInManager.accumulate(addMinutes = minutes)
        }
    }

    // 收藏状态：随单词切换重新读取
    var isFavorite by remember(currentWordId) {
        mutableStateOf(UserLibrary.isFavorite(dbName, currentWordId))
    }
    var isWrong by remember(currentWordId) {
        mutableStateOf(UserLibrary.isWrong(dbName, currentWordId))
    }
    // 液态玻璃背景采样源
    val liquidBackdrop = rememberLayerBackdrop()

    // 搜索弹窗
    var showWordSearch by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<WordSearchResult?>(null) }
    var searchLoading by remember { mutableStateOf(false) }

    // 手势交互状态
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var showExamplePanel by remember { mutableStateOf(false) }
    var showSpellPanel by remember { mutableStateOf(false) }
    var spellInput by remember { mutableStateOf("") }
    var spellResult by remember { mutableStateOf<Boolean?>(null) }

    // 获取所有词库列表
    var allDbs by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        allDbs = withContext(Dispatchers.IO) { DatabaseManager.listAssetDatabases(context) }
    }

    // 搜索单词：先在当前词库搜索，再在所有词库搜索
    suspend fun searchWord(word: String): WordSearchResult? {
        // 先在当前词库搜索
        val currentResult = withContext(Dispatchers.IO) {
            val db = DatabaseManager.openDatabase(context, dbName)
            DatabaseManager.searchWords(db, word, limit = 1).firstOrNull()
        }
        if (currentResult != null) return WordSearchResult(dbName, currentResult)
        // 在所有词库中搜索
        for (otherDb in allDbs) {
            val otherDbName = otherDb.removeSuffix(".db")
            if (otherDbName == dbName) continue
            val result = withContext(Dispatchers.IO) {
                val db = DatabaseManager.openDatabase(context, otherDbName)
                DatabaseManager.searchWords(db, word, limit = 1).firstOrNull()
            }
            if (result != null) return WordSearchResult(otherDbName, result)
        }
        return null
    }

    fun handleWordClick(word: String) {
        searchKeyword = word
        searchLoading = true
        showWordSearch = true
    }

    LaunchedEffect(showWordSearch, searchKeyword) {
        if (showWordSearch && searchKeyword.isNotEmpty()) {
            searchResult = searchWord(searchKeyword)
            searchLoading = false
        }
    }

    fun toggleFavorite() {
        val word = detail ?: return
        if (isFavorite) {
            UserLibrary.removeFavorite(dbName, word.wordId)
            isFavorite = false
        } else {
            UserLibrary.addFavorite(
                WordEntry(
                    wordId = word.wordId,
                    headWord = word.headWord,
                    dbName = dbName,
                    tranCn = word.trans.firstOrNull()?.tranCn,
                )
            )
            isFavorite = true
        }
    }

    fun markWrong() {
        val word = detail ?: return
        if (!isWrong) {
            val entry = WordEntry(
                wordId = word.wordId,
                headWord = word.headWord,
                dbName = dbName,
                tranCn = word.trans.firstOrNull()?.tranCn,
            )
            UserLibrary.addWrong(entry)
            isWrong = true
            // 自动收藏开关：开启时同步加入收藏夹
            if (UserLibrary.autoFavoriteEnabled(dbName) && !isFavorite) {
                UserLibrary.addFavorite(entry)
                isFavorite = true
            }
        }
    }

    fun markKnown() {
        // 「认识」后从错题本移除
        if (isWrong) {
            UserLibrary.removeWrong(dbName, currentWordId)
            isWrong = false
        }
    }

    // 有道词典发音 API：https://dict.youdao.com/dictvoice?audio={word}&type={type}
    // type=1 英音，type=2 美音。MediaPlayer 播放完自动 release。
    val speak: (String, Int) -> Unit = { word, type ->
        try {
            val url = "https://dict.youdao.com/dictvoice?audio=" +
                URLEncoder.encode(word, "UTF-8") + "&type=$type"
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { start() }
                setOnCompletionListener { release() }
                setOnErrorListener { _, _, _ -> release(); true }
                prepareAsync()
            }
        } catch (_: Exception) {
            // 忽略播放失败
        }
    }

    LaunchedEffect(dbName, currentWordId) {
        loading = true
        revealed = false
        detail = withContext(Dispatchers.IO) {
            val db = DatabaseManager.openDatabase(context, dbName)
            DatabaseManager.getWordDetail(db, currentWordId)
        }
        loading = false
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
                    .size(200.dp)
                    .offset(x = (-50).dp, y = 80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            )
            Box(
                Modifier
                    .size(160.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = 300.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
            )
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
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
                                        dy < -160 && revealed -> showExamplePanel = true
                                        dy > 160 && revealed -> {
                                            showSpellPanel = true
                                            spellInput = ""
                                            spellResult = null
                                        }
                                    }
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
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        ),
                        onClick = { revealed = !revealed },
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CompactPronounceButton(label = "英", onClick = { speak(word.headWord, 1) })
                                            CompactPronounceButton(label = "美", onClick = { speak(word.headWord, 2) })
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
                                                ClickableWordText(it.content ?: "", onWordClick = { handleWordClick(it) })
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
                                                ClickableWordText(enText, onWordClick = { handleWordClick(it) })
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

                    // 底部情感化三按钮
                    // 单词模式：点击翻面查看释义
                    // 背诵模式：未翻面时点击翻面；已翻面后点击进入下一个单词（或完成）
                    val onGrade: (grade: Int) -> Unit = { grade ->
                        // grade: 0=不认识, 1=模糊, 2=认识
                        // 第一次点击（卡片尚未翻面）：先翻面看释义，不要立刻跳到下一个单词；
                        // 已翻面后再点击评分才会记录并进入下一个单词。
                        if (!revealed) {
                            revealed = true
                            CheckInManager.accumulate(addWordsLearned = 1)
                        } else {
                            if (grade == 0) markWrong()
                            if (grade == 2) {
                                markKnown()
                                CheckInManager.accumulate(addWordsMastered = 1)
                            }
                            if (isStudyMode) {
                                if (currentIndex < total - 1) currentIndex++
                                else onBack()
                            }
                        }
                    }
                    // 底部悬浮操作栏：顶部大圆角 + elevation + 按钮色彩层级化
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 不认识：轮廓线按钮，error 色边框/文字（视觉权重最低，表示需复习）
                            OutlinedButton(
                                onClick = { onGrade(0) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(
                                    if (isWrong) "不认识 ✓" else "不认识",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            // 模糊：浅色填充按钮，secondaryContainer（视觉适中）
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { onGrade(1) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text("模糊", style = MaterialTheme.typography.labelLarge)
                            }
                            // 认识：实心填充按钮，primary（视觉权重最高，表示完成）
                            Button(
                                onClick = { onGrade(2) },
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text("认识", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWordSearch) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showWordSearch = false
                searchResult = null
                searchKeyword = ""
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "搜索 \"$searchKeyword\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            showWordSearch = false
                            searchResult = null
                            searchKeyword = ""
                        }) {
                            Icon(Icons.Filled.Close, "关闭")
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    if (searchLoading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    } else if (searchResult != null) {
                        val result = searchResult!!
                        val word = result.word
                        Column {
                            Text(
                                word.headWord,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            word.trans.firstOrNull()?.let {
                                Text(
                                    listOfNotNull(it.pos, it.tranCn).joinToString("  "),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                word.ukPhone?.let {
                                    androidx.compose.material3.FilledTonalButton(
                                        onClick = { speak(word.headWord, 1) },
                                    ) {
                                        Text("英音")
                                    }
                                }
                                word.usPhone?.let {
                                    androidx.compose.material3.FilledTonalButton(
                                        onClick = { speak(word.headWord, 2) },
                                    ) {
                                        Text("美音")
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    showWordSearch = false
                                    if (result.dbName == dbName && wordQueue != null) {
                                        val idx = wordQueue.indexOf(word.wordId)
                                        if (idx >= 0) currentIndex = idx
                                    } else {
                                        onNavigateToWord(result.dbName, word.wordId)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("查看单词卡片")
                            }
                        }
                    } else {
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
                                "未找到单词 \"$searchKeyword\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // 例句面板：上滑触发
    if (showExamplePanel && detail != null) {
        val word = detail!!
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showExamplePanel = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
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
                    word.sentences.forEach { s ->
                        Spacer(Modifier.height(8.dp))
                        GlassMiniCard(backdrop = liquidBackdrop) {
                            ClickableWordText(s.content ?: "", onWordClick = { handleWordClick(it) })
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
    }

    // 拼写面板：下滑触发
    if (showSpellPanel && detail != null) {
        val word = detail!!
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSpellPanel = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
            shape = RoundedCornerShape(24.dp),
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
            ShimmerBox(modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)))
            ShimmerBox(modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)))
            ShimmerBox(modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)))
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
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var wordIds by remember { mutableStateOf<List<String>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(dbName) {
        wordIds = null
        loadFailed = false
        val ids = withContext(Dispatchers.IO) {
            val db = DatabaseManager.openDatabase(context, dbName)
            DatabaseManager.getWordList(db).map { it.wordId }
        }
        if (ids.isEmpty()) loadFailed = true else wordIds = ids
    }

    when {
        wordIds != null -> {
            val ids = wordIds!!
            // 若指定了起始单词，则从该单词开始；否则读取上次进度（越界则回到 0）
            var startIdx = if (startWordId != null) {
                ids.indexOf(startWordId).takeIf { it >= 0 } ?: UserLibrary.studyIndex(dbName)
            } else {
                val saved = UserLibrary.studyIndex(dbName)
                if (saved >= ids.size) 0 else saved
            }
            WordDetailScreen(
                dbName = dbName,
                wordId = ids[startIdx],
                onBack = onBack,
                wordQueue = ids,
                startIndex = startIdx,
            )
        }
        loadFailed -> Box(Modifier.fillMaxSize()) {
            Text(
                "词库为空，无法开始背诵",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    LiquidGlassCard(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        blurRadius = 8.dp,
        lensHeight = 8.dp,
        lensAmount = 14.dp,
        surfaceColor = Color.White.copy(alpha = 0.18f),
        shadow = glassShadow(12.dp, 0.12f),
        highlight = Highlight.Default.copy(alpha = 0.5f),
        innerShadow = glassInnerShadow(4.dp, 0.08f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
    val wordPattern = Regex("[a-zA-Z]+")
    val matches = wordPattern.findAll(text).toList()
    
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
                annotatedString.getStringAnnotations("WORD", offset, offset).firstOrNull()?.let {
                    onWordClick(it.item)
                }
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { textLayoutResult.value = it },
    )
}

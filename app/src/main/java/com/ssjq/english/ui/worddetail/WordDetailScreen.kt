@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.worddetail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
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
import com.ssjq.english.ui.common.ShimmerBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

@Composable
fun WordDetailScreen(
    dbName: String,
    wordId: String,
    onBack: () -> Unit,
    wordQueue: List<String>? = null,
    startIndex: Int = 0,
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<WordDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var revealed by remember { mutableStateOf(false) }

    // 背诵会话模式：wordQueue 非空时，按队列顺序遍历单词
    val isStudyMode = wordQueue != null
    var currentIndex by remember(wordQueue) {
        mutableStateOf(
            if (isStudyMode) startIndex.coerceIn(0, (wordQueue!!.size - 1).coerceAtLeast(0))
            else wordQueue?.indexOf(wordId)?.coerceAtLeast(0) ?: 0
        )
    }
    val currentWordId = wordQueue?.getOrNull(currentIndex) ?: wordId
    val total = wordQueue?.size ?: 1
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

    Scaffold(
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
                        Image(
                            painter = painterResource(R.drawable.diamond),
                            contentDescription = if (isFavorite) "取消收藏" else "收藏",
                            contentScale = ContentScale.Fit,
                            alpha = if (isFavorite) 1f else 0.4f,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (loading) {
                // 骨架屏：模拟 Hero 区 + 释义区布局，比转圈更有"内容正在浮现"感
                DetailSkeleton()
            } else if (detail != null) {
                val word = detail!!
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
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
                            .graphicsLayer {
                                rotationY = flipRotation
                                cameraDistance = 12 * density
                            },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                                // 正面：第一层单词(超大字号) + 第二层音标 + 发音 + 引导
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        word.headWord,
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    // 音标：英音 / uk_phone，美音 / us_phone
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        word.ukPhone?.takeIf { it.isNotBlank() }?.let {
                                            Text("英 /$it/", style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        word.usPhone?.takeIf { it.isNotBlank() }?.let {
                                            Text("美 /$it/", style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // 难度星级
                                    if (word.star > 0) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            repeat(word.star) {
                                                Icon(Icons.Filled.Star, null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp))
                                            }
                                            Text("难度", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.height(12.dp))
                                    }
                                    // 发音按钮：圆形 IconButton + 主色背景 + 按压动画
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        PronounceButton(
                                            label = "英音",
                                            onClick = { speak(word.headWord, 1) },
                                        )
                                        PronounceButton(
                                            label = "美音",
                                            onClick = { speak(word.headWord, 2) },
                                        )
                                    }
                                    Spacer(Modifier.height(24.dp))
                                    // 呼吸式引导提示
                                    val breath = rememberInfiniteTransition(label = "breath")
                                    val breathAlpha by breath.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                                        label = "a",
                                    )
                                    Text(
                                        "轻触卡片查看释义",
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
                                    // 释义
                                    word.trans.forEach { t ->
                                        Text(
                                            listOfNotNull(t.pos, t.tranCn).joinToString("  "),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    // 短语
                                    if (word.phrases.isNotEmpty()) {
                                        SectionHeader("短语")
                                        word.phrases.forEach {
                                            Spacer(Modifier.height(4.dp))
                                            Text("• ${it.content ?: ""}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface)
                                            Text(it.cn ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    // 例句：左侧主色竖线引用样式 + 关键词高亮
                                    if (word.sentences.isNotEmpty()) {
                                        SectionHeader("例句")
                                        val primaryColor = MaterialTheme.colorScheme.primary
                                        val quoteBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        word.sentences.forEach { s ->
                                            Spacer(Modifier.height(6.dp))
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp)
                                                    .drawBehind {
                                                        drawLine(
                                                            color = primaryColor,
                                                            strokeWidth = 4.dp.toPx(),
                                                            start = Offset(0f, 0f),
                                                            end = Offset(0f, size.height),
                                                        )
                                                    }
                                                    .background(quoteBgColor)
                                                    .padding(12.dp),
                                            ) {
                                                val enText = s.content ?: ""
                                                val cnText = s.cn ?: ""
                                                // 关键词高亮：当前单词在例句中加粗+主色+下划线
                                                val highlighted = buildAnnotatedString {
                                                    val target = word.headWord
                                                    val idx = enText.indexOf(target, ignoreCase = true)
                                                    if (idx >= 0) {
                                                        append(enText.substring(0, idx))
                                                        withStyle(SpanStyle(
                                                            color = primaryColor,
                                                            fontWeight = FontWeight.Bold,
                                                            textDecoration = TextDecoration.Underline,
                                                        )) {
                                                            append(enText.substring(idx, idx + target.length))
                                                        }
                                                        append(enText.substring(idx + target.length))
                                                    } else {
                                                        append(enText)
                                                    }
                                                }
                                                Text(
                                                    highlighted,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontStyle = FontStyle.Italic,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
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
                        if (!revealed) {
                            revealed = true
                            // 翻面时即记录「不认识」到错题本
                            if (grade == 0) markWrong()
                            // 翻面 = 学过一个单词，自动打卡 +1 词
                            CheckInManager.accumulate(addWordsLearned = 1)
                        } else {
                            // 已翻面后再次点击：背诵模式进下一个，单词模式仅记录
                            if (grade == 0) markWrong()
                            if (grade == 2) {
                                markKnown()
                                // 标记认识 = 掌握一个单词
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
            // 读取上次进度：越界则回到 0（已背完或词库变化）
            var startIdx = UserLibrary.studyIndex(dbName)
            if (startIdx >= ids.size) startIdx = 0
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

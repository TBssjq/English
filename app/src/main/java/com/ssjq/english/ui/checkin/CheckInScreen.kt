@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.checkin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssjq.english.R
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.CheckInRecord
import com.ssjq.english.data.CheckInStats
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 每日打卡页：今日状态卡片 + 统计仪表盘 + GitHub 风格贡献热力图 + 成就徽章 + 近期记录。
 * 自动打卡 —— 用户在背诵/测验中的学习行为会自动累加到当日记录，无需手动点击。
 */
@Composable
fun CheckInScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var stats by remember { mutableStateOf(CheckInManager.stats()) }
    var recent by remember { mutableStateOf(CheckInManager.recentDays(84)) } // 12 周

    // 进入页面时刷新一次（学习行为可能在其他页面已触发打卡）
    LaunchedEffect(Unit) {
        stats = CheckInManager.stats()
        recent = CheckInManager.recentDays(84)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("每日打卡", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            // 今日状态英雄卡片
            item(key = "hero") { TodayHeroCard(stats) }

            // 快速统计三连卡
            item(key = "quick-stats") { QuickStatsRow(stats) }

            // 贡献热力图
            item(key = "heatmap") { ContributionHeatmap(recent) }

            // 成就徽章
            item(key = "achievements") { AchievementBadges(stats) }

            // 近期记录
            item(key = "recent-header") {
                Text(
                    "近 7 天记录",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            val last7 = recent.takeLast(7).filterNotNull()
            if (last7.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "还没有学习记录，去词库开始背诵吧",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(last7.reversed(), key = { it.date }) { record ->
                    RecentRecordItem(record)
                }
            }
        }
    }
}

// ---------------- 今日状态英雄卡片 ----------------

@Composable
private fun TodayHeroCard(stats: CheckInStats) {
    val today = stats.todayRecord
    val checkedIn = stats.isCheckedInToday
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checkedIn)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = if (checkedIn) listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        ) else listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ),
                    ),
                )
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 火焰图标：累计打卡时做轻微脉冲动画
                FlameIcon(
                    streak = stats.currentStreak,
                    active = checkedIn,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (checkedIn) "今天已打卡" else "今天还未打卡",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (checkedIn)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "已累计 ${stats.currentStreak} 天 · 最长 ${stats.longestStreak} 天",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (checkedIn)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (today != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "今日：${today.wordsLearned} 词 · ${today.studyMinutes} 分钟" +
                                if (today.quizTotal > 0) " · 测验 ${today.quizCorrect}/${today.quizTotal}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (checkedIn)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "去词库背诵或测验即可自动打卡",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlameIcon(streak: Int, active: Boolean, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "flame")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (active && streak > 0) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale",
    )
    val tint = when {
        streak >= 30 -> Color(0xFFE65100) // 深橙
        streak >= 7 -> Color(0xFFFF6D00)  // 橙
        streak >= 3 -> Color(0xFFFFAB00)  // 琥珀
        active -> Color(0xFFFFC107)       // 黄
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = "累计 $streak 天",
                tint = tint,
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}

// ---------------- 快速统计三连卡 ----------------

@Composable
private fun QuickStatsRow(stats: CheckInStats) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.CalendarMonth,
            value = "${stats.totalDays}",
            label = "累计天数",
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.MenuBook,
            value = "${stats.totalWordsLearned}",
            label = "学习单词",
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Timer,
            value = "${stats.totalMinutes}",
            label = "累计分钟",
        )
    }
}

@Composable
private fun StatMiniCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- GitHub 风格贡献热力图 ----------------

@Composable
private fun ContributionHeatmap(records: List<CheckInRecord?>) {
    // records 已按日期升序排列，长度 84（12 周）。热力图按列（周）排布，每列 7 天。
    val weeks = records.chunked(7)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "近 12 周学习热力图",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            // 热力图主体：横向滚动避免溢出
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { record ->
                            HeatmapCell(record)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "少",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                listOf(0, 1, 2, 3, 4).forEach { level ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatColor(level)),
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    "多",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(record: CheckInRecord?) {
    val level = heatLevel(record)
    val color = heatColor(level)
    val tooltip = record?.let {
        "${it.date}：${it.wordsLearned} 词 / ${it.studyMinutes} 分钟"
    } ?: "未学习"
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

/** 根据学习强度分级 0-4 */
private fun heatLevel(record: CheckInRecord?): Int {
    if (record == null) return 0
    val score = record.wordsLearned + record.studyMinutes / 2
    return when {
        score == 0 -> 0
        score < 10 -> 1
        score < 30 -> 2
        score < 60 -> 3
        else -> 4
    }
}

/** 热力图配色（与主题 primary 同色系，深浅递进） */
private fun heatColor(level: Int): Color {
    val primary = Color(0xFF6750A4) // M3 默认 primary
    return when (level) {
        0 -> Color(0xFFE7E0EC) // 浅灰紫
        1 -> primary.copy(alpha = 0.25f)
        2 -> primary.copy(alpha = 0.5f)
        3 -> primary.copy(alpha = 0.75f)
        else -> primary
    }
}

// ---------------- 成就徽章 ----------------

@Composable
private fun AchievementBadges(stats: CheckInStats) {
    val achievements = remember(stats) { buildAchievements(stats) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.trophy_hi_res),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "成就徽章",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            // 3 列网格
            val rows = achievements.chunked(3)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { a ->
                        BadgeItem(
                            modifier = Modifier.weight(1f),
                            achievement = a,
                        )
                    }
                    // 补齐占位
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private data class Achievement(
    val iconRes: Int,
    val title: String,
    val desc: String,
    val unlocked: Boolean,
    val tint: Color,
)

private fun buildAchievements(stats: CheckInStats): List<Achievement> {
    return listOf(
        Achievement(
            R.drawable.treefood, "初心者", "首次打卡",
            stats.totalDays >= 1, Color(0xFF7CB342),
        ),
        Achievement(
            R.drawable.treefood, "三日之约", "累计打卡 3 天",
            stats.longestStreak >= 3, Color(0xFFFFAB00),
        ),
        Achievement(
            R.drawable.treefood, "一周不落", "累计打卡 7 天",
            stats.longestStreak >= 7, Color(0xFFFF6D00),
        ),
        Achievement(
            R.drawable.treefood, "半月坚持", "累计打卡 15 天",
            stats.longestStreak >= 15, Color(0xFFE65100),
        ),
        Achievement(
            R.drawable.treefood, "月度达人", "累计打卡 30 天",
            stats.longestStreak >= 30, Color(0xFFD32F2F),
        ),
        Achievement(
            R.drawable.treefood, "百日长征", "累计打卡 100 天",
            stats.longestStreak >= 100, Color(0xFF6A1B9A),
        ),
        Achievement(
            R.drawable.stinky_turn1, "百词斩", "累计学习 100 词",
            stats.totalWordsLearned >= 100, Color(0xFF1565C0),
        ),
        Achievement(
            R.drawable.zengarden_wateringcan1, "千词破", "累计学习 1000 词",
            stats.totalWordsLearned >= 1000, Color(0xFF0D47A1),
        ),
        Achievement(
            R.drawable.zengarden_wateringcan1_gold, "勤学不倦", "累计学习 500 分钟",
            stats.totalMinutes >= 500, Color(0xFF00838F),
        ),
    )
}

@Composable
private fun BadgeItem(modifier: Modifier = Modifier, achievement: Achievement) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (achievement.unlocked) achievement.tint.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(achievement.iconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = if (achievement.unlocked) 1f else 0.35f,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            achievement.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (achievement.unlocked) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text(
            achievement.desc,
            style = MaterialTheme.typography.labelSmall,
            color = if (achievement.unlocked) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

// ---------------- 近期记录项 ----------------

@Composable
private fun RecentRecordItem(record: CheckInRecord) {
    val dateFmt = remember { SimpleDateFormat("MM-dd EEE", Locale.getDefault()) }
    val date = remember(record.date) {
        runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dateFmt.format(sdf.parse(record.date) ?: Date())
        }.getOrDefault(record.date)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MenuBook, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    date,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("${record.wordsLearned} 词")
                        if (record.wordsMastered > 0) append(" · 认识 ${record.wordsMastered}")
                        if (record.studyMinutes > 0) append(" · ${record.studyMinutes} 分钟")
                        if (record.quizTotal > 0) append(" · 测验 ${record.quizCorrect}/${record.quizTotal}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

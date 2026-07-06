@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.ssjq.english.ui.wordlist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssjq.english.R
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.WordListItem
import com.ssjq.english.ui.common.FancyToast
import com.ssjq.english.ui.common.ImportExportDialog
import com.ssjq.english.ui.common.ShimmerBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GROUP_SIZE = 30

@Composable
fun WordListScreen(
    dbName: String,
    onBack: () -> Unit,
    onWordClick: (String) -> Unit,
    onStartStudy: () -> Unit,
    onSearch: () -> Unit,
    onOpenLibrary: (com.ssjq.english.ui.nav.LibraryType) -> Unit,
    onStartQuiz: (mode: String?) -> Unit,
) {
    val context = LocalContext.current
    var words by remember { mutableStateOf<List<WordListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // 错题/收藏数量 + 背诵进度 + 自动收藏开关：进入页面与从子页返回时刷新
    var wrongCount by remember { mutableStateOf(0) }
    var favoriteCount by remember { mutableStateOf(0) }
    var studyIndex by remember { mutableStateOf(0) }
    var studyTotal by remember { mutableStateOf(0) }
    var autoFav by remember { mutableStateOf(false) }
    // 词级状态集合（一次性加载，避免列表项逐个查询）
    var wrongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val snackbarHost = remember { SnackbarHostState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showQuizDialog by remember { mutableStateOf(false) }
    var showFancyToast by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }

    // 通知权限申请：后台复习需要（Android 13+）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            com.ssjq.english.service.LearningForegroundService.start(context, dbName)
        }
    }

    fun refreshCounts() {
        wrongCount = com.ssjq.english.data.UserLibrary.wrongCount(dbName)
        favoriteCount = com.ssjq.english.data.UserLibrary.favoriteCount(dbName)
        studyIndex = com.ssjq.english.data.UserLibrary.studyIndex(dbName)
        studyTotal = words.size
        autoFav = com.ssjq.english.data.UserLibrary.autoFavoriteEnabled(dbName)
        wrongIds = com.ssjq.english.data.UserLibrary.wrongWords(dbName).map { it.wordId }.toSet()
        favoriteIds = com.ssjq.english.data.UserLibrary.favorites(dbName).map { it.wordId }.toSet()
    }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshCounts()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(dbName) {
        loading = true
        words = withContext(Dispatchers.IO) {
            val db = DatabaseManager.openDatabase(context, dbName)
            DatabaseManager.getWordList(db)
        }
        loading = false
        refreshCounts()
    }

    val groups = remember(words) {
        words.groupBy { (it.wordRank - 1) / GROUP_SIZE }
            .toSortedMap()
            .map { (idx, list) -> "List ${idx + 1}" to list }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            LargeTopAppBar(
                title = { Text(dbName.removeSuffix(".db"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, "搜索")
                    }
                    IconButton(onClick = { onOpenLibrary(com.ssjq.english.ui.nav.LibraryType.WRONG) }) {
                        BadgedIconBox(
                            count = wrongCount,
                            icon = { Icon(Icons.Filled.MenuBook, "错题本") },
                        )
                    }
                    IconButton(onClick = { onOpenLibrary(com.ssjq.english.ui.nav.LibraryType.FAVORITE) }) {
                        BadgedIconBox(
                            count = favoriteCount,
                            icon = {
                                Image(
                                    painter = painterResource(R.drawable.diamond),
                                    contentDescription = "收藏夹",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                        )
                    }
                    // 溢出菜单：自动收藏开关 + 清空进度（带文字说明）
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, "更多")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (autoFav) "自动收藏：开启中" else "自动收藏：已关闭") },
                            onClick = {
                                autoFav = !autoFav
                                com.ssjq.english.data.UserLibrary.setAutoFavorite(dbName, autoFav)
                            },
                            leadingIcon = {
                                Icon(
                                    if (autoFav) Icons.Filled.ToggleOn else Icons.Filled.ToggleOff,
                                    contentDescription = null,
                                    tint = if (autoFav) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                Text(
                                    "不认识→错题本+收藏夹",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("清空背诵进度") },
                            onClick = {
                                menuExpanded = false
                                showResetDialog = true
                            },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                            trailingIcon = {
                                Text(
                                    "$studyIndex/$studyTotal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("数据管理") },
                            onClick = {
                                menuExpanded = false
                                showImportExportDialog = true
                            },
                            leadingIcon = { Icon(Icons.Filled.Shuffle, null) },
                            trailingIcon = {
                                Text(
                                    "导入/导出",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Row(verticalAlignment = Alignment.Bottom) {
                FloatingActionButton(
                    onClick = {
                        if (com.ssjq.english.service.NotificationPermission.hasPermission(context)) {
                            com.ssjq.english.service.LearningForegroundService.start(context, dbName)
                            showFancyToast = true
                        } else {
                            notificationPermissionLauncher.launch(
                                com.ssjq.english.service.NotificationPermission.permission
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Icon(Icons.Filled.Headphones, contentDescription = "后台复习")
                }
                // 开始背诵按钮（主要 FAB）
                ExtendedFloatingActionButton(
                    onClick = { onStartStudy() },
                    text = {
                        val progressed = studyTotal > 0 && studyIndex in 1 until studyTotal
                        Text(if (progressed) "继续背诵 ($studyIndex/$studyTotal)" else "开始背诵")
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                )
            }
        },
    ) { padding ->
        if (loading) {
            // 骨架屏占位
            LazyVerticalGrid(
                columns = GridCells.Adaptive(320.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(8) { SkeletonRow() }
            }
        } else {
            // 自适应栅格：手机单列，平板/折叠屏自动多列
            LazyVerticalGrid(
                columns = GridCells.Adaptive(320.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 显眼的快速测验入口卡片（全宽）
                item(key = "quiz-entry", span = { GridItemSpan(maxLineSpan) }) {
                    QuizEntryCard(
                        onClick = { showQuizDialog = true },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                groups.forEach { (title, list) ->
                    item(key = "h-$title", span = { GridItemSpan(maxLineSpan) }) {
                        GroupHeader(
                            title = title,
                            total = list.size,
                            studied = list.count { it.wordId in wrongIds || it.wordId in favoriteIds },
                        )
                    }
                    items(list, key = { it.wordId }) { word ->
                        WordRow(
                            word = word,
                            isWrong = word.wordId in wrongIds,
                            isFavorite = word.wordId in favoriteIds,
                            onClick = { onWordClick(word.wordId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    // 清空进度确认对话框（Scaffold 之外，避免 padding 干扰）
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("清空背诵进度") },
            text = { Text("下次开始背诵将从头开始。错题本与收藏夹不会被清空。") },
            confirmButton = {
                TextButton(onClick = {
                    com.ssjq.english.data.UserLibrary.resetStudyIndex(dbName)
                    studyIndex = 0
                    showResetDialog = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            },
        )
    }

    // 快速测验模式选择对话框
    if (showQuizDialog) {
        QuizModeDialog(
            onDismiss = { showQuizDialog = false },
            onSelect = { mode -> onStartQuiz(mode) },
        )
    }

    FancyToast(
        message = "开始后台背诵啦",
        visible = showFancyToast,
        onDismiss = { showFancyToast = false },
    )

    ImportExportDialog(
        visible = showImportExportDialog,
        onDismiss = { showImportExportDialog = false },
        onResult = { message -> },
    )
}

/** 骨架屏行：模拟列表项布局 */
@Composable
private fun SkeletonRow() {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(28.dp).clip(CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ShimmerBox(Modifier.fillMaxWidth(0.5f).height(18.dp))
                Spacer(Modifier.height(6.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.3f).height(12.dp))
            }
        }
    }
}

@Composable
private fun WordRow(
    word: WordListItem,
    isWrong: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        ListItem(
            leadingContent = { StatusIndicator(isWrong = isWrong, isFavorite = isFavorite) },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        word.headWord,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Image(
                            painter = painterResource(R.drawable.diamond),
                            contentDescription = "已收藏",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    word.tranCn ?: "No.${word.wordRank}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            },
            trailingContent = {
                if (word.star > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Text(" ${word.star}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
        )
    }
}

/**
 * 学习状态指示器（左侧圆形）：
 * - 错题：红色实心
 * - 已收藏(非错题)：暖黄实心
 * - 未学：浅灰描边
 */
@Composable
private fun StatusIndicator(isWrong: Boolean, isFavorite: Boolean) {
    val color = when {
        isWrong -> MaterialTheme.colorScheme.error
        isFavorite -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier.size(12.dp).clip(CircleShape).background(color)
    )
}

/** 分组头：标题 + 词数 + 进度条 */
@Composable
private fun GroupHeader(title: String, total: Int, studied: Int) {
    val progress = if (total > 0) studied.toFloat() / total else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600),
        label = "grpProgress",
    )
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$studied / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/** 带数量徽标的图标按钮内容（顶栏用） */
@Composable
private fun BadgedIconBox(count: Int, icon: @Composable () -> Unit) {
    if (count > 0) {
        BadgedBox(badge = { Badge { Text("$count") } }) { icon() }
    } else {
        icon()
    }
}

/**
 * 快速测验入口大卡片：列表顶部显眼位置，带渐变图标和副标题。
 * 点击后弹出模式选择对话框，选一个模式开始测验。
 */
@Composable
private fun QuizEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 大图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Quiz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "快速测验",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "4 种考察模式，检验真实掌握程度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "开始",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 快速测验模式选择对话框：选一个模式立即开始。
 * mode 参数：null=随机，"EnSelectCn"/"CnSelectEn"/"AudioSelect"/"Spelling"=固定模式
 */
@Composable
fun QuizModeDialog(
    onDismiss: () -> Unit,
    onSelect: (mode: String?) -> Unit,
) {
    val options = listOf(
        Triple("随机模式", "4 种模式加权随机切换", null),
        Triple("英选中", "看英文选中文释义", "EnSelectCn"),
        Triple("中选英", "看中文选英文单词", "CnSelectEn"),
        Triple("听音辨意", "听发音选正确释义", "AudioSelect"),
        Triple("拼写测试", "看中文拼写英文单词", "Spelling"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Quiz, null, modifier = Modifier.size(36.dp)) },
        title = { Text("选择测验模式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (label, desc, mode) ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(label, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(desc) },
                        leadingContent = {
                            androidx.compose.material3.Icon(
                                if (mode == null) Icons.Filled.Shuffle else Icons.Filled.PlayArrow,
                                null,
                                tint = if (mode == null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(mode)
                                onDismiss()
                            },
                        colors = androidx.compose.material3.ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

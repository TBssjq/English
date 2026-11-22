@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssjq.english.R
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.ReviewScheduler
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.LibraryCategory
import com.ssjq.english.data.LibrarySubcategory
import com.ssjq.english.data.SearchResultItem
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.UserManager
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ssjq.english.ui.glass.LocalAppBackdrop
import com.ssjq.english.ui.nav.LibraryType
import com.ssjq.english.ui.common.FancyToast
import com.ssjq.english.ui.common.LiquidGlassListItem
import com.ssjq.english.ui.glass.GlassQuality
import com.ssjq.english.ui.glass.GlassShapes
import com.ssjq.english.ui.glass.glassTopBar
import com.ssjq.english.ui.glass.glassTopBarColors
import com.ssjq.english.ui.common.performExport
import com.ssjq.english.ui.common.performImport
import com.ssjq.english.ui.glass.Capsule
import com.ssjq.english.ui.glass.LiquidButton
import com.ssjq.english.ui.glass.LiquidCard
import com.ssjq.english.ui.glass.LiquidToggle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onPickDatabase: (String) -> Unit,
    onOpenCheckIn: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenCrazyQuiz: () -> Unit = {},
    onStartReview: (String, List<String>) -> Unit,
    onStartStudy: (String) -> Unit,
    /** 打开错题本 / 收藏夹；第三个参数为是否直接展示全部词库的条目 */
    onOpenLibrary: (dbName: String, type: LibraryType, showAll: Boolean) -> Unit,
    /** 打开某个单词的详情页（首页跨词库搜索结果） */
    onOpenWord: (dbName: String, wordId: String) -> Unit,
) {
    val context = LocalContext.current

    // 当前选定的学习词库（注册时选择）。为空表示尚未选择，仍展示完整分类树。
    var bookVersion by remember { mutableStateOf(0) }
    val currentBook = remember(bookVersion) { UserManager.getCurrentBook() }
    // 是否已选定词库且未主动展开全部词库
    var showAllBooks by remember { mutableStateOf(false) }
    val showFullCatalog = currentBook == null || showAllBooks

    fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "来和我一起用这款背单词 App 学习英语吧！\nGitHub 仓库：https://github.com/TBssjq/English",
            )
        }
        context.startActivity(Intent.createChooser(intent, "分享 App"))
    }

    var allDbs by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    // 展开的分类 key（分类名称）
    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 展开的子分类 key（分类名::子分类名）
    var expandedSubs by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 排序编辑模式
    var isEditingOrder by remember { mutableStateOf(false) }
    // 当前排序中的分类列表（编辑模式下修改，保存后写入 SP）
    var editedOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    // 导出数据弹窗
    var showExportDialog by remember { mutableStateOf(false) }
    var importExportMessage by remember { mutableStateOf<String?>(null) }
    // 修改用户名弹窗
    var showUsernameDialog by remember { mutableStateOf(false) }
    var currentUsername by remember { mutableStateOf(UserManager.getUsername() ?: "") }
    var newUsername by remember { mutableStateOf(currentUsername) }
    var usernameError by remember { mutableStateOf("") }
    // 主题模式 - 响应式观察 Flow 变化
    val darkThemeMode by UserManager.darkThemeFlow.collectAsState()
    // 玻璃特效开关 - 响应式观察 Flow 变化
    val glassEffect by UserManager.glassEffectFlow.collectAsState()

    LaunchedEffect(Unit) {
        allDbs = withContext(Dispatchers.IO) { DatabaseManager.listAssetDatabases(context) }
    }

    // 分类目录（按实际可用 db 过滤）
    val catalog = remember(allDbs) { LibraryCatalog.buildCatalogForAvailableDbs(allDbs) }

    // 异步加载各词库单词总数：db名(不带.db) → 单词数
    var wordCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // 仅在需要展示完整分类树时才统计各词库词数：
    // 该操作会逐个打开所有词库（可达数十个），是启动阶段最重的 IO，
    // 已选定词库时跳过，可显著加快首屏显示。
    LaunchedEffect(allDbs, showFullCatalog) {
        if (allDbs.isEmpty() || !showFullCatalog) return@LaunchedEffect
        // 在 IO 线程一次性加载完所有计数，避免逐个更新导致频繁重组
        val counts = withContext(Dispatchers.IO) {
            allDbs.associate { dbName ->
                val pure = dbName.removeSuffix(".db")
                pure to DatabaseManager.getWordCount(context, dbName)
            }
        }
        wordCounts = counts
    }

    // 读取保存的分类顺序，用于对 catalog 排序。保存后通过 orderVersion 触发重读
    var orderVersion by remember { mutableStateOf(0) }
    var savedOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(allDbs, orderVersion) {
        savedOrder = withContext(Dispatchers.IO) { UserLibrary.categoryOrder() }
    }

    // 打卡统计：stats() 要解析 SP 里的 JSON，较重，放到 IO 线程；
    // 并在从打卡/学习页返回主页时刷新（学习行为会触发自动打卡）
    val homeScope = rememberCoroutineScope()
    var checkInStats by remember { mutableStateOf(CheckInManager.EmptyStats) }
    var dueReviewIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var dailyGoal by remember { mutableStateOf(UserManager.getDailyGoal()) }
    var showGoalPicker by remember { mutableStateOf(false) }
    // 顶栏溢出菜单：目标 / 用户名 / 分享 / 排序 / 关于
    var showMoreMenu by remember { mutableStateOf(false) }
    // 错题与收藏的总数按全部词库统计：跨词库测验的错题分散在各库
    var wrongTotal by remember { mutableStateOf(0) }
    var favoriteTotal by remember { mutableStateOf(0) }
    fun refreshCheckInStats() {
        homeScope.launch {
            val snapshot = runCatching {
                withContext(Dispatchers.IO) {
                    val book = UserManager.getCurrentBook()
                    val due = if (book != null) ReviewScheduler.dueWordIds(book) else emptyList()
                    Triple(
                        CheckInManager.stats(),
                        due,
                        UserLibrary.allWrongWords().size to UserLibrary.allFavorites().size,
                    )
                }
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Toast.makeText(context, "学习数据读取失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            checkInStats = snapshot.first
            dueReviewIds = snapshot.second
            wrongTotal = snapshot.third.first
            favoriteTotal = snapshot.third.second
        }
    }
    LaunchedEffect(currentBook) { refreshCheckInStats() }
    val homeLifecycle = LocalLifecycleOwner.current
    DisposableEffect(homeLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshCheckInStats()
        }
        homeLifecycle.lifecycle.addObserver(observer)
        onDispose { homeLifecycle.lifecycle.removeObserver(observer) }
    }

    // 应用自定义顺序后的分类列表（非编辑模式下使用）
    val orderedCatalog = remember(catalog, savedOrder) {
        if (savedOrder.isEmpty()) return@remember catalog
        val orderMap = savedOrder.withIndex().associate { it.value to it.index }
        catalog.sortedBy { cat -> orderMap[cat.name] ?: Int.MAX_VALUE }
    }

    // 编辑模式下的分类名称列表（可拖动/上下移动）
    val editingOrderNames = remember(isEditingOrder, orderedCatalog, editedOrder) {
        if (!isEditingOrder) emptyList()
        else if (editedOrder.isNotEmpty()) editedOrder
        else orderedCatalog.map { it.name }
    }

    // 搜索结果：有搜索词时用 search 结果，无搜索词时显示完整分类目录
    val searchResults = remember(filter, allDbs) {
        LibraryCatalog.search(filter, allDbs)
    }
    val hasFilter = filter.isNotBlank()
    // 搜索中 / 调整顺序时隐藏入口卡片与仪表盘，聚焦当前任务
    val showEntryCards = !hasFilter && !isEditingOrder
    // 跨词库单词搜索结果（异步加载）
    var wordSearchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var wordSearchLoading by remember { mutableStateOf(false) }
    LaunchedEffect(filter, allDbs) {
        if (filter.isBlank() || allDbs.isEmpty()) {
            wordSearchResults = emptyList()
            wordSearchLoading = false
            return@LaunchedEffect
        }
        wordSearchLoading = true
        try {
            // 轻微延迟去抖
            kotlinx.coroutines.delay(200)
            wordSearchResults = withContext(Dispatchers.IO) {
                DatabaseManager.searchAllDbs(context, allDbs, filter, limitPerDb = 8)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            wordSearchResults = emptyList()
            Toast.makeText(context, "搜索失败，请稍后重试", Toast.LENGTH_SHORT).show()
        } finally {
            wordSearchLoading = false
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // 全应用共享的背景采样层
    val liquidBackdrop = LocalAppBackdrop.current

    // 选定 / 切换当前学习词库，并进入该词库
    fun pickBook(db: String) {
        UserManager.setCurrentBook(db)
        bookVersion++
        showAllBooks = false
        onPickDatabase(db)
    }

    fun openEditUsername() {
        newUsername = currentUsername
        usernameError = ""
        showUsernameDialog = true
    }

    val todayWords = checkInStats.todayRecord?.wordsLearned ?: 0
    val safeDailyGoal = dailyGoal.coerceAtLeast(1)
    val goalReached = todayWords >= safeDailyGoal
    val remainingWords = (safeDailyGoal - todayWords).coerceAtLeast(0)
    val streakDays = checkInStats.currentStreak
    val welcomeLine = when {
        goalReached && streakDays > 0 -> "连续 $streakDays 天，真稳！"
        goalReached -> "今日目标达成！"
        remainingWords <= 5 && streakDays > 0 -> "就差 $remainingWords 词，别断 $streakDays 天"
        remainingWords <= 5 -> "就差 $remainingWords 词！"
        streakDays > 0 -> "欢迎回来，连续 $streakDays 天"
        else -> "欢迎背单词"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                // 头部常驻区：顶栏与搜索框固定在此，列表筛选变化不影响输入框节点，
                // 连续输入时输入法保持展开
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        modifier = Modifier.glassTopBar(liquidBackdrop),
                        colors = glassTopBarColors(),
                        title = {
                            if (isEditingOrder) {
                                Text("调整分类顺序", fontWeight = FontWeight.Bold)
                            } else if (!hasFilter) {
                                // 顶栏标题区总高固定 64dp，只放两行：欢迎语一行、学习进度一行，
                                // 行间距 2dp，两行合计不超过 44dp，避免垂直方向被裁切。
                                // 用户名已移到左上角，第二行不再重复用户名。
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        welcomeLine,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        // 欢迎语必须完整显示：不换行、不加省略号，
                                        // 左侧头像已替换为用户名，标题可用宽度足够
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "今日 $todayWords / $safeDailyGoal 词",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (goalReached || remainingWords <= 5) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Visible,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        // 进度以 safeDailyGoal 为分母，目标为 0 时不会出现 NaN
                                        LinearProgressIndicator(
                                            progress = {
                                                (todayWords.toFloat() / safeDailyGoal)
                                                    .coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier
                                                .width(48.dp)
                                                .height(3.dp)
                                                .clip(GlassShapes.Tag),
                                            color = if (goalReached) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.secondary
                                            },
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            if (!isEditingOrder && !hasFilter) {
                                // 左上角改为显示当前用户名（点击仍进入「关于与检查更新」）。
                                // 用户名限宽 110dp，超长才省略，避免把右侧标题再次挤扁。
                                Text(
                                    currentUsername,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 110.dp)
                                        .clip(GlassShapes.Tag)
                                        .clickable(onClick = onOpenAbout)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        actions = {
                            if (hasFilter) return@TopAppBar
                            if (isEditingOrder) {
                                TextButton(onClick = {
                                    // 保存顺序并触发重读
                                    UserLibrary.saveCategoryOrder(editingOrderNames)
                                    orderVersion++
                                    isEditingOrder = false
                                    editedOrder = emptyList()
                                }) {
                                    Text("保存", color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = {
                                    isEditingOrder = false
                                    editedOrder = emptyList()
                                }) {
                                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                // 目标 / 用户名 / 分享 / 排序四项收进溢出菜单：
                                // 平铺时标题可用宽度不足，是「欢迎…」被截断的直接原因
                                Box {
                                    IconButton(
                                        onClick = { showMoreMenu = true },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (goalReached) {
                                                        "每日目标：$safeDailyGoal 词（今日已达标）"
                                                    } else {
                                                        "每日目标：$safeDailyGoal 词（还差 $remainingWords 词）"
                                                    },
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                showGoalPicker = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("修改用户名") },
                                            onClick = {
                                                showMoreMenu = false
                                                openEditUsername()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("分享给朋友") },
                                            onClick = {
                                                showMoreMenu = false
                                                shareApp()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("调整分类顺序") },
                                            onClick = {
                                                showMoreMenu = false
                                                isEditingOrder = true
                                                editedOrder = orderedCatalog.map { it.name }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("关于与检查更新") },
                                            onClick = {
                                                showMoreMenu = false
                                                onOpenAbout()
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    // 搜索框常驻在顶栏正下方、列表之外：位置与节点都不随筛选结果变化
                    if (!isEditingOrder) {
                        HomeSearchField(
                            value = filter,
                            onValueChange = { filter = it },
                            backdrop = liquidBackdrop,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                // 顶部迷你设置条：深色模式（三态）+ 玻璃特效开关
                // 列表内所有 item 都显式给 key：条目会随筛选条件整体增删，
                // key 保证节点身份稳定，滚动位置与内部状态不被牵连
                if (showEntryCards) item(key = "settings-bar") {
                    // lambda 必须稳定：LiquidToggle 内部以 selected 为 LaunchedEffect 的 key，
                    // 每次重组新建 lambda 会导致内部状态/动画被反复重置
                    val toggleSelected = remember(darkThemeMode) { { darkThemeMode == true } }
                    val toggleOnSelect = remember { { v: Boolean -> UserManager.setDarkThemeMode(v) } }
                    val glassToggleSelected = remember(glassEffect) { { glassEffect } }
                    val glassToggleOnSelect = remember {
                        { v: Boolean ->
                            UserManager.setGlassEffectEnabled(v)
                            GlassQuality.setEffectsEnabled(v)
                        }
                    }
                    LiquidCard(
                        backdrop = liquidBackdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = GlassShapes.Item,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.DarkMode,
                                    contentDescription = "深色模式",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "深色模式",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    // 固定标签宽度：两行的状态小字（深色 / 折射与色散）落在同一列，
                                    // 旧实现按文字实际宽度排在标签后，两行缩进不一致，看着松散
                                    modifier = Modifier.width(72.dp),
                                )
                                Text(
                                    when (darkThemeMode) {
                                        true -> "深色"
                                        false -> "浅色"
                                        null -> "跟随系统"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.weight(1f))
                                if (darkThemeMode != null) {
                                    TextButton(
                                        onClick = { UserManager.setDarkThemeMode(null) },
                                        modifier = Modifier.height(32.dp),
                                    ) {
                                        Text("自动", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                LiquidToggle(
                                    selected = toggleSelected,
                                    onSelect = toggleOnSelect,
                                    backdrop = liquidBackdrop,
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.BlurOn,
                                    contentDescription = "玻璃特效",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "玻璃特效",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(72.dp),
                                )
                                Text(
                                    if (glassEffect) "折射与色散" else "已关闭",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.weight(1f))
                                LiquidToggle(
                                    selected = glassToggleSelected,
                                    onSelect = glassToggleOnSelect,
                                    backdrop = liquidBackdrop,
                                )
                            }
                        }
                    }
                }

                // 当前学习词库 + 开始学习：提升到列表最前，确保首屏即可点击，无需滚动
                if (currentBook != null && !showFullCatalog) item(key = "current-book-panel") {
                    val book = currentBook
                    CurrentBookPanel(
                        backdrop = liquidBackdrop,
                        dbName = book,
                        dueCount = dueReviewIds.size,
                        onStartStudy = {
                            UserManager.setCurrentBook(book)
                            onStartStudy(book)
                        },
                        onStartReview = { onStartReview(book, dueReviewIds) },
                        onBrowseAll = { showAllBooks = true },
                        wrongTotal = wrongTotal,
                        favoriteTotal = favoriteTotal,
                        onOpenWrong = { onOpenLibrary(book, LibraryType.WRONG, true) },
                        onOpenFavorite = { onOpenLibrary(book, LibraryType.FAVORITE, true) },
                    )
                }

                // 疯狂刷题入口（液态玻璃按钮）
                if (showEntryCards) item(key = "crazy-quiz-entry") {
                    LiquidButton(
                        onClick = onOpenCrazyQuiz,
                        backdrop = liquidBackdrop,
                        height = 76.dp,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "疯狂刷题",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onError,
                            )
                            Text(
                                "挑战词汇极限，获得评分等级",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onError.copy(alpha = 0.8f),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // 每日打卡入口（液态玻璃按钮）
                item(key = "checkin-entry") {
                    val stats = checkInStats
                    val checkedIn = stats.isCheckedInToday
                    val tint = if (checkedIn) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                    val onTint = if (checkedIn) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onSecondary
                    }
                    LiquidButton(
                        onClick = onOpenCheckIn,
                        backdrop = liquidBackdrop,
                        height = 76.dp,
                        tint = tint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = onTint,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (checkedIn) "今日已打卡 · 累计 ${stats.currentStreak} 天"
                                else "今日还未打卡 · 去学习打卡",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onTint,
                            )
                            Text(
                                "累计 ${stats.totalDays} 天 · ${stats.totalWordsLearned} 词 · 最长 ${stats.longestStreak} 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = onTint.copy(alpha = 0.8f),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = onTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // 导出数据入口（液态玻璃按钮）
                if (showEntryCards) item(key = "export-entry") {
                    LiquidButton(
                        onClick = { showExportDialog = true },
                        backdrop = liquidBackdrop,
                        height = 72.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "导出全部数据",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "包含使用天数、成就、错题本、收藏夹",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // 外观设置：液态玻璃面板 + 库自带 LiquidToggle

                if (hasFilter) {
                    // 单词搜索结果（跨所有词库）
                    if (wordSearchLoading) {
                        item(key = "word-search-loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    } else if (wordSearchResults.isNotEmpty()) {
                        item(key = "word-search-header") {
                            Text(
                                "单词结果",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(wordSearchResults, key = { "${it.dbName}-${it.wordId}" }) { item ->
                            WordSearchResultCard(
                                item = item,
                                // 直接看该词的详情，不再顺手把当前词库切走
                                onClick = { onOpenWord(item.dbName, item.wordId) },
                                backdrop = liquidBackdrop,
                            )
                        }
                    }

                    // 词库搜索结果
                    if (searchResults.isNotEmpty()) {
                        item(key = "lib-search-header") {
                            Text(
                                "词库结果",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        searchResults.forEach { (fullName, dbs) ->
                            item(key = "s-$fullName") {
                                Text(
                                    fullName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(dbs, key = { it }) { db ->
                                LibraryCard(
                                    dbName = db,
                                    wordCount = wordCounts[db],
                                    onClick = { pickBook("$db.db") },
                                    backdrop = liquidBackdrop,
                                )
                            }
                        }
                    }

                    // 完全没有结果
                    if (!wordSearchLoading && wordSearchResults.isEmpty() && searchResults.isEmpty()) {
                        item(key = "search-empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "没有找到匹配的结果",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (isEditingOrder) {
                    // 排序编辑模式：仅显示分类卡片，右侧有上下箭头
                    item(key = "order-hint") {
                        Text(
                            "点击上/下箭头调整顺序，或拖动条目",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // key 带上 index 兜底：分类名理论上可能重复，重复 key 会让 LazyColumn 崩溃
                    itemsIndexed(editingOrderNames, key = { i, name -> "edit-$i-$name" }) { index, catName ->
                        val cat = catalog.find { it.name == catName } ?: return@itemsIndexed
                        OrderableCategoryItem(
                            category = cat,
                            index = index,
                            total = editingOrderNames.size,
                            onMoveUp = {
                                if (index > 0) {
                                    val list = editingOrderNames.toMutableList()
                                    list[index] = list[index - 1].also { list[index - 1] = list[index] }
                                    editedOrder = list
                                }
                            },
                            onMoveDown = {
                                if (index < editingOrderNames.size - 1) {
                                    val list = editingOrderNames.toMutableList()
                                    list[index] = list[index + 1].also { list[index + 1] = list[index] }
                                    editedOrder = list
                                }
                            },
                            backdrop = liquidBackdrop,
                        )
                    }
                    item(key = "order-reset") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    UserLibrary.resetCategoryOrder()
                                    orderVersion++
                                    editedOrder = catalog.map { it.name }
                                },
                            ) { Text("恢复默认顺序") }
                        }
                    }
                } else if (!showFullCatalog) {
                    // 当前学习词库面板已提升到列表顶部（深色模式条之后），
                    // 此处不再重复渲染
                } else {
                    // 分类目录：两级可展开
                    orderedCatalog.forEach { cat ->
                        val catKey = cat.name
                        val catExpanded = catKey in expandedCategories
                        item(key = "cat-$catKey") {
                            CategoryHeader(
                                category = cat,
                                expanded = catExpanded,
                                wordCounts = wordCounts,
                                onClick = {
                                    expandedCategories = if (catExpanded)
                                        expandedCategories - catKey
                                    else
                                        expandedCategories + catKey
                                },
                                backdrop = liquidBackdrop,
                            )
                        }
                        if (catExpanded) {
                            cat.subcategories.forEach { sub ->
                                val subKey = "$catKey::${sub.name}"
                                val subExpanded = subKey in expandedSubs
                                item(key = "sub-$subKey") {
                                    SubcategoryCard(
                                        subcategory = sub,
                                        expanded = subExpanded,
                                        wordCounts = wordCounts,
                                        onClick = {
                                            expandedSubs = if (subExpanded)
                                                expandedSubs - subKey
                                            else
                                                expandedSubs + subKey
                                        },
                                        backdrop = liquidBackdrop,
                                    )
                                }
                                if (subExpanded) {
                                    items(sub.dbFiles, key = { "$subKey-$it" }) { db ->
                                        LibraryCard(
                                            dbName = db,
                                            wordCount = wordCounts[db],
                                            onClick = { pickBook("$db.db") },
                                            backdrop = liquidBackdrop,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        com.ssjq.english.ui.common.ImportExportDialog(
            visible = showExportDialog,
            onDismiss = { showExportDialog = false },
            onExportRequested = { uri, scope, groupName, dbName, contentType ->
                homeScope.launch {
                    val message = withContext(Dispatchers.IO) {
                        performExport(context, uri, scope, groupName, dbName, contentType)
                    }
                    importExportMessage = message
                }
            },
            onImportConfirmed = { uri, strategy ->
                homeScope.launch {
                    val message = withContext(Dispatchers.IO) {
                        performImport(context, uri, strategy)
                    }
                    importExportMessage = message
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        FancyToast(
            message = importExportMessage.orEmpty(),
            visible = importExportMessage != null,
            onDismiss = { importExportMessage = null },
            backdrop = liquidBackdrop,
        )
    }

    // 每日学习目标：今日学习量已记录在打卡数据里，这里只调整目标值
    if (showGoalPicker) {
        AlertDialog(
            onDismissRequest = { showGoalPicker = false },
            title = { Text("每日学习目标", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    UserManager.DAILY_GOAL_OPTIONS.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(GlassShapes.Item)
                                .clickable {
                                    UserManager.setDailyGoal(option)
                                    dailyGoal = option
                                    showGoalPicker = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == dailyGoal, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "$option 词 / 天",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGoalPicker = false }) { Text("关闭") }
            },
        )
    }

    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = { Text("修改用户名", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = {
                            newUsername = it
                            usernameError = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入新用户名") },
                        textStyle = MaterialTheme.typography.titleMedium,
                        shape = GlassShapes.Tag,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        singleLine = true,
                        isError = usernameError.isNotBlank(),
                        supportingText = if (usernameError.isNotBlank()) {
                            { Text(usernameError, color = MaterialTheme.colorScheme.error) }
                        } else null,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newUsername.trim().isBlank()) {
                        usernameError = "请输入用户名"
                        return@TextButton
                    }
                    if (newUsername.trim().length < 2) {
                        usernameError = "用户名至少需要2个字符"
                        return@TextButton
                    }
                    UserManager.setUsername(newUsername.trim())
                    currentUsername = newUsername.trim()
                    showUsernameDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/** 排序模式下的分类项：玻璃面板 + 内嵌上/下移动按钮 */
@Composable
private fun OrderableCategoryItem(
    category: LibraryCategory,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    backdrop: Backdrop,
) {
    val totalDbs = category.subcategories.sumOf { it.dbFiles.size }
    LiquidCard(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = GlassShapes.Card,
        blurRadius = 4.dp,
        lensHeight = 10.dp,
        lensAmount = 16.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.name.substringBefore("（").trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$totalDbs 个词库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "上移",
                    tint = if (index > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < total - 1,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "下移",
                    tint = if (index < total - 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** 单词搜索结果：液态玻璃按钮，单词 + 释义 + 所属词库标签 */
@Composable
private fun WordSearchResultCard(
    item: SearchResultItem,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    // 搜索结果可能有几十项，每项使用静态磨砂，避免同屏创建几十套离屏缓冲
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.headWord,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.tranCn?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = GlassShapes.Item,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    // 标签显示中文册名，旧实现直接露出 BeiShiGaoZhong_10 这类文件名
                    LibraryCatalog.displayName(item.dbName),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 分类标题：液态玻璃按钮，点击展开/折叠 */
@Composable
private fun CategoryHeader(
    category: LibraryCategory,
    expanded: Boolean,
    wordCounts: Map<String, Int>,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    val totalDbs = category.subcategories.sumOf { it.dbFiles.size }
    val totalWords = category.subcategories.flatMap { it.dbFiles }.sumOf { wordCounts[it] ?: 0 }
    val allLoaded = category.subcategories.flatMap { it.dbFiles }.all { it in wordCounts }
    // 分类标题属于列表项，使用轻量玻璃避免每项都创建多层 GraphicsLayer
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // 只显示中文分类名，去掉「（CET4）」这类英文括号注释
                    category.name.substringBefore("（").trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (allLoaded && totalWords > 0) "$totalWords 词 · $totalDbs 库"
                    else "$totalDbs 个词库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/** 子分类：次级液态玻璃按钮，点击展开显示具体词库 */
@Composable
private fun SubcategoryCard(
    subcategory: LibrarySubcategory,
    expanded: Boolean,
    wordCounts: Map<String, Int>,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    val totalWords = subcategory.dbFiles.sumOf { wordCounts[it] ?: 0 }
    val allLoaded = subcategory.dbFiles.all { it in wordCounts }
    // 子分类同属列表项，使用轻量玻璃
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    subcategory.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                if (allLoaded && totalWords > 0) "$totalWords 词 · ${subcategory.dbFiles.size} 本"
                else "${subcategory.dbFiles.size} 本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/** 词库卡片：液态玻璃按钮，圆形图标 + 名称 + 描述 + 右箭头 */
@Composable
private fun LibraryCard(
    dbName: String,
    wordCount: Int?,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    // 标题只显示中文册名（旧实现直接显示 BeiShiGaoZhong_10 这类英文文件名）
    val parts = LibraryCatalog.displayParts(dbName)
    // 词库列表项使用轻量玻璃
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    parts.bookName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (parts.mapped) append("${parts.category} · ")
                        append(if (wordCount != null) "共 $wordCount 词" else "正在统计词数…")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 当前学习词库面板：注册时选定词库后，主界面只呈现这一本，
 * 避免首屏罗列全部分类造成的拥挤。可随时更换或浏览全部词库。
 */
@Composable
private fun CurrentBookPanel(
    backdrop: Backdrop,
    dbName: String,
    dueCount: Int,
    onStartStudy: () -> Unit,
    onStartReview: () -> Unit,
    onBrowseAll: () -> Unit,
    wrongTotal: Int,
    favoriteTotal: Int,
    onOpenWrong: () -> Unit,
    onOpenFavorite: () -> Unit,
) {
    LiquidCard(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = GlassShapes.Panel,
        blurRadius = 10.dp,
        lensHeight = 18.dp,
        lensAmount = 30.dp,
        surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "当前学习词库",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // 旧实现把「高中英语 · 北师大版高中 10」整串塞进 headlineMedium（28sp），
            // 一行放不下就折行，只剩「高中 10」独占一行且字号最大，视觉极不平衡。
            // 现在拆成「分类（小字）+ 册名（中号、可两行）」两层排版，册名内部不再被折断。
            val bookParts = LibraryCatalog.displayParts(dbName)
            Text(
                if (bookParts.mapped) bookParts.category else "其他词库",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                bookParts.bookName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!bookParts.mapped) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "未登记词库，显示文件名",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(20.dp))
            LiquidButton(
                onClick = onStartStudy,
                backdrop = backdrop,
                height = 60.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text("开始学习", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (dueCount > 0) {
                LiquidButton(
                    onClick = onStartReview,
                    backdrop = backdrop,
                    height = 46.dp,
                    surfaceColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "今日待复习 $dueCount 词",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            LiquidButton(
                onClick = onBrowseAll,
                backdrop = backdrop,
                height = 46.dp,
                surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("更换词库 / 浏览全部", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            // 错题本与收藏夹直接放在主页：之前入口藏在词库列表页，
            // 加上「开始学习」已直达背诵，实际上很难再走到那里
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiquidButton(
                    onClick = onOpenWrong,
                    backdrop = backdrop,
                    height = 46.dp,
                    surfaceColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "错题本 $wrongTotal",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
                LiquidButton(
                    onClick = onOpenFavorite,
                    backdrop = backdrop,
                    height = 46.dp,
                    surfaceColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "收藏夹 $favoriteTotal",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/**
 * 首页搜索框：常驻在顶栏下方、列表之外。
 *
 * 输入框节点与列表解耦，筛选结果增删不打断输入；键盘动作为 ImeAction.Search，
 * 清空按钮不夺走焦点，交互全程保持输入状态。
 */
@Composable
private fun HomeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    // lambda 必须稳定：BasicTextField 重组时不应重建键盘动作
    val keyboardActions = remember(focusManager) {
        KeyboardActions(onSearch = {
            // 用户主动提交搜索后才收起输入法，焦点交给系统清理
            focusManager.clearFocus()
        })
    }
    val keyboardOptions = remember { KeyboardOptions(imeAction = ImeAction.Search) }

    LiquidCard(
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp),
        shape = Capsule,
        blurRadius = 8.dp,
        lensHeight = 12.dp,
        lensAmount = 22.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isBlank()) {
                    Text(
                        "搜索单词或词库…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                )
            }
            if (value.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        // 清空后把焦点还给输入框，用户可以接着输入
                        .clip(CircleShape)
                        .clickable {
                            onValueChange("")
                            focusRequester.requestFocus()
                        }
                        .padding(2.dp),
                )
            }
        }
    }
}

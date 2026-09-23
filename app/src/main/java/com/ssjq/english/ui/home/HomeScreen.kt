@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.home

import android.content.Intent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssjq.english.R
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.LibraryCategory
import com.ssjq.english.data.LibrarySubcategory
import com.ssjq.english.data.SearchResultItem
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.UserManager
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ssjq.english.ui.glass.Capsule
import com.ssjq.english.ui.glass.LiquidButton
import com.ssjq.english.ui.glass.LiquidCard
import com.ssjq.english.ui.glass.LiquidToggle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onPickDatabase: (String) -> Unit,
    onOpenCheckIn: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenCrazyQuiz: () -> Unit = {},
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
            putExtra(Intent.EXTRA_TEXT, "来和我一起用这款背单词 App 学习英语吧！")
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
    // 修改用户名弹窗
    var showUsernameDialog by remember { mutableStateOf(false) }
    var currentUsername by remember { mutableStateOf(UserManager.getUsername() ?: "") }
    var newUsername by remember { mutableStateOf(currentUsername) }
    var usernameError by remember { mutableStateOf("") }
    // 主题模式 - 响应式观察 Flow 变化
    val darkThemeMode by UserManager.darkThemeFlow.collectAsState()

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
    val savedOrder = remember(allDbs, orderVersion) { UserLibrary.categoryOrder() }

    // 打卡统计：stats() 要解析 SP 里的 JSON，较重，放到 IO 线程；
    // 并在从打卡/学习页返回主页时刷新（学习行为会触发自动打卡）
    val homeScope = rememberCoroutineScope()
    var checkInStats by remember { mutableStateOf(CheckInManager.EmptyStats) }
    fun refreshCheckInStats() {
        homeScope.launch {
            withContext(Dispatchers.IO) { checkInStats = CheckInManager.stats() }
        }
    }
    LaunchedEffect(Unit) { refreshCheckInStats() }
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
        // 轻微延迟去抖
        kotlinx.coroutines.delay(200)
        wordSearchResults = withContext(Dispatchers.IO) {
            DatabaseManager.searchAllDbs(context, allDbs, filter, limitPerDb = 8)
        }
        wordSearchLoading = false
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // 液态玻璃：背景采样源，玻璃卡片将折射/模糊它
    val liquidBackdrop = rememberLayerBackdrop()

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

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层：渐变 + 彩色光斑（玻璃折射的采样源）
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
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
            Box(
                Modifier
                    .size(220.dp)
                    .offset(x = (-50).dp, y = 140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            )
            Box(
                Modifier
                    .size(180.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 70.dp, y = 280.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
            )
            Box(
                Modifier
                    .size(150.dp)
                    .offset(x = 60.dp, y = 520.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
            )
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        if (isEditingOrder) {
                            Text("调整分类顺序", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        if (!isEditingOrder && !hasFilter) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenAbout() }
                                    .padding(8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.walnut_body),
                                        contentDescription = "关于我",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(25.dp)
                                            .clip(CircleShape),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "关于我&检查更新",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
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
                            IconButton(onClick = { shareApp() }) {
                                Icon(Icons.Filled.Share, "分享")
                            }
                            IconButton(onClick = {
                                isEditingOrder = true
                                editedOrder = orderedCatalog.map { it.name }
                            }) {
                                Icon(Icons.Filled.SwapVert, "排序")
                            }
                        }
                    },
                )
            }
        ) { padding ->
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                // 深色模式：顶部迷你设置条（三态：跟随系统 / 深色 / 浅色）
                if (showEntryCards) item {
                    // lambda 必须稳定：LiquidToggle 内部以 selected 为 LaunchedEffect 的 key，
                    // 每次重组新建 lambda 会导致内部状态/动画被反复重置
                    val toggleSelected = remember { { darkThemeMode == true } }
                    val toggleOnSelect = remember { { v: Boolean -> UserManager.setDarkThemeMode(v) } }
                    LiquidCard(
                        backdrop = liquidBackdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
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
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when (darkThemeMode) {
                                    true -> "深色"
                                    false -> "浅色"
                                    null -> "跟随系统"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    }
                }

                // 当前学习词库 + 开始学习：提升到列表最前，确保首屏即可点击，无需滚动
                if (currentBook != null && !showFullCatalog) item {
                    CurrentBookPanel(
                        backdrop = liquidBackdrop,
                        dbName = currentBook!!,
                        onStartStudy = { pickBook(currentBook!!) },
                        onBrowseAll = { showAllBooks = true },
                    )
                }

                // 仪表盘：液态玻璃按钮，点击可修改用户名
                if (showEntryCards) item {
                    LiquidButton(
                        onClick = { openEditUsername() },
                        backdrop = liquidBackdrop,
                        height = 104.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.walnut_body),
                            contentDescription = "修改用户名",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentUsername,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Text(
                                "欢迎背单词",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "共收录 ${allDbs.size} 个词库，点击下方任意词库开始学习",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "修改用户名",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // 疯狂刷题入口（液态玻璃按钮）
                if (showEntryCards) item {
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
                item {
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
                if (showEntryCards) item {
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

                // 搜索框：液态玻璃胶囊
                if (!isEditingOrder) item {
                    LiquidCard(
                        backdrop = liquidBackdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(56.dp),
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
                                if (filter.isBlank()) {
                                    Text(
                                        "搜索单词或词库…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    )
                                }
                                BasicTextField(
                                    value = filter,
                                    onValueChange = { filter = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    singleLine = true,
                                )
                            }
                            if (filter.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "清空",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { filter = "" },
                                )
                            }
                        }
                    }
                }

                if (hasFilter) {
                    // 单词搜索结果（跨所有词库）
                    if (wordSearchLoading) {
                        item {
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
                                onClick = { pickBook("${item.dbName}.db") },
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
                                    name = db,
                                    group = fullName.substringAfter(" · "),
                                    wordCount = wordCounts[db],
                                    onClick = { pickBook("$db.db") },
                                    backdrop = liquidBackdrop,
                                )
                            }
                        }
                    }

                    // 完全没有结果
                    if (!wordSearchLoading && wordSearchResults.isEmpty() && searchResults.isEmpty()) {
                        item {
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
                    item {
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
                    item {
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
                                            name = db,
                                            group = sub.name,
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
            onResult = { },
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
                        shape = RoundedCornerShape(12.dp),
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
        shape = RoundedCornerShape(18.dp),
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
                    category.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
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
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        height = 68.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
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
        // 所属词库标签
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                item.dbName,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
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
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        height = 62.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        height = 52.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
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

/** 词库卡片：液态玻璃按钮，圆形图标 + 名称 + 描述 + 右箭头 */
@Composable
private fun LibraryCard(
    name: String,
    group: String,
    wordCount: Int?,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        height = 72.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (wordCount != null) "$group 词库 · 共 $wordCount 词"
                else "$group 词库 · 加载中…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * 当前学习词库面板：注册时选定词库后，主界面只呈现这一本，
 * 避免首屏罗列全部分类造成的拥挤。可随时更换或浏览全部词库。
 */
@Composable
private fun CurrentBookPanel(
    backdrop: Backdrop,
    dbName: String,
    onStartStudy: () -> Unit,
    onBrowseAll: () -> Unit,
) {
    LiquidCard(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
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
            Spacer(Modifier.height(10.dp))
            Text(
                dbName.removeSuffix(".db"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
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
            LiquidButton(
                onClick = onBrowseAll,
                backdrop = backdrop,
                height = 46.dp,
                surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("更换词库 / 浏览全部", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            }
        }
    }
}

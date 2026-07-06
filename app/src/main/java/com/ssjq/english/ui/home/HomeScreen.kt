@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.home
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssjq.english.R
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.LibraryCategory
import com.ssjq.english.data.LibrarySubcategory
import com.ssjq.english.data.SearchResultItem
import com.ssjq.english.data.UserLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onPickDatabase: (String) -> Unit,
    onOpenCheckIn: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        allDbs = withContext(Dispatchers.IO) { DatabaseManager.listAssetDatabases(context) }
    }

    // 分类目录（按实际可用 db 过滤）
    val catalog = remember(allDbs) { LibraryCatalog.buildCatalogForAvailableDbs(allDbs) }

    // 异步加载各词库单词总数：db名(不带.db) → 单词数
    var wordCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(allDbs) {
        if (allDbs.isEmpty()) return@LaunchedEffect
        val counts = mutableMapOf<String, Int>()
        // 顺序加载（避免同时打开过多 db 连接）；每加载完一个就更新 UI
        for (dbName in allDbs) {
            val pure = dbName.removeSuffix(".db")
            counts[pure] = withContext(Dispatchers.IO) {
                DatabaseManager.getWordCount(context, dbName)
            }
            wordCounts = counts.toMap()
        }
    }

    // 读取保存的分类顺序，用于对 catalog 排序。保存后通过 orderVersion 触发重读
    var orderVersion by remember { mutableStateOf(0) }
    val savedOrder = remember(allDbs, orderVersion) { UserLibrary.categoryOrder() }

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    if (isEditingOrder) {
                        Text("调整分类顺序", fontWeight = FontWeight.Bold)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.leafbunch1),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(56.dp)
                                    .rotate(-12f),
                            )
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(R.drawable.selectorscreenwoodsign1),
                                contentDescription = "英语词库",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.height(48.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(R.drawable.leafbunch2),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(56.dp)
                                    .rotate(12f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!isEditingOrder && !hasFilter) {
                        Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
        .clip(RoundedCornerShape(8.dp)) // 可选：加上圆角让点击涟漪更好看
        .clickable { onOpenAbout() }    // 用 clickable 代替 IconButton
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
                    if (hasFilter) return@LargeTopAppBar
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
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 仪表盘卡片：渐变背景 + 大字号问候
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    ),
                                )
                            )
                            .padding(24.dp),
                    ) {
                        Column {
                            Text(
                                "欢迎背单词",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "共收录 ${allDbs.size} 个词库，选择一个开始学习",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            // 每日打卡入口卡片
            item {
                val stats = remember { CheckInManager.stats() }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { onOpenCheckIn() },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (stats.isCheckedInToday)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (stats.isCheckedInToday)
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment, null,
                                tint = if (stats.isCheckedInToday)
                                    MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (stats.isCheckedInToday) "今日已打卡 · 累计 ${stats.currentStreak} 天"
                                else "今日还未打卡 · 去学习打卡",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (stats.isCheckedInToday)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "累计 ${stats.totalDays} 天 · ${stats.totalWordsLearned} 词 · 最长 ${stats.longestStreak} 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (stats.isCheckedInToday)
                                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                            tint = if (stats.isCheckedInToday)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // 搜索框：无边框胶囊形态
            item {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜索单词或词库…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
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
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(wordSearchResults, key = { "${it.dbName}-${it.wordId}" }) { item ->
                        WordSearchResultCard(
                            item = item,
                            onClick = { onPickDatabase("${item.dbName}.db") },
                        )
                    }
                }

                // 词库搜索结果
                if (searchResults.isNotEmpty()) {
                    item(key = "lib-search-header") {
                        Text(
                            "词库结果",
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    searchResults.forEach { (fullName, dbs) ->
                        item(key = "s-$fullName") {
                            Text(
                                fullName,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(dbs, key = { it }) { db ->
                            LibraryCard(
                                name = db,
                                group = fullName.substringAfter(" · "),
                                wordCount = wordCounts[db],
                                onClick = { onPickDatabase("$db.db") },
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
                        "长按或拖动可调整顺序，点击上/下箭头移动",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(editingOrderNames, key = { _, name -> "edit-$name" }) { index, catName ->
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
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                                )
                            }
                            if (subExpanded) {
                                items(sub.dbFiles, key = { "$subKey-$it" }) { db ->
                                    LibraryCard(
                                        name = db,
                                        group = sub.name,
                                        wordCount = wordCounts[db],
                                        onClick = { onPickDatabase("$db.db") },
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

/** 排序模式下的分类项：带上下箭头按钮 */
@Composable
private fun OrderableCategoryItem(
    category: LibraryCategory,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val totalDbs = category.subcategories.sumOf { it.dbFiles.size }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 拖拽手柄
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = "拖拽",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
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
            // 上移按钮
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "上移",
                    tint = if (index > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp),
                )
            }
            // 下移按钮
            IconButton(
                onClick = onMoveDown,
                enabled = index < total - 1,
                modifier = Modifier.size(36.dp),
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

/** 单词搜索结果卡片：单词 + 释义 + 所属词库标签 */
@Composable
private fun WordSearchResultCard(item: SearchResultItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MenuBook, null,
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            // 所属词库标签
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    item.dbName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** 分类标题：大卡片样式，点击展开/折叠 */
@Composable
private fun CategoryHeader(
    category: LibraryCategory,
    expanded: Boolean,
    wordCounts: Map<String, Int>,
    onClick: () -> Unit,
) {
    val totalDbs = category.subcategories.sumOf { it.dbFiles.size }
    val totalWords = category.subcategories.flatMap { it.dbFiles }.sumOf { wordCounts[it] ?: 0 }
    val allLoaded = category.subcategories.flatMap { it.dbFiles }.all { it in wordCounts }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 300f)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (allLoaded && totalWords > 0) "$totalWords 词 · $totalDbs 库"
                else "$totalDbs 个词库",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/** 子分类卡片：次级标题，点击展开显示具体词库 */
@Composable
private fun SubcategoryCard(
    subcategory: LibrarySubcategory,
    expanded: Boolean,
    wordCounts: Map<String, Int>,
    onClick: () -> Unit,
) {
    // 子分类下所有词库的单词总数（已加载的部分累加，未加载的不计）
    val totalWords = subcategory.dbFiles.sumOf { wordCounts[it] ?: 0 }
    val allLoaded = subcategory.dbFiles.all { it in wordCounts }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(6.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            subcategory.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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

/** 词库卡片：圆形图标 + 名称 + 描述 + 右箭头 */
@Composable
private fun LibraryCard(
    name: String,
    group: String,
    wordCount: Int?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 圆形词库图标
            Box(
                modifier = Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MenuBook, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
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
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

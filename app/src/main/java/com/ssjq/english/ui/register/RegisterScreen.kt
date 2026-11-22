@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.register

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.LibraryCategory
import com.ssjq.english.data.LibrarySubcategory
import com.ssjq.english.data.UserManager
import com.ssjq.english.ui.glass.GlassShapes
import com.ssjq.english.ui.glass.LiquidButton
import com.ssjq.english.ui.common.LiquidGlassCard
import com.ssjq.english.ui.common.LiquidGlassListItem
import com.ssjq.english.ui.common.LiquidGlassSearchBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 注册流程分两步：
 * 1. 输入用户名
 * 2. 选择要学习的词库（选定后主界面只围绕该词库展开，之后可随时更换）
 */
@Composable
fun RegisterScreen(onRegisterComplete: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var selectedBook by remember { mutableStateOf<String?>(null) }
    var allDbs by remember { mutableStateOf<List<String>>(emptyList()) }
    var dbsLoaded by remember { mutableStateOf(false) }
    var dbsError by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var bookError by remember { mutableStateOf("") }
    val liquidBackdrop = rememberLayerBackdrop()

    // 修复（界面 H-2）：原先直接调用 listAssetDatabases，assets 读取异常会抛到
    // Composable 协程里直接崩溃；同时加载中/加载失败/确实没有词库三种情况在界面上
    // 完全一样（都是空白列表），用户只能卡在第 2 步无从下手。
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { DatabaseManager.listAssetDatabases(context) } }
            .onSuccess { allDbs = it }
            .onFailure { dbsError = "词库清单加载失败：${it.message ?: "未知错误"}" }
        dbsLoaded = true
    }

    // 搜索同时匹配中文分类名 / 子分类名 / 中文册名与原始文件名：
    // 旧实现只按原始文件名（如 BeiShiGaoZhong_1）过滤，用户输入「北师大」「高中」搜不到词库
    val filteredDbs = remember(allDbs, query) {
        if (query.isBlank()) allDbs
        else allDbs.filter { LibraryCatalog.matchesQuery(it, query) }
    }

    // 按「分类 → 子分类」两级分组的词库列表，供列表渲染中文标题
    val groupedDbs = remember(filteredDbs) {
        val known = LibraryCatalog.buildCatalogForAvailableDbs(filteredDbs)
        val listed = known.flatMap { cat -> cat.subcategories.flatMap { it.dbFiles } }.toSet()
        val rest = filteredDbs.filter { it.removeSuffix(".db") !in listed }
        if (rest.isEmpty()) known
        else known + LibraryCategory(
            name = "其他词库",
            subcategories = listOf(LibrarySubcategory("未登记词库", rest)),
        )
    }

    // 修复（界面 H-1）：注册流程没有返回键拦截，第 2 步误触系统返回键会直接退出
    // 已填的用户名与已选词库全部丢失，用户下次启动又要从第 1 步重来。
    // 这里接管返回键：第 2 步退回第 1 步并保留输入；第 1 步才走「再按一次退出」。
    var lastBackPressAt by remember { mutableStateOf(0L) }
    BackHandler {
        if (step > 0) {
            step = 0
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressAt < 2000) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressAt = now
                Toast.makeText(context, "再按一次退出注册", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层：渐变 + 彩色光斑（玻璃组件的采样源）
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
                    .offset(x = (-50).dp, y = 100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            )
            Box(
                Modifier
                    .size(160.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = 300.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
            )
            Box(
                Modifier
                    .size(140.dp)
                    .offset(x = 30.dp, y = 600.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
            )
        }

        if (step == 0) {
            UsernameStep(
                backdrop = liquidBackdrop,
                username = username,
                error = error,
                onUsernameChange = { username = it; error = "" },
                onNext = {
                    when {
                        username.trim().isBlank() -> error = "请输入用户名"
                        username.trim().length < 2 -> error = "用户名至少需要2个字符"
                        else -> step = 1
                    }
                },
            )
        } else {
            PickBookStep(
                backdrop = liquidBackdrop,
                groups = groupedDbs,
                totalCount = filteredDbs.size,
                loading = !dbsLoaded,
                loadError = dbsError,
                query = query,
                onQueryChange = { query = it },
                selectedBook = selectedBook,
                bookError = bookError,
                onSelect = {
                    selectedBook = it
                    bookError = ""   // 选中后清除错误提示
                },
                onConfirm = {
                    val book = selectedBook
                    if (book != null) {
                        UserManager.setUsername(username.trim())
                        UserManager.setCurrentBook(book)
                        UserManager.setLastStudyDb(book)
                        UserManager.markLaunched()
                        onRegisterComplete()
                    } else {
                        // 旧实现：未选词库时点击「开始学习」静默无反应，
                        // 用户无法进入主界面且毫无提示
                        bookError = "请先选择一个词库"
                    }
                },
                onBack = { step = 0 },
            )
        }
    }
}

@Composable
private fun UsernameStep(
    backdrop: com.kyant.backdrop.Backdrop,
    username: String,
    error: String,
    onUsernameChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "欢迎使用",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "请输入用户名开始学习",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        LiquidGlassCard(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("输入用户名") },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                shape = GlassShapes.Item,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                singleLine = true,
                isError = error.isNotBlank(),
                supportingText = if (error.isNotBlank()) {
                    { Text(error, color = MaterialTheme.colorScheme.error) }
                } else null,
            )
        }
        Spacer(Modifier.height(24.dp))
        LiquidButton(
            onClick = onNext,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("下一步：选择词库", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun PickBookStep(
    backdrop: com.kyant.backdrop.Backdrop,
    groups: List<LibraryCategory>,
    totalCount: Int,
    loading: Boolean,
    loadError: String,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedBook: String?,
    bookError: String,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val totalWordsHint = remember(totalCount) { "共 $totalCount 本词库，按分类展开" }
    // 折叠状态：默认全部收起，避免一次性铺开上百本词库；
    // 分类与子分类各用一套 key 集合，便于「收起分类时连带收起子分类」
    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedSubcategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 40.dp, bottom = 20.dp),
    ) {
        Text(
            "选择要学习的词库",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "选定后主界面只显示这本词库，之后随时可以更换",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (totalCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                totalWordsHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(20.dp))

        if (bookError.isNotBlank()) {
            Text(
                bookError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
        }

        LiquidGlassSearchBar(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                placeholder = { Text("搜索词库…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空搜索")
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = GlassShapes.Panel,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        Spacer(Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            // 修复（界面 H-2）：加载中 / 加载失败 / 无匹配结果给出明确提示，
            // 不再是一片空白让用户以为卡死
            if (groups.isEmpty()) {
                item(key = "book-list-state") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                loading -> "正在加载词库…"
                                loadError.isNotBlank() -> loadError
                                else -> "没有匹配的词库，请换个关键词，或重新安装应用后再试"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (loadError.isNotBlank() && !loading) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            // 按中文分类分组渲染：旧实现是一长串英文文件名平铺，
            // 用户既看不出「BeiShiGaoZhong_1」是哪个版本，也难以定位。
            // 现改为「分类 → 子分类 → 词库册」三级折叠：默认全部收起，
            // 点分类行才展开该分类，再点子分类行才铺开具体词库册；
            // 搜索状态下不做折叠，直接平铺全部匹配结果。
            val searchMode = query.isNotBlank()
            if (searchMode) {
                groups.forEach { category ->
                    category.subcategories.forEach { sub ->
                        items(sub.dbFiles, key = { "reg-search-$it" }) { db ->
                            BookRow(
                                backdrop = backdrop,
                                db = db,
                                selected = db == selectedBook,
                                onClick = {
                                    focusManager.clearFocus()
                                    onSelect(db)
                                },
                            )
                        }
                    }
                }
            } else {
                groups.forEach { category ->
                    val catKey = category.name
                    val catOpen = catKey in expandedCategories
                    val catBookCount = category.subcategories.sumOf { it.dbFiles.size }
                    item(key = "reg-cat-$catKey") {
                        RegCategoryRow(
                            backdrop = backdrop,
                            name = catKey.substringBefore("（").trim(),
                            bookCount = catBookCount,
                            expanded = catOpen,
                            onClick = {
                                expandedCategories = if (catOpen) {
                                    // 收起分类时同步收起其下子分类，保持层级一致
                                    expandedSubcategories =
                                        expandedSubcategories.filterNot { it.startsWith("$catKey::") }.toSet()
                                    expandedCategories - catKey
                                } else {
                                    expandedCategories + catKey
                                }
                            },
                        )
                    }
                    if (catOpen) {
                        category.subcategories.forEach { sub ->
                            val subKey = "$catKey::${sub.name}"
                            val subOpen = subKey in expandedSubcategories
                            item(key = "reg-sub-$subKey") {
                                RegSubcategoryRow(
                                    backdrop = backdrop,
                                    name = sub.name,
                                    bookCount = sub.dbFiles.size,
                                    expanded = subOpen,
                                    onClick = {
                                        expandedSubcategories = if (subOpen) {
                                            expandedSubcategories - subKey
                                        } else {
                                            expandedSubcategories + subKey
                                        }
                                    },
                                )
                            }
                            if (subOpen) {
                                items(sub.dbFiles, key = { "reg-$it" }) { db ->
                                    BookRow(
                                        backdrop = backdrop,
                                        db = db,
                                        selected = db == selectedBook,
                                        onClick = {
                                            // 选中后收起软键盘，避免键盘挡住列表下半部分导致点错相邻词库
                                            focusManager.clearFocus()
                                            onSelect(db)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = selectedBook?.let { "已选择：${LibraryCatalog.displayParts(it).bookName}" }
                ?: "尚未选择词库，点击上方任意词库即可选中",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selectedBook != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selectedBook != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = onBack, backdrop = backdrop, modifier = Modifier.weight(1f)) {
                // 按钮文字禁止换行/省略：旧版窄屏下「开始学习」被挤成「开始学…」
                Text("上一步", fontSize = 15.sp, maxLines = 1, softWrap = false)
            }
            LiquidButton(
                onClick = onConfirm,
                backdrop = backdrop,
                modifier = Modifier.weight(1.6f),
            ) {
                Text(
                    "开始学习",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** 分类行：点击展开/收起该分类下的子分类（默认收起） */
@Composable
private fun RegCategoryRow(
    backdrop: com.kyant.backdrop.Backdrop,
    name: String,
    bookCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (expanded) "已展开 · 共 $bookCount 本" else "共 $bookCount 本 · 点击展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

/** 子分类行：点击展开/收起该子分类下的具体词库册 */
@Composable
private fun RegSubcategoryRow(
    backdrop: com.kyant.backdrop.Backdrop,
    name: String,
    bookCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier.padding(start = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$bookCount 本",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/**
 * 词库选择列表项。
 *
 * 行高固定下限 64dp 保证整行都是可点区域，配合 10dp 行间距降低长列表滚动时的误触；
 * 主标题使用词库的中文分类名，词库文件名降为副标题，长名称最多两行并省略，
 * 避免像旧实现那样把名称挤在同一行读不出来。
 */
@Composable
private fun BookRow(
    backdrop: com.kyant.backdrop.Backdrop,
    db: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val parts = LibraryCatalog.displayParts(db)
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        selected = selected,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 标题只显示中文册名（如「北师大版高中 10」），不再出现 BeiShiGaoZhong_10 这类英文文件名
                Text(
                    parts.bookName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // 标题已含子分类名，副标题只补充所属分类（如「高中英语」）；
                    // 未登记的词库写明来源，避免误以为是官方词库
                    if (parts.mapped) parts.category else "未登记词库（按文件名显示）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .then(
                        if (selected) {
                            Modifier
                        } else {
                            Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                shape = CircleShape,
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "已选择",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

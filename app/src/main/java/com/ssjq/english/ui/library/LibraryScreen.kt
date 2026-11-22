@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.library

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ssjq.english.ui.glass.LocalAppBackdrop
import com.ssjq.english.ui.glass.glassTopBar
import com.ssjq.english.ui.glass.glassTopBarColors
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.R
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.WordEntry
import com.ssjq.english.ui.common.LiquidGlassListItem
import com.ssjq.english.ui.nav.LibraryType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext

/**
 * 用户词单页：错题本 / 收藏夹共用。
 * 错题本按 addedAt 升序（先错先复习），收藏夹按 addedAt 降序（最新在前）。
 */
@Composable
fun LibraryScreen(
    dbName: String,
    type: LibraryType,
    onBack: () -> Unit,
    /** 必须带词库名：全部词库视图下条目来自不同词库，只传 wordId 会打开错词 */
    onWordClick: (dbName: String, wordId: String) -> Unit,
    /** 进入时是否直接看全部词库；从主页入口进来时为 true */
    initialShowAll: Boolean = false,
) {
    val title = when (type) {
        LibraryType.WRONG -> "错题本"
        LibraryType.FAVORITE -> "收藏夹"
    }
    // 用 mutableStateOf 触发重组；删除时刷新。
    // key 必须带上 dbName/type：旧实现无 key，切换词库或切换类型时不会重新
    // 加载，界面上显示的仍是上一份数据
    // 先给空列表，避免组合期同步解析错题/收藏 JSON 阻塞主线程
    // 跨词库测验的错题分散在多本词库里，默认只看当前词库，可切换为全部
    var showAllDbs by remember { mutableStateOf(initialShowAll) }
    var entries by remember(dbName, type, showAllDbs) { mutableStateOf<List<WordEntry>>(emptyList()) }
    var loadError by remember(dbName, type, showAllDbs) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 修复（界面 H-3）：删除错题/收藏原先点一下就立即执行且不可恢复，
    // 误触即永久丢失学习记录。改为先弹二次确认，确认后才落盘。
    var pendingDelete by remember { mutableStateOf<WordEntry?>(null) }

    suspend fun reloadEntries() {
        val result = withContext(Dispatchers.IO) {
            runCatching { loadEntries(dbName, type, showAllDbs) }
                .fold(
                    onSuccess = { it to null },
                    onFailure = { emptyList<WordEntry>() to "词单读取失败，已按空列表显示" },
                )
        }
        entries = result.first
        loadError = result.second
    }
    LaunchedEffect(dbName, type, showAllDbs) { reloadEntries() }

    fun refresh() { scope.launch { reloadEntries() } }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // 全应用共享的背景采样层
    val liquidBackdrop = LocalAppBackdrop.current

    // 修复（界面 H-3）：删除前的二次确认；删除本身放 runCatching，
    // 旧实现协程里未捕获的异常会直接抛到主线程导致闪退，
    // 且失败时界面还会显示「已删除」的假象。
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (type == LibraryType.WRONG) "移出错题本？" else "取消收藏？") },
            text = {
                Text(
                    "「${target.headWord}」将从${if (type == LibraryType.WRONG) "错题本" else "收藏夹"}中移除，" +
                        "此操作不可撤销。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            val error = withContext(Dispatchers.IO) {
                                runCatching {
                                    // 全部词库视图下必须按条目自身所属词库删除
                                    when (type) {
                                        LibraryType.WRONG ->
                                            UserLibrary.removeWrong(target.dbName, target.wordId)
                                        LibraryType.FAVORITE ->
                                            UserLibrary.removeFavorite(target.dbName, target.wordId)
                                    }
                                }.exceptionOrNull()
                            }
                            if (error != null) {
                                if (error is CancellationException) throw error
                                Toast.makeText(context, "删除失败：${error.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                            }
                            reloadEntries()
                        }
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.glassTopBar(liquidBackdrop),
                colors = glassTopBarColors(),
                title = { Text("$title (${entries.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showAllDbs = !showAllDbs }) {
                        Text(
                            if (showAllDbs) "全部词库" else "本词库",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                Text(
                    loadError ?: when {
                        showAllDbs && type == LibraryType.WRONG -> "所有词库都还没有错题"
                        showAllDbs -> "所有词库都还没有收藏"
                        type == LibraryType.WRONG -> "当前词库暂无错题，点右上角切换为「全部词库」"
                        else -> "当前词库暂无收藏，点右上角切换为「全部词库」"
                    },
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 不同词库可能存在相同 word_id，key 同时带词库名与序号
                itemsIndexed(entries, key = { index, entry -> "$index-${entry.dbName}-${entry.wordId}" }) { _, entry ->
                    LibraryRow(
                        entry = entry,
                        type = type,
                        backdrop = liquidBackdrop,
                        onClick = { onWordClick(entry.dbName, entry.wordId) },
                        onDelete = { pendingDelete = entry },
                    )
                }
            }
        }
    }
    }
}

private fun loadEntries(dbName: String, type: LibraryType, allDbs: Boolean): List<WordEntry> = when (type) {
    LibraryType.WRONG -> if (allDbs) UserLibrary.allWrongWords() else UserLibrary.wrongWords(dbName)
    LibraryType.FAVORITE -> if (allDbs) UserLibrary.allFavorites() else UserLibrary.favorites(dbName)
}

@Composable
private fun LibraryRow(
    entry: WordEntry,
    type: LibraryType,
    backdrop: Backdrop,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    // 列表项使用轻量玻璃：每项都走 drawBackdrop 会产生 3~4 个 GraphicsLayer
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        ListItem(
            leadingContent = {
                val color = when (type) {
                    LibraryType.WRONG -> MaterialTheme.colorScheme.error
                    LibraryType.FAVORITE -> MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier.size(14.dp).clip(CircleShape).background(color)
                )
            },
            headlineContent = {
                Text(
                    entry.headWord,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            supportingContent = {
                entry.tranCn?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // 全部词库视图里条目来自不同词库，标出中文词库名才看得出来源
                Spacer(Modifier.height(2.dp))
                Text(
                    LibraryCatalog.displayName(entry.dbName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (type == LibraryType.FAVORITE) {
                        Image(
                            painter = painterResource(R.drawable.diamond),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

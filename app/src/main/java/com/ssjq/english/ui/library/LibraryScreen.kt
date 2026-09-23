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
import androidx.compose.foundation.lazy.items
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
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.R
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.WordEntry
import com.ssjq.english.ui.common.LiquidGlassCard
import com.ssjq.english.ui.nav.LibraryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 用户词单页：错题本 / 收藏夹共用。
 * 错题本按 addedAt 升序（先错先复习），收藏夹按 addedAt 降序（最新在前）。
 */
@Composable
fun LibraryScreen(
    dbName: String,
    type: LibraryType,
    onBack: () -> Unit,
    onWordClick: (String) -> Unit,
) {
    val title = when (type) {
        LibraryType.WRONG -> "错题本"
        LibraryType.FAVORITE -> "收藏夹"
    }
    // 用 mutableStateOf 触发重组；删除时刷新。
    // key 必须带上 dbName/type：旧实现无 key，切换词库或切换类型时不会重新
    // 加载，界面上显示的仍是上一份数据
    // 先给空列表，避免组合期同步解析错题/收藏 JSON 阻塞主线程
    var entries by remember(dbName, type) { mutableStateOf<List<WordEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

    suspend fun reloadEntries() {
        val data = withContext(Dispatchers.IO) { loadEntries(dbName, type) }
        entries = data
    }
    LaunchedEffect(dbName, type) { reloadEntries() }

    fun refresh() { scope.launch { reloadEntries() } }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // 液态玻璃背景采样源
    val liquidBackdrop = rememberLayerBackdrop()

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
                    .size(180.dp)
                    .offset(x = (-40).dp, y = 100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            )
            Box(
                Modifier
                    .size(140.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = 200.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
            )
        }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = { Text("$title (${entries.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                Text(
                    when (type) {
                        LibraryType.WRONG -> "暂无错题，背诵时点击「不认识」会自动加入"
                        LibraryType.FAVORITE -> "暂无收藏，点击单词详情页顶栏的星标即可加入"
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
                items(entries, key = { it.wordId }) { entry ->
                    LibraryRow(
                        entry = entry,
                        type = type,
                        backdrop = liquidBackdrop,
                        onClick = { onWordClick(entry.wordId) },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    when (type) {
                                        LibraryType.WRONG -> UserLibrary.removeWrong(dbName, entry.wordId)
                                        LibraryType.FAVORITE -> UserLibrary.removeFavorite(dbName, entry.wordId)
                                    }
                                }
                                reloadEntries()
                            }
                        },
                    )
                }
            }
        }
    }
    }
}

private fun loadEntries(dbName: String, type: LibraryType): List<WordEntry> = when (type) {
    LibraryType.WRONG -> UserLibrary.wrongWords(dbName)
    LibraryType.FAVORITE -> UserLibrary.favorites(dbName)
}

@Composable
private fun LibraryRow(
    entry: WordEntry,
    type: LibraryType,
    backdrop: Backdrop,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    LiquidGlassCard(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        blurRadius = 6.dp,
        lensHeight = 8.dp,
        lensAmount = 14.dp,
        surfaceColor = Color.White.copy(alpha = 0.15f),
        shadow = glassShadow(10.dp, 0.1f),
        highlight = Highlight.Default.copy(alpha = 0.5f),
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

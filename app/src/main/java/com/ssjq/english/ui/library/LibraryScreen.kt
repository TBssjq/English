@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.library

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssjq.english.R
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.WordEntry
import com.ssjq.english.ui.nav.LibraryType

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
    // 用 mutableStateOf 触发重组；删除时刷新
    var entries by remember { mutableStateOf(loadEntries(dbName, type)) }

    fun refresh() { entries = loadEntries(dbName, type) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                        onClick = { onWordClick(entry.wordId) },
                        onDelete = {
                            when (type) {
                                LibraryType.WRONG -> UserLibrary.removeWrong(dbName, entry.wordId)
                                LibraryType.FAVORITE -> UserLibrary.removeFavorite(dbName, entry.wordId)
                            }
                            refresh()
                        },
                    )
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.search

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ssjq.english.ui.glass.GlassShapes
import com.ssjq.english.ui.glass.LocalAppBackdrop
import com.ssjq.english.ui.glass.glassTopBar
import com.ssjq.english.ui.glass.glassTopBarColors
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.WordDetail
import com.ssjq.english.ui.common.LiquidGlassListItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    dbName: String,
    onBack: () -> Unit,
    onWordClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WordDetail>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // 全应用共享的背景采样层
    val liquidBackdrop = LocalAppBackdrop.current

    // 上一次查询的任务与令牌：快速连续搜索时，慢返回的旧结果会覆盖新结果
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchToken by remember { mutableStateOf(0) }

    fun doSearch() {
        if (keyword.isBlank()) return
        searching = true
        keyboard?.hide()
        // 取消上一次查询，避免乱序覆盖
        searchJob?.cancel()
        val token = ++searchToken
        searchJob = scope.launch {
            try {
                val kw = keyword.trim()
                results = withContext(Dispatchers.IO) {
                    val db = DatabaseManager.openDatabase(context, dbName)
                    try {
                        DatabaseManager.searchWords(db, kw, dbName = dbName)
                    } finally {
                        DatabaseManager.release(db)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                results = emptyList()
                Toast.makeText(context, "词库读取失败", Toast.LENGTH_SHORT).show()
            } finally {
                // 只有「自己仍是最新一次查询」时才收起转圈：
                // 被取消的旧任务若也来重置，会把新查询的转圈提前关掉。
                if (token == searchToken) searching = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.glassTopBar(liquidBackdrop),
                colors = glassTopBarColors(),
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("输入单词或片段…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = GlassShapes.Item,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            )

            if (searching) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            } else {
                if (results.isNotEmpty()) {
                    Text(
                        "找到 ${results.size} 条结果",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 跨词库搜索时 word_id 可能重复，key 必须带词库名
                    items(results, key = { "${it.dbName}-${it.wordId}" }) { word ->
                        SearchResultCard(word = word, backdrop = liquidBackdrop, onClick = { onWordClick(word.wordId) })
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SearchResultCard(word: WordDetail, backdrop: Backdrop, onClick: () -> Unit) {
    // 列表项使用轻量玻璃：每项都走 drawBackdrop 会产生 3~4 个 GraphicsLayer
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.headWord, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                val phone = word.usPhone?.takeIf { it.isNotBlank() }?.let { "美 /$it/" }
                if (phone != null) {
                    Text(phone, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (word.trans.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    word.trans.joinToString("；") {
                        listOfNotNull(it.pos, it.tranCn).joinToString(" ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
            }
        }
    }
}

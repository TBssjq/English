@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.search

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

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
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.WordDetail
import com.ssjq.english.ui.common.LiquidGlassCard
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
    // 液态玻璃背景采样源
    val liquidBackdrop = rememberLayerBackdrop()

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
                        DatabaseManager.searchWords(db, kw)
                    } finally {
                        DatabaseManager.release(db)
                    }
                }
            } catch (_: Exception) {
                // 查询失败时保留上一次结果，不让列表莫名清空
            } finally {
                // 必须放 finally，否则异常时 searching 永远为 true，转圈永久卡死。
                // 且只有「自己仍是最新一次查询」时才收起转圈：
                // 被取消的旧任务若也来重置，会把新查询的转圈提前关掉。
                if (token == searchToken) searching = false
            }
        }
    }

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
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
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
                shape = RoundedCornerShape(16.dp),
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
                    items(results, key = { it.wordId }) { word ->
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
    LiquidGlassCard(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        blurRadius = 6.dp,
        lensHeight = 8.dp,
        lensAmount = 14.dp,
        surfaceColor = Color.White.copy(alpha = 0.15f),
        shadow = glassShadow(10.dp, 0.1f),
        highlight = Highlight.Default.copy(alpha = 0.5f),
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

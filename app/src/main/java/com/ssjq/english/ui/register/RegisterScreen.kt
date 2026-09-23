@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.register

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.UserManager
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
    var query by remember { mutableStateOf("") }
    var bookError by remember { mutableStateOf("") }
    val liquidBackdrop = rememberLayerBackdrop()

    LaunchedEffect(Unit) {
        allDbs = withContext(Dispatchers.IO) { DatabaseManager.listAssetDatabases(context) }
    }

    val filteredDbs = remember(allDbs, query) {
        if (query.isBlank()) allDbs
        else allDbs.filter { it.contains(query.trim(), ignoreCase = true) }
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
                dbs = filteredDbs,
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
                shape = RoundedCornerShape(16.dp),
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
    dbs: List<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedBook: String?,
    bookError: String,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
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
        Spacer(Modifier.height(16.dp))

        if (bookError.isNotBlank()) {
            Text(
                bookError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }

        LiquidGlassSearchBar(backdrop = backdrop, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                placeholder = { Text("搜索词库…") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(dbs, key = { it }) { db ->
                val selected = db == selectedBook
                LiquidGlassListItem(
                    backdrop = backdrop,
                    onClick = { onSelect(db) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            db.removeSuffix(".db"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidButton(onClick = onBack, backdrop = backdrop, modifier = Modifier.weight(1f)) {
                Text("上一步")
            }
            LiquidButton(
                onClick = onConfirm,
                backdrop = backdrop,
                modifier = Modifier.weight(1.4f),
            ) {
                Text("开始学习", fontWeight = FontWeight.Bold)
            }
        }
    }
}

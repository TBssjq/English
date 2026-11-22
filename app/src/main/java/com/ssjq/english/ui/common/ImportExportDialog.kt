package com.ssjq.english.ui.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.ssjq.english.ui.glass.GlassShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ssjq.english.data.ExportContentType
import com.ssjq.english.data.ExportScope
import com.ssjq.english.data.ImportConflictStrategy
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.LibraryFileManager
import com.ssjq.english.data.LibraryRepository
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    /** 选好导出 URI 后交给调用方执行，协程生命周期由调用方持有 */
    onExportRequested: (Uri, ExportScope, String, String, ExportContentType) -> Unit,
    /** 确认导入策略后交给调用方执行，避免 Dialog 退出组合导致协程被取消 */
    onImportConfirmed: (Uri, ImportConflictStrategy) -> Unit,
) {
    var showStrategyDialog by remember { mutableStateOf(false) }
    var selectedStrategy by remember { mutableStateOf(ImportConflictStrategy.MERGE_DUPLICATE) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    var showExportScopeDialog by remember { mutableStateOf(false) }
    var selectedExportScope by remember { mutableStateOf(ExportScope.ALL) }
    var selectedGroupName by remember { mutableStateOf("") }
    var selectedDbName by remember { mutableStateOf("") }
    var selectedContentType by remember { mutableStateOf(ExportContentType.BOTH) }

    LaunchedEffect(visible) {
        if (!visible) {
            showStrategyDialog = false
            showExportScopeDialog = false
            pendingUri = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { selectedUri ->
            onExportRequested(
                selectedUri,
                selectedExportScope,
                selectedGroupName,
                selectedDbName,
                selectedContentType,
            )
            onDismiss()
        } ?: onDismiss()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            showStrategyDialog = true
        } else {
            onDismiss()
        }
    }

    if (visible) {
        Dialog(onDismissRequest = onDismiss) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("数据管理", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, "关闭")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Button(
                        onClick = { showExportScopeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassShapes.Item,
                    ) {
                        Text("导出数据", fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf(LibraryFileManager.MIME_TYPE_JSON))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassShapes.Item,
                    ) {
                        Text("导入数据", fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "文件格式：JSON\n包含：错题本、收藏夹",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }

    if (showExportScopeDialog) {
        AlertDialog(
            onDismissRequest = { showExportScopeDialog = false },
            title = { Text("选择导出范围和内容") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("导出范围：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    ExportScope.entries.forEach { scope ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedExportScope = scope
                                when (scope) {
                                    ExportScope.ALL -> {
                                        selectedGroupName = ""
                                        selectedDbName = ""
                                    }
                                    ExportScope.GROUP -> {
                                        selectedDbName = ""
                                    }
                                    ExportScope.SINGLE -> {
                                        selectedGroupName = ""
                                    }
                                }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedExportScope == scope,
                                onClick = {
                                    selectedExportScope = scope
                                    when (scope) {
                                        ExportScope.ALL -> {
                                            selectedGroupName = ""
                                            selectedDbName = ""
                                        }
                                        ExportScope.GROUP -> {
                                            selectedDbName = ""
                                        }
                                        ExportScope.SINGLE -> {
                                            selectedGroupName = ""
                                        }
                                    }
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(scope.displayName, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("导出内容：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    ExportContentType.entries.forEach { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedContentType = type
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedContentType == type,
                                onClick = { selectedContentType = type },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(type.displayName, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    when (selectedExportScope) {
                        ExportScope.GROUP -> {
                            Text("选择词库组：", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(LibraryCatalog.categories) { category ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedGroupName = category.name
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = selectedGroupName == category.name,
                                            onClick = { selectedGroupName = category.name },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = MaterialTheme.colorScheme.primary,
                                            ),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(category.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        ExportScope.SINGLE -> {
                            Text("选择词库：", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                LibraryCatalog.categories.forEach { category ->
                                    category.subcategories.forEach { sub ->
                                        sub.dbFiles.forEach { dbName ->
                                            // key 必须全局唯一：同名词库文件可能出现在多个分类/子分类下，
                                            // 仅用 dbName 会触发 LazyColumn 的 "Key was already used" 崩溃
                                            item(key = "db-${category.name}-${sub.name}-$dbName") {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().clickable {
                                                        selectedDbName = dbName
                                                    },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    RadioButton(
                                                        selected = selectedDbName == dbName,
                                                        onClick = { selectedDbName = dbName },
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = MaterialTheme.colorScheme.primary,
                                                        ),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            com.ssjq.english.data.LibraryCatalog.displayName(dbName),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            category.name.substringBefore("（").trim(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val typeSuffix = when (selectedContentType) {
                            ExportContentType.BOTH -> ""
                            ExportContentType.WRONG_ONLY -> "_wrong"
                            ExportContentType.FAVORITE_ONLY -> "_fav"
                        }
                        val time = System.currentTimeMillis()
                        when (selectedExportScope) {
                            ExportScope.ALL -> {
                                val fileName = "word_library_backup_all${typeSuffix}_${time}.json"
                                exportLauncher.launch(fileName)
                            }
                            ExportScope.GROUP -> {
                                if (selectedGroupName.isNotBlank()) {
                                    val cleanName = selectedGroupName.replace("（", "_").replace("）", "_").replace(" ", "_")
                                    val fileName = "word_library_backup_${cleanName}${typeSuffix}_${time}.json"
                                    exportLauncher.launch(fileName)
                                }
                            }
                            ExportScope.SINGLE -> {
                                if (selectedDbName.isNotBlank()) {
                                    val fileName = "word_library_backup_${selectedDbName}${typeSuffix}_${time}.json"
                                    exportLauncher.launch(fileName)
                                }
                            }
                        }
                        showExportScopeDialog = false
                    },
                    enabled = when (selectedExportScope) {
                        ExportScope.ALL -> true
                        ExportScope.GROUP -> selectedGroupName.isNotBlank()
                        ExportScope.SINGLE -> selectedDbName.isNotBlank()
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExportScopeDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showStrategyDialog) {
        AlertDialog(
            onDismissRequest = {
                showStrategyDialog = false
                pendingUri = null
                onDismiss()
            },
            title = { Text("导入策略") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ImportConflictStrategy.entries.forEach { strategy ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedStrategy == strategy,
                                onClick = { selectedStrategy = strategy },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(strategy.displayName, fontWeight = FontWeight.Medium)
                                Text(strategy.description, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingUri
                        if (uri != null) {
                            onImportConfirmed(uri, selectedStrategy)
                        }
                        showStrategyDialog = false
                        pendingUri = null
                        onDismiss()
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showStrategyDialog = false
                    pendingUri = null
                    onDismiss()
                }) {
                    Text("取消")
                }
            },
        )
    }
}

private val ExportScope.displayName: String
    get() = when (this) {
        ExportScope.ALL -> "全部词库"
        ExportScope.GROUP -> "词库组（如：大学英语四级）"
        ExportScope.SINGLE -> "单个词库（如：CET4_1）"
    }

private val ExportContentType.displayName: String
    get() = when (this) {
        ExportContentType.BOTH -> "错题集 + 收藏夹"
        ExportContentType.WRONG_ONLY -> "仅错题集"
        ExportContentType.FAVORITE_ONLY -> "仅收藏夹"
    }

private val ImportConflictStrategy.displayName: String
    get() = when (this) {
        ImportConflictStrategy.OVERWRITE -> "覆盖"
        ImportConflictStrategy.MERGE_DUPLICATE -> "合并（去重）"
        ImportConflictStrategy.SKIP_EXISTING -> "跳过已存在"
    }

private val ImportConflictStrategy.description: String
    get() = when (this) {
        ImportConflictStrategy.OVERWRITE -> "清空现有数据，导入新数据"
        ImportConflictStrategy.MERGE_DUPLICATE -> "保留现有数据，新增数据合并"
        ImportConflictStrategy.SKIP_EXISTING -> "仅导入新数据，跳过重复项"
    }

internal suspend fun performExport(
    context: Context,
    uri: Uri,
    scope: ExportScope,
    groupName: String,
    dbName: String,
    contentType: ExportContentType,
): String = runCatching {
    val data = when (scope) {
        ExportScope.ALL -> LibraryRepository.exportAll(contentType)
        ExportScope.GROUP -> LibraryRepository.exportByGroup(groupName, contentType)
        ExportScope.SINGLE -> LibraryRepository.exportByDbName(dbName, contentType)
    }
    LibraryFileManager.writeFile(context, uri, data)
    "导出成功"
}.getOrElse { e ->
    if (e is CancellationException) throw e
    "导出失败：${e.message ?: "文件无法写入"}"
}

internal suspend fun performImport(
    context: Context,
    uri: Uri,
    strategy: ImportConflictStrategy,
): String = runCatching {
    // 先做大小上限与磁盘空间预检：不通过则直接失败，避免读爆内存或写坏词库
    LibraryFileManager.checkImportFeasible(context, uri)
    val jsonString = LibraryFileManager.readFile(context, uri)
    LibraryFileManager.validateJson(jsonString)
    val data = LibraryFileManager.deserializeFromJson(jsonString)
    LibraryRepository.importData(data, strategy).message
}.getOrElse { e ->
    if (e is CancellationException) throw e
    "导入失败：${e.message ?: "文件格式不正确"}"
}
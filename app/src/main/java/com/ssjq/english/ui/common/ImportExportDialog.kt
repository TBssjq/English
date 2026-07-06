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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ssjq.english.data.ExportContentType
import com.ssjq.english.data.ExportScope
import com.ssjq.english.data.ImportConflictStrategy
import com.ssjq.english.data.LibraryCatalog
import com.ssjq.english.data.LibraryFileManager
import com.ssjq.english.data.LibraryRepository
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val context = LocalContext.current
    var showStrategyDialog by remember { mutableStateOf(false) }
    var selectedStrategy by remember { mutableStateOf(ImportConflictStrategy.MERGE_DUPLICATE) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var showExportScopeDialog by remember { mutableStateOf(false) }
    var selectedExportScope by remember { mutableStateOf(ExportScope.ALL) }
    var selectedGroupName by remember { mutableStateOf("") }
    var selectedDbName by remember { mutableStateOf("") }
    var selectedContentType by remember { mutableStateOf(ExportContentType.BOTH) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { selectedUri ->
            coroutineScope.launch {
                performExport(context, selectedUri, selectedExportScope, selectedGroupName, selectedDbName, selectedContentType, onResult)
                onDismiss()
            }
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
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("导出数据", fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf(LibraryFileManager.MIME_TYPE_JSON))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
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
                                            item(key = dbName) {
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
                                                        Text(dbName, style = MaterialTheme.typography.bodyMedium)
                                                        Text(category.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            coroutineScope.launch {
                                performImport(context, uri, selectedStrategy, onResult)
                            }
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

private suspend fun performExport(
    context: Context,
    uri: Uri,
    scope: ExportScope,
    groupName: String,
    dbName: String,
    contentType: ExportContentType,
    onResult: (String) -> Unit,
) {
    android.util.Log.d("Export", "====== 开始导出 ======")
    android.util.Log.d("Export", "URI: $uri")
    android.util.Log.d("Export", "范围: $scope, groupName: $groupName, dbName: $dbName")
    android.util.Log.d("Export", "内容类型: $contentType")

    try {
        android.util.Log.d("Export", "步骤1: 获取数据...")
        val data = when (scope) {
            ExportScope.ALL -> LibraryRepository.exportAll(contentType)
            ExportScope.GROUP -> LibraryRepository.exportByGroup(groupName, contentType)
            ExportScope.SINGLE -> LibraryRepository.exportByDbName(dbName, contentType)
        }
        android.util.Log.d("Export", "步骤1完成: 错题=${data.wrongWords.size}, 收藏=${data.favorites.size}")

        android.util.Log.d("Export", "步骤2: 序列化JSON...")
        val json = LibraryFileManager.serializeToJson(data)
        android.util.Log.d("Export", "步骤2完成: JSON长度=${json.length}")

        android.util.Log.d("Export", "步骤3: 写入文件...")
        val success = LibraryFileManager.writeFile(context, uri, data)
        android.util.Log.d("Export", "步骤3完成: success=$success")

        onResult(if (success) "导出成功" else "导出失败")
    } catch (e: Exception) {
        android.util.Log.e("Export", "导出失败!", e)
        onResult("导出失败: ${e.message}")
    }
}

private suspend fun performImport(context: Context, uri: Uri, strategy: ImportConflictStrategy, onResult: (String) -> Unit) {
    android.util.Log.d("Export", "====== 开始导入 ======")
    android.util.Log.d("Export", "URI: $uri")
    android.util.Log.d("Export", "策略: ${strategy.name}")

    try {
        android.util.Log.d("Export", "步骤1: 读取文件...")
        val jsonString = LibraryFileManager.readFile(context, uri)
        if (jsonString == null) {
            android.util.Log.e("Export", "步骤1失败: 读取文件返回null")
            onResult("读取文件失败")
            return
        }
        android.util.Log.d("Export", "步骤1完成: 文件长度=${jsonString.length}")

        android.util.Log.d("Export", "步骤2: 验证JSON格式...")
        if (!LibraryFileManager.validateJson(jsonString)) {
            android.util.Log.e("Export", "步骤2失败: JSON格式无效")
            onResult("文件格式无效")
            return
        }
        android.util.Log.d("Export", "步骤2完成: JSON格式验证通过")

        android.util.Log.d("Export", "步骤3: 反序列化数据...")
        val data = LibraryFileManager.deserializeFromJson(jsonString)
        if (data == null) {
            android.util.Log.e("Export", "步骤3失败: 反序列化返回null")
            onResult("解析数据失败")
            return
        }
        android.util.Log.d("Export", "步骤3完成: 错题=${data.wrongWords.size}, 收藏=${data.favorites.size}")

        android.util.Log.d("Export", "步骤4: 导入数据...")
        val result = LibraryRepository.importData(data, strategy)
        android.util.Log.d("Export", "步骤4完成: ${result.message}")

        onResult(result.message)
    } catch (e: Exception) {
        android.util.Log.e("Export", "导入失败!", e)
        onResult("导入失败: ${e.message}")
    }
}
@file:OptIn(ExperimentalMaterial3Api::class)

package com.ssjq.english.ui.about

import com.ssjq.english.ui.common.glassInnerShadow
import com.ssjq.english.ui.common.glassShadow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
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
import com.ssjq.english.data.AppUpdateManager
import com.ssjq.english.data.NoticeData
import com.ssjq.english.data.NoticeResult
import com.ssjq.english.data.AppVersion
import com.ssjq.english.data.UpdateResult
import com.ssjq.english.data.UserManager
import com.ssjq.english.ui.common.LiquidGlassCard
import com.ssjq.english.ui.common.LiquidGlassDialog
import com.ssjq.english.ui.common.LiquidGlassListItem
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var checkingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf<AppVersion?>(null) }
    var currentVersion by remember { mutableStateOf("") }
    var updateError by remember { mutableStateOf<String?>(null) }

    var showUsernameDialog by remember { mutableStateOf(false) }
    var currentUsername by remember { mutableStateOf(UserManager.getUsername() ?: "") }
    var newUsername by remember { mutableStateOf(currentUsername) }
    var usernameError by remember { mutableStateOf("") }

    // 最新公告：与版本号无关，随时可直接拉取
    var loadingNotice by remember { mutableStateOf(false) }
    var showNoticeDialog by remember { mutableStateOf(false) }
    var latestNotice by remember { mutableStateOf<NoticeData?>(null) }
    var noticeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        currentVersion = AppUpdateManager.getCurrentVersionName(context)
    }

    fun checkUpdate() {
        scope.launch {
            checkingUpdate = true
            updateError = null
            val (version, result) = AppUpdateManager.checkUpdate(context)
            checkingUpdate = false
            when (result) {
                UpdateResult.NEW_VERSION_AVAILABLE -> {
                    latestVersion = version
                    showUpdateDialog = true
                }
                UpdateResult.NO_UPDATE -> {
                    updateError = "当前已是最新版本"
                }
                UpdateResult.NETWORK_ERROR -> {
                    updateError = "检查更新失败，请检查网络"
                }
            }
        }
    }

    /** 直接拉取最新公告，不检查版本号 */
    fun fetchLatestNotice() {
        scope.launch {
            loadingNotice = true
            noticeError = null
            val (notice, result) = AppUpdateManager.fetchNotice()
            loadingNotice = false
            when (result) {
                NoticeResult.HAS_NOTICE -> {
                    if (notice != null) {
                        latestNotice = notice
                        showNoticeDialog = true
                    } else {
                        noticeError = "暂无公告"
                    }
                }
                NoticeResult.NO_NOTICE -> noticeError = "暂无公告"
                NoticeResult.NETWORK_ERROR -> noticeError = "拉取公告失败，请检查网络"
            }
        }
    }

    fun openNoticeUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun openEditUsername() {
        newUsername = currentUsername
        usernameError = ""
        showUsernameDialog = true
    }

    fun saveUsername() {
        if (newUsername.trim().isBlank()) {
            usernameError = "请输入用户名"
            return
        }
        if (newUsername.trim().length < 2) {
            usernameError = "用户名至少需要2个字符"
            return
        }
        UserManager.setUsername(newUsername.trim())
        currentUsername = newUsername.trim()
        showUsernameDialog = false
        Toast.makeText(context, "用户名已修改", Toast.LENGTH_SHORT).show()
    }

    fun handleUpdateClick() {
        val v = latestVersion ?: return
        AppUpdateManager.openDownloadUrl(context, v)
        showUpdateDialog = false
    }

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
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            )
            Box(
                Modifier
                    .size(180.dp)
                    .offset(x = (-30).dp, y = 80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            )
            Box(
                Modifier
                    .size(140.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = 180.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
            )
            Box(
                Modifier
                    .size(120.dp)
                    .offset(x = 30.dp, y = 450.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
            )
        }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = { Text("关于我", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            // Hero：头像 + 简介（液态玻璃）
            item {
                LiquidGlassCard(
                    backdrop = liquidBackdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    blurRadius = 10.dp,
                    lensHeight = 16.dp,
                    lensAmount = 28.dp,
                    surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shadow = glassShadow(24.dp, 0.2f),
                    highlight = Highlight.Default.copy(alpha = 0.8f),
                    innerShadow = glassInnerShadow(10.dp, 0.06f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                                    ),
                                ),
                            )
                            .padding(vertical = 32.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(R.drawable.walnut_body),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(120.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                    .clickable { openEditUsername() },
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "关于我",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                currentUsername,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "这是我最近实现的一个英语学习的应用，\n我也正在尝试添加一些功能，\n如果你有任何的建议，随时联系我。\n当然，如果你想和我一块 vibe coding，欢迎一起来！",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                lineHeight = TextUnit(1.6f, TextUnitType.Em),
                            )
                        }
                    }
                }
            }

            // 版本信息 + 检查更新
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "版本信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    LiquidGlassCard(
                        backdrop = liquidBackdrop,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 5.dp,
                        lensHeight = 8.dp,
                        lensAmount = 14.dp,
                        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shadow = glassShadow(10.dp, 0.12f),
                        highlight = Highlight.Default.copy(alpha = 0.5f),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.SystemUpdate, null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "当前版本",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "v$currentVersion",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { checkUpdate() },
                                    enabled = !checkingUpdate,
                                ) {
                                    if (checkingUpdate) {
                                        Text("检查中…")
                                    } else {
                                        Text("检查更新")
                                    }
                                }
                            }
                            updateError?.let { err ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "最新公告",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "查看作者发布的最新消息",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { fetchLatestNotice() },
                                    enabled = !loadingNotice,
                                ) {
                                    if (loadingNotice) Text("拉取中…") else Text("查看公告")
                                }
                            }
                            noticeError?.let { err ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // 联系方式
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "联系方式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )

                    // QQ
                    ContactItem(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        title = "QQ",
                        subtitle = "653294795",
                        backdrop = liquidBackdrop,
                        onClick = {
                            val qqNumber = "653294795"
                            // 优先尝试打开 QQ 资料卡（加好友按钮在资料卡上）
                            var opened = false
                            val schemeIntents = listOf(
                                // QQ 资料卡（最通用）
                                Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$qqNumber&card_type=person&source=qrcode")
                                },
                                // 备用：加好友界面
                                Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("mqqwpa://im/add?uin=$qqNumber")
                                },
                                // 备用：临时会话
                                Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("mqqwpa://im/chat?chat_type=wpa&uin=$qqNumber&version=1&src_type=web&web_src=oicqzone.com")
                                },
                            )
                            for (intent in schemeIntents) {
                                try {
                                    context.startActivity(intent)
                                    opened = true
                                    break
                                } catch (_: Exception) { }
                            }
                            if (!opened) {
                                // 都打不开就复制 QQ 号到剪贴板
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("QQ号", qqNumber)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "QQ号已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )

                    // GitHub
                    ContactItem(
                        icon = {
                            Icon(
                                Icons.Filled.Language, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        title = "GitHub",
                        subtitle = "TBssjq.github.io",
                        backdrop = liquidBackdrop,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://TBssjq.github.io"))
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        },
                    )

                    // Email
                    ContactItem(
                        icon = {
                            Icon(
                                Icons.Filled.Email, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        title = "Email",
                        subtitle = "653294795@qq.com",
                        backdrop = liquidBackdrop,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:653294795@qq.com")
                            }
                            try {
                                context.startActivity(
                                    Intent.createChooser(intent, "发送邮件")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) { }
                        },
                    )
                }
            }
        }
    }
    } // end of Box (liquid glass background)

    // 更新弹窗
    if (showUpdateDialog && latestVersion != null) {
        val v = latestVersion!!
        AlertDialog(
            onDismissRequest = {
                if (!v.forceUpdate) showUpdateDialog = false
            },
            title = {
                Text(
                    "发现新版本 v${v.latestVersion}",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    v.changelog.ifBlank { "新版本已发布，立即体验！" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { handleUpdateClick() }) {
                    Text("立即更新")
                }
            },
            dismissButton = {
                if (!v.forceUpdate) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("稍后再说")
                    }
                }
            },
        )
    }

    // 修改用户名弹窗
    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = {
                Text("修改用户名", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = {
                            newUsername = it
                            usernameError = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("输入新用户名") },
                        textStyle = MaterialTheme.typography.titleMedium,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        singleLine = true,
                        isError = usernameError.isNotBlank(),
                        supportingText = if (usernameError.isNotBlank()) { { Text(usernameError, color = MaterialTheme.colorScheme.error) } } else null,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { saveUsername() }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    // 最新公告：液态玻璃对话框（与版本号无关，可直接拉取）
    if (showNoticeDialog && latestNotice != null) {
        val n = latestNotice!!
        LiquidGlassDialog(
            backdrop = liquidBackdrop,
            onDismissRequest = { showNoticeDialog = false },
        ) {
            Text(
                n.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                n.content,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showNoticeDialog = false }) {
                    Text("知道了")
                }
                if (n.url.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { openNoticeUrl(n.url) }) {
                        Text(n.btnText.ifBlank { "查看详情" })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
) {
    LiquidGlassListItem(
        backdrop = backdrop,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

package com.ssjq.english.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ssjq.english.data.AppUpdateManager
import com.ssjq.english.data.AppVersion
import com.ssjq.english.data.NoticeData
import com.ssjq.english.data.NoticeResult
import com.ssjq.english.data.UpdateResult
import com.ssjq.english.data.UserManager
import com.ssjq.english.quiz.QuizScreen
import com.ssjq.english.ui.about.AboutScreen
import com.ssjq.english.ui.checkin.CheckInScreen
import com.ssjq.english.ui.common.LiquidGlassDialog
import com.ssjq.english.ui.glass.LiquidBottomTab
import com.ssjq.english.ui.glass.LiquidBottomTabs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.ssjq.english.ui.nav.MainTab
import com.ssjq.english.ui.crazyquiz.CrazyQuizScreen
import com.ssjq.english.ui.home.HomeScreen
import com.ssjq.english.ui.nav.AboutRoute
import com.ssjq.english.ui.register.RegisterScreen
import com.ssjq.english.ui.nav.CheckInRoute
import com.ssjq.english.ui.nav.CrazyQuizRoute
import com.ssjq.english.ui.nav.Home
import com.ssjq.english.ui.nav.LibraryRoute
import com.ssjq.english.ui.nav.QuizRoute
import com.ssjq.english.ui.nav.SearchRoute
import com.ssjq.english.ui.nav.WordDetailRoute
import com.ssjq.english.ui.nav.WordListRoute
import com.ssjq.english.ui.nav.WordStudyRoute
import com.ssjq.english.ui.library.LibraryScreen
import com.ssjq.english.ui.search.SearchScreen
import com.ssjq.english.ui.worddetail.WordDetailScreen
import com.ssjq.english.ui.worddetail.WordStudyScreen
import com.ssjq.english.ui.wordlist.WordListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用导航根：用 Compose 标准库的状态切换 + AnimatedContent 实现页面导航。
 * 返回栈用 mutableStateListOf 维护，BackHandler 接管系统返回键。
 * 不依赖 Navigation 3，避免新库版本/API 不稳定导致的编译问题。
 */
private fun MainTab.glassIcon(): ImageVector = when (this) {
    MainTab.STUDY -> Icons.Filled.MenuBook
    MainTab.ACHIEVEMENT -> Icons.Filled.EmojiEvents
    MainTab.ABOUT -> Icons.Filled.Person
}

private fun MainTab.glassLabel(): String = when (this) {
    MainTab.STUDY -> "学习"
    MainTab.ACHIEVEMENT -> "成就"
    MainTab.ABOUT -> "关于作者"
}

@Composable
fun AppNav() {
    // 返回栈：栈底为当前页签的根页面，栈顶为当前显示页。
    // 栈内只有 1 个元素时处于顶层，此时显示底部导航栏。
    val backStack = remember { mutableStateListOf<Any>(Home) }
    var currentTab by remember { mutableStateOf(MainTab.STUDY) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRegistered by remember { mutableStateOf(UserManager.isRegistered()) }

    if (!isRegistered) {
        RegisterScreen(onRegisterComplete = {
            UserManager.setUsername(UserManager.getUsername() ?: "")
            UserManager.markLaunched()
            isRegistered = true
        })
        return
    }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf<AppVersion?>(null) }

    var showNoticeDialog by remember { mutableStateOf(false) }
    var latestNotice by remember { mutableStateOf<NoticeData?>(null) }

    // 启动时并发检查更新和拉取公告
    LaunchedEffect(Unit) {
        val updateJob = scope.launch(Dispatchers.IO) {
            val (version, result) = AppUpdateManager.checkUpdate(context)
            if (result == UpdateResult.NEW_VERSION_AVAILABLE && version != null) {
                withContext(Dispatchers.Main) {
                    latestVersion = version
                    showUpdateDialog = true
                }
            }
        }

        val noticeJob = scope.launch(Dispatchers.IO) {
            // 仅首次进入强制拉取并展示公告，之后不再强制弹出
            if (!UserManager.hasSeenAnnouncement()) {
                val (notice, result) = AppUpdateManager.fetchNotice()
                if (result == NoticeResult.HAS_NOTICE && notice != null) {
                    withContext(Dispatchers.Main) {
                        latestNotice = notice
                        showNoticeDialog = true
                    }
                }
                UserManager.markAnnouncementSeen()
            }
        }

        updateJob.join()
        noticeJob.join()
    }

    // 每次重组重新计算：栈内只有根页面时处于顶层
    val isTopLevel = backStack.size <= 1
    val current: Any = backStack.last()

    fun navigate(route: Any) {
        backStack.add(route)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** 切换底部页签：回到该页签的根页面 */
    fun selectTab(tab: MainTab) {
        if (tab == currentTab && isTopLevel) return
        currentTab = tab
        val root: Any = when (tab) {
            MainTab.STUDY -> Home
            MainTab.ACHIEVEMENT -> CheckInRoute
            MainTab.ABOUT -> AboutRoute
        }
        backStack.clear()
        backStack.add(root)
    }

    fun openDownload() {
        val v = latestVersion ?: return
        AppUpdateManager.openDownloadUrl(context, v)
        if (!(v.forceUpdate)) {
            showUpdateDialog = false
        }
    }

    fun openNoticeUrl() {
        val n = latestNotice ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(n.url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
        if (!n.isForce) {
            showNoticeDialog = false
        }
    }

    BackHandler(enabled = backStack.size > 1) { back() }

    // 全局背景采样源：仅当「底栏/弹窗」这类需要透视背景的玻璃元素可见时才录制。
    // 处在二级页面（无底栏、无弹窗）时不录制，避免不必要的全屏离屏渲染。
    val appBackdrop = rememberLayerBackdrop()
    val glassDialogVisible = showUpdateDialog || showNoticeDialog

    Box(modifier = Modifier.fillMaxSize()) {
        // 全局玻璃采样源：固定背景层（渐变 + 彩色光斑）。
        // 关键：appBackdrop 只录制这一独立层级，绝不包裹 AnimatedContent——
        // 否则各页面内部的 layerBackdrop 背景层会被嵌套录进 appBackdrop，
        // 形成「appBackdrop 录制页面 → 页面内部 layerBackdrop 又采样自身」的
        // 自引用递归，渲染树递归过深而栈溢出崩溃（进入单词表时尤为明显）。
        // 底栏 / 弹窗统一采样本固定背景，呈现液态玻璃折射。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(appBackdrop),
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
                    .size(240.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-50).dp, y = 60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)),
            )
            Box(
                Modifier
                    .size(180.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = 200.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)),
            )
            Box(
                Modifier
                    .size(150.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = 40.dp, y = (-180).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f)),
            )
        }

        // 内容区：位于背景层之上，且【不被 appBackdrop 录制】，切断嵌套递归。
        Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = current,
            // 顶层页面为悬浮式底部导航栏预留空间，避免内容被遮挡
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTopLevel) Modifier.padding(bottom = 88.dp) else Modifier),
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(280)) +
                    fadeIn(tween(280)) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it / 6 }, animationSpec = tween(280)) +
                    fadeOut(tween(280))
            },
            label = "nav",
        ) { route ->
        when (route) {
            is Home -> HomeScreen(
                onPickDatabase = {
                    UserManager.setLastStudyDb(it)
                    navigate(WordListRoute(it))
                },
                onOpenCheckIn = { selectTab(MainTab.ACHIEVEMENT) },
                onOpenAbout = { selectTab(MainTab.ABOUT) },
                onOpenCrazyQuiz = { navigate(CrazyQuizRoute("crazy", com.ssjq.english.ui.nav.CrazyQuizSource.LIBRARY, 0)) },
            )

            is CheckInRoute -> CheckInScreen(onBack = { back() })

            is AboutRoute -> AboutScreen(onBack = { back() })

            is WordListRoute -> WordListScreen(
                dbName = route.dbName,
                onBack = { back() },
                onWordClick = { navigate(WordStudyRoute(route.dbName, startWordId = it)) },
                onStartStudy = { navigate(WordStudyRoute(route.dbName)) },
                onSearch = { navigate(SearchRoute(route.dbName)) },
                onOpenLibrary = { type -> navigate(LibraryRoute(route.dbName, type)) },
                onStartQuiz = { mode -> navigate(QuizRoute(route.dbName, 20, mode)) },
            )

            is WordDetailRoute -> WordDetailScreen(
                dbName = route.dbName,
                wordId = route.wordId,
                onBack = { back() },
                onNavigateToWord = { targetDb, targetWordId ->
                    navigate(WordStudyRoute(targetDb, startWordId = targetWordId))
                },
            )

            is WordStudyRoute -> WordStudyScreen(
                dbName = route.dbName,
                startWordId = route.startWordId,
                onBack = { back() },
            )

            is LibraryRoute -> LibraryScreen(
                dbName = route.dbName,
                type = route.type,
                onBack = { back() },
                onWordClick = { navigate(WordStudyRoute(route.dbName, startWordId = it)) },
            )

            is SearchRoute -> SearchScreen(
                dbName = route.dbName,
                onBack = { back() },
                onWordClick = { navigate(WordStudyRoute(route.dbName, startWordId = it)) },
            )

            is QuizRoute -> QuizScreen(
                dbName = route.dbName,
                questionCount = route.count,
                mode = route.mode,
                sourceWords = route.sourceWords,
                onBack = { back() },
            )

            is CrazyQuizRoute -> CrazyQuizScreen(
                onBack = { back() },
                onStartQuiz = { _, words ->
                    navigate(QuizRoute("crazy_quiz", words.size, null, words))
                },
            )
        }
        }   // AnimatedContent 结束
        }   // 内容区（backdrop 采样源）结束：底栏与弹窗必须在此之外

        // 悬浮式液态玻璃底部导航栏（直接采用库提供的 LiquidBottomTabs）：仅在顶层页面显示
        if (isTopLevel) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                LiquidBottomTabs(
                    selectedTabIndex = { currentTab.ordinal },
                    onTabSelected = { selectTab(MainTab.entries[it]) },
                    backdrop = appBackdrop,
                    tabsCount = MainTab.entries.size,
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    MainTab.entries.forEach { tab ->
                        LiquidBottomTab(onClick = { selectTab(tab) }) {
                            Icon(tab.glassIcon(), tab.glassLabel(), modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                tab.glassLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // 全局更新弹窗（液态玻璃）
        if (showUpdateDialog && latestVersion != null) {
            val v = latestVersion!!
            LiquidGlassDialog(
                backdrop = appBackdrop,
                onDismissRequest = {
                    if (!v.forceUpdate) showUpdateDialog = false
                },
            ) {
                Text(
                    "发现新版本 v${v.latestVersion}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    v.changelog.ifBlank { "新版本已发布，立即体验！" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!v.forceUpdate) {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("稍后再说")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { openDownload() }) {
                        Text("立即下载")
                    }
                }
            }
        }

        // 全局公告弹窗（液态玻璃）
        if (showNoticeDialog && latestNotice != null) {
            val n = latestNotice!!
            LiquidGlassDialog(
                backdrop = appBackdrop,
                onDismissRequest = {
                    if (!n.isForce) showNoticeDialog = false
                },
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!n.isForce) {
                        TextButton(onClick = { showNoticeDialog = false }) {
                            Text("知道了")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { openNoticeUrl() }) {
                        Text(n.btnText.ifBlank { "查看详情" })
                    }
                }
            }
        }
    }
}

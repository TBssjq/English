package com.ssjq.english.ui

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import com.ssjq.english.ui.glass.GlassBackdropSpec
import com.ssjq.english.ui.glass.LocalAppBackdrop
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
import com.ssjq.english.ui.common.GlassDialogBody
import com.ssjq.english.ui.common.GlassDialogHost
import com.ssjq.english.ui.common.LiquidGlassDialog
import com.ssjq.english.ui.glass.LiquidBottomTab
import com.ssjq.english.ui.glass.LiquidBottomTabs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
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
import com.ssjq.english.ui.nav.LibraryType
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
    MainTab.STUDY -> Icons.AutoMirrored.Filled.MenuBook
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
    var currentTab by rememberSaveable { mutableStateOf(MainTab.STUDY) }
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
    var pendingNotice by remember { mutableStateOf<NoticeData?>(null) }

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
            // 每次启动都拉一次：同一条公告只弹一次，服务端换了公告就再弹一次。
            // 旧实现只判断「是否弹过」，作者后来发布的新公告老用户永远收不到
            val (notice, result) = AppUpdateManager.fetchNotice()
            if (result == NoticeResult.HAS_NOTICE && notice != null && notice.hasContent()) {
                if (UserManager.announcementSeenKey() != notice.seenKey()) {
                    withContext(Dispatchers.Main) { pendingNotice = notice }
                }
            }
        }

        updateJob.join()
        noticeJob.join()
    }

    // 公告与更新弹窗排队显示：升级首启时两者会同时到达，
    // 直接各弹一个会叠在一起，底下的那个既看不见又抢走返回键
    LaunchedEffect(pendingNotice, showUpdateDialog) {
        val notice = pendingNotice ?: return@LaunchedEffect
        if (showUpdateDialog) return@LaunchedEffect
        latestNotice = notice
        showNoticeDialog = true
        pendingNotice = null
        UserManager.setAnnouncementSeenKey(notice.seenKey())
    }

    // 每次重组重新计算：栈内只有根页面时处于顶层
    val isTopLevel = backStack.size <= 1
    val current: Any = backStack.last()

    /**
     * 过场期间锁定导航。
     *
     * AnimatedContent 切换的 280ms 内，**旧页面仍然留在组合树中且完全可点击**
     * （它的返回箭头、列表项都还在）。此时若再响应一次返回或跳转，就会
     * 一次退掉多级页面，甚至在返回途中又压入新页面，栈状态错乱。
     * 这里用一个略长于过场时长的时间窗把这类误触挡掉。
     */
    var navLockedUntil by remember { mutableStateOf(0L) }
    // 1=前进，-1=返回，0=切换页签；AnimatedContent 根据它选择对应方向的动画
    var navDirection by remember { mutableStateOf(0) }

    fun isNavLocked(): Boolean = SystemClock.uptimeMillis() < navLockedUntil

    fun lockNav() {
        navLockedUntil = SystemClock.uptimeMillis() + 320
    }

    fun navigate(route: Any) {
        if (isNavLocked()) return
        lockNav()
        navDirection = 1
        backStack.add(route)
    }

    fun back() {
        if (isNavLocked()) return
        if (backStack.size > 1) {
            lockNav()
            navDirection = -1
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /** 切换底部页签：回到该页签的根页面 */
    fun selectTab(tab: MainTab) {
        if (tab == currentTab && isTopLevel) return
        if (isNavLocked()) return
        lockNav()
        navDirection = 0
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
        // 打不开就保持弹窗并给出提示：原来的写法无条件关弹窗，链接异常时用户毫无反馈
        if (!AppUpdateManager.openDownloadUrl(context, v)) {
            Toast.makeText(context, "下载地址无效，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        if (!(v.forceUpdate)) {
            showUpdateDialog = false
        }
    }

    fun openNoticeUrl() {
        val n = latestNotice ?: return
        if (!AppUpdateManager.openUrl(context, n.url)) {
            Toast.makeText(context, "链接无效，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        if (!n.isForce) {
            showNoticeDialog = false
        }
    }

    BackHandler(enabled = backStack.size > 1) { back() }

    // 顶层页面按返回键：2 秒内连按两次才退出。
    // 旧实现在顶层没有 BackHandler，误触一下就直接杀掉 App，
    // 学习进度、答题状态全部丢失。
    var lastBackPressAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = backStack.size <= 1) {
        // 停留在「成就」或「关于」页签时，返回键先回到「学习」页签，
        // 而不是直接进入退出确认，与系统返回的分层习惯一致
        if (currentTab != MainTab.STUDY) {
            selectTab(MainTab.STUDY)
            return@BackHandler
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBackPressAt < 2000) {
            (context as? Activity)?.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
        }
    }

    // 全应用唯一的背景采样源：所有页面、底栏与弹窗共用这一层固定背景。
    val appBackdrop = rememberLayerBackdrop()
    val blobAlphas = GlassBackdropSpec.blobAlphas()

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
                    .size(GlassBackdropSpec.blobSizes[0])
                    .align(Alignment.TopStart)
                    .offset(x = (-50).dp, y = 60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = blobAlphas[0])),
            )
            Box(
                Modifier
                    .size(GlassBackdropSpec.blobSizes[1])
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = 200.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = blobAlphas[1])),
            )
            Box(
                Modifier
                    .size(GlassBackdropSpec.blobSizes[2])
                    .align(Alignment.BottomStart)
                    .offset(x = 40.dp, y = (-180).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = blobAlphas[2])),
            )
        }

        // 内容区：所有页面共用同一个背景采样层，各页面不再各自录制全屏背景。
        Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAppBackdrop provides appBackdrop) {
        AnimatedContent(
            targetState = current,
            // 顶层页面为悬浮式底部导航栏预留空间，避免内容被遮挡
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTopLevel) Modifier.padding(bottom = 88.dp) else Modifier),
            // 方向映射自 android/nav3-recipes 的 animations 示例：
            // 前进从右进、旧页向左；返回从左进、旧页向右；页签切换用淡入淡出加轻微缩放。
            transitionSpec = {
                when (navDirection) {
                    -1 ->
                        (slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(280)
                        ) + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(280)
                            ) + fadeOut(tween(280)))

                    0 ->
                        (scaleIn(initialScale = 0.96f, animationSpec = tween(240)) +
                            fadeIn(tween(240))) togetherWith
                            (scaleOut(targetScale = 1.04f, animationSpec = tween(200)) +
                                fadeOut(tween(200)))

                    else ->
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(280)
                        ) + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(280)
                            ) + fadeOut(tween(280)))
                }
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
                onOpenCrazyQuiz = { navigate(CrazyQuizRoute) },
                onStartReview = { reviewDb, wordIds ->
                    navigate(WordStudyRoute(reviewDb, reviewQueue = wordIds))
                },
                onStartStudy = { studyDb -> navigate(WordStudyRoute(studyDb)) },
                onOpenLibrary = { libraryDb, type, showAll ->
                    navigate(LibraryRoute(libraryDb, type, showAll))
                },
                onOpenWord = { db, wordId -> navigate(WordDetailRoute(db, wordId)) },
            )

            // 这两个是页签根页面，backStack 只有一项，back() 不会生效；
            // 返回箭头改为切回「学习」页签
            is CheckInRoute -> CheckInScreen(onBack = { selectTab(MainTab.STUDY) })

            is AboutRoute -> AboutScreen(onBack = { selectTab(MainTab.STUDY) })

            is WordListRoute -> WordListScreen(
                dbName = route.dbName,
                onBack = { back() },
                // 点单词看详情；进入背诵走下方专门的「开始/继续背诵」按钮
                onWordClick = { navigate(WordDetailRoute(route.dbName, it)) },
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
                    // 词库搜索只是看一下词条，进入详情而不是另一本词库的背诵会话
                    navigate(WordDetailRoute(targetDb, targetWordId))
                },
            )

            is WordStudyRoute -> WordStudyScreen(
                dbName = route.dbName,
                startWordId = route.startWordId,
                reviewQueue = route.reviewQueue,
                onBack = { back() },
                onNavigateToWord = { targetDb, targetWordId ->
                    // 词库搜索只是看一下词条，进入详情而不是另一本词库的背诵会话
                    navigate(WordDetailRoute(targetDb, targetWordId))
                },
            )

            is LibraryRoute -> LibraryScreen(
                dbName = route.dbName,
                type = route.type,
                onBack = { back() },
                // 点条目看单词详情，不再直接进背诵会话；全部词库视图下用条目自身词库
                onWordClick = { db, wordId -> navigate(WordDetailRoute(db, wordId)) },
                initialShowAll = route.showAll,
            )

            is SearchRoute -> SearchScreen(
                dbName = route.dbName,
                onBack = { back() },
                onWordClick = { navigate(WordDetailRoute(route.dbName, it)) },
            )

            is QuizRoute -> QuizScreen(
                dbName = route.dbName,
                questionCount = route.count,
                mode = route.mode,
                sourceWords = route.sourceWords,
                sourceKey = route.sourceKey,
                onBack = { back() },
                onOpenWord = { wordDb, wordId -> navigate(WordDetailRoute(wordDb, wordId)) },
            )

            is CrazyQuizRoute -> CrazyQuizScreen(
                onBack = { back() },
                onStartQuiz = { _, words, key, count ->
                    navigate(QuizRoute("crazy_quiz", count, null, words, key))
                },
            )
        }
        }   // AnimatedContent 结束
        }   // CompositionLocalProvider 结束
        }   // 内容区结束：底栏与弹窗必须在此之外

        // 悬浮式液态玻璃底部导航栏（直接采用库提供的 LiquidBottomTabs）：仅在顶层页面显示。
        // 玻璃弹窗是同窗口覆盖层，底栏在其之后绘制会盖住遮罩，因此弹窗可见时临时隐藏底栏。
        if (isTopLevel && !GlassDialogHost.isDialogVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                // 这两个 lambda 必须保持引用稳定：LiquidBottomTabs 内部把它们作为
                // remember 的 key，若每次重组都新建实例，会导致内部指示器状态被反复重置。
                // selectedTabIndex 仅在 currentTab 真正变化时重建，onTabSelected 永久稳定。
                val selectedIndexLambda = remember(currentTab) { { currentTab.ordinal } }
                val onTabSelectedStable = remember { { index: Int -> selectTab(MainTab.entries[index]) } }
                LiquidBottomTabs(
                    selectedTabIndex = selectedIndexLambda,
                    onTabSelected = onTabSelectedStable,
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
                GlassDialogBody(v.changelog.ifBlank { "新版本已发布，立即体验！" })
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
                GlassDialogBody(n.content)
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!n.isForce) {
                        TextButton(onClick = { showNoticeDialog = false }) {
                            Text("知道了")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (n.url.isNotBlank()) {
                        TextButton(onClick = { openNoticeUrl() }) {
                            Text(n.btnText.ifBlank { "查看详情" })
                        }
                    }
                }
            }
        }
    }
}

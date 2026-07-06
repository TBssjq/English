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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.ssjq.english.data.AppUpdateManager
import com.ssjq.english.data.AppVersion
import com.ssjq.english.data.UpdateResult
import com.ssjq.english.quiz.QuizScreen
import com.ssjq.english.ui.about.AboutScreen
import com.ssjq.english.ui.checkin.CheckInScreen
import com.ssjq.english.ui.home.HomeScreen
import com.ssjq.english.ui.nav.AboutRoute
import com.ssjq.english.ui.nav.CheckInRoute
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
import kotlinx.coroutines.launch

/**
 * 应用导航根：用 Compose 标准库的状态切换 + AnimatedContent 实现页面导航。
 * 返回栈用 mutableStateListOf 维护，BackHandler 接管系统返回键。
 * 不依赖 Navigation 3，避免新库版本/API 不稳定导致的编译问题。
 */
@Composable
fun AppNav() {
    val backStack = remember { mutableStateListOf<Any>() }
    var current by remember { mutableStateOf<Any>(Home) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf<AppVersion?>(null) }

    // 启动时自动检查更新
    LaunchedEffect(Unit) {
        val (version, result) = AppUpdateManager.checkUpdate(context)
        if (result == UpdateResult.NEW_VERSION_AVAILABLE && version != null) {
            latestVersion = version
            showUpdateDialog = true
        }
    }

    fun navigate(route: Any) {
        backStack.add(current)
        current = route
    }

    fun back() {
        if (backStack.isNotEmpty()) current = backStack.removeLast()
    }

    fun openDownload() {
        val v = latestVersion ?: return
        AppUpdateManager.openDownloadUrl(context, v)
        if (!(v.forceUpdate)) {
            showUpdateDialog = false
        }
    }

    BackHandler(enabled = backStack.isNotEmpty()) { back() }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = current,
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
                onPickDatabase = { navigate(WordListRoute(it)) },
                onOpenCheckIn = { navigate(CheckInRoute) },
                onOpenAbout = { navigate(AboutRoute) },
            )

            is CheckInRoute -> CheckInScreen(onBack = { back() })

            is AboutRoute -> AboutScreen(onBack = { back() })

            is WordListRoute -> WordListScreen(
                dbName = route.dbName,
                onBack = { back() },
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
            )

            is WordStudyRoute -> WordStudyScreen(
                dbName = route.dbName,
                onBack = { back() },
            )

            is LibraryRoute -> LibraryScreen(
                dbName = route.dbName,
                type = route.type,
                onBack = { back() },
                onWordClick = { navigate(WordDetailRoute(route.dbName, it)) },
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
                onBack = { back() },
            )
        }
    }

        // 全局更新弹窗
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
                    TextButton(onClick = { openDownload() }) {
                        Text("立即下载")
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
    }
}

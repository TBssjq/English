package com.ssjq.english

import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.UserManager
import com.ssjq.english.ui.AppNav
import com.ssjq.english.ui.glass.GlassQuality
import com.ssjq.english.ui.theme.EnglishTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UserLibrary.init(this)
        CheckInManager.init(this)
        UserManager.init(this)
        // 修复（启动 H-1）：升级后的首次启动必须先清空旧词库副本，再渲染首屏。
        // 旧实现把清理放在后台协程里，与首屏统计/查词的拷贝并发，会出现
        // 「刚拷完的副本被删掉」「打开已被删除的路径」，表现为首屏崩溃或词库丢失。
        // 清理本身只做文件 rename（进 .trash），耗时可忽略，因此放在 setContent 之前同步完成。
        val versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode
        if (!UserManager.isVersionChecked(versionCode)) {
            // 清理成功才落版本标记：失败时下次启动会重试，不会永久残留旧副本
            runCatching { DatabaseManager.clearCachedDatabases(applicationContext) }
                .onSuccess { UserManager.markVersionChecked(versionCode) }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            UserLibrary.repairStoredLists()
        }
        val activityManager = checkNotNull(getSystemService(ActivityManager::class.java))
        // 以系统低内存档位为准。仅凭堆上限会把部分正常设备误判成低端机，
        // 导致折射、色散与装饰层全部关闭，玻璃看起来没有厚度。
        GlassQuality.configure(activityManager.isLowRamDevice)
        GlassQuality.setEffectsEnabled(UserManager.glassEffectFlow.value)
        setContent {
            EnglishTheme {
                AppNav()
            }
        }
    }

    override fun onDestroy() {
        DatabaseManager.closeDatabase()
        super.onDestroy()
    }
}

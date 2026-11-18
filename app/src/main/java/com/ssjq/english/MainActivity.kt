package com.ssjq.english

import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.DatabaseManager
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.data.UserManager
import com.ssjq.english.ui.AppNav
import com.ssjq.english.ui.glass.GlassQuality
import com.ssjq.english.ui.theme.EnglishTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UserLibrary.init(this)
        CheckInManager.init(this)
        UserManager.init(this)
        // 升级后的第一次启动清空词库副本：副本不随应用更新，
        // 沿用旧副本会让新版本里修正过的词库内容不生效
        val versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode
        if (UserManager.markVersionChecked(versionCode)) {
            DatabaseManager.clearCachedDatabases(this)
        }
        val activityManager = checkNotNull(getSystemService(ActivityManager::class.java))
        GlassQuality.configure(
            activityManager.isLowRamDevice || Runtime.getRuntime().maxMemory() < 256L * 1024 * 1024
        )
        GlassQuality.setEffectsEnabled(UserManager.glassEffectEnabled())
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

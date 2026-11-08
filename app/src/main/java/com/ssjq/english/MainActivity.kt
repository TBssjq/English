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

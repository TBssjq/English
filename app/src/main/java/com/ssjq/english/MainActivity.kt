package com.ssjq.english

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ssjq.english.data.CheckInManager
import com.ssjq.english.data.UserLibrary
import com.ssjq.english.ui.AppNav
import com.ssjq.english.ui.theme.EnglishTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UserLibrary.init(this)
        CheckInManager.init(this)
        setContent {
            EnglishTheme {
                AppNav()
            }
        }
    }
}

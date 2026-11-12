package com.xiaozhangben.ledger

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaozhangben.ledger.data.prefs.AppSettings
import com.xiaozhangben.ledger.ui.navigation.LedgerRoot
import com.xiaozhangben.ledger.ui.theme.XiaoZhangBenTheme

/**
 * 用 FragmentActivity 而不是 ComponentActivity：androidx.biometric 的
 * BiometricPrompt 需要 FragmentActivity。
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as LedgerApp).container

        setContent {
            val settings by container.repository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            XiaoZhangBenTheme(themeMode = settings.themeMode) {
                LedgerRoot(
                    repository = container.repository,
                    settings = settings,
                )
            }
        }
    }
}

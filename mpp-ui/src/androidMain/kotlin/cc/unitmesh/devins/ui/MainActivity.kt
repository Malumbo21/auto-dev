package cc.unitmesh.devins.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cc.unitmesh.devins.db.DatabaseDriverFactory
import cc.unitmesh.devins.ui.compose.PlatformAutoDevApp
import cc.unitmesh.devins.ui.compose.launch.XiuperLaunchScreen
import cc.unitmesh.devins.ui.compose.theme.AutoDevTheme
import cc.unitmesh.devins.ui.compose.theme.ThemeManager
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.ui.platform.AndroidActivityProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidActivityProvider.setActivity(this)

        // 初始化数据库
        DatabaseDriverFactory.init(this)

        // 初始化配置管理器（必须在使用前调用）
        ConfigManager.initialize(this)

        enableEdgeToEdge()
        setContent {
            var showSplash by remember { mutableStateOf(true) }

            if (showSplash) {
                // Keep splash in a consistent dark theme for brand readability.
                AutoDevTheme(themeMode = ThemeManager.ThemeMode.DARK) {
                    XiuperLaunchScreen(
                        onFinished = { showSplash = false },
                        reducedMotion = false
                    )
                }
            } else {
                PlatformAutoDevApp()
            }
        }
    }
}

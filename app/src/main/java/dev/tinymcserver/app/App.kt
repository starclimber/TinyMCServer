package dev.tinymcserver.app

import android.app.Application
import dev.tinymcserver.app.core.frp.FrpAutoStarter
import dev.tinymcserver.app.core.i18n.I18n
import dev.tinymcserver.app.core.storage.Settings

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 尽早初始化语言：用户偏好优先，否则按系统语言自动选择，保证首帧即为目标语言
        I18n.init(this)
        Settings.attach(this)
        FrpAutoStarter.attach(this)
    }
}

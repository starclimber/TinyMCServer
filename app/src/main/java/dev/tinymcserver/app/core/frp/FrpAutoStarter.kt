package dev.tinymcserver.app.core.frp

import android.content.Context
import java.lang.ref.WeakReference

/**
 * 关联启动：服务器实例进入 RUNNING 时拉起绑定它的 frp 隧道，退出时收尾。
 * 由 ServerManager 在状态切换点调用；ctx 通过 attach 记住（Application context）。
 */
object FrpAutoStarter {

    private var ctxRef: WeakReference<Context>? = null

    fun attach(ctx: Context) { ctxRef = WeakReference(ctx.applicationContext) }

    private fun ctx(): Context? = ctxRef?.get()

    /** 服务器启动完成 → 拉起绑定的隧道 */
    fun onServerStarted(instanceId: String) {
        val c = ctx() ?: return
        FrpStore.load(c)
            .filter { it.enabled && it.autoStart && it.instanceId == instanceId }
            .forEach { FrpManager.start(c, it) }
    }

    /** 服务器停止/崩溃 → 停掉绑定的隧道 */
    fun onServerStopped(instanceId: String) {
        val c = ctx() ?: return
        FrpStore.load(c)
            .filter { it.instanceId == instanceId }
            .forEach { FrpManager.stop(it.id) }
    }
}

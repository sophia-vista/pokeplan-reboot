package app.pokeplan.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred

/** What the system installer answered for the update in flight: null for an install that went through, else words for the screen. */
object UpdateInstallResult {
    @Volatile
    private var pending: CompletableDeferred<String?>? = null

    fun begin(): CompletableDeferred<String?> = CompletableDeferred<String?>().also { pending = it }

    fun finish(problem: String?) {
        pending?.complete(problem)
    }
}

/** Receives the PackageInstaller session's status: opens the system's confirm page, or ends the wait with the reason. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE)?.takeIf { it.isNotBlank() }
        when (extras.getInt(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }
                if (confirm == null) {
                    UpdateInstallResult.finish("The installer did not ask to continue.")
                } else {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        UpdateInstallResult.finish("The installer could not open.")
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstallResult.finish(null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateInstallResult.finish("The install was cancelled.")
            PackageInstaller.STATUS_FAILURE_BLOCKED -> UpdateInstallResult.finish("The phone blocked the install${message?.let { ": $it" } ?: ""}. Check Auto Blocker and Play Protect.")
            PackageInstaller.STATUS_FAILURE_CONFLICT -> UpdateInstallResult.finish("The update conflicts with the installed app${message?.let { ": $it" } ?: ""}.")
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> UpdateInstallResult.finish("This phone cannot run the update${message?.let { ": $it" } ?: ""}.")
            PackageInstaller.STATUS_FAILURE_INVALID -> UpdateInstallResult.finish("The update file was rejected${message?.let { ": $it" } ?: ""}.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateInstallResult.finish("Not enough room to install${message?.let { ": $it" } ?: ""}.")
            else -> UpdateInstallResult.finish("The install failed${message?.let { ": $it" } ?: ""}.")
        }
    }

    companion object {
        const val ACTION = "app.pokeplan.UPDATE_INSTALL_STATUS"
    }
}

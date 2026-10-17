package com.homeos.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Background Health Connect -> HomeOS sync: every 15 minutes (WorkManager's minimum), or on demand. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val days = inputData.getLong("days", 2L)
        return runCatching {
            val summary = HealthSync.sync(applicationContext, prefs.url, prefs.alias, days)
            prefs.lastSync = "${java.time.LocalTime.now().withNano(0)} (background, $days d): $summary"
            Result.success()
        }.getOrElse {
            prefs.lastSync = "${java.time.LocalTime.now().withNano(0)} (background, $days d) failed: ${it.javaClass.simpleName}: ${it.message}"
            Result.retry()
        }
    }

    companion object {
        private val NETWORK = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(NETWORK).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("homeos-health-sync", ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(ctx: Context, days: Long) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(NETWORK).setInputData(workDataOf("days" to days)).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("homeos-health-sync-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

/**
 * Lets the team trigger a sync from a laptop without touching the phone's screen:
 *   adb shell am broadcast -n com.homeos.bridge/.SyncReceiver --el days 120
 * Restricted to the shell/system (android.permission.DUMP) so other apps can't fire it.
 */
class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        SyncWorker.runNow(ctx, intent.getLongExtra("days", 2L))
    }
}

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("homeos", Context.MODE_PRIVATE)
    var url: String
        get() = p.getString("url", BuildConfig.HOMEOS_URL)!!
        set(v) = p.edit().putString("url", v.trimEnd('/')).apply()
    var alias: String
        get() = p.getString("alias", BuildConfig.HOMEOS_ALIAS)!!
        set(v) = p.edit().putString("alias", v.trim()).apply()
    var lastSync: String
        get() = p.getString("lastSync", "never")!!
        set(v) = p.edit().putString("lastSync", v).commit().let { }
}

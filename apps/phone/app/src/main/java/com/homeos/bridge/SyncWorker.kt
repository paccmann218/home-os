package com.homeos.bridge

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Background Health Connect -> HomeOS sync every 15 minutes (WorkManager's minimum period). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        return runCatching {
            val summary = HealthSync.sync(applicationContext, prefs.url, prefs.alias, days = 2)
            prefs.lastSync = "${java.time.LocalTime.now().withNano(0)} (background): ${summary.lineSequence().first()}"
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("homeos-health-sync", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
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
        set(v) = p.edit().putString("lastSync", v).apply()
}

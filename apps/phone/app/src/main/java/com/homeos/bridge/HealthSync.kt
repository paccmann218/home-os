package com.homeos.bridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Reads the ring's data from Health Connect (Gabit writes sleep stages, heart rate, resting HR,
 * steps, VO2max; Samsung Health may add more) and posts it to the HomeOS backend, which turns
 * every night into a sleep Episode in the person's private FalkorDB graph.
 */
object HealthSync {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()

    fun available(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    private suspend fun <T : Record> readAll(client: HealthConnectClient, type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val res = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageSize = 1000, pageToken = token))
            out += res.records
            token = res.pageToken
        } while (token != null && out.size < 50_000)
        return out
    }

    private suspend fun <T : Record> safe(block: suspend () -> List<T>): List<T> = runCatching { block() }.getOrElse { emptyList() }

    private val STAGES = mapOf(
        SleepSessionRecord.STAGE_TYPE_DEEP to "deep",
        SleepSessionRecord.STAGE_TYPE_REM to "rem",
        SleepSessionRecord.STAGE_TYPE_LIGHT to "light",
        SleepSessionRecord.STAGE_TYPE_AWAKE to "awake",
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to "awake",
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to "awake",
        SleepSessionRecord.STAGE_TYPE_SLEEPING to "light",
    )

    /** Read [days] of history and post it. Returns a short human summary. */
    suspend fun sync(ctx: Context, baseUrl: String, alias: String, days: Long): String {
        val client = HealthConnectClient.getOrCreate(ctx)
        val to = Instant.now()
        val from = to.minus(Duration.ofDays(days))
        val sleep = safe { readAll(client, SleepSessionRecord::class, from, to) }
        val hr = safe { readAll(client, HeartRateRecord::class, to.minus(Duration.ofDays(minOf(days, 14L))), to) }
        val rhr = safe { readAll(client, RestingHeartRateRecord::class, from, to) }
        val hrv = safe { readAll(client, HeartRateVariabilityRmssdRecord::class, from, to) }
        val steps = safe { readAll(client, StepsRecord::class, from, to) }
        val ex = safe { readAll(client, ExerciseSessionRecord::class, from, to) }
        val vo2 = safe { readAll(client, Vo2MaxRecord::class, from, to) }

        val body = JSONObject()
            .put("source", "health_connect")
            .put("sleep", JSONArray(sleep.map { s ->
                JSONObject().put("start", s.startTime.toEpochMilli()).put("end", s.endTime.toEpochMilli())
                    .put("origin", s.metadata.dataOrigin.packageName)
                    .put("stages", JSONArray(s.stages.map {
                        JSONObject().put("start", it.startTime.toEpochMilli()).put("end", it.endTime.toEpochMilli()).put("stage", STAGES[it.stage] ?: "light")
                    }))
            }))
            .put("heart_rate", JSONArray(hr.flatMap { r -> r.samples.map { JSONObject().put("t", it.time.toEpochMilli()).put("bpm", it.beatsPerMinute) } }.takeLast(20_000)))
            .put("resting_hr", JSONArray(rhr.map { JSONObject().put("t", it.time.toEpochMilli()).put("bpm", it.beatsPerMinute) }))
            .put("hrv", JSONArray(hrv.map { JSONObject().put("t", it.time.toEpochMilli()).put("rmssd", it.heartRateVariabilityMillis) }))
            .put("steps", JSONArray(steps.map { JSONObject().put("start", it.startTime.toEpochMilli()).put("end", it.endTime.toEpochMilli()).put("count", it.count) }))
            .put("exercise", JSONArray(ex.map { JSONObject().put("start", it.startTime.toEpochMilli()).put("end", it.endTime.toEpochMilli()).put("type", it.exerciseType) }))
            .put("vo2max", JSONArray(vo2.map { JSONObject().put("t", it.time.toEpochMilli()).put("value", it.vo2MillilitersPerMinuteKilogram) }))

        val req = Request.Builder().url("$baseUrl/api/health/$alias").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val resp = http.newCall(req).execute().use { it.body?.string() ?: "" }
        val origins = sleep.map { it.metadata.dataOrigin.packageName }.distinct().joinToString()
        return "Read ${sleep.size} nights, ${hr.sumOf { it.samples.size }} HR samples, ${rhr.size} resting HR, ${steps.size} step records" +
            (if (origins.isNotBlank()) " (from $origins)" else "") + "\nServer: $resp"
    }
}

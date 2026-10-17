package com.homeos.wear

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType

/**
 * Continuous heart rate while the app is open, from Health Services (works on Galaxy Watch4+).
 * If the Samsung Health Sensor SDK AAR is present, [SamsungIbiStream] adds inter-beat intervals.
 */
class HeartRateStream(
    context: Context,
    private val onHr: (bpm: Double) -> Unit,
    private val onBody: (Boolean) -> Unit,
) {
    private val measure = HealthServices.getClient(context).measureClient
    private var registered = false

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (availability == DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY) onBody(false)
            if (availability == DataTypeAvailability.AVAILABLE) onBody(true)
        }

        override fun onDataReceived(data: DataPointContainer) {
            for (p in data.getData(DataType.HEART_RATE_BPM)) {
                val v = p.value
                if (v in 30.0..230.0) onHr(v)
            }
        }
    }

    fun start() {
        if (registered) return
        try {
            measure.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
            registered = true
        } catch (t: Throwable) {
            Log.w("HomeOSHr", "register failed: ${t.message}")
        }
    }

    fun stop() {
        if (!registered) return
        try {
            measure.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        } catch (_: Throwable) {
        }
        registered = false
    }
}

/**
 * Samsung Health Sensor SDK `HEART_RATE_CONTINUOUS` via reflection (compiles without the AAR).
 * Emits batches of heart rate + IBI (ms) so the backend can compute RMSSD (live HRV).
 * Reflection pattern adapted from the team's Rakshak SamsungSpo2Provider.
 */
class SamsungIbiStream(private val context: Context, private val onData: (hr: Double?, ibi: List<Int>) -> Unit) {
    private var service: Any? = null
    private var tracker: Any? = null

    fun available(): Boolean = try {
        Class.forName(SERVICE); true
    } catch (_: Throwable) {
        false
    }

    fun start(): Boolean {
        if (!available()) return false
        return try {
            val listenerCls = Class.forName("$SERVICE\$ConnectionListener")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(listenerCls.classLoader, arrayOf(listenerCls)) { _, m, _ ->
                if (m.name == "onConnectionSuccess") attach()
                null
            }
            val svc = Class.forName(SERVICE).getConstructor(Context::class.java, listenerCls).newInstance(context.applicationContext, proxy)
            service = svc
            svc.javaClass.getMethod("connectService").invoke(svc)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "connect failed: ${t.message}")
            false
        }
    }

    private fun attach() {
        try {
            val svc = service ?: return
            val typeCls = Class.forName("com.samsung.android.service.health.tracking.data.HealthTrackerType")
            @Suppress("UNCHECKED_CAST")
            val hrType = java.lang.Enum.valueOf(typeCls as Class<out Enum<*>>, "HEART_RATE_CONTINUOUS")
            val t = svc.javaClass.getMethod("getHealthTracker", typeCls).invoke(svc, hrType) ?: return
            tracker = t
            val lCls = Class.forName("com.samsung.android.service.health.tracking.HealthTracker\$TrackerEventListener")
            val keys = Class.forName("com.samsung.android.service.health.tracking.data.ValueKey\$HeartRateSet")
            val kHr = keys.getField("HEART_RATE").get(null)
            val kIbi = keys.getField("IBI_LIST").get(null)
            val kIbiStatus = runCatching { keys.getField("IBI_STATUS_LIST").get(null) }.getOrNull()
            val proxy = java.lang.reflect.Proxy.newProxyInstance(lCls.classLoader, arrayOf(lCls)) { _, m, args ->
                if (m.name == "onDataReceived") {
                    val points = args?.getOrNull(0) as? List<*> ?: return@newProxyInstance null
                    var hr: Double? = null
                    val ibis = mutableListOf<Int>()
                    for (p in points) {
                        if (p == null) continue
                        val get = p.javaClass.methods.first { it.name == "getValue" && it.parameterTypes.size == 1 }
                        (get.invoke(p, kHr) as? Number)?.toDouble()?.takeIf { it in 30.0..230.0 }?.let { hr = it }
                        val list = get.invoke(p, kIbi) as? List<*> ?: continue
                        val status = kIbiStatus?.let { get.invoke(p, it) as? List<*> }
                        list.forEachIndexed { i, v ->
                            val ok = status == null || (status.getOrNull(i) as? Number)?.toInt() == 0
                            val ms = (v as? Number)?.toInt() ?: return@forEachIndexed
                            if (ok && ms in 300..2000) ibis += ms
                        }
                    }
                    if (hr != null || ibis.isNotEmpty()) onData(hr, ibis)
                }
                null
            }
            t.javaClass.getMethod("setEventListener", lCls).invoke(t, proxy)
        } catch (e: Throwable) {
            Log.w(TAG, "attach failed: ${e.message}")
        }
    }

    fun stop() {
        runCatching { tracker?.javaClass?.getMethod("unsetEventListener")?.invoke(tracker) }
        runCatching { service?.javaClass?.getMethod("disconnectService")?.invoke(service) }
        tracker = null
        service = null
    }

    companion object {
        private const val TAG = "HomeOSIbi"
        private const val SERVICE = "com.samsung.android.service.health.tracking.HealthTrackingService"
    }
}

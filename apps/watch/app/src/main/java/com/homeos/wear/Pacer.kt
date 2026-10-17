package com.homeos.wear

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic breathing pacer: a rising buzz at the start of each inhale, a short tap at the start of
 * each exhale. At 6 breaths/min one breath is 10 s (inhale 4 s, exhale 6 s).
 */
class Pacer(context: Context) {
    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    fun start(bpm: Double, seconds: Int) {
        val breathMs = (60_000 / bpm).toLong()
        val inhale = (breathMs * 0.4).toLong()
        val exhale = breathMs - inhale
        val cycles = (seconds * 1000 / breathMs).toInt().coerceAtLeast(1)
        val timings = mutableListOf<Long>()
        val amps = mutableListOf<Int>()
        repeat(cycles) {
            // inhale: three ramping pulses
            timings += listOf(60L, 80L, 60L, 80L, 120L); amps += listOf(60, 0, 120, 0, 220)
            timings += inhale - 400; amps += 0
            // exhale: one soft tap
            timings += 40L; amps += 90
            timings += exhale - 40; amps += 0
        }
        vibrator.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amps.toIntArray(), -1))
    }

    fun stop() = vibrator.cancel()
}

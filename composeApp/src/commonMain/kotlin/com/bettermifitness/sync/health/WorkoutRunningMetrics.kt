package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.WorkoutRoutePoint
import com.bettermifitness.sync.data.api.WorkoutSession
import com.bettermifitness.sync.data.api.WorkoutTimedSample
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fills empty running-form series from Mi summary fields and GPS so HealthKit /
 * Health Connect workout Details (pace, cadence, stride, …) can populate.
 *
 * Does **not** invent power / GCT / vertical oscillation when Mi never sent them.
 */
object WorkoutRunningMetrics {

    private const val SAMPLE_STEP_SEC = 15L

    fun enrich(session: WorkoutSession): WorkoutSession {
        val start = session.startTime
        val end = session.endTime
        if (end <= start) return session

        var speed = session.speedSeries
        var pace = session.paceSeries
        var cadence = session.cadenceSeries
        var stride = session.strideMetersSeries
        var power = session.powerWattsSeries
        var gct = session.groundContactMsSeries
        var vo = session.verticalOscillationCmSeries

        // GPS → instantaneous speed
        if (speed.size < 2 && session.route.size >= 3) {
            speed = speedFromRoute(session.route)
        }
        // Speed → pace (sec/km)
        if (pace.size < 2 && speed.size >= 2) {
            pace = speed.mapNotNull { s ->
                if (s.value <= 0.05) return@mapNotNull null
                WorkoutTimedSample(s.timeSec, 1000.0 / s.value)
            }
        }
        // Summary pace / max speed → flat speed + pace
        if (speed.size < 2) {
            val mps = session.maxSpeedMps?.takeIf { it > 0.3 }
                ?: session.avgPaceSecPerKm?.takeIf { it in 60.0..3600.0 }?.let { 1000.0 / it }
            if (mps != null) speed = flatSeries(start, end, mps)
        }
        if (pace.size < 2) {
            session.avgPaceSecPerKm?.takeIf { it in 60.0..3600.0 }?.let {
                pace = flatSeries(start, end, it)
            }
        }
        // Cadence summary
        if (cadence.size < 2) {
            session.avgCadenceSpm?.takeIf { it in 40.0..250.0 }?.let {
                cadence = flatSeries(start, end, it)
            }
        }
        // Stride: Mi cm → meters, or derive from speed ÷ step rate
        if (stride.size < 2) {
            session.avgStrideCm?.takeIf { it in 20.0..250.0 }?.let {
                stride = flatSeries(start, end, it / 100.0)
            }
        }
        if (stride.size < 2) {
            val cad = session.avgCadenceSpm?.takeIf { it in 40.0..250.0 }
            val mps = speed.firstOrNull()?.value
                ?: session.maxSpeedMps?.takeIf { it > 0.3 }
                ?: session.avgPaceSecPerKm?.takeIf { it in 60.0..3600.0 }?.let { 1000.0 / it }
            if (cad != null && mps != null) {
                val strideM = mps / (cad / 60.0)
                if (strideM in 0.2..2.5) stride = flatSeries(start, end, strideM)
            }
        }
        // Power / GCT / VO only when Mi provided them (never invent)
        if (power.size < 2) {
            val w = session.avgPowerWatts?.takeIf { it in 20.0..2000.0 }
            if (w != null) power = flatSeries(start, end, w)
        }
        if (gct.size < 2) {
            session.avgGroundContactMs?.takeIf { it in 50.0..500.0 }?.let {
                gct = flatSeries(start, end, it)
            }
        }
        if (vo.size < 2) {
            session.avgVerticalOscillationCm?.takeIf { it in 1.0..30.0 }?.let {
                vo = flatSeries(start, end, it)
            }
        }

        return session.copy(
            speedSeries = speed,
            paceSeries = pace,
            cadenceSeries = cadence,
            strideMetersSeries = stride,
            powerWattsSeries = power,
            groundContactMsSeries = gct,
            verticalOscillationCmSeries = vo,
        )
    }

    fun flatSeries(start: Long, end: Long, value: Double): List<WorkoutTimedSample> {
        if (end <= start) return emptyList()
        val out = ArrayList<WorkoutTimedSample>()
        var t = start
        while (t <= end) {
            out += WorkoutTimedSample(t, value)
            t += SAMPLE_STEP_SEC
        }
        if (out.isEmpty() || out.last().timeSec != end) {
            out += WorkoutTimedSample(end, value)
        }
        return out
    }

    fun speedFromRoute(route: List<WorkoutRoutePoint>): List<WorkoutTimedSample> {
        if (route.size < 2) return emptyList()
        val sorted = route.sortedBy { it.timeSec }
        val out = ArrayList<WorkoutTimedSample>()
        for (i in 1 until sorted.size) {
            val a = sorted[i - 1]
            val b = sorted[i]
            val dt = (b.timeSec - a.timeSec).toDouble()
            if (dt <= 0) continue
            val d = haversineM(a.latitude, a.longitude, b.latitude, b.longitude)
            val mps = d / dt
            if (mps in 0.3..12.0) {
                out += WorkoutTimedSample(b.timeSec, mps)
            }
        }
        // Downsample dense GPS to ~SAMPLE_STEP_SEC for Health write size
        if (out.size <= 2) return out
        return downsample(out, SAMPLE_STEP_SEC)
    }

    private fun downsample(samples: List<WorkoutTimedSample>, step: Long): List<WorkoutTimedSample> {
        if (samples.isEmpty()) return samples
        val out = ArrayList<WorkoutTimedSample>()
        var next = samples.first().timeSec
        for (s in samples) {
            if (s.timeSec >= next || out.isEmpty()) {
                out += s
                next = s.timeSec + step
            }
        }
        val last = samples.last()
        if (out.last().timeSec != last.timeSec) out += last
        return out
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = lat1 * PI / 180.0
        val p2 = lat2 * PI / 180.0
        val dp = (lat2 - lat1) * PI / 180.0
        val dl = (lon2 - lon1) * PI / 180.0
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    private const val PI = 3.141592653589793
}

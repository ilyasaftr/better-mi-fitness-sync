package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.WorkoutRoutePoint
import com.bettermifitness.sync.data.api.WorkoutSession
import kotlin.test.Test
import kotlin.test.assertTrue

class WorkoutRunningMetricsTest {

    @Test
    fun enrich_fillsSpeedCadenceStrideFromSummary() {
        val s = WorkoutSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_003_600L,
            activityType = "running",
            avgPaceSecPerKm = 600.0,
            avgCadenceSpm = 160.0,
            avgStrideCm = 110.0,
            maxSpeedMps = 3.0,
        )
        val e = WorkoutRunningMetrics.enrich(s)
        assertTrue(e.speedSeries.size >= 2, "speed series from pace/max speed")
        assertTrue(e.paceSeries.size >= 2, "pace series")
        assertTrue(e.cadenceSeries.size >= 2, "cadence series")
        assertTrue(e.strideMetersSeries.size >= 2, "stride series")
        assertTrue(e.strideMetersSeries.first().value in 1.0..1.2)
        assertTrue(e.powerWattsSeries.isEmpty(), "no invented power")
        assertTrue(e.groundContactMsSeries.isEmpty())
        assertTrue(e.verticalOscillationCmSeries.isEmpty())
    }

    @Test
    fun enrich_derivesStrideFromSpeedAndCadence() {
        val s = WorkoutSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_003_600L,
            activityType = "running",
            avgPaceSecPerKm = 360.0, // 10 km/h → ~2.778 m/s
            avgCadenceSpm = 180.0,
            // no avgStrideCm
        )
        val e = WorkoutRunningMetrics.enrich(s)
        assertTrue(e.strideMetersSeries.size >= 2, "derived stride")
        // stride ≈ 2.778 / 3.0 ≈ 0.926 m
        val m = e.strideMetersSeries.first().value
        assertTrue(m in 0.85..1.0, "derived stride meters was $m")
    }

    @Test
    fun speedFromRoute_producesSamples() {
        val route = listOf(
            WorkoutRoutePoint(1_700_000_000L, -6.20, 106.80),
            WorkoutRoutePoint(1_700_000_010L, -6.2001, 106.8001),
            WorkoutRoutePoint(1_700_000_020L, -6.2002, 106.8002),
            WorkoutRoutePoint(1_700_000_030L, -6.2003, 106.8003),
        )
        val speed = WorkoutRunningMetrics.speedFromRoute(route)
        assertTrue(speed.isNotEmpty())
        assertTrue(speed.all { it.value in 0.3..12.0 })
    }
}

package com.bettermifitness.sync.health

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutMetricFamilyTest {

    @Test
    fun classify_running() {
        assertEquals(WorkoutMetricFamily.RUNNING, WorkoutMetricFamilies.classify("outdoor_running"))
        assertEquals(WorkoutMetricFamily.RUNNING, WorkoutMetricFamilies.classify("indoor_running"))
        assertEquals(WorkoutMetricFamily.RUNNING, WorkoutMetricFamilies.classify("Morning Run"))
    }

    @Test
    fun classify_walking() {
        assertEquals(WorkoutMetricFamily.WALKING, WorkoutMetricFamilies.classify("outdoor_walking"))
        assertEquals(WorkoutMetricFamily.WALKING, WorkoutMetricFamilies.classify("hiking"))
    }

    @Test
    fun classify_cycling() {
        assertEquals(WorkoutMetricFamily.CYCLING, WorkoutMetricFamilies.classify("outdoor_riding"))
        assertEquals(WorkoutMetricFamily.CYCLING, WorkoutMetricFamilies.classify("spinning"))
        assertEquals(WorkoutMetricFamily.CYCLING, WorkoutMetricFamilies.classify("cycling"))
    }

    @Test
    fun classify_swimming() {
        assertEquals(WorkoutMetricFamily.SWIMMING, WorkoutMetricFamilies.classify("pool_swimming"))
        assertEquals(WorkoutMetricFamily.SWIMMING, WorkoutMetricFamilies.classify("open_swimming"))
    }
}

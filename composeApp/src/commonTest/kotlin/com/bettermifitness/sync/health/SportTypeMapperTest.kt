package com.bettermifitness.sync.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SportTypeMapperTest {

    @Test
    fun exactApkKeys_mapToKnownHcTypes() {
        // Values match ExerciseSessionRecord constants from HC APK / connect-client.
        assertEquals(56, SportTypeMapper.healthConnectExerciseType("outdoor_running"))
        assertEquals(57, SportTypeMapper.healthConnectExerciseType("indoor_running"))
        assertEquals(8, SportTypeMapper.healthConnectExerciseType("outdoor_riding"))
        assertEquals(9, SportTypeMapper.healthConnectExerciseType("spinning"))
        assertEquals(74, SportTypeMapper.healthConnectExerciseType("pool_swimming"))
        assertEquals(73, SportTypeMapper.healthConnectExerciseType("open_swimming"))
        assertEquals(83, SportTypeMapper.healthConnectExerciseType("yoga"))
        assertEquals(70, SportTypeMapper.healthConnectExerciseType("strength_training"))
        assertEquals(36, SportTypeMapper.healthConnectExerciseType("high_interval_training"))
        assertEquals(5, SportTypeMapper.healthConnectExerciseType("basketball"))
        assertEquals(2, SportTypeMapper.healthConnectExerciseType("badminton"))
    }

    @Test
    fun exactApkKeys_mapToKnownHkTypes() {
        assertEquals(37L, SportTypeMapper.healthKitActivityType("outdoor_running")) // Running
        assertEquals(52L, SportTypeMapper.healthKitActivityType("outdoor_walking")) // Walking
        assertEquals(13L, SportTypeMapper.healthKitActivityType("outdoor_riding")) // Cycling
        assertEquals(46L, SportTypeMapper.healthKitActivityType("pool_swimming")) // Swimming
        assertEquals(57L, SportTypeMapper.healthKitActivityType("yoga")) // Yoga
        assertEquals(50L, SportTypeMapper.healthKitActivityType("weight_lifting")) // TraditionalStrength
        assertEquals(63L, SportTypeMapper.healthKitActivityType("high_interval_training")) // HIIT
    }

    @Test
    fun titles_areHumanReadable() {
        assertEquals("Outdoor Running", SportTypeMapper.displayTitle("outdoor_running"))
        assertEquals("Pool Swimming", SportTypeMapper.displayTitle("pool_swimming"))
        assertEquals("Jump Rope", SportTypeMapper.displayTitle("rope_skipping"))
    }

    @Test
    fun fuzzyFallback_stillBetterThanOther() {
        val run = SportTypeMapper.map("Morning Run Session")
        assertEquals(56, run.healthConnectType)
        assertEquals(37L, run.healthKitType)

        val unknown = SportTypeMapper.map("quantum_hoverboard")
        assertEquals(0, unknown.healthConnectType) // OTHER_WORKOUT
        assertEquals(3000L, unknown.healthKitType)
    }

    @Test
    fun normalize_handlesSpacesAndCase() {
        assertEquals(
            SportTypeMapper.map("Outdoor Running").healthConnectType,
            SportTypeMapper.map("outdoor_running").healthConnectType,
        )
    }

    @Test
    fun indoorVsOutdoor_runningDifferOnHc() {
        assertNotEquals(
            SportTypeMapper.healthConnectExerciseType("outdoor_running"),
            SportTypeMapper.healthConnectExerciseType("indoor_running"),
        )
        assertTrue(SportTypeMapper.healthConnectExerciseType("indoor_running") == 57)
    }
}

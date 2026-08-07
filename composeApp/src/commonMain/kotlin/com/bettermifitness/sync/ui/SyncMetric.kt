package com.bettermifitness.sync.ui

/** A health metric the app can sync (platform-agnostic; UI maps icons separately). */
enum class SyncMetric(
    val key: String,
    val label: String,
) {
    HEART_RATE("heart_rate", "Heart Rate"),
    RESTING_HEART_RATE("resting_heart_rate", "Resting Heart Rate"),
    SLEEP("sleep", "Sleep"),
    /** Overnight HRV from sleep payload; 0 samples if the band does not support HRV. */
    HRV("hrv", "HRV (overnight)"),
    STEPS("steps", "Steps"),
    DISTANCE("distance", "Distance"),
    ACTIVE_CALORIES("active_calories", "Active Calories"),
    SPO2("spo2", "Blood Oxygen (SpO2)"),
    WEIGHT("weight", "Weight & Body Fat"),
    WORKOUTS("workouts", "Workouts"),
    BLOOD_PRESSURE("blood_pressure", "Blood Pressure"),
    TEMPERATURE("temperature", "Body / Skin Temperature"),
    VO2_MAX("vo2_max", "VO2 Max"),
}

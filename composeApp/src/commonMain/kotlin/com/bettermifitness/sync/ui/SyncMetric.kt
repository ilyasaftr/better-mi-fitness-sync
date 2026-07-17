package com.bettermifitness.sync.ui

import com.bettermifitness.sync.ui.icons.AppIcons
import org.jetbrains.compose.resources.DrawableResource

/** A health metric the app can sync, with its display label and Material Symbol. */
enum class SyncMetric(
    val key: String,
    val label: String,
    val iconRes: DrawableResource,
) {
    HEART_RATE("heart_rate", "Heart Rate", AppIcons.MonitorHeart),
    RESTING_HEART_RATE("resting_heart_rate", "Resting Heart Rate", AppIcons.Favorite),
    SLEEP("sleep", "Sleep", AppIcons.Bedtime),
    STEPS("steps", "Steps", AppIcons.DirectionsWalk),
    DISTANCE("distance", "Distance", AppIcons.Straighten),
    ACTIVE_CALORIES("active_calories", "Active Calories", AppIcons.LocalFireDepartment),
    SPO2("spo2", "Blood Oxygen (SpO2)", AppIcons.Bloodtype),
    WEIGHT("weight", "Weight & Body Fat", AppIcons.MonitorWeight),
    WORKOUTS("workouts", "Workouts", AppIcons.FitnessCenter),
    // P2A — APK FitnessPersistKey + HC/HK write support
    BLOOD_PRESSURE("blood_pressure", "Blood Pressure", AppIcons.Bloodtype),
    TEMPERATURE("temperature", "Body / Skin Temperature", AppIcons.Favorite),
    VO2_MAX("vo2_max", "VO2 Max", AppIcons.FitnessCenter),
}

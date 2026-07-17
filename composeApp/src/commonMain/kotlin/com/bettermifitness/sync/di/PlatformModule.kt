package com.bettermifitness.sync.di

import org.koin.core.module.Module

/**
 * Platform composition root: DataStore file location + HealthWriter (HC / HealthKit).
 */
expect fun platformModule(): Module

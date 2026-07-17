package com.bettermifitness.sync.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * Starts Koin once with the shared app graph plus platform factories.
 * Call from Android [Application] / iOS entry only.
 */
fun initKoin(extraModules: List<Module> = emptyList()) {
    startKoin {
        modules(listOf(commonAppModule(), platformModule()) + extraModules)
    }
}

package com.bettermifitness.sync.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.bettermifitness.sync.health.HealthWriter
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module

private lateinit var appContext: Context

/** Must be called before [initKoin] on Android. */
fun provideAndroidContext(context: Context) {
    appContext = context.applicationContext
}

actual fun platformModule(): Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                appContext.filesDir
                    .resolve("mi_fitness_prefs.preferences_pb")
                    .absolutePath
                    .toPath()
            },
        )
    }
    single { HealthWriter(appContext) }
}

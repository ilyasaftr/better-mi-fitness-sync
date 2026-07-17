package com.bettermifitness.sync

import android.app.Application
import com.bettermifitness.sync.di.initKoin
import com.bettermifitness.sync.di.provideAndroidContext

class BetterMiFitnessSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        provideAndroidContext(this)
        AutoSyncPlatform.init(this)
        initKoin()
        AutoSyncSchedule.restore()
        AndroidForegroundAutoSync.register()
    }
}

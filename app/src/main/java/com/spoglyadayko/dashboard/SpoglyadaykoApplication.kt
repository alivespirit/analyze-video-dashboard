package com.spoglyadayko.dashboard

import android.app.Application
import com.spoglyadayko.dashboard.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SpoglyadaykoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SpoglyadaykoApplication)
            modules(appModule)
        }
    }
}

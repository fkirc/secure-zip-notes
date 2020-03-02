package com.ditronic.securezipnotes.app


import android.app.Application
import com.ditronic.securezipnotes.BuildConfig
import com.ditronic.securezipnotes.logging.CrashlyticsTree
import timber.log.Timber

class ApplicationExt : Application() {

    override fun onCreate() {
        super.onCreate()
        AppEnvironment.initialize(context = this)

        initLogging()
    }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }
    }
}

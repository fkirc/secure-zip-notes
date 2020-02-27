package com.ditronic.securezipnotes.app


import android.app.Application

class ApplicationExt : Application() {


    override fun onCreate() {
        super.onCreate()
        AppEnvironment.initialize(context = this)

        initLogging()
    }

    private fun initLogging() {
        // TODO: Plant Timber and Crashlytics tree
//        if (BuildConfig.DEBUG) {
//            Timber.plant(Timber.DebugTree())
//        }
    }
}

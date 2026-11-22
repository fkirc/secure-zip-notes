package com.ditronic.securezipnotes.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

class CrashlyticsTree : Timber.Tree() {

    private val crashlytics by lazy {
        FirebaseCrashlytics.getInstance()
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.ERROR || priority == Log.ASSERT) {
            logErrorWithoutCrash(message, t)
        }
    }

    private fun logErrorWithoutCrash(message: String, t: Throwable?) {
        if (t != null) {
            // If we have an exception, then the message is most likely redundant since it will also contain the stacktrace.
            crashlytics.recordException(t)
        } else {
            // If we do not have an exception, then we wrap the message inside an exception.
            crashlytics.recordException(IllegalStateException(message))
        }
    }
}

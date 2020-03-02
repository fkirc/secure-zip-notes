package com.ditronic.securezipnotes.contrib

import android.util.Log
import androidx.test.espresso.Espresso
import org.junit.Assert
import timber.log.Timber

/**
 * During UI tests, we want to fail the test immediately if any "non-fatal error" happens.
 * The purpose of non-fatal errors is to inform as about production failures without crashing the entire app.
 */
object NonFatalAbortTree: Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
        if (priority == Log.ERROR || priority == Log.ASSERT) {
            abortTest(message=message, throwable = throwable)
        }
    }

    private fun abortTest(message: String, throwable: Throwable?) {
        if (disabled) {
            return
        }
        Assert.fail(message)
        throw IllegalStateException(message, throwable)
    }

    fun plantInTimber() {
        Timber.plant(this)
    }

    private var disabled = false

    fun runWithoutAborts(waitIdle: Boolean = true, runnable: () -> Unit) {
        disabled = true
        runnable()
        if (waitIdle) {
            Espresso.onIdle()
        }
        disabled = false
    }
}

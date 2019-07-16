package com.ditronic.securezipnotes.util

import android.os.SystemClock
import android.view.View

abstract class OnThrottleClickListener : View.OnClickListener {

    private var lastClickTime: Long = 0
    protected abstract fun onThrottleClick(v: View)

    override fun onClick(v: View) {
        val clickTime = SystemClock.elapsedRealtime()
        if (clickTime - lastClickTime < THROTTLE_TIME) {
            return
        }
        lastClickTime = clickTime
        onThrottleClick(v)
    }

    companion object {

        private const val THROTTLE_TIME: Long = 500 // milliseconds
    }
}

package com.ditronic.securezipnotes.util

import android.os.SystemClock
import android.view.View
import android.widget.AdapterView

abstract class OnThrottleItemClickListener : AdapterView.OnItemClickListener {

    private var lastClickTime: Long = 0
    protected abstract fun onThrottleItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long)

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val clickTime = SystemClock.elapsedRealtime()
        if (clickTime - lastClickTime < THROTTLE_TIME) {
            return
        }
        lastClickTime = clickTime
        onThrottleItemClick(parent, view, position, id)
    }

    companion object {

        private val THROTTLE_TIME: Long = 500 // milliseconds
    }
}

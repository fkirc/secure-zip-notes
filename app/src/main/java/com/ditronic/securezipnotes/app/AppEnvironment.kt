package com.ditronic.securezipnotes.app

import android.content.Context


class AppEnvironment(val context: Context) {

    companion object {
        lateinit var current: AppEnvironment

        fun initialize(context: Context) {
            current = AppEnvironment(context = context)
        }
    }
}

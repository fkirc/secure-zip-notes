package com.ditronic.securezipnotes.app

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
class AppEnvironment(val context: Context) {

    companion object {
        lateinit var current: AppEnvironment

        fun initialize(context: Context) {
            current = AppEnvironment(context = context)
        }
    }
}

package com.ditronic.securezipnotes.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources.NotFoundException
import android.widget.Toast
import java.lang.ref.WeakReference

/**
 * [Toast] decorator allowing for easy cancellation of notifications. Use
 * this class if you want subsequent Toast notifications to overwrite current
 * ones.
 */
class Boast private constructor(private val internalToast: Toast) {

    fun show() {
        val oldToast = globalBoast?.get()
        oldToast?.cancel()
        globalBoast = WeakReference(internalToast)
        internalToast.show()
    }

    companion object {

        @Volatile
        private var globalBoast: WeakReference<Toast>? = null

        @SuppressLint("ShowToast")
        fun makeText(context: Context?, text: CharSequence?,
                     duration: Int): Boast {
            return Boast(Toast.makeText(context, text, duration))
        }

        @SuppressLint("ShowToast")
        @Throws(NotFoundException::class)
        fun makeText(context: Context?, resId: Int, duration: Int): Boast {
            return Boast(Toast.makeText(context, resId, duration))
        }

        @SuppressLint("ShowToast")
        fun makeText(context: Context?, text: CharSequence?): Boast {
            return Boast(Toast.makeText(context, text, Toast.LENGTH_SHORT))
        }

        @SuppressLint("ShowToast")
        @Throws(NotFoundException::class)
        fun makeText(context: Context?, resId: Int): Boast {
            return Boast(Toast.makeText(context, resId, Toast.LENGTH_SHORT))
        }

        fun showText(context: Context?, text: CharSequence?, duration: Int) {
            makeText(context, text, duration).show()
        }

        @Throws(NotFoundException::class)
        fun showText(context: Context?, resId: Int, duration: Int) {
            makeText(context, resId, duration).show()
        }

        fun showText(context: Context?, text: CharSequence?) {
            makeText(context, text, Toast.LENGTH_SHORT).show()
        }

        @Throws(NotFoundException::class)
        fun showText(context: Context?, resId: Int) {
            makeText(context, resId, Toast.LENGTH_SHORT).show()
        }
    }
}

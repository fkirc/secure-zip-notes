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
 *
 * By default, a current [Boast] notification will be cancelled by a
 * subsequent notification. This default behaviour can be changed by calling
 * certain methods like [.show].
 */
class Boast private constructor(toast: Toast?) {
    // ////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Internal reference to the [Toast] object that will be displayed.
     */
    private val internalToast: Toast
    // ////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Close the view if it's showing, or don't show it if it isn't showing yet.
     * You do not normally have to call this. Normally view will disappear on
     * its own after the appropriate duration.
     */
    fun cancel() {
        internalToast.cancel()
    }
    /**
     * Show the view for the specified duration. This method can be used to
     * cancel the current notification, or to queue up notifications.
     *
     * @param cancelCurrent
     * `true` to cancel any current notification and replace
     * it with this new one
     *
     * @see .show
     */
    /**
     * Show the view for the specified duration. By default, this method cancels
     * any current notification to immediately display the new one. For
     * conventional [Toast.show] queueing behaviour, use method
     * [.show].
     *
     * @see .show
     */
    @JvmOverloads
    fun show(cancelCurrent: Boolean = true) { // cancel current Toast if existing and requested
        if (globalBoast != null) {
            val globBoastRef = globalBoast!!.get()
            if (cancelCurrent && globBoastRef != null) {
                globBoastRef.cancel()
            }
        }
        // save an instance of this current notification
        globalBoast = WeakReference(this)
        internalToast.show()
    }

    companion object {
        /**
         * Keeps track of certain [Boast] notifications that may need to be cancelled.
         * This functionality is only offered by some of the methods in this class.
         */
        @Volatile
        private var globalBoast: WeakReference<Boast>? = null
        // ////////////////////////////////////////////////////////////////////////////////////////////////////////
        /**
         * Make a standard [Boast] that just contains a text view.
         *
         * @param context
         * The context to use. Usually your [android.app.Application]
         * or [android.app.Activity] object.
         * @param text
         * The text to show. Can be formatted text.
         * @param duration
         * How long to display the message. Either [] or
         * []
         */
        @SuppressLint("ShowToast")
        fun makeText(context: Context?, text: CharSequence?,
                     duration: Int): Boast {
            return Boast(Toast.makeText(context, text, duration))
        }

        /**
         * Make a standard [Boast] that just contains a text view with the
         * text from a resource.
         *
         * @param context
         * The context to use. Usually your [android.app.Application]
         * or [android.app.Activity] object.
         * @param resId
         * The resource id of the string resource to use. Can be formatted
         * text.
         * @param duration
         * How long to display the message. Either [] or
         * []
         *
         * @throws Resources.NotFoundException
         * if the resource can't be found.
         */
        @SuppressLint("ShowToast")
        @Throws(NotFoundException::class)
        fun makeText(context: Context?, resId: Int, duration: Int): Boast {
            return Boast(Toast.makeText(context, resId, duration))
        }

        /**
         * Make a standard [Boast] that just contains a text view. Duration
         * defaults to [].
         *
         * @param context
         * The context to use. Usually your [android.app.Application]
         * or [android.app.Activity] object.
         * @param text
         * The text to show. Can be formatted text.
         */
        @SuppressLint("ShowToast")
        fun makeText(context: Context?, text: CharSequence?): Boast {
            return Boast(Toast.makeText(context, text, Toast.LENGTH_SHORT))
        }

        /**
         * Make a standard [Boast] that just contains a text view with the
         * text from a resource. Duration defaults to [].
         *
         * @param context
         * The context to use. Usually your [android.app.Application]
         * or [android.app.Activity] object.
         * @param resId
         * The resource id of the string resource to use. Can be formatted
         * text.
         *
         * @throws Resources.NotFoundException
         * if the resource can't be found.
         */
        @SuppressLint("ShowToast")
        @Throws(NotFoundException::class)
        fun makeText(context: Context?, resId: Int): Boast {
            return Boast(Toast.makeText(context, resId, Toast.LENGTH_SHORT))
        }
        // ////////////////////////////////////////////////////////////////////////////////////////////////////////
        /**
         * Show a standard [Boast] that just contains a text view.
         *
         * @param context
         * The context to use. Usually your [android.app.Application]
         * or [android.app.Activity] object.
         * @param text
         * The text to show. Can be formatted text.
         * @param duration
         * How long to display the message. Either [] or
         * []
         */
        fun showText(context: Context?, text: CharSequence?, duration: Int) {
            makeText(context, text, duration).show()
        }

        /**
         * Show a standard [Boast] that just contains a text view with the
         * text from a resource.
         *
         * @param context
         * The context to use. Usually your [android.app.Application]
         * or [android.app.Activity] object.
         * @param resId
         * The resource id of the string resource to use. Can be formatted
         * text.
         * @param duration
         * How long to display the message. Either [] or
         * []
         *
         * @throws Resources.NotFoundException
         * if the resource can't be found.
         */
        @Throws(NotFoundException::class)
        fun showText(context: Context?, resId: Int, duration: Int) {
            makeText(context, resId, duration).show()
        }

        /**
         * Show a standard [Boast] that just contains a text view. Duration
         * defaults to [].
         *
         * @param context
         * The context to use. Usually your [android.app.Application]
         * or [android.app.Activity] object.
         * @param text
         * The text to show. Can be formatted text.
         */
        fun showText(context: Context?, text: CharSequence?) {
            makeText(context, text, Toast.LENGTH_SHORT).show()
        }

        /**
         * Show a standard [Boast] that just contains a text view with the
         * text from a resource. Duration defaults to [].
         *
         * @param context
         * The context to use. Usually your [android.app.Application]
         * or [android.app.Activity] object.
         * @param resId
         * The resource id of the string resource to use. Can be formatted
         * text.
         *
         * @throws Resources.NotFoundException
         * if the resource can't be found.
         */
        @Throws(NotFoundException::class)
        fun showText(context: Context?, resId: Int) {
            makeText(context, resId, Toast.LENGTH_SHORT).show()
        }
    }
    // ////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Private constructor creates a new [Boast] from a given
     * [Toast].
     *
     * @throws NullPointerException
     * if the parameter is `null`.
     */
    init { // null check
        if (toast == null) {
            throw NullPointerException(
                    "Boast.Boast(Toast) requires a non-null parameter.")
        }
        internalToast = toast
    }
}
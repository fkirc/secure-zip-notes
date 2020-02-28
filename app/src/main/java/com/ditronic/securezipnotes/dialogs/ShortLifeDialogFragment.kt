package com.ditronic.securezipnotes.dialogs

import android.util.Log
import androidx.annotation.CallSuper
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity

fun DialogFragment.dismissCrashSafe() {
    try {
        dismiss()
    } catch (exception: Exception) {
        // TODO: Integrate Timber
        Log.e("DialogFragment", "dismissCrashSafe", exception)
    }
}

data class FragmentTag(val value: String)

/**
 * Generic dialog that can hold an arbitrary state and gets dismissed upon activity re-creation.
 */
abstract class ShortLifeDialogFragment<EphemeralState>: DialogFragment() {

    abstract fun getFragmentTag(): FragmentTag

    private var ephemeralState: EphemeralState? = null

    fun show(activity: FragmentActivity, state: EphemeralState) {
        this.ephemeralState = state
        dismissIfActive(activity = activity)
        show(activity.supportFragmentManager, getFragmentTag().value)
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        fetchStateOrDie()
    }

    protected fun fetchStateOrDie(): EphemeralState? {
        if (ephemeralState == null) {
            dismissCrashSafe()
        }
        return ephemeralState
    }

    private fun dismissIfActive(activity: FragmentActivity) {
        val fragmentManager = activity.supportFragmentManager
        val oldDialog = fragmentManager.findFragmentByTag(getFragmentTag().value) as? DialogFragment
        oldDialog?.dismissCrashSafe()
    }
}

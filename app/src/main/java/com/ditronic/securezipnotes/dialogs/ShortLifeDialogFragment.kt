package com.ditronic.securezipnotes.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

fun DialogFragment.dismissCrashSafe() {
    try {
        dismiss()
    } catch (exception: Exception) {
        Timber.e(exception)
    }
}

data class FragmentTag(val value: String)

/**
 * Generic dialog that can hold an arbitrary state and gets dismissed upon activity re-creation.
 */
abstract class ShortLifeDialogFragment<EphemeralState>: DialogFragment() {

    abstract fun getFragmentTag(): FragmentTag

    abstract fun onCreateDialog(savedInstanceState: Bundle?, state: EphemeralState): Dialog

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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val state = fetchStateOrDie()
        return if (state != null) {
            onCreateDialog(savedInstanceState, state)
        } else {
            androidx.appcompat.app.AlertDialog.Builder(requireContext()).create()
        }
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

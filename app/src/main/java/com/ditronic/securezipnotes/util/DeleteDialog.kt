package com.ditronic.securezipnotes.util

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.ditronic.securezipnotes.password.FragmentTag
import com.ditronic.securezipnotes.password.PwDialog
import com.ditronic.securezipnotes.password.dismissCrashSafe

// TODO: Empty fragment constructor
class DeleteDialog(val continuations: DialogActions,
                   val message: String): DialogFragment() {

    interface DialogActions {
        fun onPositiveClick()
        fun onNegativeClick()
    }

    companion object {
        val TAG = FragmentTag("DeleteDialog")

        private fun dismissIfActive(activity: FragmentActivity) {
            val fragmentManager = activity.supportFragmentManager
            val oldDialog = fragmentManager.findFragmentByTag(TAG.value) as? DialogFragment
            oldDialog?.dismissCrashSafe()
        }

        fun show(message: String, activity: FragmentActivity, continuations: DialogActions) {
            val dialog = DeleteDialog(continuations = continuations, message = message)
            dismissIfActive(activity = activity)
            dialog.show(activity.supportFragmentManager, PwDialog.TAG.value)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes) { _, _ -> continuations.onPositiveClick() }
                .setNegativeButton(android.R.string.no) { _, _ -> continuations.onNegativeClick() }
                .create()
    }
}

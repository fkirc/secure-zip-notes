package com.ditronic.securezipnotes.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog

abstract class DeleteDialogState(val message: String) {
    abstract fun onPositiveClick()
    abstract fun onNegativeClick()
}

class DeleteDialog: ShortLifeDialogFragment<DeleteDialogState>() {

    companion object {
        val TAG = FragmentTag("DeleteDialog")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?, state: DeleteDialogState): Dialog {
        return AlertDialog.Builder(requireContext())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(state.message)
                .setPositiveButton("OK") { _, _ -> state.onPositiveClick() }
                .setNegativeButton("Cancel") { _, _ -> state.onNegativeClick() }
                .create()
    }

    override fun getFragmentTag() = TAG
}

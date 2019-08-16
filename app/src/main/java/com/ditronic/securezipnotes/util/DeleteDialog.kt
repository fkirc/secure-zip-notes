package com.ditronic.securezipnotes.util

import android.content.Context

import androidx.appcompat.app.AlertDialog

object DeleteDialog {

    interface DialogActions {
        fun onPositiveClick()
        fun onNegativeClick()
    }

    fun showDeleteQuestion(message: String, cx: Context, target: DialogActions) {
        AlertDialog.Builder(cx)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes) { dialog, id -> target.onPositiveClick() }
                .setNegativeButton(android.R.string.no) { dialog, id -> target.onNegativeClick() }
                .show()
    }
}

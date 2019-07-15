package com.ditronic.securezipnotes.util;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

public class DeleteDialog {

    public interface DialogActions {
        void onPositiveClick();
        @SuppressWarnings("EmptyMethod")
        void onNegativeClick();
    }

    public static void showDeleteQuestion(final String message, final Context cx, final DialogActions target) {
        new AlertDialog.Builder(cx)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, (dialog, id) -> target.onPositiveClick())
                .setNegativeButton(android.R.string.no, (dialog, id) -> target.onNegativeClick())
                .show();
    }
}

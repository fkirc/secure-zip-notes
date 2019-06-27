package com.ditronic.securezipnotes.util;

import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

public class DeleteDialog {

    public interface DialogActions {
        void onPositiveClick();
        void onNegativeClick();
    }

    public static void showDeleteQuestion(final String message, final Context cx, final DialogActions target) {
        new AlertDialog.Builder(cx)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        target.onPositiveClick();
                    }
                }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        target.onNegativeClick();
                    }
                }).show();
    }
}

package com.ditronic.securezipnotes;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.ditronic.simplefilesync.util.FilesUtil;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.AESExtraDataRecord;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;


import static com.ditronic.securezipnotes.CryptoZip.MIN_INNER_FILE_NAME_LEN;

public class NotesImport {

    private static final String TAG = NotesImport.class.getName();

    private static void alertDialog(final Context cx, final String message) {
        new AlertDialog.Builder(cx)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
    }


    private static boolean isValidAesZipFile(final Context cx, final File tmpFile) {
        final ZipFile tmpZipFile;
        try {
            tmpZipFile = new ZipFile(tmpFile.getPath());
            tmpZipFile.readZipInfo();
        } catch (Exception e) {
            Log.d(TAG, "Failed to import zip notes", e);
            alertDialog(cx, "Import failed. Probably this is not a valid *.aeszip file.");
            return false;
        }

        final List<FileHeader> fileHeaders = tmpZipFile.getFileHeadersFast();

        for (final FileHeader fh : fileHeaders) {
            if (fh.isDirectory()) {
                alertDialog(cx, "Import failed. Zip files with subdirectories are not supported.");
                return false;
            }
        }
        for (final FileHeader fh : fileHeaders) {
            if (!fh.isEncrypted()) {
                alertDialog(cx, "Import failed. Zip files with non-encrypted files are not supported.");
                return false;
            }
        }
        for (final FileHeader fh : fileHeaders) {
            if (fh.getEncryptionMethod() != EncryptionMethod.AES) {
                alertDialog(cx, "Import failed due to unsupported encryption algorithm. This app only supports Zip files with AES encryption.");
                return false;
            }
        }
        for (final FileHeader fh : fileHeaders) {
            final AESExtraDataRecord aesExtraDataRecord = fh.getAesExtraDataRecord();
            if (aesExtraDataRecord == null) {
                alertDialog(cx, "Import failed. Could not find AES data record.");
                return false;
            }
        }
        for (final FileHeader fh : fileHeaders) {
            if (fh.getFileName().length() < MIN_INNER_FILE_NAME_LEN) {
                alertDialog(cx, "Import failed due to short inner file names. Inner file names must be long enough to contain a unique identifier at the end of the file name.");
                return false;
            }
        }

        return true;
    }

    public static void importFromFile(final Context cx, final File tmpFile, final String successMessage) {
        if (!isValidAesZipFile(cx, tmpFile)) {
            tmpFile.delete();
            return;
        }

        try {
            FilesUtil.copyFile(tmpFile, CryptoZip.getMainFilePath(cx));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        tmpFile.delete();
        CryptoZip.resetCryptoZip(cx); // Refresh data after an import
        alertDialog(cx, successMessage);
    }

    public static void importFromUri(final Context cx, @Nullable final Uri importUri) {
        if (importUri == null) {
            alertDialog(cx, "Failed to select a file");
            return;
        }

        final InputStream is;
        try {
            is = cx.getContentResolver().openInputStream(importUri);
        } catch (FileNotFoundException e) {
            alertDialog(cx, "Failed to find file.");
            return;
        }
        if (is == null) {
            alertDialog(cx, "Failed to open file.");
            return;
        }
        final File tmpFile = FilesUtil.streamToTmpFile(is);
        importFromFile(cx, tmpFile, "Successfully imported zip notes.");
    }
}

package com.ditronic.securezipnotes

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog

import com.ditronic.simplefilesync.util.FilesUtil

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.AESExtraDataRecord
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.util.Zip4jConstants

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream


import com.ditronic.securezipnotes.CryptoZip.MIN_INNER_FILE_NAME_LEN

object NotesImport {

    private val TAG = NotesImport::class.java.name

    private fun alertDialog(cx: Context, message: String) {
        AlertDialog.Builder(cx)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { dialog, which -> }.show()
    }


    private fun isValidAesZipFile(cx: Context, tmpFile: File): Boolean {
        val tmpZipFile: ZipFile
        try {
            tmpZipFile = ZipFile(tmpFile.path)
            tmpZipFile.readZipInfo()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to import zip notes", e)
            alertDialog(cx, "Import failed. Probably this is not a valid *.aeszip file.")
            return false
        }

        val fileHeaders = tmpZipFile.fileHeadersFast

        for (fh in fileHeaders!!) {
            if (fh.isDirectory()) {
                alertDialog(cx, "Import failed. Zip files with subdirectories are not supported.")
                return false
            }
        }
        for (fh in fileHeaders) {
            if (!fh.isEncrypted()) {
                alertDialog(cx, "Import failed. Zip files with non-encrypted files are not supported.")
                return false
            }
        }
        for (fh in fileHeaders) {
            if (fh.getEncryptionMethod() != Zip4jConstants.ENC_METHOD_AES) {
                alertDialog(cx, "Import failed due to unsupported encryption algorithm. This app only supports Zip files with AES encryption.")
                return false
            }
        }
        for (fh in fileHeaders) {
            val aesExtraDataRecord = fh.getAesExtraDataRecord()
            if (aesExtraDataRecord == null) {
                alertDialog(cx, "Import failed. Could not find AES data record.")
                return false
            }
        }
        for (fh in fileHeaders) {
            if (fh.getFileName().length < MIN_INNER_FILE_NAME_LEN) {
                alertDialog(cx, "Import failed due to short inner file names. Inner file names must be long enough to contain a unique identifier at the end of the file name.")
                return false
            }
        }

        return true
    }

    fun importFromFile(cx: Context, tmpFile: File, successMessage: String) {
        if (!isValidAesZipFile(cx, tmpFile)) {
            tmpFile.delete()
            return
        }

        try {
            FilesUtil.copyFile(tmpFile, CryptoZip.getMainFilePath(cx))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        tmpFile.delete()
        CryptoZip.resetCryptoZip(cx) // Refresh data after an import
        alertDialog(cx, successMessage)
    }

    fun importFromUri(cx: Context, importUri: Uri?) {
        if (importUri == null) {
            alertDialog(cx, "Failed to select a file")
            return
        }

        val `is`: InputStream?
        try {
            `is` = cx.contentResolver.openInputStream(importUri)
        } catch (e: FileNotFoundException) {
            alertDialog(cx, "Failed to find file.")
            return
        }

        if (`is` == null) {
            alertDialog(cx, "Failed to open file.")
            return
        }
        val tmpFile = FilesUtil.streamToTmpFile(`is`)
        importFromFile(cx, tmpFile, "Successfully imported zip notes.")
    }
}

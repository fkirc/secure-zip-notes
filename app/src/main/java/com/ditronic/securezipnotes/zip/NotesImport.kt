package com.ditronic.securezipnotes.zip

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.ditronic.simplefilesync.util.FilesUtil
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.util.Zip4jConstants
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream


object NotesImport {

    private val TAG = NotesImport::class.java.name

    private fun alertDialog(cx: Context, message: String) {
        AlertDialog.Builder(cx)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> }.show()
    }

    private fun validateCompressionMethod(cx: Context, fileHeader: FileHeader) : Boolean {
        val aesExtraDataRecord = fileHeader.aesExtraDataRecord
        if (aesExtraDataRecord == null) {
            alertDialog(cx, "Import failed. Could not find AES data record.")
            return false
        }
        val compMethod = aesExtraDataRecord.compressionMethod
        if (compMethod == Zip4jConstants.COMP_STORE) {
            return true
        } else if (compMethod == Zip4jConstants.COMP_DEFLATE) {
            return true
        } else if (compMethod == 12) {
            alertDialog(cx, "Import failed: This app does not support BZIP2 compression.")
            return false
        } else if (compMethod == 9) {
            alertDialog(cx, "Import failed: This app does not support DEFLATE64 compression.")
            return false
        } else if (compMethod == 14) {
            alertDialog(cx, "Import failed: This app does not support LZMA compression.")
            return false
        } else if (compMethod == 98) {
            alertDialog(cx, "Import failed: This app does not support PPMD compression.")
            return false
        } else {
            alertDialog(cx, "Import failed: Unsupported compression method (" + compMethod + ").")
            return false
        }
    }

    private fun isValidAesZipFile(cx: Context, tmpFile: File): Boolean {
        val tmpZipFile: ZipFile
        try {
            tmpZipFile = ZipFile(tmpFile.path)
            tmpZipFile.readZipInfo()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to import zip notes", e)
            alertDialog(cx, "Import failed. Probably this is not a valid Zip file.")
            return false
        }

        val fileHeaders = tmpZipFile.fileHeadersFast

        fileHeaders.forEach {
            if (it.isDirectory) {
                alertDialog(cx, "Import failed. Zip files with pure directory entries are not supported.")
                return false
            }
        }
        fileHeaders.forEach {
            if (!it.isEncrypted) {
                alertDialog(cx, "Import failed. Zip files with non-encrypted entries are not supported.")
                return false
            }
        }
        fileHeaders.forEach {
            if (it.encryptionMethod != Zip4jConstants.ENC_METHOD_AES) {
                alertDialog(cx, "Unsupported encryption algorithm. This app only supports Zip files with AES encryption.")
                return false
            }
        }
        fileHeaders.forEach {
            if (it.fileName.isEmpty()) {
                alertDialog(cx, "File names must not have zero lengths.")
                return false
            }
        }
        fileHeaders.forEach {
            if (!validateCompressionMethod(cx, it)) {
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

package com.ditronic.securezipnotes.zip

import android.content.Context
import com.ditronic.securezipnotes.util.Boast
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.exception.ZipExceptionConstants
import net.lingala.zip4j.io.ZipInputStream
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream


/**
 * This singleton class acts as a frontend to the zip4j library, holding the main zip file instance.
 */
class CryptoZip private constructor(cx: Context) {

    private var zipFile: ZipFile

    val fileHeadersFast: List<FileHeader>?
        get() {
            try {
                return zipFile.fileHeadersFast
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

    val numFileHeaders: Int
        get() {
            val fileHeaders = fileHeadersFast ?: return 0
            return fileHeaders.size
        }

    private fun refreshZipInfo(cx: Context) {
        val f = getMainFilePath(cx)
        if (!f.exists()) {
            return
        }
        try {
            zipFile.readZipInfo()
        } catch (e: ZipException) {
            throw RuntimeException(e)
        }

    }

    init {

        val zipStoragePath = getMainFilePath(cx)
        try {
            zipFile = ZipFile(zipStoragePath)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        refreshZipInfo(cx)
    }

    fun isDuplicateEntryName(entryName: String) : Boolean {
        val fileHeaders = zipFile.fileHeadersFast ?: return false
        return null != fileHeaders.find {
            it.fileName == entryName
        }
    }

    fun addStream(displayName: String, pw: String, inputStream: InputStream) {

        if (isDuplicateEntryName(displayName)) {
            throw java.lang.RuntimeException("Must not add a duplicate entry name")
        }

        val parameters = ZipParameters()
        parameters.fileNameInZip = displayName
        // For security reasons, it is best to NOT compress data before encrypting it.
        // Compressing data after encryption is useless since AES ciphertexts are random bits with maximal entropy.
        parameters.compressionMethod = Zip4jConstants.COMP_STORE
        //parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_FASTEST);
        parameters.isEncryptFiles = true
        parameters.isSourceExternalStream = true
        // The standard Zip encryption is broken, therefore we use AES.
        parameters.encryptionMethod = Zip4jConstants.ENC_METHOD_AES
        parameters.aesKeyStrength = Zip4jConstants.AES_STRENGTH_256

        parameters.password = pw.toCharArray()

        try {
            zipFile.addStream(inputStream, parameters)
            inputStream.close()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }


    fun updateStream(pw: String, fileHeader: FileHeader, newEntryName: String, newContent: String) {

        val inStream = ByteArrayInputStream(newContent.toByteArray())
        try {
            // This might lead to a potential data loss in the case of an early exception.
            // However, there must not exist two simultaneous entries with the same name.
            // Moreover, this seems like the solution with less memory overhead.
            zipFile.removeFile(fileHeader)
            addStream(displayName = newEntryName, pw = pw, inputStream = inStream)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun renameFile(pw: String, fileHeader: FileHeader, newEntryName: String, cx : Context) {
        if (isDuplicateEntryName(newEntryName)) {
            Boast.makeText(cx, newEntryName + " already exists").show()
            return
        }
        if (!validateEntryNameToast(newEntryName, cx)) {
            return
        }
        fileHeader.password = pw.toCharArray()
        try {
            val inputStream = zipFile.getInputStream(fileHeader)
            addStream(displayName = newEntryName, pw = pw, inputStream = inputStream) // closes input stream
            zipFile.removeFile(fileHeader)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun isPasswordValid(fileHeader: FileHeader, password: String?): ZipInputStream? {

        if (password == null) {
            return null
        }

        if (!fileHeader.isEncrypted) {
            throw RuntimeException("Expected encrypted file header")
        }

        fileHeader.password = password.toCharArray()

        val zipStream : ZipInputStream?
        try {
            zipStream = zipFile.getInputStream(fileHeader)
            //is.close(true);
        } catch (e: ZipException) {
            // This check is a workaround for a bug in Zip4j version 2.0.3.
            //if (e.message!!.contains("Wrong Password")) {
            //    return false
            //}
            if (e.code == ZipExceptionConstants.WRONG_PASSWORD) {
                return null
            } else {
                throw RuntimeException(e)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        return zipStream
    }


    fun removeFile(cx: Context, fileHeader: FileHeader) {
        if (numFileHeaders <= 1) {
            // Work around seek bug with zero entries and enable a fresh import after deleting everything

            getMainFilePath(cx).delete()
            instance_ = null
        } else {
            try {
                zipFile.removeFile(fileHeader)
            } catch (e: ZipException) {
                throw RuntimeException(e)
            }

        }
        Boast.makeText(cx, "Removed " + getDisplayName(fileHeader)).show()
    }

    fun getFileHeader(innerFileName: String): FileHeader? {
        try {
            return zipFile.getFileHeader(innerFileName)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }


    fun extractFileString(zipInputStream: ZipInputStream): String {

        try {
            val content = inputStreamToString(zipInputStream)
            zipInputStream.close()
            return content
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    companion object {

        private var instance_: CryptoZip? = null

        fun instance(cx: Context): CryptoZip {
            if (instance_ == null) {
                instance_ = CryptoZip(cx.applicationContext)
            }
            return instance_!!
        }

        private const val MAIN_FILE_NAME = "securezipnotes_internal.aeszip"

        fun getDisplayName(fileHeader: FileHeader): String {
            return fileHeader.fileName
        }

        fun getMainFilePath(cx: Context): File {
            return File(cx.filesDir, MAIN_FILE_NAME)
        }

        fun resetCryptoZip(cx: Context) {
            instance_ = null // This is an expensive operation that should be only done after a fresh import.
            instance(cx)
        }

        private val TAG = CryptoZip::class.java.name
    }


    fun generateUnusedFileName() : String {
        lateinit var displayName : String
        var upCount = 1 // This stays at 1 in almost all cases.
        do {
            displayName = "Note " + (upCount++ + numFileHeaders)
        } while(isDuplicateEntryName(displayName))
        return displayName
    }
}

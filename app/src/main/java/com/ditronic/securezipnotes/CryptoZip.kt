package com.ditronic.securezipnotes

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
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID


/**
 * This singleton class acts as a frontend to the zip4j library, holding the main zip file instance.
 */
class CryptoZip private constructor(cx: Context) {

    private var zipFile: ZipFile? = null

    val fileHeadersFast: List<FileHeader>?
        get() {
            try {
                return zipFile!!.fileHeadersFast
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
            zipFile!!.readZipInfo()
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

    fun addStream(displayName: String, `is`: InputStream): String {
        val parameters = ZipParameters()
        val innerFileName = constructUIDName(displayName)
        parameters.fileNameInZip = innerFileName
        // For security reasons, it is best to NOT compress data before encrypting it.
        // Compressing data after encryption is useless since the entropy of encrypted data is expected to be maximal.
        parameters.compressionMethod = Zip4jConstants.COMP_STORE
        //parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_FASTEST);
        parameters.isEncryptFiles = true
        parameters.isSourceExternalStream = true
        // The standard Zip encryption is broken, therefore we use AES.
        parameters.encryptionMethod = Zip4jConstants.ENC_METHOD_AES
        parameters.aesKeyStrength = Zip4jConstants.AES_STRENGTH_256

        parameters.password = PwManager.instance().passwordFast

        try {
            zipFile!!.addStream(`is`, parameters)
            `is`.close()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        return parameters.fileNameInZip
    }

    fun updateStream(fileHeader: FileHeader, newFileName: String, newContent: String): String {

        val newInnerFileName: String

        val `is` = ByteArrayInputStream(newContent.toByteArray())
        try {
            newInnerFileName = addStream(newFileName, `is`)
            zipFile!!.removeFile(fileHeader)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        return newInnerFileName
    }

    fun renameFile(fileHeader: FileHeader, newDisplayName: String) {

        fileHeader.password = PwManager.instance().passwordFast

        try {
            val `is` = zipFile!!.getInputStream(fileHeader)
            addStream(newDisplayName, `is`) // closes input stream
            zipFile!!.removeFile(fileHeader)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }

    fun isPasswordValid(fileHeader: FileHeader, password: String): Boolean {
        if (!fileHeader.isEncrypted) {
            throw RuntimeException("Expected encrypted file header")
        }
        if (password.isEmpty()) {
            return false
        }

        fileHeader.password = password.toCharArray()

        try {
            val `is` = zipFile!!.getInputStream(fileHeader)
            //is.close();
            `is`.close(true)
        } catch (e: ZipException) {
            // This check is a workaround for a bug in Zip4j version 2.0.3.
            //if (e.message!!.contains("Wrong Password")) {
            //    return false
            //}
            return if (e.code == ZipExceptionConstants.WRONG_PASSWORD) {
                false
            } else {
                throw RuntimeException(e)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        return true
    }


    fun removeFile(cx: Context, fileHeader: FileHeader) {
        if (numFileHeaders <= 1) {
            // Work around seek bug with zero entries and enable a fresh import after deleting everything

            getMainFilePath(cx).delete()
            instance_ = null
        } else {
            try {
                zipFile!!.removeFile(fileHeader)
            } catch (e: ZipException) {
                throw RuntimeException(e)
            }

        }
        Boast.makeText(cx, "Removed " + getDisplayName(fileHeader)).show()
    }

    fun getFileHeader(innerFileName: String): FileHeader? {
        try {
            return zipFile!!.getFileHeader(innerFileName)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    }


    fun extractFileString(fileHeader: FileHeader): String? {

        val pw = PwManager.instance().passwordFast
                ?: return null // Prior singleton instance has been killed, we cannot recreate it synchronously
        fileHeader.password = pw

        try {
            val `is` = zipFile!!.getInputStream(fileHeader)
            val content = inputStreamToString(`is`)
            `is`.close()
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

        private val MAIN_FILE_NAME = "securezipnotes_internal.aeszip"
        private val UUID_SEPARATOR = "__"
        val MIN_INNER_FILE_NAME_LEN = UUID_SEPARATOR.length + 32 + 4

        private fun constructUIDName(displayName: String): String {
            return displayName + UUID_SEPARATOR + UUID.randomUUID().toString()
        }

        fun getDisplayName(fileHeader: FileHeader): String {
            val len = fileHeader.fileName.length
            if (len < MIN_INNER_FILE_NAME_LEN) {
                throw RuntimeException("file header name too short")
            }
            return fileHeader.fileName.substring(0, len - MIN_INNER_FILE_NAME_LEN)
        }


        fun getMainFilePath(cx: Context): File {
            return File(cx.filesDir, MAIN_FILE_NAME)
        }

        fun resetCryptoZip(cx: Context) {
            instance_ = null // This is an expensive operation that should be only done after a fresh import.
            CryptoZip.instance(cx)
        }

        private val TAG = CryptoZip::class.java.name


        @Throws(IOException::class)
        private fun inputStreamToString(`is`: ZipInputStream): String {
            val ir = InputStreamReader(`is`, StandardCharsets.UTF_8)
            val sb = StringBuilder()
            val buf = CharArray(1024)
            while (true) {
                val n : Int = ir.read(buf)
                if (n != -1) {
                    sb.append(buf, 0, n)
                } else {
                    break
                }
            }
            return sb.toString()
        }
    }

}

package com.ditronic.securezipnotes.menu

import android.app.Activity
import android.content.Intent
import android.view.MenuItem
import androidx.core.content.FileProvider
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.util.Boast
import com.ditronic.securezipnotes.zip.CryptoZip
import com.ditronic.simplefilesync.DriveFileSync
import com.ditronic.simplefilesync.DropboxFileSync
import com.ditronic.simplefilesync.util.FilesUtil
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object MenuOptions {


    private val exportFileName: String
        get() {
            val dateString = SimpleDateFormat("yyyy-MM-dd-HH:mm", Locale.getDefault()).format(Calendar.getInstance().timeInMillis)
            return dateString + "_securezipnotes.aeszip"
        }

    private fun exportZipFile(ac: Activity) {

        val zipNotes = CryptoZip.getMainFilePath(ac)
        if (!zipNotes.exists()) {
            Boast.makeText(ac, R.string.toast_notes_empty_export).show()
            return
        }

        val tmpShareFile = File(ac.cacheDir, exportFileName)
        tmpShareFile.delete()
        tmpShareFile.deleteOnExit()
        try {
            FilesUtil.copyFile(zipNotes, tmpShareFile)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        val shareUri = FileProvider.getUriForFile(ac, ac.applicationContext.packageName + ".provider", tmpShareFile)
        intent.type = "application/octet-stream"
        intent.putExtra(Intent.EXTRA_STREAM, shareUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ac.startActivity(Intent.createChooser(intent, null)) // This provides a better menu than startActivity(intent)
    }

    fun onOptionsSharedItemSelected(item: MenuItem, ac: Activity): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                ac.onBackPressed()
                return true
            }
            R.id.action_export_zip_file -> {
                exportZipFile(ac)
                return true
            }
            R.id.action_sync_dropbox -> {
                DropboxFileSync.launchInitialOauthActivity(ac)
                return true
            }
            R.id.action_sync_drive -> {
                DriveFileSync.launchInitialOauthActivity(ac)
                return true
            }
            else -> return false
        }
    }
}

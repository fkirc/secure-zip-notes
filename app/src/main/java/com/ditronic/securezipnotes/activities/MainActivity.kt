package com.ditronic.securezipnotes.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.ditronic.securezipnotes.*
import com.ditronic.securezipnotes.adapters.NoteSelectAdapter
import com.ditronic.securezipnotes.password.PwManager
import com.ditronic.securezipnotes.util.*
import com.ditronic.simplefilesync.AbstractFileSync
import com.ditronic.simplefilesync.DriveFileSync
import com.ditronic.simplefilesync.DropboxFileSync
import com.ditronic.simplefilesync.util.ResultCode
import com.ditronic.simplefilesync.util.SSyncResult
import net.lingala.zip4j.model.FileHeader
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var noteSelectAdapter: NoteSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById<Toolbar>(R.id.tool_bar)
        setSupportActionBar(toolbar)
        toolbar.setLogo(R.mipmap.ic_launcher)
        if (supportActionBar != null) {
            supportActionBar!!.setTitle(R.string.app_name_main_activity)
        }
        noteSelectAdapter = NoteSelectAdapter(this)

        val notesListView = findViewById<ListView>(R.id.list_view_notes)
        notesListView.adapter = noteSelectAdapter

        registerForContextMenu(notesListView)

        notesListView.onItemClickListener = object : OnThrottleItemClickListener() {
            public override fun onThrottleItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val fileHeader = noteSelectAdapter.getItem(position) as FileHeader
                PwManager.instance().retrievePasswordAsync(this@MainActivity, fileHeader) { NoteEditActivity.launch(this@MainActivity, fileHeader.fileName) }
            }
        }
        notesListView.emptyView = findViewById(R.id.list_view_empty)

        findViewById<View>(R.id.btn_create_new_note).setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                btnNewNote()
            }
        })
        findViewById<View>(R.id.btn_import_existing_notes).setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                btnImportExistingNotes()
            }
        })
        findViewById<View>(R.id.btn_sync_dropbox).setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                DropboxFileSync.launchInitialOauthActivity(this@MainActivity)
            }
        })
        findViewById<View>(R.id.btn_sync_drive).setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                DriveFileSync.launchInitialOauthActivity(this@MainActivity)
            }
        })

        val bundle = intent.extras
        if (bundle != null && bundle.getBoolean(INTENT_NEW_NOTE)) {
            btnNewNote()
        }

        BannerAds.loadBottomAdsBanner(this)
    }

    private fun btnImportExistingNotes() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE_RES_CODE)
    }


    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_noteselect_longclick, menu)
    }

    private fun askNoteDelete(fileHeader: FileHeader) {
        DeleteDialog.showDeleteQuestion("Delete " + CryptoZip.getDisplayName(fileHeader) + "?", this, object : DeleteDialog.DialogActions {
            override fun onPositiveClick() {
                CryptoZip.instance(this@MainActivity).removeFile(this@MainActivity, fileHeader)
                this@MainActivity.noteSelectAdapter.notifyDataSetChanged()
            }

            override fun onNegativeClick() {}
        })
    }


    private fun renameFileDialog(fileHeader: FileHeader) {

        // Retrieving the password for renames should not be necessary, but this is the current implementation
        PwManager.instance().retrievePasswordAsync(this@MainActivity, fileHeader) {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Rename " + CryptoZip.getDisplayName(fileHeader))
            val input = EditText(this@MainActivity)
            input.inputType = InputType.TYPE_CLASS_TEXT
            input.setText(CryptoZip.getDisplayName(fileHeader))
            builder.setView(input)
            builder.setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString()
                CryptoZip.instance(this@MainActivity).renameFile(fileHeader, newName, this@MainActivity)
                this@MainActivity.noteSelectAdapter.notifyDataSetChanged()
            }

            builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            val dialog = builder.create()
            val window = dialog.window
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.show()
            input.requestFocus()
        }

    }


    override fun onContextItemSelected(item: MenuItem): Boolean {
        val info = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val fileHeader = noteSelectAdapter.getItem(info.position) as FileHeader
        return when (item.itemId) {
            R.id.long_click_delete -> {
                askNoteDelete(fileHeader)
                true
            }
            R.id.long_click_rename -> {
                renameFileDialog(fileHeader)
                true
            }
            else -> false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_noteselect, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {

        val menuSyncDropbox = menu.findItem(R.id.action_sync_dropbox)
        val menuSyncDrive = menu.findItem(R.id.action_sync_drive)
        menuSyncDropbox.isCheckable = false
        menuSyncDrive.isCheckable = false

        val syncBackend = AbstractFileSync.getCurrentSyncBackend(this)
        if (syncBackend != null && syncBackend == DropboxFileSync::class.java.simpleName) {
            menuSyncDropbox.setCheckable(true).isChecked = true
        } else if (syncBackend != null && syncBackend == DriveFileSync::class.java.simpleName) {
            menuSyncDrive.setCheckable(true).isChecked = true
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        DriveFileSync.onActivityResultOauthSignIn(this, requestCode, resultCode, data
        ) { this.initiateFileSync() }

        if (requestCode == REQUEST_CODE_IMPORT_FILE_RES_CODE && resultCode == Activity.RESULT_OK) {
            val importUri = data!!.data
            NotesImport.importFromUri(this, importUri)
            noteSelectAdapter.notifyDataSetChanged()
        }
    }


    public override fun onResume() {
        super.onResume()
        noteSelectAdapter.notifyDataSetChanged()

        // This has to be called before the actual Dropbox sync
        DropboxFileSync.onResumeFetchOAuthToken(this)

        initiateFileSync()
    }

    private fun initiateFileSync() {
        val localFile = CryptoZip.getMainFilePath(this)
        val REMOTE_BACKUP_FILE_NAME = "autosync_securezipnotes.aeszip"

        val syncBackend = AbstractFileSync.getCurrentSyncBackend(this) ?: return

        if (syncBackend == DropboxFileSync::class.java.simpleName) {
            DropboxFileSync(this, localFile, REMOTE_BACKUP_FILE_NAME
            ) { res -> this@MainActivity.onSyncCompleted(res, "Dropbox") }.execute()
        } else if (syncBackend == DriveFileSync::class.java.simpleName) {
            DriveFileSync(this, localFile, REMOTE_BACKUP_FILE_NAME
            ) { res -> this@MainActivity.onSyncCompleted(res, "Google Drive") }.execute()
        }
    }

    private fun onSyncCompleted(res: SSyncResult, cloudBackend: String) {

        if (res.resultCode == ResultCode.CONNECTION_FAILURE) {
            Boast.makeText(this@MainActivity, "Failed to connect to $cloudBackend", Toast.LENGTH_LONG).show()
        } else if (res.resultCode == ResultCode.DOWNLOAD_SUCCESS) {
            NotesImport.importFromFile(this@MainActivity, res.tmpDownloadFile, "Downloaded Zip notes from $cloudBackend")
            noteSelectAdapter.notifyDataSetChanged()
        } else if (res.resultCode == ResultCode.FILES_NOT_EXIST_OR_EMPTY && res.isSyncTriggeredByUser) {
            // Special case for new users that click "Dropbox sync" without having any data.
            Boast.makeText(this@MainActivity, "Could not find a $cloudBackend backup - Creating new Zip file...", Toast.LENGTH_LONG).show()
            btnNewNote()
        } else if (res.resultCode == ResultCode.REMOTE_EQUALS_LOCAL && res.isSyncTriggeredByUser) {
            // Give the user some feedback if he started this sync explicitly
            // since REMOTE_EQUALS_LOCAL did not show the ProgressDialog on its own.
            Boast.makeText(this@MainActivity, "$cloudBackend synchronized").show()
        }
    }

    private fun createNewNote() {
        val displayName = CryptoZip.instance(this).generateUnusedFileName()
        CryptoZip.instance(this@MainActivity).addStream(displayName, ByteArrayInputStream(ByteArray(0)))
        noteSelectAdapter.notifyDataSetChanged()
        NoteEditActivity.launch(this@MainActivity, displayName)
    }

    private fun btnNewNote() {

        if (CryptoZip.instance(this).numFileHeaders == 0) {
            if (PwManager.instance().passwordFast == null) {
                val intent = Intent(this, NewPasswordActivity::class.java)
                startActivity(intent)
            } else {
                createNewNote()
            }
        } else {
            val fileHeader = CryptoZip.instance(this).fileHeadersFast!![0] // We use this to ensure password consistency accross the zip file
            PwManager.instance().retrievePasswordAsync(this, fileHeader) { this.createNewNote() }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (MenuOptions.onOptionsSharedItemSelected(item, this)) {
            return true
        }
        return when (item.itemId) {
            R.id.action_add_note -> {
                btnNewNote()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {

        private const val INTENT_NEW_NOTE = "intent_new_note"

        fun launchCleanWithNewNote(cx: Context) {
            val intent = Intent(cx, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra(INTENT_NEW_NOTE, true)
            cx.startActivity(intent)
        }


        private const val REQUEST_CODE_IMPORT_FILE_RES_CODE = 1
    }
}

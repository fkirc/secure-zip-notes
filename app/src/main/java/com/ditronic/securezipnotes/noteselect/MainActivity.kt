package com.ditronic.securezipnotes.noteselect

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.databinding.ActivityMainBinding
import com.ditronic.securezipnotes.dialogs.DeleteDialog
import com.ditronic.securezipnotes.dialogs.DeleteDialogState
import com.ditronic.securezipnotes.dialogs.RenameFileDialog
import com.ditronic.securezipnotes.dialogs.RenameFileDialogState
import com.ditronic.securezipnotes.menu.MenuOptions
import com.ditronic.securezipnotes.noteedit.NoteEditActivity
import com.ditronic.securezipnotes.onboarding.NewPasswordActivity
import com.ditronic.securezipnotes.password.PwManager
import com.ditronic.securezipnotes.util.BannerAds
import com.ditronic.securezipnotes.util.Boast
import com.ditronic.securezipnotes.util.OnThrottleClickListener
import com.ditronic.securezipnotes.util.OnThrottleItemClickListener
import com.ditronic.securezipnotes.zip.CryptoZip
import com.ditronic.securezipnotes.zip.NotesImport
import com.ditronic.simplefilesync.AbstractFileSync
import com.ditronic.simplefilesync.DropboxFileSync
import com.ditronic.simplefilesync.util.ResultCode
import com.ditronic.simplefilesync.util.SSyncResult
import net.lingala.zip4j.model.FileHeader
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var noteSelectAdapter: NoteSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.toolBar
        setSupportActionBar(toolbar)
        toolbar.setLogo(R.mipmap.ic_launcher)
        supportActionBar?.setTitle(R.string.app_name_main_activity)

        noteSelectAdapter = NoteSelectAdapter(this)

        val notesListView = binding.listViewNotes
        notesListView.adapter = noteSelectAdapter

        registerForContextMenu(notesListView)

        notesListView.onItemClickListener = object : OnThrottleItemClickListener() {
            public override fun onThrottleItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val fileHeader = noteSelectAdapter.getItem(position) as FileHeader
                PwManager.retrievePasswordAsync(this@MainActivity, fileHeader = fileHeader) { res ->
                    NoteEditActivity.launch(this@MainActivity, fileHeader.fileName, res.inputStream)
                }
            }
        }
        notesListView.emptyView = binding.listViewEmpty

        binding.btnCreateNewNote.setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                btnNewNote()
            }
        })
        binding.btnImportExistingNotes.setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                btnImportExistingNotes()
            }
        })
        binding.btnSyncDropbox.setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                DropboxFileSync.launchInitialOauthActivity(this@MainActivity)
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
        val deleteMessage = "Delete " + CryptoZip.getDisplayName(fileHeader) + "?"
        DeleteDialog().show(this, object : DeleteDialogState(message = deleteMessage) {
            override fun onPositiveClick() {
                CryptoZip.instance(this@MainActivity).removeFile(this@MainActivity, fileHeader)
                this@MainActivity.noteSelectAdapter.notifyDataSetChanged()
            }
            override fun onNegativeClick() {}
        })
    }


    private fun renameFileDialog(fileHeader: FileHeader) {
        PwManager.retrievePasswordAsync(this@MainActivity, fileHeader) {
            it.inputStream?.close(true)
            RenameFileDialog().show(this@MainActivity, object: RenameFileDialogState(pwResult = it, fileHeader = fileHeader) {
                override fun onRenameReturned() {
                    this@MainActivity.noteSelectAdapter.notifyDataSetChanged()
                }
            })
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
        menuSyncDropbox.isCheckable = false

        val syncBackend = AbstractFileSync.getCurrentSyncBackend(this)
        if (syncBackend != null && syncBackend == DropboxFileSync::class.java.simpleName) {
            menuSyncDropbox.setCheckable(true).isChecked = true
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

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
        }
    }

    // TODO: Remove memory leak. Use ViewModel and observables instead.
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

    private fun createNewNote(pw: String) {
        val displayName = CryptoZip.instance(this).generateUnusedFileName()
        CryptoZip.instance(this@MainActivity).addStream(displayName = displayName, pw = pw, inputStream = ByteArrayInputStream(ByteArray(0)))
        noteSelectAdapter.notifyDataSetChanged()
        NoteEditActivity.launch(this@MainActivity, displayName, null)
    }

    private fun btnNewNote() {
        val fileHeaders = CryptoZip.instance(this).fileHeadersFast
        if (fileHeaders == null) {
            val cachedPw = PwManager.cachedPassword
            if (cachedPw == null) {
                val intent = Intent(this, NewPasswordActivity::class.java)
                startActivity(intent)
            } else {
                createNewNote(pw = cachedPw)
            }
        } else {
            val fileHeader = fileHeaders[0] // We use this to ensure password consistency across the zip file
            PwManager.retrievePasswordAsync(this, fileHeader) { res ->
                res.inputStream?.close(true)
                this.createNewNote(res.password)
            }
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

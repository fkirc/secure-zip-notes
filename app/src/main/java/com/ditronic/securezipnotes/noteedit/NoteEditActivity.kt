package com.ditronic.securezipnotes.noteedit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.util.BannerAds
import com.ditronic.securezipnotes.zip.CryptoZip
import com.ditronic.securezipnotes.zip.validateEntryNameToast
import net.lingala.zip4j.model.FileHeader

class NoteEditActivity : AppCompatActivity() {

    internal var editMode: Boolean = false
    internal lateinit var editTextMain: EditText
    internal lateinit var editTextTitle: EditText
    internal lateinit var innerFileName: String
    internal var secretContent: String? = null

    private val fileHeader: FileHeader
        get() = CryptoZip.instance(this).getFileHeader(innerFileName)!!

    private fun saveContent() {
        val newContent = editTextMain.text.toString()
        var newFileName = editTextTitle.text.toString()

        if (newFileName != fileHeader.fileName && CryptoZip.instance(this).isDuplicateEntryName(newFileName)) {
            Toast.makeText(this, newFileName + " already exists", Toast.LENGTH_SHORT).show()
            newFileName = CryptoZip.getDisplayName(fileHeader)
        }
        if (!validateEntryNameToast(newFileName, this)) {
            // Use old entry name as a fallback mode if there is a problem.
            newFileName = CryptoZip.getDisplayName(fileHeader)
        }

        if (newContent == secretContent && newFileName == CryptoZip.getDisplayName(fileHeader)) {
            editTextTitle.setText(newFileName)
            return  // Nothing to save, text unchanged
        }

        secretContent = newContent
        CryptoZip.instance(this).updateStream(fileHeader, newFileName, secretContent!!)
        innerFileName = newFileName // This must be set after the updateStream!
        editTextTitle.setText(CryptoZip.getDisplayName(fileHeader))
        Toast.makeText(this, "Saved " + CryptoZip.getDisplayName(fileHeader), Toast.LENGTH_SHORT).show()
    }

    private fun saveClick() {
        saveContent()
        applyEditMode(false)
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putString(INNER_FILE_NAME, innerFileName) // TODO: Replace this with ViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_edit)
        val toolbar = findViewById<Toolbar>(R.id.tool_bar_edit)
        setSupportActionBar(toolbar)
        editTextTitle = findViewById(R.id.edit_text_title)
        editTextMain = findViewById(R.id.edit_text_main)
        if (savedInstanceState != null) {
            innerFileName = savedInstanceState.getString(INNER_FILE_NAME)!!
        } else {
            innerFileName = intent.extras!!.getString(INNER_FILE_NAME)!!
        }

        if (supportActionBar != null) { // add back arrow to toolbar
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
            // We do not want a "title" since the EditText consumes all the space
            supportActionBar!!.setDisplayShowTitleEnabled(false)
        }

        // TODO: Check password at startup, make this self-contained...
        secretContent = CryptoZip.instance(this).extractFileString(fileHeader)
        if (secretContent == null) {
            finish() // Should almost never happen
            return
        }
        applyEditMode(secretContent!!.isEmpty())
        editTextTitle.setText(CryptoZip.getDisplayName(fileHeader))
        editTextMain.setText(secretContent)

        // Required to make links clickable
        //editTextMain.setMovementMethod(LinkMovementMethod.getInstance());

        BannerAds.loadBottomAdsBanner(this)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_edit_note).isVisible = !editMode
        menu.findItem(R.id.action_save_note).isVisible = editMode
        return true
    }

    override fun onPause() {
        super.onPause()
        // This needs to happen in onPause.
        // onStop is already too late because the onResume of the previous activity might be called before onStop.
        saveContent()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_noteedit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_edit_note -> {
                applyEditMode(true)
                true
            }
            R.id.action_save_note -> {
                saveClick()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {

        internal const val INNER_FILE_NAME = "inner_file_name"

        fun launch(cx: Context, innerFileName: String) {
            val intent = Intent(cx, NoteEditActivity::class.java)
            intent.putExtra(INNER_FILE_NAME, innerFileName)
            cx.startActivity(intent)
        }

        internal const val MIN_API_COPY_READ_ONLY = 23
    }
}

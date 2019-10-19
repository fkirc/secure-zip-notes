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

class NoteEditActivity : AppCompatActivity() {

    internal lateinit var editTextMain: EditText
    internal lateinit var editTextTitle: EditText
    internal lateinit var model: NoteEditViewModel

    private fun saveContent() {
        val newContent = editTextMain.text.toString()
        var newNoteName = editTextTitle.text.toString()

        if (newNoteName != model.noteName && CryptoZip.instance(this).isDuplicateEntryName(newNoteName)) {
            Toast.makeText(this, newNoteName + " already exists", Toast.LENGTH_SHORT).show()
            newNoteName = model.noteName
        }
        if (!validateEntryNameToast(newNoteName, this)) {
            // Use old entry name as a fallback mode if there is a problem.
            newNoteName = model.noteName
        }

        if (newContent == model.secretContent && newNoteName == model.noteName) {
            editTextTitle.setText(newNoteName)
            return  // Nothing to save, text unchanged
        }

        model.secretContent = newContent
        CryptoZip.instance(this).updateStream(model.fileHeader, newNoteName, model.secretContent!!)
        model.innerFileName = newNoteName // This must be set after the updateStream!
        editTextTitle.setText(model.noteName)
        Toast.makeText(this, "Saved " + model.noteName, Toast.LENGTH_SHORT).show()
    }

    private fun saveClick() {
        saveContent()
        applyEditMode(false)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val innerFileName = intent.extras!!.getString(INNER_FILE_NAME)!!
        model = NoteEditViewModel.instantiate(this, innerFileName)

        setContentView(R.layout.activity_note_edit)
        val toolbar = findViewById<Toolbar>(R.id.tool_bar_edit)
        setSupportActionBar(toolbar)

        editTextTitle = findViewById(R.id.edit_text_title)
        editTextMain = findViewById(R.id.edit_text_main)

        if (supportActionBar != null) { // add back arrow to toolbar
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
            // We do not want a "title" since the EditText consumes all the space
            supportActionBar!!.setDisplayShowTitleEnabled(false)
        }

        // TODO: Check password at startup, make this self-contained...
        model.secretContent = CryptoZip.instance(this).extractFileString(model.fileHeader)
        if (model.secretContent == null) {
            finish() // Should almost never happen
            return
        }
        applyEditMode(model.secretContent!!.isEmpty())
        editTextTitle.setText(model.noteName)
        editTextMain.setText(model.secretContent)

        // Required to make links clickable
        //editTextMain.setMovementMethod(LinkMovementMethod.getInstance());

        BannerAds.loadBottomAdsBanner(this)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_edit_note).isVisible = !model.editMode
        menu.findItem(R.id.action_save_note).isVisible = model.editMode
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

package com.ditronic.securezipnotes.activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

import com.ditronic.securezipnotes.CryptoZip
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.util.BannerAds
import com.ditronic.securezipnotes.util.Boast

import net.lingala.zip4j.model.FileHeader

import java.util.Objects

class NoteEditActivity : AppCompatActivity() {

    private var editMode: Boolean = false
    private lateinit var editTextMain: EditText
    private lateinit var editTextTitle: EditText
    private lateinit var innerFileName: String
    private var secretContent: String? = null

    private val fileHeader: FileHeader
        get() = CryptoZip.instance(this).getFileHeader(innerFileName)!!

    private fun applyEditMode(enable: Boolean) {
        editMode = enable

        // Rather simple procedure for title edit text
        editTextTitle.isCursorVisible = editMode
        editTextTitle.isClickable = editMode
        editTextTitle.isFocusable = editMode
        editTextTitle.isLongClickable = editMode
        editTextTitle.setTextIsSelectable(editMode)
        editTextTitle.isLongClickable = editMode


        // Complicated procedure for the main edit text
        if (Build.VERSION.SDK_INT >= MIN_API_COPY_READ_ONLY) { // 21
            editTextMain.showSoftInputOnFocus = editMode
            editTextMain.isCursorVisible = editMode
        } else {
            editTextMain.isCursorVisible = editMode
            editTextMain.isClickable = editMode
            editTextMain.isFocusable = editMode
            editTextMain.isLongClickable = editMode
            editTextMain.setTextIsSelectable(editMode)
            editTextMain.isLongClickable = editMode
        }

        if (!editMode) {
            editTextMain.customSelectionActionModeCallback = CustomSelectionActionModeCallback()
            if (Build.VERSION.SDK_INT >= MIN_API_COPY_READ_ONLY) { // 23
                editTextMain.customInsertionActionModeCallback = CustomInsertionActionModeCallback()
            }
            // Close keyboard
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editTextMain.windowToken, 0)
        } else {
            editTextMain.customSelectionActionModeCallback = null
            if (Build.VERSION.SDK_INT >= MIN_API_COPY_READ_ONLY) { // 23
                editTextMain.customInsertionActionModeCallback = null
            }
            // Open keyboard
            editTextMain.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editTextMain, InputMethodManager.SHOW_IMPLICIT)
        }

        invalidateOptionsMenu()
    }

    private class CustomSelectionActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            try {
                val copyItem = menu.findItem(android.R.id.copy)
                val title = copyItem.title
                menu.clear() // We only want copy functionality, no paste, no cut.
                menu.add(0, android.R.id.copy, 0, title)
            } catch (ignored: Exception) {
            }

            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    private class CustomInsertionActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    private fun saveContent() {
        if (!editMode) {
            return
        }
        val newContent = editTextMain.text.toString()
        var newFileName = editTextTitle.text.toString()

        if (newFileName != fileHeader.fileName && CryptoZip.instance(this).isDuplicateEntryName(newFileName)) {
            // Use old entry name as a fallback mode if there is a name conflict.
            Toast.makeText(this, newFileName + " already exists, keeping old name", Toast.LENGTH_SHORT).show()
            newFileName = CryptoZip.getDisplayName(fileHeader)
        }
        if (newFileName.isEmpty()) {
            Toast.makeText(this, "Empty file names are not allowed", Toast.LENGTH_SHORT).show()
            newFileName = CryptoZip.getDisplayName(fileHeader)
        }

        if (newContent == secretContent && newFileName == CryptoZip.getDisplayName(fileHeader)) {
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
        bundle.putString(INNER_FILE_NAME, innerFileName)
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

        private const val INNER_FILE_NAME = "inner_file_name"

        fun launch(cx: Context, innerFileName: String) {
            val intent = Intent(cx, NoteEditActivity::class.java)
            intent.putExtra(INNER_FILE_NAME, innerFileName)
            cx.startActivity(intent)
        }

        private const val MIN_API_COPY_READ_ONLY = 23
    }
}

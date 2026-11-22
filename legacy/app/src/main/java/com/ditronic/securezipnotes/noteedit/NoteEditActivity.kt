package com.ditronic.securezipnotes.noteedit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.app.AppEnvironment
import com.ditronic.securezipnotes.app.ViewModelFactory
import com.ditronic.securezipnotes.password.PwManager
import com.ditronic.securezipnotes.util.BannerAds
import com.ditronic.securezipnotes.util.Boast
import com.ditronic.securezipnotes.zip.CryptoZip
import com.ditronic.securezipnotes.zip.validateEntryNameToast
import kotlinx.android.synthetic.main.activity_note_edit.*
import net.lingala.zip4j.io.ZipInputStream
import java.util.*
import java.util.Collections.synchronizedMap

class NoteEditActivity : AppCompatActivity() {

    val model: NoteEditViewModel by lazy {
        ViewModelProvider(this, ViewModelFactory(AppEnvironment.current)).get(NoteEditViewModel::class.java)
    }

    private fun saveContent() {
        PwManager.retrievePasswordAsync(ac = this, fileHeader = model.fileHeader) {
            saveContent(password = it.password)
        }
    }

    private fun saveContent(password: String) {
        val oldContent = model.secretContent ?: return // Uninitialized state
        val oldNoteName = model.noteName

        val newContent = edit_text_main.text.toString()
        var newNoteName = edit_text_title.text.toString()

        if (newNoteName != oldNoteName && CryptoZip.instance(this).isDuplicateEntryName(newNoteName)) {
            Boast.makeText(this, newNoteName + " already exists", Toast.LENGTH_SHORT).show()
            newNoteName = oldNoteName
        }
        if (!validateEntryNameToast(newNoteName, this)) {
            // Use old entry name as a fallback mode if there is a problem.
            newNoteName = oldNoteName
        }

        if (newContent == oldContent && newNoteName == oldNoteName) {
            edit_text_title.setText(newNoteName)
            return  // Nothing to save, text unchanged
        }

        // Apply changes, point of no return
        model.secretContent = newContent
        CryptoZip.instance(this).updateStream(password, model.fileHeader, newNoteName, newContent)
        model.innerFileName = newNoteName // This must be set after the updateStream!

        edit_text_title.setText(newNoteName)
        Boast.makeText(this, "Saved " + newNoteName, Toast.LENGTH_SHORT).show()
    }

    private fun saveClick() {
        saveContent()
        applyEditMode(false)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_edit)
        setSupportActionBar(tool_bar_edit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        // We do not want a "title" since the EditText consumes all the space
        supportActionBar?.setDisplayShowTitleEnabled(false)

        model.innerFileName = intent.extras!!.getString(INNER_FILE_NAME)!!
        edit_text_title.setText(model.noteName)

        var inputStream = inputStreamCache.remove(model.innerFileName)
        if (inputStream == null) {
            inputStream = CryptoZip.instance(this).isPasswordValid(fileHeader = model.fileHeader, password = PwManager.cachedPassword)
        }
        if (inputStream == null) {
            finish() // Wrong password, should never happen at this point...
        } else {
            showSecretContent(inputStream)
        }

        BannerAds.loadBottomAdsBanner(this)
    }

    private fun showSecretContent(inputStream: ZipInputStream) {
        val secretContent = CryptoZip.instance(this).extractFileString(inputStream)
        model.secretContent = secretContent
        applyEditMode(secretContent.isEmpty())
        edit_text_main.setText(secretContent)
        // Required to make links clickable
        //editTextMain.setMovementMethod(LinkMovementMethod.getInstance());
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

        private val inputStreamCache = synchronizedMap(TreeMap<String, ZipInputStream>())

        internal const val INNER_FILE_NAME = "inner_file_name"

        fun launch(cx: Context, innerFileName: String, inputStream: ZipInputStream?) {
            val intent = Intent(cx, NoteEditActivity::class.java)
            intent.putExtra(INNER_FILE_NAME, innerFileName)
            inputStreamCache.put(innerFileName, inputStream)
            cx.startActivity(intent)
        }

        internal const val MIN_API_COPY_READ_ONLY = 23
    }
}

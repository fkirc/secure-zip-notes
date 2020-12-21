package com.ditronic.securezipnotes.tests

import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.robotpattern.*
import com.ditronic.securezipnotes.testutils.assertToast
import com.ditronic.securezipnotes.testutils.pressBack
import org.hamcrest.core.StringContains
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class ITest {

    companion object {
        private const val SECRET_NOTE = "My secret note"
    }

    @Test
    fun dropBoxInitOauth() {
        precondition_cleanStart()
        Intents.init()

        main_clickOptionsMenu(R.string.sync_with_dropbox)

        // First intent
        intended(IntentMatchers.hasComponent(hasClassName("com.dropbox.core.android.AuthActivity")))

        // Second intent
        intended(IntentMatchers.hasAction(Intent.ACTION_VIEW))
        intended(IntentMatchers.hasDataString(StringContains("https://www.dropbox.com/")))

        Intents.release()
    }

    @Test
    fun addMultipleNotes() {
        precondition_singleNote()

        val noteEntries = listOf("Note 1", "Note 2", "Note 3", "Note 4")
        for (noteCnt in 2..noteEntries.size) {
            main_addNewNote(typePassword = (noteCnt == 2))
            noteEdit_assertState(noteEntries[noteCnt - 1], "", editMode = true)
            noteEdit_typeText(noteCnt.toString())
            pressBack()
        }
        main_assertListState(noteEntries.reversed())
    }

    @Test
    fun invalidRenameMainMenu() {
        precondition_fourNotes()

        main_renameNote("Note 1", "Note 2", typePassword = true)
        Thread.sleep(1000)
        assertToast("Note 2 already exists")

        main_renameNote("Note 2", "")
        Thread.sleep(1000)
        assertToast("Empty file names are not allowed")

        main_renameNote("Note 2", "directory/")
        assertToast("directory/ is an invalid entry name")

        main_assertListState(listOf("Note 1", "Note 2", "Note 3", "Note 4").reversed())
    }

    @Test
    fun invalidRenameOpenedNote() {
        precondition_fourNotes()
        main_clickNote("Note 1", password = TESTPASSWORD)

        noteEdit_rename("Note 1", "Note 2")
        Thread.sleep(2000)
        //assertToast("Note 2 already exists")

        noteEdit_rename("Note 1", "")
        //assertToast("Empty file names are not allowed")

        noteEdit_rename("Note 1", "\\")
        //assertToast("\\ is an invalid entry name")

        pressBack()
        main_assertListState(listOf("Note 1", "Note 2", "Note 3", "Note 4").reversed())
    }

    @Test
    fun renameSingleNoteMainMenu() {
        precondition_singleNote()
        main_renameNote("Note 1", "Note renamed", typePassword = true)
        main_assertListState(listOf("Note renamed"))
    }

    @Test
    fun renameSingleOpen() {
        precondition_singleNote()
        main_clickNote("Note 1", password = TESTPASSWORD)
        noteEdit_assertState("Note 1", SECRET_NOTE)
        noteEdit_rename("Note 1", "Note renamed")
        noteEdit_assertState("Note renamed", SECRET_NOTE)
        pressBack()
        main_assertListState(listOf("Note renamed"))
    }

    @Test
    fun deleteMultipleNotes() {
        precondition_fourNotes()
        main_deleteNote("Note 2")
        main_deleteNote("Note 3")
        main_deleteNote("Note 4")
        main_assertListState(listOf("Note 1"))
    }

    @Test
    fun addNoteNamingConflict() {
        precondition_singleNote()
        main_addNewNote(typePassword = true)
        noteEdit_assertState("Note 2", "", editMode = true)
        pressBack()
        pressBack()
        main_assertListState(listOf("Note 1", "Note 2").reversed())
        main_renameNote("Note 2", "Note 3")
        main_assertListState(listOf("Note 1", "Note 3").reversed())
        main_addNewNote()
        pressBack()
        pressBack()
        main_assertListState(listOf("Note 1", "Note 3", "Note 4").reversed())
    }

    @Test
    fun deleteLastNote() {
        precondition_singleNote()
        main_deleteNote("Note 1")
        main_assertEmtpy()
    }

    @Test
    fun exportEmptyNote() {
        precondition_cleanStart()
        main_clickOptionsMenu(R.string.export_zip_file)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertToast(context.getString(R.string.toast_notes_empty_export))
    }
}

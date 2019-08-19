package com.ditronic.securezipnotes

import android.content.Intent
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import assertToast
import com.ditronic.securezipnotes.activities.MainActivity
import org.hamcrest.core.StringContains
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pressBack


@RunWith(AndroidJUnit4::class)
@LargeTest
class ITest {

    companion object {
        private const val SECRET_NOTE = "My secret note"
    }

    //@get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()
    @get:Rule var acRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)


    @Test
    fun dropBoxInitOauth() {
        precondition_cleanStart(acRule)
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
        precondition_singleNote(acRule)

        val noteEntries = listOf("Note 1", "Note 2", "Note 3", "Note 4")
        for (noteCnt in 2..noteEntries.size) {
            main_addNewNote(typePassword = (noteCnt == 2))
            noteEdit_assertState(noteEntries[noteCnt-1], "", editMode = true)
            noteEdit_typeText(noteCnt.toString())
            pressBack()
        }
        main_assertListState(noteEntries, acRule.activity)
    }

    @Test
    fun invalidRenameMainMenu() {
        precondition_fourNotes(acRule)

        main_renameNote("Note 1", "Note 2", typePassword = true)
        assertToast("Note 2 already exists", acRule.activity)

        main_renameNote("Note 2", "")
        assertToast("Empty file names are not allowed", acRule.activity)

        main_renameNote("Note 2", "directory/")
        assertToast("directory/ is an invalid entry name", acRule.activity)

        main_assertListState(listOf("Note 1", "Note 2", "Note 3", "Note 4"), acRule.activity)
    }

    @Test
    fun invalidRenameOpenedNote() {
        precondition_fourNotes(acRule)
        main_clickNote("Note 1", typePassword = true)

        noteEdit_rename("Note 1", "Note 2")
        assertToast("Note 2 already exists", acRule.activity)

        noteEdit_rename("Note 1", "")
        assertToast("Empty file names are not allowed", acRule.activity)

        noteEdit_rename("Note 1", "\\")
        assertToast("\\ is an invalid entry name", acRule.activity)

        pressBack()
        main_assertListState(listOf("Note 1", "Note 2", "Note 3", "Note 4"), acRule.activity)
    }

    @Test
    fun renameSingleNoteMainMenu() {
        precondition_singleNote(acRule)
        main_renameNote("Note 1", "Note renamed", typePassword = true)
        main_assertListState(listOf("Note renamed"), acRule.activity)
    }

    @Test
    fun renameSingleOpen() {
        precondition_singleNote(acRule)
        main_clickNote("Note 1", typePassword = true)
        noteEdit_assertState("Note 1", SECRET_NOTE)
        noteEdit_rename("Note 1", "Note renamed")
        noteEdit_assertState("Note renamed", SECRET_NOTE)
        pressBack()
        main_assertListState(listOf("Note renamed"), acRule.activity)
    }

    @Test
    fun deleteMultipleNotes() {
        precondition_fourNotes(acRule)
        main_deleteNote("Note 2")
        main_deleteNote("Note 3")
        main_deleteNote("Note 4")
        main_assertListState(listOf("Note 1"), acRule.activity)
    }

    @Test
    fun addNoteNamingConflict() {
        precondition_singleNote(acRule)
        main_addNewNote(typePassword = true)
        noteEdit_assertState("Note 2", "", editMode = true)
        pressBack()
        pressBack()
        main_assertListState(listOf("Note 1", "Note 2"), acRule.activity)
        main_renameNote("Note 2", "Note 3")
        main_assertListState(listOf("Note 1", "Note 3"), acRule.activity)
        main_addNewNote()
        main_assertListState(listOf("Note 1", "Note 3", "Note 4"), acRule.activity)
    }

    // TODO: Fix this test
    /*@Test
    fun exportNote() {
        precondition_singleNote(acRule)
        Intents.init()

        main_clickOptionsMenu(R.string.export_zip_file)

        intended(hasAction(Intent.ACTION_CHOOSER))
        intended(hasExtras(BundleMatchers.hasEntry("key", "value")))

        Intents.release()
    }*/

    @Test
    fun deleteLastNote() {
        precondition_singleNote(acRule)
        main_deleteNote("Note 1")
        main_assertEmtpy(acRule.activity)
    }

    @Test
    fun exportEmptyNote() {
        precondition_cleanStart(acRule)
        main_clickOptionsMenu(R.string.export_zip_file)
        assertToast(acRule.activity.getString(R.string.toast_notes_empty_export), acRule.activity)
    }

    // TODO: Measure code coverage
}

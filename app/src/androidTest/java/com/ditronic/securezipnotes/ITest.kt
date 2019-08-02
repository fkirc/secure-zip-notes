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
import com.ditronic.securezipnotes.util.TestUtil
import org.hamcrest.core.StringContains
import org.junit.Before
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

    @Before
    fun beforeEachTest() {
        TestUtil.isInstrumentationTest = true
    }

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
    fun renameNotesEdgeCases() {
        precondition_fourNotes(acRule)
        //TODO: Fix toast asserts

        // Verify that duplicate rename from main menu fails gracefully
        main_renameNote("Note 1", "Note 2", typePassword = true)
        //assertToast("Note 2 already exists!", acRule.activity)

        // Verify that empty rename from main menu does not change anything
        main_renameNote("Note 2", "")
        //assertToast("Name must not be empty", acRule.activity)

        // Verify that duplicate rename of opened note fails gracefully
        main_clickNote("Note 1")
        noteEdit_rename("Note 1", "Note 2")
        //assertToast("Note 2 already exists, keeping old name", acRule.activity)

        // Verify that empty rename of opened note does not touch the name
        noteEdit_rename("Note 2", "")
        //assertToast("Empty file names are not allowed", acRule.activity)

        pressBack()
        main_assertListState(listOf("Note 1", "Note 2", "Note 3", "Note 4"), acRule.activity)
    }

    @Test
    fun renameSingleNote() {
        precondition_singleNote(acRule)
        val noteRenamed = "Note renamed"

        main_renameNote("Note 1", noteRenamed, typePassword = true)
        main_assertListState(listOf(noteRenamed), acRule.activity)
        main_clickNote(noteRenamed)
        noteEdit_assertState(noteRenamed, SECRET_NOTE)

        // Rename back to old name
        noteEdit_rename(noteRenamed, "Note 1")
        noteEdit_assertState("Note 1", SECRET_NOTE)
        pressBack()
        main_assertListState(listOf("Note 1"), acRule.activity)
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
    // TODO: Import tests with Zip files from different source programs
}

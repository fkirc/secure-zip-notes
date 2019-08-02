package com.ditronic.securezipnotes

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import assertToast
import com.ditronic.securezipnotes.activities.MainActivity
import com.ditronic.securezipnotes.util.TestUtil
import com.ditronic.simplefilesync.AbstractFileSync
import com.ditronic.simplefilesync.DropboxFileSync
import com.ditronic.simplefilesync.util.ResultCode
import org.hamcrest.Matchers.containsString
import org.hamcrest.core.StringContains
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import pressBack
import targetContext
import java.text.SimpleDateFormat


@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
class ChangeTextBehaviorKtTest {

    companion object {
        private const val SECRET_NOTE = "My secret note"

        private const val DROPBOX_OAUTH_TOKEN = "T6OO59Oo9FoAAAAAAAANTySOeCziL-1_agAU2sr2mU8ArSZqr3RKb6ICU5a_JJVt"
    }

    //@get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()
    @get:Rule var acRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)

    @Before
    fun beforeEachTest() {
        TestUtil.isInstrumentationTest = true
    }

    @Test
    fun createNewPassword() {
        precondition_cleanStart(acRule)

        init_createNewZipFile()

        // TODO: Confirm that random master password gets generated within separate test
        init_genRandomPassword()
        init_chooseNewPassword("")
        init_chooseNewPassword(MASTER_PASSWORD)
        // TODO: Confirm wrong password tests
        init_confirmNewPassword(MASTER_PASSWORD)

        noteEdit_typeText(SECRET_NOTE)
        noteEdit_assertState("Note 1", SECRET_NOTE, editMode = true)
        pressBack()

        main_assertListState(listOf("Note 1"), acRule.activity)
        val noteEntry = main_extractEntryList(acRule.activity)[0]
        assertEquals("Size: " + ChangeTextBehaviorKtTest.SECRET_NOTE.length, noteEntry.size)
        assertThat(noteEntry.modDate, containsString(SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Calendar.getInstance().timeInMillis)))

        main_clickNote("Note 1")
        noteEdit_assertState("Note 1", SECRET_NOTE, editMode = false)
        pressBack()

        // Back to MainActivity, assert that nothing changed
        main_assertListState(listOf("Note 1"), acRule.activity)
        val newNoteEntry = main_extractEntryList(acRule.activity)[0]
        assertEquals(noteEntry, newNoteEntry)
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

    // TODO: Maybe move dbx tests to other class?
    @Test
    fun dbx1_dropBoxUpload() {
        precondition_singleNote(acRule, preLaunchHook = {
            DropboxFileSync.storeNewOauthToken(DROPBOX_OAUTH_TOKEN, targetContext())
        })
        Espresso.onIdle()
        assertEquals(ResultCode.UPLOAD_SUCCESS, AbstractFileSync.getLastSyncResult()!!.resultCode)
    }

    @Test
    fun dbx2_dropBoxRemoteEqualsLocal() {
        precondition_singleNote(acRule, preLaunchHook = {
            DropboxFileSync.storeNewOauthToken(DROPBOX_OAUTH_TOKEN, targetContext())
        })
        acRule.launchActivity(null)
        Espresso.onIdle()
        assertEquals(ResultCode.REMOTE_EQUALS_LOCAL, DropboxFileSync.getLastSyncResult()!!.resultCode)
    }

    @Test
    fun dbx3_dropBoxDownload() {
        precondition_cleanStart(acRule, preLaunchHook = {
            DropboxFileSync.storeNewOauthToken(DROPBOX_OAUTH_TOKEN, targetContext())
        })
        Espresso.onIdle()
        assertEquals(ResultCode.DOWNLOAD_SUCCESS, DropboxFileSync.getLastSyncResult()!!.resultCode)
        main_assertNonEmpty(acRule.activity)
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

    // TODO: Establish preconditions more reliably, split into different preconditions group
    // TODO: Measure code coverage

    // TODO: Import tests with Zip files from different source programs
    // TODO: Maybe decouple tests and split into "empty precondition" and "nonempty precondition"
}

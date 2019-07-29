package com.ditronic.securezipnotes

import android.content.Intent
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.size
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import com.ditronic.securezipnotes.util.TestUtil
import com.ditronic.simplefilesync.AbstractFileSync
import com.ditronic.simplefilesync.DropboxFileSync
import com.ditronic.simplefilesync.util.ResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.hamcrest.CoreMatchers.anything
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.core.StringContains
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
class ChangeTextBehaviorKtTest {

    companion object {

        private const val PASSWORD_TO_BE_TYPED = "testpassword"
        private const val PASSWORD_TOO_SHORT = ""

        private const val FIRST_NOTE_NAME = "Note 1"
        private const val RENAMED_NOTE_NAME = "Note renamed"

        private const val SECRET_NOTE = "My secret note"

        private const val DROPBOX_OAUTH_TOKEN = "T6OO59Oo9FoAAAAAAAANTySOeCziL-1_agAU2sr2mU8ArSZqr3RKb6ICU5a_JJVt"
    }


    //@get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()
    @get:Rule var activityTestRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)

    /*@BeforeClass
    fun setup() {
    }*/
    @Before
    fun beforeEachTest() {
        TestUtil.isInstrumentationTest = true
    }

    @Test
    fun t1_createNewPassword() {

        resetAppData()
        activityTestRule.launchActivity(null)

        // MainActivity
        onView(withId(R.id.btn_create_new_note)).perform(click())

        // NewPasswordActivity
        onView(withId(R.id.btn_generate_master_password)).perform(click())
        onView(withId(R.id.input_password)).perform(replaceText(PASSWORD_TOO_SHORT))
        onView(withId(R.id.btn_next)).perform(click())
        onView(withId(R.id.input_password)).perform(replaceText(PASSWORD_TO_BE_TYPED))
        onView(withId(R.id.btn_next)).perform(click())

        // PasswordConfirmActivity
        //onView(withId(R.id.btn_confirm_master_password)).perform(click())
        onView(withId(R.id.input_password_confirm)).perform(replaceText(PASSWORD_TO_BE_TYPED))
        onView(withId(R.id.btn_confirm_master_password)).perform(click())

        val currentTime = Calendar.getInstance().timeInMillis

        // NoteEditActivity
        onView(withId(R.id.edit_text_main)).perform(typeText(SECRET_NOTE), closeSoftKeyboard())
        assertNoteEditState(FIRST_NOTE_NAME, SECRET_NOTE, editMode = true)
        doBackPress()
        Espresso.onIdle()

        // MainActivity
        assertMainMenuState(listOf(FIRST_NOTE_NAME), activityTestRule.activity)
        val item = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes)[0]
        val noteDate = item.findViewById<TextView>(R.id.txt_cardview_2).text.toString()
        val noteSize = item.findViewById<TextView>(R.id.txt_cardview_3).text.toString()
        // This may fail with a race condition when executed around midnight
        assertTrue(noteDate.contains(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentTime)))
        assertEquals("Size: " + SECRET_NOTE.length, noteSize)

        clickNoteMenuItem(FIRST_NOTE_NAME)

        // NoteEditActivity
        onView(withId(R.id.edit_text_main)).check(matches(withText(SECRET_NOTE)))
        doBackPress()

        // Back to MainActivity, assert that nothing changed
        assertMainMenuState(listOf(FIRST_NOTE_NAME), activityTestRule.activity)
        val newItem = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes).get(index = 0)
        assertEquals(noteDate, newItem.findViewById<TextView>(R.id.txt_cardview_2).text.toString())
        assertEquals(noteSize, newItem.findViewById<TextView>(R.id.txt_cardview_3).text.toString())
    }


    @Test
    fun t2_dropBoxInitOauth() {
        activityTestRule.launchActivity(null)
        Intents.init()

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        Espresso.openActionBarOverflowOrOptionsMenu(targetContext)
        onView(withText(R.string.sync_with_dropbox)).perform(click())

        // First intent
        intended(hasComponent(hasClassName("com.dropbox.core.android.AuthActivity")))

        // Second intent
        intended(hasAction(Intent.ACTION_VIEW))
        intended(hasDataString(StringContains("https://www.dropbox.com/")))

        Intents.release()
    }


    @Test
    fun t31_dropBoxUpload() {

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        DropboxFileSync.storeNewOauthToken(DROPBOX_OAUTH_TOKEN, targetContext)

        activityTestRule.launchActivity(null)
        Espresso.onIdle()
        assertEquals(ResultCode.UPLOAD_SUCCESS, AbstractFileSync.getLastSyncResult()!!.resultCode)
    }

    @Test
    fun t32_dropBoxRemoteEqualsLocal() {

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        DropboxFileSync.storeNewOauthToken(DROPBOX_OAUTH_TOKEN, targetContext)

        activityTestRule.launchActivity(null)
        Espresso.onIdle()
        assertEquals(ResultCode.REMOTE_EQUALS_LOCAL, DropboxFileSync.getLastSyncResult()!!.resultCode)
    }

    @Test
    fun t4_dropBoxDownload() {

        clearLocalFilesDir()

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        DropboxFileSync.storeNewOauthToken(DROPBOX_OAUTH_TOKEN, targetContext)

        activityTestRule.launchActivity(null)
        Espresso.onIdle()

        assertEquals(ResultCode.DOWNLOAD_SUCCESS, DropboxFileSync.getLastSyncResult()!!.resultCode)
        val listView = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes)
        assertThat(listView.size, greaterThan(0))
    }

    @Test
    fun t5_renameSingleNote() {

        activityTestRule.launchActivity(null)

        // Check whether rename in main menu works
        assertMainMenuState(listOf(FIRST_NOTE_NAME), activityTestRule.activity)
        renameNoteMainMenu(FIRST_NOTE_NAME, RENAMED_NOTE_NAME)
        assertMainMenuState(listOf(RENAMED_NOTE_NAME), activityTestRule.activity)

        // Open renamed note to verify that its content is not corrupted
        clickNoteMenuItem(RENAMED_NOTE_NAME)
        assertNoteEditState(RENAMED_NOTE_NAME, SECRET_NOTE, editMode = false)

        // Rename back to old name within EditNoteActivity
        renameNoteEditActivity(RENAMED_NOTE_NAME, FIRST_NOTE_NAME, editMode = false)
        assertNoteEditState(FIRST_NOTE_NAME, SECRET_NOTE, editMode = false)
        doBackPress()
        assertMainMenuState(listOf(FIRST_NOTE_NAME), activityTestRule.activity)
    }


    @Test
    fun t61_renameNotesEdgeCases() {

        activityTestRule.launchActivity(null)
        val ac = activityTestRule.activity
        assertMainMenuState(listOf(FIRST_NOTE_NAME), ac)

        val NUM_NOTES = 4
        val noteEntries = mutableListOf<String>()
        noteEntries.add(FIRST_NOTE_NAME)
        for (noteCnt in 2..NUM_NOTES) {
            // MainActivity
            onView(withId(R.id.action_add_note)).perform(ViewActions.click())
            // NoteEditActivity
            val expectedNoteEntry = "Note " + noteCnt
            assertNoteEditState(expectedNoteEntry, "", editMode = true)
            onView(withId(R.id.edit_text_main)).perform(typeText(noteCnt.toString()), closeSoftKeyboard())
            doBackPress()
            noteEntries.add(expectedNoteEntry)
        }

        // Verify entire MainActivity state
        assertMainMenuState(noteEntries, ac)

        // Verify that duplicate rename from MainActivity fails gracefully
        renameNoteMainMenu("Note 1", "Note 2")
        assertToastDisplayed("Note 2 already exists!", ac)

        // Verify that empty rename from MainActivity does not change anything
        renameNoteMainMenu("Note 2", "")
        assertToastDisplayed("Name must not be empty", ac)

        // Verify that duplicate rename from NoteEditActivity fails gracefully
        clickNoteMenuItem("Note 1")
        renameNoteEditActivity("Note 1", "Note 2", editMode = false)
        assertToastDisplayed("Note 2 already exists, keeping old name", ac)

        // Verify that empty rename from NoteEditActivity does not touch the name
        renameNoteEditActivity("Note 2", "", editMode = true)
        assertToastDisplayed("Empty file names are not allowed", ac)
    }

    @Test
    fun t62_deleteMultipleNotes() {

        activityTestRule.launchActivity(null)
        assertMainMenuState(listOf("Note 1", "Note 2", "Note 3", "Note 4"), activityTestRule.activity)

        deleteNote("Note 2")
        deleteNote("Note 3")
        deleteNote("Note 4")

        assertMainMenuState(listOf(FIRST_NOTE_NAME), activityTestRule.activity)

    }

    @Test
    fun t63_addNoteNamingConflict() {
        activityTestRule.launchActivity(null)
        val ac = activityTestRule.activity
        assertMainMenuState(listOf(FIRST_NOTE_NAME), ac)

        onView(withId(R.id.action_add_note)).perform(ViewActions.click())
        assertNoteEditState("Note 2", "", editMode = true)
        doBackPress()
        doBackPress()

        assertMainMenuState(listOf(FIRST_NOTE_NAME, "Note 2"), ac)
        renameNoteMainMenu("Note 2", "Note 3")
        assertMainMenuState(listOf(FIRST_NOTE_NAME, "Note 3"), ac)
        onView(withId(R.id.action_add_note)).perform(ViewActions.click())
        assertMainMenuState(listOf(FIRST_NOTE_NAME, "Note 3", "Note 4"), ac)
    }

    // TODO: Fix this test
    /*@Test
    fun t6_exportNote() {
        activityTestRule.launchActivity(null)
        Intents.init()

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        Espresso.openActionBarOverflowOrOptionsMenu(targetContext)
        onView(withText(R.string.export_zip_file)).perform(click())

        intended(hasAction(Intent.ACTION_CHOOSER))
        intended(hasExtras(BundleMatchers.hasEntry("key", "value")))

        Intents.release()
    }*/

    // TODO: Establish preconditions more reliably, split into different preconditions group
    // TODO: Measure code coverage

    @Test
    fun t91_deleteLastNote() {

        activityTestRule.launchActivity(null)
        assertMainMenuState(listOf(FIRST_NOTE_NAME), activityTestRule.activity)

        // Check whether the startup screen re-appears after deleting the last note.
        deleteNote(FIRST_NOTE_NAME)
        assertEmptyStartupScreen(activityTestRule.activity)
    }


    @Test
    fun t92_exportEmptyNote() {

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        clearLocalFilesDir()
        AbstractFileSync.clearCurrentSyncBackend(targetContext)

        activityTestRule.launchActivity(null)
        val ac = activityTestRule.activity

        assertEmptyStartupScreen(ac)

        Espresso.openActionBarOverflowOrOptionsMenu(targetContext)
        onView(withText(R.string.export_zip_file)).perform(click())

        assertToastDisplayed(ac.getString(R.string.toast_notes_empty_export), ac)
    }

    // TODO: Import tests with Zip files from different source programs
    // TODO: Maybe decouple tests and split into "empty precondition" and "nonempty precondition"
}

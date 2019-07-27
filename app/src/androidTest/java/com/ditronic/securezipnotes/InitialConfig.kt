package com.ditronic.securezipnotes

import android.content.Context
import android.content.Intent
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.size
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
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
import com.ditronic.simplefilesync.DropboxFileSync
import com.ditronic.simplefilesync.util.ResultCode
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.hamcrest.CoreMatchers.anything
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.core.StringContains
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
class ChangeTextBehaviorKtTest {

    companion object {

        const val PASSWORD_TO_BE_TYPED = "testpassword";
        const val PASSWORD_TOO_SHORT = "";

        const val FIRST_NOTE_NAME = "Note 1";
        const val RENAMED_NOTE_NAME = "Note renamed";

        const val SECRET_NOTE = "My secret note"

        const val DROPBOX_OAUTH_TOKEN = "T6OO59Oo9FoAAAAAAAANTySOeCziL-1_agAU2sr2mU8ArSZqr3RKb6ICU5a_JJVt"
    }

    /**
     * Use [ActivityScenarioRule] to create and launch the activity under test before each test,
     * and close it after each test. This is a replacement for
     * [androidx.test.rule.ActivityTestRule].
     */
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
        onView(withId(R.id.edit_text_title)).check(matches(withText(FIRST_NOTE_NAME)))
        onView(withId(R.id.edit_text_main)).perform(typeText(SECRET_NOTE), closeSoftKeyboard())

        // MainActivity
        onView(isRoot()).perform(pressBack())
        Espresso.onIdle()
        val listView = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes)
        val item = listView[0]
        val noteName = item.findViewById<TextView>(R.id.txt_cardview).text.toString()
        val noteDate = item.findViewById<TextView>(R.id.txt_cardview_2).text.toString()
        val noteSize = item.findViewById<TextView>(R.id.txt_cardview_3).text.toString()
        assertEquals(FIRST_NOTE_NAME, noteName)
        // This may fail with a race condition when executed around midnight
        assertTrue(noteDate.contains(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentTime)))
        assertEquals("Size: " + SECRET_NOTE.length, noteSize)
        assertEquals(1, listView.size)

        Espresso.onData(anything())
                .inAdapterView(withId(R.id.list_view_notes)).atPosition(0)
                .perform(click())

        // NoteEditActivity
        onView(withId(R.id.edit_text_main)).check(matches(withText(SECRET_NOTE)))
        onView(isRoot()).perform(pressBack())

        // MainActivity
        Espresso.onData(anything()).inAdapterView(withId(R.id.list_view_notes)).atPosition(0)
                .onChildView(withId(R.id.txt_cardview)).check(matches(withText(FIRST_NOTE_NAME)))
        // Assert that nothing changed
        val newItem = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes).get(index = 0)
        assertEquals(noteName, newItem.findViewById<TextView>(R.id.txt_cardview).text.toString())
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
    }


    @Test
    fun t3_dropBoxUpload() {

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        DropboxFileSync.storeNewOauthToken(DROPBOX_OAUTH_TOKEN, targetContext)

        DropboxFileSync.clearLastSyncResult()
        activityTestRule.launchActivity(null)
        Espresso.onIdle()

        assertTrue(DropboxFileSync.getLastSyncResult()!!.resultCode == ResultCode.UPLOAD_SUCCESS)
    }

    @Test
    fun t4_dropBoxDownload() {

        clearLocalFilesDir()

        DropboxFileSync.clearLastSyncResult()
        activityTestRule.launchActivity(null)
        Espresso.onIdle()

        assertTrue(DropboxFileSync.getLastSyncResult()!!.resultCode == ResultCode.DOWNLOAD_SUCCESS)
        val listView = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes)
        assertThat(listView.size, greaterThan(0))
    }

    @Test
    fun t5_renameSingleNote() {

        activityTestRule.launchActivity(null)

        // Rename
        Espresso.onData(anything())
                .inAdapterView(withId(R.id.list_view_notes)).onChildView(withText(FIRST_NOTE_NAME))
                .perform(longClick())
        onView(withText("Rename")).perform(click())
        onView(withText(FIRST_NOTE_NAME)).inRoot(isDialog()).perform(replaceText(RENAMED_NOTE_NAME))
        onView(withText("OK")).inRoot(isDialog()).perform(click())

        // Check whether rename worked
        val listView = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes)
        val item = listView[0]
        val noteName = item.findViewById<TextView>(R.id.txt_cardview).text.toString()
        assertEquals(RENAMED_NOTE_NAME, noteName)
        assertEquals(1, listView.size)

        // Open renamed note to verify that its content is not corrupted
        Espresso.onData(anything())
                .inAdapterView(withId(R.id.list_view_notes)).atPosition(0)
                .perform(click())
        onView(withId(R.id.edit_text_title)).check(matches(withText(RENAMED_NOTE_NAME)))
        onView(withId(R.id.edit_text_main)).check(matches(withText(SECRET_NOTE)))
    }

    @Test
    fun t6_deleteSingleNote() {

        activityTestRule.launchActivity(null)
        val listView = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes)
        assertEquals(1, listView.size)

        // Rename
        Espresso.onData(anything())
                .inAdapterView(withId(R.id.list_view_notes)).atPosition(0)
                .perform(longClick())
        onView(withText("Delete")).perform(click())
        onView(withText("OK")).inRoot(isDialog()).perform(click())

        // Check whether the startup screen re-appears after deleting all notes
        onView(withId(R.id.btn_create_new_note)).check(matches(isDisplayed()))
        assertEquals(0, listView.size)
    }

    // TODO: Import test
    // TODO: Export test

    private fun resetAppData() {
        clearLocalFilesDir()
        removeAllPrefs()
    }

    private fun clearLocalFilesDir() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val root = appContext.filesDir
        val files = root.listFiles()
        if (files != null) {
            var i = 0
            while (i < files.size) {
                println("Remove file prior to UI test: " + files[i].absolutePath)
                println(files[i].delete())
                i++
            }
        }
    }

    private fun removeAllPrefs() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val root = targetContext.filesDir.parentFile
        val sharedPreferencesFileNames = File(root, "shared_prefs").list()
        for (fileName in sharedPreferencesFileNames) {
            targetContext.getSharedPreferences(fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}

package com.ditronic.securezipnotes

import android.content.Context
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.size
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import com.ditronic.securezipnotes.util.TestUtil
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.hamcrest.CoreMatchers.anything
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


@RunWith(AndroidJUnit4::class)
@LargeTest
class ChangeTextBehaviorKtTest {

    companion object {

        const val PASSWORD_TO_BE_TYPED = "testpasswordnd6jedjd$$";
        const val PASSWORD_TOO_SHORT = "";

        const val FIRST_NOTE_NAME = "Note 1";

        const val SECRET_NOTE = "My secret note"
    }

    /**
     * Use [ActivityScenarioRule] to create and launch the activity under test before each test,
     * and close it after each test. This is a replacement for
     * [androidx.test.rule.ActivityTestRule].
     */
    //@get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()

    //@Rule
    @get:Rule var activityTestRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)

    @Before
    fun setup() {
        resetAppData()
        TestUtil.isInstrumentationTest = true
        activityTestRule.launchActivity(null)
    }

    @Test
    fun createNewPassword() {

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

        onData(anything())
                .inAdapterView(withId(R.id.list_view_notes)).atPosition(0)
                .perform(click())

        // NoteEditActivity
        onView(withId(R.id.edit_text_main)).check(matches(withText(SECRET_NOTE)))
        onView(isRoot()).perform(pressBack())

        // MainActivity
        onData(anything()).inAdapterView(withId(R.id.list_view_notes)).atPosition(0)
                .onChildView(withId(R.id.txt_cardview)).check(matches(withText(FIRST_NOTE_NAME)))
        // Assert that nothing changed
        val newItem = activityTestRule.activity.findViewById<ListView>(R.id.list_view_notes).get(index = 0)
        assertEquals(noteName, newItem.findViewById<TextView>(R.id.txt_cardview).text.toString())
        assertEquals(noteDate, newItem.findViewById<TextView>(R.id.txt_cardview_2).text.toString())
        assertEquals(noteSize, newItem.findViewById<TextView>(R.id.txt_cardview_3).text.toString())
    }

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

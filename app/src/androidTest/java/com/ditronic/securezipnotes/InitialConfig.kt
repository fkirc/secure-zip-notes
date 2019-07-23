package com.ditronic.securezipnotes

import android.content.Context
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File


@RunWith(AndroidJUnit4::class)
@LargeTest
class ChangeTextBehaviorKtTest {

    companion object {

        const val PASSWORD_TO_BE_TYPED = "testpasswordnd6jedjd$$";
        const val PASSWORD_TOO_SHORT = "";

        const val FIRST_NOTE_NAME = "Note 1";

        const val THROTTLE_CLICK_WAIT : Long = 500
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
        activityTestRule.launchActivity(null)
    }

    @Test
    fun createNewPassword() {

        // MainActivity
        onView(withId(R.id.btn_create_new_note)).perform(click())

        // NewPasswordActivity
        onView(withId(R.id.btn_generate_master_password)).perform(click())
        /*onView(withId(R.id.input_password)).perform(replaceText(PASSWORD_TOO_SHORT))
        onView(withId(R.id.btn_next)).perform(click())
        Thread.sleep(THROTTLE_CLICK_WAIT)*/
        onView(withId(R.id.input_password)).perform(replaceText(PASSWORD_TO_BE_TYPED))
        onView(withId(R.id.btn_next)).perform(click())

        // PasswordConfirmActivity
        /*onView(withId(R.id.btn_confirm_master_password)).perform(click())
        Thread.sleep(THROTTLE_CLICK_WAIT)*/
        onView(withId(R.id.input_password_confirm)).perform(replaceText(PASSWORD_TO_BE_TYPED))
        onView(withId(R.id.btn_confirm_master_password)).perform(click())

        // NoteEditActivity
        onView(withId(R.id.edit_text_title)).check(matches(withText(FIRST_NOTE_NAME)))

        // MainActivity
        pressBack()
        // This view is in a different Activity, no need to tell Espresso.
        //onView(withId(R.id.show_text_view)).check(matches(withText(STRING_TO_BE_TYPED)))
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
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val root = appContext.filesDir.parentFile
        val sharedPreferencesFileNames = File(root, "shared_prefs").list()
        for (fileName in sharedPreferencesFileNames) {
            appContext.getSharedPreferences(fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}

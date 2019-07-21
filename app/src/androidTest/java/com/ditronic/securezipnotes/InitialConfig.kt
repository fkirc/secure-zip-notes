package com.ditronic.securezipnotes

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.ditronic.securezipnotes.activities.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class ChangeTextBehaviorKtTest {

    companion object {

        val PASSWORD_TO_BE_TYPED = "testpasswordj289fj289f3jj$$";
        val PASSWORD_TOO_SHORT = "";

        val FIRST_NOTE_NAME = "Note 1";
    }

    /**
     * Use [ActivityScenarioRule] to create and launch the activity under test before each test,
     * and close it after each test. This is a replacement for
     * [androidx.test.rule.ActivityTestRule].
     */
    @get:Rule var activityScenarioRule = activityScenarioRule<MainActivity>()

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
        onView(withId(R.id.btn_confirm_master_password)).perform(click())
        onView(withId(R.id.input_password_confirm)).perform(replaceText(PASSWORD_TO_BE_TYPED))
        onView(withId(R.id.btn_confirm_master_password)).perform(click())

        // NoteEditActivity
        onView(withId(R.id.tool_bar_edit)).check(matches(withText(FIRST_NOTE_NAME)))

        // This view is in a different Activity, no need to tell Espresso.
        //onView(withId(R.id.show_text_view)).check(matches(withText(STRING_TO_BE_TYPED)))
    }
}

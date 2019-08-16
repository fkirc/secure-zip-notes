package com.ditronic.securezipnotes

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import com.ditronic.securezipnotes.util.TestUtil
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pressBack
import java.text.SimpleDateFormat


@RunWith(AndroidJUnit4::class)
@LargeTest
class SetupTests {

    // TODO: Measure code coverage

    companion object {
        private const val SECRET_NOTE = "My secret note"
    }

    @get:Rule var acRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)

    @Before
    fun beforeEachTest() {
        TestUtil.isInstrumentationTest = true
    }

    @Test
    fun createNewPassword() {
        precondition_cleanStart(acRule)

        init_createNewZipFile()

        init_genRandomPassword()
        init_chooseNewPassword(MASTER_PASSWORD)
        init_confirmNewPassword(MASTER_PASSWORD)

        noteEdit_typeText(SECRET_NOTE)
        noteEdit_assertState("Note 1", SECRET_NOTE, editMode = true)
        pressBack()

        main_assertListState(listOf("Note 1"), acRule.activity)
        val noteEntry = main_extractEntryList(acRule.activity)[0]
        assertEquals("Size: " + SECRET_NOTE.length, noteEntry.size)
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
    fun passwordTooShort() {
        precondition_cleanStart(acRule)

        init_createNewZipFile()
        init_chooseNewPassword("")
        init_newPassword_assertErrorText("Minimum length: 8 characters")
        init_chooseNewPassword("sfse")
        init_newPassword_assertErrorText("Minimum length: 8 characters")
        init_chooseNewPassword(MASTER_PASSWORD)
        init_confirmNewPassword(MASTER_PASSWORD)
        noteEdit_assertState("Note 1", "", editMode = true)
    }

    @Test
    fun passwordMismatch() {
        precondition_cleanStart(acRule)
        TestUtil.isInstrumentationTest = true

        init_createNewZipFile()
        init_chooseNewPassword(MASTER_PASSWORD)
        init_confirmNewPassword(MASTER_PASSWORD + "mismatch")
        init_confirmPassword_assertErrorText("Passwords do not match")
        pressBack()
        pressBack()
        // TODO: Fix this test: Click happens on "Passwords do not match" overlay message.
        // TODO: Therefore, the click gets ignored and the message does not disappear.
        init_chooseNewPassword("lalalalalala")
        init_confirmNewPassword("lalalalalala mismatch")
        init_confirmPassword_assertErrorText("Passwords do not match")
        init_confirmNewPassword("lalalalalala")
        noteEdit_assertState("Note 1", "", editMode = true)
    }



    @Test
    fun generateRandomPassword() {
        // TODO: Implement this test
        precondition_cleanStart(acRule)

        init_createNewZipFile()
        init_genRandomPassword()
        init_confirmNewPassword("Randomly wrong")
    }
}

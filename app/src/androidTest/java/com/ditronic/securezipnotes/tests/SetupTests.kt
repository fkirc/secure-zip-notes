package com.ditronic.securezipnotes.tests

import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.ditronic.securezipnotes.robotpattern.*
import com.ditronic.securezipnotes.testutils.pressBack
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.Matchers.containsString
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat


@RunWith(AndroidJUnit4::class)
@LargeTest
class SetupTests {

    companion object {
        private const val SECRET_NOTE = "My secret note"

        private fun matchesRandomPassword(): Matcher<String> = object : TypeSafeMatcher<String>() {
            override fun describeTo(description: Description?) = Unit
            override fun matchesSafely(password: String): Boolean {
                if (password.length != 20) {
                    return false
                }
                return true
            }
        }
    }

    @Test
    fun createNewPassword() {
        precondition_cleanStart()

        init_createNewZipFile()

        init_genRandomPassword()
        init_typeNewPassword(TESTPASSWORD)
        init_confirmNewPassword(TESTPASSWORD)

        noteEdit_typeText(SECRET_NOTE)
        noteEdit_assertState("Note 1", SECRET_NOTE, editMode = true)
        pressBack()

        main_assertListState(listOf("Note 1"))
        val noteEntry = main_extractEntryList()[0]
        assertEquals("Size: " + SECRET_NOTE.length, noteEntry.size)
        assertThat(noteEntry.modDate, containsString(SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Calendar.getInstance().timeInMillis)))

        main_clickNote("Note 1")
        noteEdit_assertState("Note 1", SECRET_NOTE, editMode = false)
        pressBack()

        // Back to MainActivity, assert that nothing changed
        main_assertListState(listOf("Note 1"))
        val newNoteEntry = main_extractEntryList()[0]
        assertEquals(noteEntry, newNoteEntry)
    }

    @Test
    fun passwordTooShort() {
        precondition_cleanStart()

        init_createNewZipFile()
        init_typeNewPassword("")
        init_newPassword_assertErrorText("Minimum length: 8 characters")
        init_typeNewPassword("sfse")
        init_newPassword_assertErrorText("Minimum length: 8 characters")
        init_typeNewPassword(TESTPASSWORD)
        Thread.sleep(1000)
        init_confirmNewPassword(TESTPASSWORD)
        noteEdit_assertState("Note 1", "", editMode = true)
    }

    @Test
    fun passwordMismatch() {
        precondition_cleanStart()

        init_createNewZipFile()
        init_typeNewPassword(TESTPASSWORD)
        init_confirmNewPassword(TESTPASSWORD + "mismatch")
        init_confirmPassword_assertErrorText("Passwords do not match")
        pressBack()
        pressBack()
        init_typeNewPassword("lalalalalala")
        init_confirmNewPassword("lalalalalala mismatch")
        init_confirmPassword_assertErrorText("Passwords do not match")
        init_confirmNewPassword("lalalalalala")
        noteEdit_assertState("Note 1", "", editMode = true)
    }



    @Test
    fun generateRandomPassword() {
        precondition_cleanStart()

        init_createNewZipFile()
        init_onViewPassword().check(ViewAssertions.matches(ViewMatchers.withText(matchesRandomPassword())))
        init_typeNewPassword("")
        init_onViewPassword().check(ViewAssertions.matches(ViewMatchers.withText(Matchers.isEmptyString())))
        init_genRandomPassword()
        init_onViewPassword().check(ViewAssertions.matches(ViewMatchers.withText(matchesRandomPassword())))
        init_chooseNewPassword()
        init_confirmNewPassword("")
    }
}

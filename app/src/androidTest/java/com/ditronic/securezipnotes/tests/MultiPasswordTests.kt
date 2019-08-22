package com.ditronic.securezipnotes.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import com.ditronic.securezipnotes.robotpattern.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class MultiPasswordTests {

    @get:Rule
    var acRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)

    @Test
    fun twoPasswords() {
        precondition_loadAsset(acRule, "twopasswords.ZIP")
        main_assertListState(entries = listOf("pw2_entry", "testpassword_entry"), ac = acRule.activity)

        main_clickAssertCloseNote(noteName = "testpassword_entry", secretContent = "My secret note", password = TESTPASSWORD)
        main_clickAssertCloseNote(noteName = "pw2_entry", secretContent = "secret", password = "pw2")
        main_clickAssertCloseNote(noteName = "testpassword_entry", secretContent = "My secret note", password = TESTPASSWORD)
    }

    @Test
    fun test4Passwords() {
        precondition_loadAsset(acRule, "4passwords_subdirs.aeszip")
        main_assertListState(entries = listOf("pw4_entry", "pw3_entry/dir/dir/pw2", "pw2_entry", "pw1_entry/dir/pw1"),
                ac = acRule.activity)

        main_clickAssertCloseNote(noteName = "pw4_entry", secretContent = "pw4_secret", password = "pw4")
        main_clickAssertCloseNote(noteName = "pw3_entry/dir/dir/pw2", secretContent = "pw3_secret", password = "pw3")
        main_clickAssertCloseNote(noteName = "pw2_entry", secretContent = "pw2_secret", password = "pw2")
        main_clickAssertCloseNote(noteName = "pw1_entry/dir/pw1", secretContent = "pw1_secret", password = "pw1")
    }
}

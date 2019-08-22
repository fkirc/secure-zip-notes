package com.ditronic.securezipnotes.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import com.ditronic.securezipnotes.common.*
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

    // TODO: 4passwords test

}

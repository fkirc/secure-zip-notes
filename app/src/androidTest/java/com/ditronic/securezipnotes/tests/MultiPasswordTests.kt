package com.ditronic.securezipnotes.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import com.ditronic.securezipnotes.common.main_assertListState
import com.ditronic.securezipnotes.common.main_clickNote
import com.ditronic.securezipnotes.common.noteEdit_assertState
import com.ditronic.securezipnotes.common.precondition_loadAsset
import com.ditronic.securezipnotes.testutils.pressBack
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
        main_clickNote("testpassword_entry", typePassword = true)
        noteEdit_assertState(noteTitle = "testpassword_entry", secretContent =  "My secret note")
        pressBack()
        main_clickNote("pw2_entry", typePassword = true, password = "pw2")
        noteEdit_assertState(noteTitle = "pw2_entry", secretContent =  "secret")
        pressBack()
        main_clickNote("testpassword_entry", typePassword = true)
        noteEdit_assertState(noteTitle = "testpassword_entry", secretContent =  "My secret note")
    }

    // TODO: 4passwords test
    // TODO: Click-assert-back method

}

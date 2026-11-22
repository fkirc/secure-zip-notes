package com.ditronic.securezipnotes.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.ditronic.securezipnotes.robotpattern.TESTPASSWORD
import com.ditronic.securezipnotes.robotpattern.main_assertListState
import com.ditronic.securezipnotes.robotpattern.main_clickAssertCloseNote
import com.ditronic.securezipnotes.robotpattern.precondition_loadAsset
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
@LargeTest
class MultiPasswordTests {

    @Test
    fun twoPasswords() {
        precondition_loadAsset("twopasswords.ZIP")
        main_assertListState(entries = listOf("pw2_entry", "testpassword_entry"))

        main_clickAssertCloseNote(noteName = "testpassword_entry", secretContent = "My secret note", password = TESTPASSWORD)
        main_clickAssertCloseNote(noteName = "pw2_entry", secretContent = "secret", password = "pw2")
        main_clickAssertCloseNote(noteName = "testpassword_entry", secretContent = "My secret note", password = TESTPASSWORD)
    }

    @Test
    fun test4Passwords() {
        precondition_loadAsset("4passwords_subdirs.aeszip")
        main_assertListState(entries = listOf("pw4_entry", "pw3_entry/dir/dir/pw2", "pw2_entry", "pw1_entry/dir/pw1"))

        main_clickAssertCloseNote(noteName = "pw4_entry", secretContent = "pw4_secret", password = "pw4")
        main_clickAssertCloseNote(noteName = "pw3_entry/dir/dir/pw2", secretContent = "pw3_secret", password = "pw3")
        main_clickAssertCloseNote(noteName = "pw2_entry", secretContent = "pw2_secret", password = "pw2")
        main_clickAssertCloseNote(noteName = "pw2_entry", secretContent = "pw2_secret")
        main_clickAssertCloseNote(noteName = "pw1_entry/dir/pw1", secretContent = "pw1_secret", password = "pw1")
    }
}

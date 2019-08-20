package com.ditronic.securezipnotes

import android.app.Activity
import android.text.InputType
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.size
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import net.lingala.zip4j.model.FileHeader
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.collection.IsArrayContaining
import org.junit.Assert

data class NoteEntry(var name: String,
                     var modDate: String,
                     var size: String)

fun init_newPassword_assertErrorText(errorText: String) {
    Espresso.onView(ViewMatchers.withId(R.id.input_password)).check(ViewAssertions.matches(ViewMatchers.hasErrorText(errorText)))
}

fun init_confirmPassword_assertErrorText(errorText: String) {
    Espresso.onView(ViewMatchers.withId(R.id.input_password_confirm)).check(ViewAssertions.matches(ViewMatchers.hasErrorText(errorText)))
}

fun main_assertAlertDialog(expectedText: String) {
    Espresso.onView(ViewMatchers.withText(expectedText)).inRoot(RootMatchers.isDialog())
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
}

fun main_extractEntryList(ac: Activity) : List<NoteEntry> {
    val noteEntries = mutableListOf<NoteEntry>()
    val listView = ac.findViewById<ListView>(R.id.list_view_notes)
    for (idx in 0 until listView.size) {
        val item = listView.get(idx)
        val noteEntry = NoteEntry(
                name = item.findViewById<TextView>(R.id.txt_cardview).text.toString(),
                modDate = item.findViewById<TextView>(R.id.txt_cardview_2).text.toString(),
                size = item.findViewById<TextView>(R.id.txt_cardview_3).text.toString())
        noteEntries.add(noteEntry)
    }
    return noteEntries
}


fun noteEdit_assertState(noteTitle: String, secretContent: String, editMode: Boolean = false) {
    Espresso.onView(ViewMatchers.withId(R.id.edit_text_title)).check(ViewAssertions.matches(ViewMatchers.withText(noteTitle)))
    Espresso.onView(ViewMatchers.withId(R.id.edit_text_main)).check(ViewAssertions.matches(ViewMatchers.withText(secretContent)))
    if (editMode) {
        Espresso.onView(ViewMatchers.withId(R.id.action_save_note)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.action_edit_note)).check(ViewAssertions.doesNotExist())
    } else {
        Espresso.onView(ViewMatchers.withId(R.id.action_edit_note)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(ViewMatchers.withId(R.id.action_save_note)).check(ViewAssertions.doesNotExist())
    }
}



fun main_assertListState(entries: Collection<String>, ac: Activity) {
    val listView = ac.findViewById<ListView>(R.id.list_view_notes)
    Assert.assertEquals(entries.size, listView.size)

    for (idx in 0 until listView.size) {
        val item = listView.get(idx)
        val noteName = item.findViewById<TextView>(R.id.txt_cardview).text.toString()
        Assert.assertThat(entries.toTypedArray(), IsArrayContaining.hasItemInArray(Matchers.`is`(noteName)))
    }
}


fun main_assertEmtpy(ac : Activity) {
    val listView = ac.findViewById<ListView>(R.id.list_view_notes)
    Espresso.onView(ViewMatchers.withId(R.id.btn_create_new_note)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    Assert.assertEquals(0, listView.size)
}

fun main_assertNonEmpty(ac: Activity) {
    val listView = ac.findViewById<ListView>(R.id.list_view_notes)
    ViewMatchers.assertThat(listView.size, Matchers.greaterThan(0))
}

// Utility -----------------------------------------------------------------------

fun matchesFileHeader(entryName: String): Matcher<FileHeader> = object : TypeSafeMatcher<FileHeader>() {
    override fun describeTo(description: Description?) {
        description?.appendText(entryName)
    }
    override fun matchesSafely(fileHeader: FileHeader?): Boolean {
        return fileHeader?.fileName == entryName
    }
}

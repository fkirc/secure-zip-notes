package com.ditronic.securezipnotes.robotpattern

import android.app.Activity
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.core.view.iterator
import androidx.core.view.size
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.testutils.getCurrentActivity
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

fun main_extractEntryList() : List<NoteEntry> {
    val ac = getCurrentActivity()
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


fun withListSize(size: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        public override fun matchesSafely(view: View): Boolean {
            return (view as ListView).count == size
        }
        override fun describeTo(description: Description) {
            description.appendText("ListView should have $size items")
        }
    }
}

private fun assertCardViewContents(expectedEntries: Collection<String>): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        public override fun matchesSafely(view: View): Boolean {
            val listView = view as ListView
            val actualEntries = mutableListOf<String>()
            listView.forEach {
                actualEntries += it.findViewById<TextView>(R.id.txt_cardview).text.toString()
            }
            actualEntries.reverse()
            Assert.assertEquals(expectedEntries, actualEntries)
            return true
        }
        override fun describeTo(description: Description) {
            description.appendText("ListView should have entries $expectedEntries")
        }
    }
}


fun main_assertListState(entries: Collection<String>) {
    Espresso.onView(ViewMatchers.withId(R.id.list_view_notes)).check(ViewAssertions.matches(assertCardViewContents(expectedEntries = entries)))
}


fun main_assertEmtpy() {
    val ac = getCurrentActivity()
    val listView = ac.findViewById<ListView>(R.id.list_view_notes)
    //Espresso.onView(ViewMatchers.withId(R.id.btn_create_new_note)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    Assert.assertEquals(0, listView.size)
}

fun main_assertNonEmpty() {
    val ac = getCurrentActivity()
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

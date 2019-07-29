package com.ditronic.securezipnotes

import android.app.Activity
import android.content.Context
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.get
import androidx.core.view.size
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import net.lingala.zip4j.model.FileHeader
import org.hamcrest.*
import org.hamcrest.collection.IsArrayContaining
import org.junit.Assert
import java.io.File


private fun noteLongClick(entryName: String) {
    Espresso.onData(matchesFileHeader(entryName))
            .inAdapterView(ViewMatchers.withId(R.id.list_view_notes))
            .perform(ViewActions.longClick())
}


fun renameNoteMainMenu(oldName: String, newName: String) {
    noteLongClick(oldName)
    Espresso.onView(ViewMatchers.withText("Rename")).perform(ViewActions.click())
    Espresso.onView(ViewMatchers.withText(oldName)).inRoot(RootMatchers.isDialog()).perform(ViewActions.replaceText(newName))
    Espresso.onView(ViewMatchers.withText("OK")).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
}


fun deleteNote(entryName: String) {
    noteLongClick(entryName)
    Espresso.onView(ViewMatchers.withText("Delete")).perform(ViewActions.click())
    Espresso.onView(ViewMatchers.withText("OK")).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
}


fun renameNoteEditActivity(oldName: String, newName: String, editMode: Boolean) {
    Espresso.onView(ViewMatchers.withId(R.id.edit_text_title)).check(ViewAssertions.matches(ViewMatchers.withText(oldName)))
    if (editMode) {
        Espresso.onView(ViewMatchers.withId(R.id.action_edit_note)).perform(ViewActions.click())
    }
    Espresso.onView(ViewMatchers.withId(R.id.edit_text_title))
            .perform(ViewActions.click(), ViewActions.clearText(), ViewActions.typeText(newName))
    Espresso.onView(ViewMatchers.withId(R.id.action_save_note)).perform(ViewActions.click())
}


fun clickNoteMenuItem(noteName: String) {
    Espresso.onData(matchesFileHeader(noteName))
            .inAdapterView(ViewMatchers.withId(R.id.list_view_notes))
            //.onChildView(ViewMatchers.withText(noteName))
            .perform(ViewActions.click())
}


fun matchesFileHeader(entryName: String): Matcher<FileHeader> = object : TypeSafeMatcher<FileHeader>() {
    override fun describeTo(description: Description?) {
        description?.appendText(entryName)
    }
    override fun matchesSafely(fileHeader: FileHeader?): Boolean {
        return fileHeader?.fileName == entryName
    }
}


fun assertNoteEditState(noteTitle: String, secretContent: String, editMode: Boolean) {
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


fun assertMainMenuState(entries: Collection<String>, ac: Activity) {
    val listView = ac.findViewById<ListView>(R.id.list_view_notes)
    Assert.assertEquals(entries.size, listView.size)

    for (idx in 0 until listView.size) {
        val item = listView.get(idx)
        val noteName = item.findViewById<TextView>(R.id.txt_cardview).text.toString()
        Assert.assertThat(entries.toTypedArray(), IsArrayContaining.hasItemInArray(Matchers.`is`(noteName)))
    }
}


fun assertEmptyStartupScreen(ac : Activity) {
    val listView = ac.findViewById<ListView>(R.id.list_view_notes)
    Espresso.onView(ViewMatchers.withId(R.id.btn_create_new_note)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    Assert.assertEquals(0, listView.size)
}


fun assertToastDisplayed(toastMessage : String, ac : Activity) {
    Espresso.onView(ViewMatchers.withText(toastMessage)).inRoot(RootMatchers.withDecorView(CoreMatchers.not(CoreMatchers.`is`(ac.window.getDecorView())))).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
}


fun resetAppData() {
    clearLocalFilesDir()
    removeAllPrefs()
}

fun clearLocalFilesDir() {
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

fun removeAllPrefs() {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val root = targetContext.filesDir.parentFile
    val sharedPrefs = File(root, "shared_prefs").list()
    if (sharedPrefs == null) {
        println("Failed to list shared preference files")
        return
    }
    for (fileName in sharedPrefs) {
        targetContext.getSharedPreferences(fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().commit()
    }
}

fun doBackPress() {
    Espresso.onView(ViewMatchers.isRoot()).perform(ViewActions.pressBack())
}

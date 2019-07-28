package com.ditronic.securezipnotes

import android.app.Activity
import android.content.Context
import android.widget.ListView
import androidx.core.view.size
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.Assert
import org.hamcrest.CoreMatchers
import java.io.File


fun assertEmptyStartupScreen(ac : Activity) {
    val listView = ac.findViewById<ListView>(R.id.list_view_notes)
    Espresso.onView(ViewMatchers.withId(R.id.btn_create_new_note)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    Assert.assertEquals(0, listView.size)
}


fun assertToastDisplayed(toastMessage : String, ac : Activity) {
    Espresso.onView(ViewMatchers.withText(toastMessage)).inRoot(RootMatchers.withDecorView(CoreMatchers.not(CoreMatchers.`is`(ac.getWindow().getDecorView())))).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
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

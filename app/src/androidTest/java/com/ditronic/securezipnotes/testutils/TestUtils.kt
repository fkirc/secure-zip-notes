package com.ditronic.securezipnotes.testutils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.*
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ditronic.securezipnotes.BuildConfig
import org.hamcrest.CoreMatchers
import java.io.File

fun targetContext() : Context {
    return InstrumentationRegistry.getInstrumentation().targetContext
}

fun assertToast(toastMessage : String) {
    val ac = getCurrentActivity()
    //Espresso.onView(ViewMatchers.withText(toastMessage)).inRoot(RootMatchers.withDecorView(CoreMatchers.not(CoreMatchers.`is`(ac.window.getDecorView())))).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    Espresso.onView(ViewMatchers.withText(toastMessage)).inRoot(RootMatchers.withDecorView(CoreMatchers.not(ac.window.getDecorView()))).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
}

fun pressBack() {
    Espresso.onView(ViewMatchers.isRoot()).perform(ViewActions.pressBack())
}

fun click_dialogOK() {
    Espresso.onView(ViewMatchers.withText("OK")).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
}

fun clickBottomCenter(): ViewAction {
    return ViewActions.actionWithAssertions(
            GeneralClickAction(
                    Tap.SINGLE,
                    GeneralLocation.BOTTOM_CENTER,
                    Press.FINGER,
                    InputDevice.SOURCE_UNKNOWN,
                    MotionEvent.BUTTON_PRIMARY))
}


fun clearLocalFilesDir() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val root = appContext.filesDir
    val files = root.listFiles()
    files?.forEach {
        println("Remove file prior to UI test: " + it.absolutePath)
        if (it.delete()) {
            throw RuntimeException("Failed to delete from filesDir")
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
    sharedPrefs.forEach { fileName ->
        targetContext.getSharedPreferences(fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().apply()
    }
}

fun getCurrentActivity(): Activity {
    lateinit var currentActivity: Activity
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        run {
            currentActivity = getCurrentActivityMainThread()
        }
    }
    return currentActivity
}

fun getCurrentActivityMainThread(): Activity {
    val currentActivity =
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .elementAt(0)
    return currentActivity
}

internal fun launchActivity(activityClass : Class<out Activity>) {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    intent.setClassName(BuildConfig.APPLICATION_ID, activityClass.name)
    InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
    Espresso.onIdle()
}


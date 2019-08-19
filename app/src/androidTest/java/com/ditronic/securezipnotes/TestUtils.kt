import android.app.Activity
import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.*
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers
import java.io.File

fun targetContext() : Context {
    return InstrumentationRegistry.getInstrumentation().targetContext
}

fun assertToast(toastMessage : String, ac : Activity) {
    //Espresso.onView(ViewMatchers.withText(toastMessage)).inRoot(RootMatchers.withDecorView(CoreMatchers.not(CoreMatchers.`is`(ac.window.getDecorView())))).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    Espresso.onView(ViewMatchers.withText(toastMessage)).inRoot(RootMatchers.withDecorView(CoreMatchers.not(ac.window.getDecorView()))).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
}

fun pressBack() {
    Espresso.onView(ViewMatchers.isRoot()).perform(ViewActions.pressBack())
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
    if (files != null) {
        var i = 0
        while (i < files.size) {
            println("Remove file prior to UI test: " + files[i].absolutePath)
            if (files[i].delete()) {
                throw RuntimeException("Failed to delete from filesDir")
            }
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
        targetContext.getSharedPreferences(fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear().apply()
    }
}

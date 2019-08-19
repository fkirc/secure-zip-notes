package com.ditronic.securezipnotes

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File


@RunWith(AndroidJUnit4::class)
@LargeTest
class ImportTests {

    @get:Rule var acRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)

    // TODO: Import tests with Zip files from different source programs

   @Test
    fun importWithSubDirs() {
       precondition_cleanStart(acRule)
       Intents.init()


       //Intents.intended(IntentMatchers.hasAction(Intent.ACTION_GET_CONTENT))
       //Intents.intended(IntentMatchers.hasExtras(BundleMatchers.hasEntry("key", "value")))
       val resIntent = Intent()
       // TODO: This does not work, finish import test.
       resIntent.data = Uri.fromFile(File("file:///android_asset/subdirs.aeszip"))
       val res = Instrumentation.ActivityResult(Activity.RESULT_OK, resIntent)
       Intents.intending(IntentMatchers.hasAction(Intent.ACTION_GET_CONTENT)).respondWith(res)

       init_importExistingNotes()

       main_assertListState(entries = listOf("whatever"), ac = acRule.activity)
       Intents.release()
    }

}

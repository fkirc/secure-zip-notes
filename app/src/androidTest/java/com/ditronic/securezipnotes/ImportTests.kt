package com.ditronic.securezipnotes

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.ditronic.securezipnotes.activities.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream


@RunWith(AndroidJUnit4::class)
@LargeTest
class ImportTests {

    @get:Rule var acRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java, false, false)

    // TODO: Import tests with Zip files from different source programs
    @After
    fun afterEachTest() {
        Intents.release()
    }

    @Test
    fun singleNoteImport() {
        precondition_cleanStart(acRule)
        importExistingNotes("singlenote.aeszip")
        // TODO: Assert alert dialog
        main_assertListState(entries = listOf("Note 1"), ac = acRule.activity)
    }

    @Test
    fun importWithInvalidSub() {
       precondition_cleanStart(acRule)

        importExistingNotes("subdirs.aeszip")

       // TODO: Assert alert dialog
       main_assertListState(entries = listOf("whatever"), ac = acRule.activity)
    }

    private fun importExistingNotes(assetToImport: String) {
        prepareImportResponseIntent(assetToImport)
        init_importExistingNotes()
    }

    private fun prepareImportResponseIntent(assetToImport: String) {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        Intents.init()
        val resIntent = Intent()
        val cacheFile = loadAssetToCacheDir(assetToImport)
        val cUri = FileProvider.getUriForFile(appContext, appContext.applicationContext.packageName + ".provider", cacheFile)
        resIntent.data = cUri
        val res = Instrumentation.ActivityResult(Activity.RESULT_OK, resIntent)
        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_GET_CONTENT)).respondWith(res)
    }

    private fun loadAssetToCacheDir(assetPath: String) : File {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context

        val sourceStream = testContext.assets.open(assetPath)
        val targetFile = File(appContext.cacheDir, assetPath)
        targetFile.delete()
        targetFile.deleteOnExit()
        val targetStream = FileOutputStream(targetFile)
        sourceStream.copyTo(targetStream)
        sourceStream.close()
        targetStream.close()
        return targetFile
    }
}

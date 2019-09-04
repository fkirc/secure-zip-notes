package com.ditronic.securezipnotes.tests

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
import com.ditronic.securezipnotes.testutils.click_dialogOK
import com.ditronic.securezipnotes.activities.MainActivity
import com.ditronic.securezipnotes.robotpattern.*
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


    @After
    fun afterEachTest() {
        Intents.release()
    }

    @Test
    fun importSingleNote() {
        importWithSuccess("singlenote.aeszip")
        main_assertListState(entries = listOf("Note 1"), ac = acRule.activity)
    }

    @Test
    fun importInvalidSub() {
        precondition_cleanStart(acRule)
        importExistingNotes("subdirs.aeszip")
        main_assertAlertDialog("Import failed. Zip files with pure directory entries are not supported.")
    }

    @Test
    fun import7z() {
        precondition_cleanStart(acRule)
        importExistingNotes("invalid/emptynote.7z")
        main_assertAlertDialog("Import failed. Probably this is not a valid Zip file.")
    }

    @Test
    fun importEmptyZip() {
        precondition_cleanStart(acRule)
        importExistingNotes("invalid/emptyzip.zip")
        main_assertAlertDialog("Import failed. Probably this is not a valid Zip file.")
    }

    @Test
    fun importUnencryptedNote() {
        precondition_cleanStart(acRule)
        importExistingNotes("invalid/unencrypted_note.zip")
        main_assertAlertDialog("Import failed. Zip files with non-encrypted entries are not supported.")
    }

    @Test
    fun importBrokenZipCrypto() {
        precondition_cleanStart(acRule)
        importExistingNotes("invalid/broken_zipcrypto.zip")
        main_assertAlertDialog("Unsupported encryption algorithm. This app only supports Zip files with AES encryption.")
    }

    @Test
    fun importSubDirs() {
        importWithSuccess("4passwords_subdirs.aeszip")
        main_assertListState(entries = listOf("pw4_entry", "pw3_entry/dir/dir/pw2", "pw2_entry", "pw1_entry/dir/pw1"),
                ac = acRule.activity)
    }

    @Test
    fun importTwoPasswords() {
        importWithSuccess("twopasswords.ZIP")
        main_assertListState(entries = listOf("pw2_entry", "testpassword_entry"),
                ac = acRule.activity)
    }


    @Test
    fun importCompressionDeflate() {
        importCompressionWithSuccess("compression/100a_deflate.aeszip")
    }

    @Test
    fun importCompressionDeflate64() {
        importExpectFailureTest("compression/100a_deflate64.aeszip",
                errorMessage = "Import failed: This app does not support DEFLATE64 compression.")
    }

    @Test
    fun importCompressionDeflateFast() {
        importCompressionWithSuccess("compression/100a_deflate_fast.aeszip")
    }

    @Test
    fun importCompressionDeflateUltra() {
        importCompressionWithSuccess("compression/100a_deflate_ultra.aeszip")
    }

    @Test
    fun importCompressionBZIP2() {
        importExpectFailureTest("compression/100a_bzip2.aeszip",
                errorMessage = "Import failed: This app does not support BZIP2 compression.")
    }

    @Test
    fun importCompressionFlat() {
        importCompressionWithSuccess("compression/100a_flat.aeszip")
    }

    @Test
    fun importCompressionLZMA() {
        importExpectFailureTest("compression/100a_lzma.aeszip",
                errorMessage = "Import failed: This app does not support LZMA compression.")
    }

    @Test
    fun importCompressionPPMD() {
        importExpectFailureTest("compression/100a_ppmd.aeszip",
                errorMessage = "Import failed: This app does not support PPMD compression.")
    }


    private fun importExpectFailureTest(assetToImport: String, errorMessage: String) {
        precondition_cleanStart(acRule)
        importExistingNotes(assetToImport)
        main_assertAlertDialog(errorMessage)
        main_assertEmtpy(acRule.activity)
    }

    private fun importCompressionWithSuccess(assetToImport: String) {
        importWithSuccess(assetToImport)
        click_dialogOK()
        main_assertListState(entries = listOf("100a.txt"), ac = acRule.activity)
        main_clickNote("100a.txt", password = TESTPASSWORD)
        noteEdit_assertState(noteTitle = "100a.txt", secretContent = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    }

    private fun importWithSuccess(assetToImport: String) {
        precondition_cleanStart(acRule)
        importExistingNotes(assetToImport)
        main_assertAlertDialog("Successfully imported zip notes.")
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
        val targetFile = File(appContext.cacheDir, File(assetPath).name)
        targetFile.delete()
        targetFile.deleteOnExit()
        val targetStream = FileOutputStream(targetFile)
        sourceStream.copyTo(targetStream)
        sourceStream.close()
        targetStream.close()
        return targetFile
    }
}

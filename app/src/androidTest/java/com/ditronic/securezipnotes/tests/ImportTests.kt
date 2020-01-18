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
import com.ditronic.securezipnotes.robotpattern.*
import com.ditronic.securezipnotes.testutils.click_dialogOK
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream


@RunWith(AndroidJUnit4::class)
@LargeTest
class ImportTests {


    @After
    fun afterEachTest() {
        Intents.release()
    }

    @Test
    fun importSingleNote() {
        importWithSuccess("singlenote.aeszip")
        main_assertListState(entries = listOf("Note 1"))
    }

    @Test
    fun importInvalidSub() {
        precondition_cleanStart()
        importExistingNotes("subdirs.aeszip")
        main_assertAlertDialog("Import failed. Zip files with pure directory entries are not supported.")
    }

    @Test
    fun import7z() {
        precondition_cleanStart()
        importExistingNotes("invalid/emptynote.7z")
        main_assertAlertDialog("Import failed. Probably this is not a valid Zip file.")
    }

    @Test
    fun importEmptyZip() {
        precondition_cleanStart()
        importExistingNotes("invalid/emptyzip.zip")
        main_assertAlertDialog("Import failed. Probably this is not a valid Zip file.")
    }

    @Test
    fun importUnencryptedNote() {
        precondition_cleanStart()
        importExistingNotes("invalid/unencrypted_note.zip")
        main_assertAlertDialog("Import failed. Zip files with non-encrypted entries are not supported.")
    }

    @Test
    fun importBrokenZipCrypto() {
        precondition_cleanStart()
        importExistingNotes("invalid/broken_zipcrypto.zip")
        main_assertAlertDialog("Unsupported encryption algorithm. This app only supports Zip files with AES encryption.")
    }

    @Test
    fun importSubDirs() {
        importWithSuccess("4passwords_subdirs.aeszip")
        main_assertListState(entries = listOf("pw4_entry", "pw3_entry/dir/dir/pw2", "pw2_entry", "pw1_entry/dir/pw1"))
    }

    @Test
    fun importTwoPasswords() {
        importWithSuccess("twopasswords.ZIP")
        main_assertListState(entries = listOf("testpassword_entry", "pw2_entry").reversed())
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
        precondition_cleanStart()
        importExistingNotes(assetToImport)
        main_assertAlertDialog(errorMessage)
        main_assertEmtpy()
    }

    private fun importCompressionWithSuccess(assetToImport: String) {
        importWithSuccess(assetToImport)
        main_assertListState(entries = listOf("100a.txt"))
        main_clickNote("100a.txt", password = TESTPASSWORD)
        noteEdit_assertState(noteTitle = "100a.txt", secretContent = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    }

    private fun importWithSuccess(assetToImport: String) {
        precondition_cleanStart()
        importExistingNotes(assetToImport)
        main_assertAlertDialog("Successfully imported zip notes.")
        click_dialogOK()
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

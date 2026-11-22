package com.ditronic.securezipnotes.robotpattern

import androidx.test.platform.app.InstrumentationRegistry
import com.ditronic.securezipnotes.contrib.NonFatalAbortTree
import com.ditronic.securezipnotes.noteselect.MainActivity
import com.ditronic.securezipnotes.testutils.launchActivity
import com.ditronic.securezipnotes.zip.CryptoZip
import java.io.FileOutputStream

private fun commonPreconditions() {
    NonFatalAbortTree.plantInTimber()
    launchActivity(MainActivity::class.java)
}

fun precondition_cleanStart() {
    commonPreconditions()
}

fun precondition_singleNote() {
    precondition_loadAsset("singlenote.aeszip")
    main_assertListState(listOf("Note 1"))
}

fun precondition_fourNotes() {
    precondition_loadAsset("4notes.aeszip")
    main_assertListState(listOf("Note 1", "Note 2", "Note 3", "Note 4").reversed())
}

fun precondition_loadAsset(assetPath: String) {
    loadAsset(assetPath)
    commonPreconditions()
}

private fun loadAsset(assetPath: String) {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val testContext = InstrumentationRegistry.getInstrumentation().context

    val sourceStream = testContext.assets.open(assetPath)
    val targetStream = FileOutputStream(CryptoZip.getMainFilePath(appContext))
    sourceStream.copyTo(targetStream)
    sourceStream.close()
    targetStream.close()
}

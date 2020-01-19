package com.ditronic.securezipnotes.onlinetests

import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.ditronic.securezipnotes.robotpattern.*
import com.ditronic.securezipnotes.testutils.pressBack
import com.ditronic.securezipnotes.testutils.targetContext
import com.ditronic.simplefilesync.AbstractFileSync
import com.ditronic.simplefilesync.DropboxFileSync
import com.ditronic.simplefilesync.util.ResultCode
import org.junit.Assert
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.*

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
class SyncTests {

    // TODO: Assert popup messages instead of internal state

    companion object {
        private const val DROPBOX_OAUTH_TOKEN = "TODO set token via environment or property file"
    }

    @Before
    fun beforeEachTest() {
        DropboxFileSync.storeNewOauthToken(DROPBOX_OAUTH_TOKEN, targetContext())
    }

    @Test
    fun dbx1_dropBoxFreshRandomUpload() {
        precondition_singleNote()
        main_clickNote("Note 1", password = TESTPASSWORD)
        noteEdit_typeText(UUID.randomUUID().toString())
        pressBack()
        Espresso.onIdle()
        Assert.assertEquals(ResultCode.UPLOAD_SUCCESS, AbstractFileSync.getLastSyncResult()!!.resultCode)
    }

    @Test
    fun dbx2_dropBoxSingleNoteUpload() {
        precondition_singleNote()
        Espresso.onIdle()
        Assert.assertEquals(ResultCode.UPLOAD_SUCCESS, AbstractFileSync.getLastSyncResult()!!.resultCode)
    }

    @Test
    fun dbx3_dropBoxRemoteEqualsLocal() {
        precondition_singleNote()
        Espresso.onIdle()
        Assert.assertEquals(ResultCode.REMOTE_EQUALS_LOCAL, DropboxFileSync.getLastSyncResult()!!.resultCode)
    }

    @Test
    fun dbx4_dropBoxDownload() {
        precondition_cleanStart()
        Espresso.onIdle()
        Assert.assertEquals(ResultCode.DOWNLOAD_SUCCESS, DropboxFileSync.getLastSyncResult()!!.resultCode)
        main_assertNonEmpty()
    }
}

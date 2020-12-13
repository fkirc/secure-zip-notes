package com.ditronic.securezipnotes.noteedit

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import com.ditronic.securezipnotes.zip.CryptoZip
import net.lingala.zip4j.model.FileHeader

@SuppressLint("StaticFieldLeak")
class NoteEditViewModel(val appContext: Context) : ViewModel() {

    internal var secretContent: String? = null
    internal var editMode: Boolean = false

    lateinit var innerFileName: String

    internal val fileHeader: FileHeader
        get() = CryptoZip.instance(appContext).getFileHeader(innerFileName)!!

    internal val noteName: String
        get() = CryptoZip.getDisplayName(fileHeader)
}

package com.ditronic.securezipnotes.noteedit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import com.ditronic.securezipnotes.zip.CryptoZip
import net.lingala.zip4j.model.FileHeader

class NoteEditViewModel : ViewModel() {

    companion object {
        internal fun instantiate(ac: NoteEditActivity, innerFileName: String) : NoteEditViewModel {
            val  model = ViewModelProviders.of(ac)[NoteEditViewModel::class.java]
            model.innerFileName = innerFileName
            model.ctx = ac.applicationContext
            return model
        }
    }

    internal var secretContent: String? = null
    internal var editMode: Boolean = false
    internal lateinit var innerFileName: String

    private lateinit var ctx: Context

    internal val fileHeader: FileHeader
        get() = CryptoZip.instance(ctx).getFileHeader(innerFileName)!!

    internal val noteName: String
        get() = CryptoZip.getDisplayName(fileHeader)
}

package com.ditronic.securezipnotes.noteedit

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class PlainEditText : AppCompatEditText {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onTextContextMenuItem(id: Int): Boolean {
        var newId = id
        if (id == android.R.id.paste) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                newId = android.R.id.pasteAsPlainText
            }
        }
        return super.onTextContextMenuItem(newId)
    }
}

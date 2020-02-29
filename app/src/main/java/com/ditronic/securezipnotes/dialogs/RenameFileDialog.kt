package com.ditronic.securezipnotes.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.ditronic.securezipnotes.password.PwResult
import com.ditronic.securezipnotes.zip.CryptoZip
import net.lingala.zip4j.model.FileHeader

abstract class RenameFileDialogState(val pwResult: PwResult.Success,
                                     val fileHeader: FileHeader) {
    abstract fun onRenameReturned()
}

class RenameFileDialog: ShortLifeDialogFragment<RenameFileDialogState>() {

    companion object {
        val TAG = FragmentTag("RenameFileDialog")
    }

    lateinit var editText: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?, state: RenameFileDialogState): Dialog {
        editText = EditText(requireContext())

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Rename " + CryptoZip.getDisplayName(state.fileHeader))
        editText.inputType = InputType.TYPE_CLASS_TEXT
        editText.setText(CryptoZip.getDisplayName(state.fileHeader))
        builder.setView(editText)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            onRenameConfirmed()
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
        val dialog = builder.create()
        val window = dialog.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editText.requestFocus()
        return dialog
    }

    override fun getFragmentTag() = TAG

    private fun onRenameConfirmed() {
        val state = fetchStateOrDie() ?: return

        val newName = editText.text.toString()
        CryptoZip.instance(requireContext()).renameFile(
                pw = state.pwResult.password,
                fileHeader = state.fileHeader,
                newEntryName = newName,
                cx = requireContext())

        state.onRenameReturned()
    }
}

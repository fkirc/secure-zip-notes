package com.ditronic.securezipnotes.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.ditronic.securezipnotes.password.PwManager
import com.ditronic.securezipnotes.password.PwRequest
import com.ditronic.securezipnotes.password.PwResult
import com.ditronic.securezipnotes.zip.CryptoZip


class PwDialog: ShortLifeDialogFragment<PwRequest>() {

    companion object {
        val TAG = FragmentTag("PwDialog")
    }

    lateinit var editText: EditText

    override fun getFragmentTag() = TAG

    override fun onCreateDialog(savedInstanceState: Bundle?, state: PwRequest): Dialog {
        editText = EditText(requireContext())

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Master password:")
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        editText.hint = "Master password"
        builder.setView(editText)
        builder.setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
        val dialog = builder.create()
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onPositiveButtonClick()
                return@setOnEditorActionListener true
            }
            false
        }
        val window = dialog.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editText.requestFocus()
        return dialog
    }

    override fun onResume() {
        super.onResume()
        val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {
            onPositiveButtonClick()
        }
    }

    private fun onPositiveButtonClick() {
        val pwRequest = fetchStateOrDie() ?: return
        val typedPassword = editText.text.toString()
        val zipStream = CryptoZip.instance(requireActivity()).isPasswordValid(pwRequest.fileHeader, typedPassword)
        if (zipStream != null) {
            editText.error = null
            PwManager.saveUserProvidedPassword(requireActivity(), PwResult.Success(inputStream = zipStream, password = typedPassword), pwRequest.continuation)
            dismiss()
        } else {
            editText.error = "Wrong password"
        }
    }
}

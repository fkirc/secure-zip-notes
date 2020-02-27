package com.ditronic.securezipnotes.password

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.ditronic.securezipnotes.zip.CryptoZip
import net.lingala.zip4j.model.FileHeader

fun DialogFragment.dismissCrashSafe() {
    try {
        dismiss()
    } catch (exception: Exception) {
        // TODO: Integrate Timber
        Log.e("DialogFragment", "dismissCrashSafe", exception)
    }
}

data class FragmentTag(val value: String)

// TODO: Make fragment constructor empty. Avoid potential activity leaks.
class PwDialog(val continuation: (res: PwResult.Success) -> Unit,
               val fileHeader: FileHeader): DialogFragment() {

    companion object {
        val TAG = FragmentTag("PwDialog")

        private fun dismissIfActive(activity: FragmentActivity) {
            val fragmentManager = activity.supportFragmentManager
            val oldDialog = fragmentManager.findFragmentByTag(TAG.value) as? DialogFragment
            oldDialog?.dismissCrashSafe()
        }

        fun show(activity: FragmentActivity,
                 pwRequest: PwRequest) {
            val dialog = PwDialog(continuation = pwRequest.continuation, fileHeader = pwRequest.fileHeader)
            dismissIfActive(activity = activity)
            dialog.show(activity.supportFragmentManager, TAG.value)
        }
    }

    lateinit var editText: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
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
        val typedPassword = editText.text.toString()
        val zipStream = CryptoZip.instance(requireActivity()).isPasswordValid(fileHeader, typedPassword)
        if (zipStream != null) {
            editText.error = null
            PwManager.saveUserProvidedPassword(requireActivity(), PwResult.Success(inputStream = zipStream, password = typedPassword), continuation)
            dismiss()
        } else {
            editText.error = "Wrong password"
        }
    }
}

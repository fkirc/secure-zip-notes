package com.ditronic.securezipnotes.password

import android.app.AlertDialog
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ditronic.securezipnotes.CryptoZip
import net.lingala.zip4j.model.FileHeader
import javax.crypto.Cipher

class PwManager private constructor() {

    private var password: String? = null

    val passwordFast
        get() = if (password == null) {
            null
        } else {
            password!!.toCharArray()
        }



    private fun onRetrievedPassword(ac: FragmentActivity, fileHeader: FileHeader, cb: () -> Unit) {
        if (password == null) {
            showPasswordDialog(ac, fileHeader, cb)
            return
        }
        if (CryptoZip.instance(ac).isPasswordValid(fileHeader, password!!)) {
            cb() // Password valid, run success callback.
        } else {
            Log.d(TAG, "Outdated password, invalidate preferences and show password dialog")
            password = null
            clearPrivatePrefs(ac)
            // Ask the user for the right password, which runs the callback later on.
            showPasswordDialog(ac, fileHeader, cb)
        }
    }


    fun retrievePasswordAsync(ac: FragmentActivity, fileHeader: FileHeader, cb: () -> Unit) {

        val aSync = retrievePasswordInternal(ac, fileHeader, cb)
        if (!aSync) {
            onRetrievedPassword(ac, fileHeader, cb)
        }
    }


    private fun retrievePasswordInternal(ac: FragmentActivity, fileHeader: FileHeader, cb: () -> Unit): Boolean {

        if (password != null) {
            return false // Password already present.
        }

        if (Build.VERSION.SDK_INT < 23) {
            password = getOldApiPw(ac)
            return false
        }

        val cipherToUnlock = initDecryptCipher(ac) ?: return false

        unlockCipherWithBiometricPrompt(ac, cipherToUnlock) { unlockedCipher ->
            if (unlockedCipher != null) {
                password = decryptPassword(ac, unlockedCipher)
            }
            onRetrievedPassword(ac, fileHeader, cb)
        }
        return true // Asynchronous case
    }


    private fun showPasswordDialog(ac: FragmentActivity, fileHeader: FileHeader, cb: () -> Unit) {

        // Ask the user for the password (asynchronously)
        val builder = AlertDialog.Builder(ac)
        builder.setTitle("Master password:")
        val input = EditText(ac)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "Master password"
        builder.setView(input)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            // Ignored
        }.setNeutralButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
        val dialog = builder.create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onPosBtnClick(ac, input, fileHeader, cb, dialog)
                return@setOnEditorActionListener true
            }
            false
        }
        val window = dialog.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        input.requestFocus()
        val posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        posBtn.setOnClickListener { onPosBtnClick(ac, input, fileHeader, cb, dialog) }
    }


    private fun onPosBtnClick(ac: FragmentActivity, input: EditText, fileHeader: FileHeader, cb: () -> Unit, dialog: AlertDialog) {
        val typedPassword = input.text.toString()
        if (CryptoZip.instance(ac).isPasswordValid(fileHeader, typedPassword)) {
            input.error = null
            saveUserProvidedPassword(ac, typedPassword, cb)
            dialog.dismiss()
        } else {
            input.error = "Wrong password"
        }
    }

    fun saveUserProvidedPassword(ac: FragmentActivity, password: String, cb: () -> Unit) {

        val aSync = savePasswordInternal(ac, password, cb)
        if (!aSync) {
            cb()
        }
    }


    private fun savePasswordInternal(ac: FragmentActivity, pw: String, cb: () -> Unit): Boolean {

        password = pw // This should be assigned before any callback is executed.

        // The salt must be different for each file since this ZIP format uses counter mode with a constant IV!
        // Therefore we cannot simply store a key that is derived via PBKDF2. Instead, we encrypt the password via KeyStore.

        if (Build.VERSION.SDK_INT < 23) {
            saveLowAPIPw(pw, ac)
            return false
        }

        val secretKey = tryGenerateKeyStoreKey() ?: return false
        val cipherToUnlock = initEncryptCipher(secretKey) ?: return false

        // Unlock the freshly created key in order to encrypt the password.
        unlockCipherWithBiometricPrompt(ac, cipherToUnlock) { unlockedCipher ->
            val success = finalizePwEncryption(ac, pw, unlockedCipher)
            if (success) {
                Toast.makeText(ac, "Password configured successfully", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ac, "Failed to encrypt the password", Toast.LENGTH_LONG).show()
            }
            cb() // Callback must be executed regardless of success or failure.
        }
        return true // Asynchronous case
    }

    companion object {

        private val instance_ = PwManager()

        fun instance(): PwManager {
            return instance_
        }


        private val TAG = PwManager::class.java.name


        @RequiresApi(23)
        private fun unlockCipherWithBiometricPrompt(ac: FragmentActivity, cipherToUnlock: Cipher, authCallback: (Cipher?) -> Unit) {

            // Here we should use BiometricPrompt.Builder.setDeviceCredentialAllowed(true).
            // However, setDeviceCredentialAllowed is not yet available within the compat lib.

            val executor = ContextCompat.getMainExecutor(ac)
            val biometricPrompt = BiometricPrompt(ac, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.d(TAG, "onAuthenticationError $errorCode: $errString")
                    // Check whether the user deliberately aborted the operation.
                    if (errorCode != BiometricConstants.ERROR_USER_CANCELED &&
                            errorCode != BiometricConstants.ERROR_CANCELED &&
                            errorCode != BiometricConstants.ERROR_NEGATIVE_BUTTON) {
                        // Try to use the original cipher in case of authentication errors.
                        // This should work in case of devices that do not have any fingerprint registered.
                        authCallback(cipherToUnlock)
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val cryptoObject = result.cryptoObject
                    authCallback(cryptoObject?.cipher)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "onAuthenticationFailed")
                }
            })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Encryption Key")
                    //.setSubtitle("Subtitle")
                    //.setDescription("Description")
                    .setNegativeButtonText(ac.getString(android.R.string.cancel))
                    .build()

            val cryptoObject = BiometricPrompt.CryptoObject(cipherToUnlock)
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        }
    }
}

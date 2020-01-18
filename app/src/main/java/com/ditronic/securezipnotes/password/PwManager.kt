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
import com.ditronic.securezipnotes.zip.CryptoZip
import net.lingala.zip4j.io.ZipInputStream
import net.lingala.zip4j.model.FileHeader
import javax.crypto.Cipher

sealed class PwResult {
    class Success(val inputStream: ZipInputStream?,
                  val password: String): PwResult()
    object Failure : PwResult()
}

object PwManager {

    private var cachedPw: String? = null

    val cachedPassword
        get() = cachedPw


    private fun onRetrievedPassword(ac: FragmentActivity, fileHeader: FileHeader, pw: String?, cb: (res: PwResult.Success) -> Unit) {
        if (pw == null) {
            showPasswordDialog(ac, fileHeader, cb)
            return
        }
        val zipStream = CryptoZip.instance(ac).isPasswordValid(fileHeader, pw)
        if (zipStream != null) {
            cachedPw = pw // Assign password before running any callback!
            cb(PwResult.Success(inputStream = zipStream, password = pw)) // Password valid, run success callback.
        } else {
            Log.d(TAG, "Outdated password, invalidate preferences and show password dialog")
            cachedPw = null
            clearPrivatePrefs(ac)
            // Ask the user for the right password, which runs the callback later on.
            showPasswordDialog(ac, fileHeader, cb)
        }
    }


    fun retrievePasswordAsync(ac: FragmentActivity, fileHeader: FileHeader, cb: (res: PwResult.Success) -> Unit) {

        val cachedPassword = cachedPassword
        if (cachedPassword != null) {
            onRetrievedPassword(ac, fileHeader, cachedPassword, cb) // Synchronous case: Password already present
            return
        }

        if (Build.VERSION.SDK_INT < 23) {
            val pw = getOldApiPw(ac)
            onRetrievedPassword(ac, fileHeader, pw, cb) // Synchronous case: Old API
            return
        }

        val cipherToUnlock = initDecryptCipher(ac)
        if (cipherToUnlock == null) {
            onRetrievedPassword(ac, fileHeader, null, cb) // Synchronous case: Cipher failure
            return
        }

        unlockCipherWithBiometricPrompt(ac, cipherToUnlock) { unlockedCipher ->
            var pw: String? = null
            if (unlockedCipher != null) {
                pw = decryptPassword(ac, unlockedCipher)
            }
            onRetrievedPassword(ac, fileHeader, pw, cb) // Asynchronous case
        }
    }


    private fun showPasswordDialog(ac: FragmentActivity, fileHeader: FileHeader, cb: (res: PwResult.Success) -> Unit) {

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


    private fun onPosBtnClick(ac: FragmentActivity, input: EditText, fileHeader: FileHeader, cb: (res: PwResult.Success) -> Unit, dialog: AlertDialog) {
        val typedPassword = input.text.toString()
        val zipStream = CryptoZip.instance(ac).isPasswordValid(fileHeader, typedPassword)
        if (zipStream != null) {
            input.error = null
            saveUserProvidedPassword(ac, PwResult.Success(inputStream = zipStream, password = typedPassword), cb)
            dialog.dismiss()
        } else {
            input.error = "Wrong password"
        }
    }

    private enum class SyncMode {
        SYNC, ASYNC
    }

    fun saveUserProvidedPassword(ac: FragmentActivity, res: PwResult.Success, cb: (res: PwResult.Success) -> Unit) {

        val syncMode = savePasswordInternal(ac, res, cb)
        if (syncMode == SyncMode.SYNC) {
            cb(res)
        }
    }


    private fun savePasswordInternal(ac: FragmentActivity, res: PwResult.Success, cb: (res: PwResult.Success) -> Unit): SyncMode {

        cachedPw = res.password // Assign password before running any callback!

        // The salt must be different for each file since this ZIP format uses counter mode with a constant IV!
        // Therefore we cannot simply store a key that is derived via PBKDF2. Instead, we encrypt the password via KeyStore.

        if (Build.VERSION.SDK_INT < 23) {
            saveLowAPIPw(res.password, ac)
            return SyncMode.SYNC
        }

        val secretKey = tryGenerateKeyStoreKey() ?: return SyncMode.SYNC
        val cipherToUnlock = initEncryptCipher(secretKey) ?: return SyncMode.SYNC

        // Unlock the freshly created key in order to encrypt the password.
        unlockCipherWithBiometricPrompt(ac, cipherToUnlock) { unlockedCipher ->
            val success = finalizePwEncryption(ac, res.password, unlockedCipher)
            if (success) {
                Toast.makeText(ac, "Password configured successfully", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ac, "Failed to encrypt the password", Toast.LENGTH_LONG).show()
            }
            cb(res) // Callback must be executed regardless of success or failure.
        }
        return SyncMode.ASYNC // Asynchronous case
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

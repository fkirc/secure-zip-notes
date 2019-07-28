package com.ditronic.securezipnotes

import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.util.Base64
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
import net.lingala.zip4j.model.FileHeader
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class PwManager private constructor() {

    private var password: String? = null


    val passwordFast: CharArray?
        get() = if (password == null) {
            null
        } else {
            password!!.toCharArray()
        }


    private fun decryptPassword(cx: Context, cipher: Cipher?): String? {
        try {
            val encPw = getEncPw(cx) ?: return null
            return String(cipher!!.doFinal(encPw), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "doFinal failed to decrypt the password", e)
            return null
        }

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
            ac.getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit().clear().apply()
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

        val secretKey = pwEncKey
        val pwEncIv = getEncPwIv(ac)
        if (secretKey == null || pwEncIv == null) {
            return false // Password material not available.
        }

        val cipherToUnlock: Cipher
        try {
            cipherToUnlock = Cipher.getInstance(PW_ENCRYPT_ALGORITHM)
            cipherToUnlock.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(pwEncIv))
        } catch (e: Exception) {
            Log.d(TAG, "Failed to init decrypt cipher", e)
            return false
        }

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
        posBtn.setOnClickListener { view -> onPosBtnClick(ac, input, fileHeader, cb, dialog) }
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
            // Low API levels do not support AndroidKeystore with symmetric encryption.
            // Or they might even not support AndroidKeystore at all.
            val prefs = ac.getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            prefs.edit().putString(PREF_LOW_API_PW, pw).apply()
            return false
        }

        var secretKey: SecretKey? = null
        try {
            secretKey = tryGenerateKeystoreKey(true)
        } catch (e1: Exception) {
            Log.d(TAG, "Failed to generate key with UserAuthenticationRequired", e1)
            try {
                secretKey = tryGenerateKeystoreKey(false)
            } catch (e2: Exception) {
                Log.d(TAG, "Failed the second attempt to generate a keystore key", e2)
            }

        }

        if (secretKey == null) {
            return false // Failure
        }

        val cipherToUnlock: Cipher
        try {
            cipherToUnlock = Cipher.getInstance(PW_ENCRYPT_ALGORITHM)
            cipherToUnlock.init(Cipher.ENCRYPT_MODE, secretKey)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to init encrypt cipher", e)
            return false
        }

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

        private const val SEC_ALIAS = "pw_enc_key_alias_v3"

        private const val PREF_FILE = "pref_private_no_backup"
        private const val PREF_ENC_PW = "pref_enc_pw$SEC_ALIAS"
        private const val PREF_ENC_PW_IV = "pref_enc_pw_iv$SEC_ALIAS"
        private const val PREF_LOW_API_PW = "pref_low_api_pw$SEC_ALIAS"

        @RequiresApi(23)
        private val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
        @RequiresApi(23)
        private val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
        @RequiresApi(23)
        private val PW_ENCRYPT_ALGORITHM = "AES/$BLOCK_MODE/$PADDING"

        private const val KEY_STORE = "AndroidKeyStore"
        private val TAG = PwManager::class.java.name


        private fun getEncPw(cx: Context): ByteArray? {
            val prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            if (!prefs.contains(PREF_ENC_PW)) {
                return null
            }
            val encPw = prefs.getString(PREF_ENC_PW, "")
            return Base64.decode(encPw, Base64.NO_WRAP)
        }

        private fun getEncPwIv(cx: Context): ByteArray? {
            val prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            if (!prefs.contains(PREF_ENC_PW_IV)) {
                return null
            }
            val encPwIv = prefs.getString(PREF_ENC_PW_IV, "")
            return Base64.decode(encPwIv, Base64.NO_WRAP)
        }


        private val pwEncKey: SecretKey?
            get() {
                return try {
                    val ks = KeyStore.getInstance(KEY_STORE)
                    ks.load(null)
                    ks.getKey(SEC_ALIAS, null) as SecretKey
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to retrieve KeyStore key", e)
                    null
                }

            }


        private fun getOldApiPw(cx: Context): String? {
            if (Build.VERSION.SDK_INT >= 23) {
                return null
            }
            val prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            return prefs.getString(PREF_LOW_API_PW, null)
        }


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


        @RequiresApi(23)
        @Throws(NoSuchAlgorithmException::class, NoSuchProviderException::class, InvalidAlgorithmParameterException::class)
        private fun tryGenerateKeystoreKey(userAuthenticationRequired: Boolean): SecretKey {

            // Try to generate a symmetric key within the AndroidKeyStore.
            val keyGenerator = KeyGenerator.getInstance("AES", KEY_STORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(SEC_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(userAuthenticationRequired)
                    .build()
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }


        private fun finalizePwEncryption(cx: Context, password: String, unlockedCipher: Cipher?): Boolean {
            if (unlockedCipher == null) {
                // Should never happen
                return false
            }
            val encPwIv = unlockedCipher.iv
            val encPw: ByteArray
            try {
                encPw = unlockedCipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                Log.e(TAG, "doFinal failed to encrypt the password", e)
                return false
            }

            // Save the encrypted password
            val prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE)
            val edit = prefs.edit()
            edit.putString(PREF_ENC_PW, Base64.encodeToString(encPw, Base64.NO_WRAP))
            edit.putString(PREF_ENC_PW_IV, Base64.encodeToString(encPwIv, Base64.NO_WRAP))
            edit.apply()
            return true
        }
    }
}

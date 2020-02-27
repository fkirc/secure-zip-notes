package com.ditronic.securezipnotes.password

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec


private const val SEC_ALIAS = "pw_enc_key_alias_v3"

private const val KEY_STORE = "AndroidKeyStore"
private val TAG = "PwStorage"

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

@RequiresApi(23)
@Throws(NoSuchAlgorithmException::class, NoSuchProviderException::class, InvalidAlgorithmParameterException::class)
private fun tryGenerateKeystoreKeyUnchecked(userAuthenticationRequired: Boolean): SecretKey {

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

@RequiresApi(23)
fun tryGenerateKeyStoreKey() : SecretKey? {
    try {
        return tryGenerateKeystoreKeyUnchecked(true)
    } catch (e1: Exception) {
        Log.d(TAG, "Failed to generate key with UserAuthenticationRequired", e1)
        try {
            return tryGenerateKeystoreKeyUnchecked(false)
        } catch (e2: Exception) {
            Log.d(TAG, "Failed the second attempt to generate a keystore key", e2)
            return null
        }
    }
}

private val getKeyStoreKey: SecretKey?
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

private fun getEncPw(cx: Context): ByteArray? {
    val prefs = cx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    if (!prefs.contains(PREF_ENC_PW)) {
        return null
    }
    val encPw = prefs.getString(PREF_ENC_PW, "")
    return Base64.decode(encPw, Base64.NO_WRAP)
}

private fun getEncPwIv(cx: Context): ByteArray? {
    val prefs = cx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    if (!prefs.contains(PREF_ENC_PW_IV)) {
        return null
    }
    val encPwIv = prefs.getString(PREF_ENC_PW_IV, "")
    return Base64.decode(encPwIv, Base64.NO_WRAP)
}

fun finalizePwEncryption(cx: Context, password: String, unlockedCipher: Cipher?): Boolean {
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
    val prefs = cx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    val edit = prefs.edit()
    edit.putString(PREF_ENC_PW, Base64.encodeToString(encPw, Base64.NO_WRAP))
    edit.putString(PREF_ENC_PW_IV, Base64.encodeToString(encPwIv, Base64.NO_WRAP))
    edit.apply()
    return true
}

@RequiresApi(23)
fun initEncryptCipher(secretKey: SecretKey) : Cipher? {
    try {
        val cipherToUnlock = Cipher.getInstance(PW_ENCRYPT_ALGORITHM)
        cipherToUnlock.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipherToUnlock
    } catch (e: Exception) {
        Log.d(TAG, "Failed to init encrypt cipher", e)
        return null
    }
}

@RequiresApi(23)
fun initDecryptCipher(ac: FragmentActivity) : Cipher? {
    val secretKey = getKeyStoreKey
    val pwEncIv = getEncPwIv(ac)
    if (secretKey == null || pwEncIv == null) {
        return null // Password material not available.
    }
    try {
        val cipherToUnlock = Cipher.getInstance(PW_ENCRYPT_ALGORITHM)
        cipherToUnlock.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(pwEncIv))
        return cipherToUnlock
    } catch (e: Exception) {
        Log.d(TAG, "Failed to init decrypt cipher", e)
        return null
    }
}

fun decryptPassword(cx: Context, cipher: Cipher): String? {
    try {
        val encPw = getEncPw(cx) ?: return null
        return String(cipher.doFinal(encPw), StandardCharsets.UTF_8)
    } catch (e: Exception) {
        Log.e(TAG, "doFinal failed to decrypt the password", e)
        return null
    }
}

fun clearPrivatePrefs(ac: FragmentActivity) {
    ac.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().clear().apply()
}


fun saveLowAPIPw(pw: String, ac: FragmentActivity) {
    if (Build.VERSION.SDK_INT >= 23) {
        throw RuntimeException("Do not store passwords in plaintext")
    }
    // Low API levels do not support AndroidKeystore with symmetric encryption.
    // Or they might even not support AndroidKeystore at all.
    val prefs = ac.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    prefs.edit().putString(PREF_LOW_API_PW, pw).apply()
}

fun getOldApiPw(cx: Context): String? {
    if (Build.VERSION.SDK_INT >= 23) {
        return null
    }
    val prefs = cx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    return prefs.getString(PREF_LOW_API_PW, null)
}

@RequiresApi(23)
internal fun unlockCipherWithBiometricPrompt(ac: FragmentActivity, cipherToUnlock: Cipher, authCallback: (Cipher?) -> Unit) {

    // Here we should use BiometricPrompt.Builder.setDeviceCredentialAllowed(true).
    // However, setDeviceCredentialAllowed is not yet available within the compat lib.

    val executor = ContextCompat.getMainExecutor(ac)
    val biometricPrompt = BiometricPrompt(ac, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            Log.d("PwCrypt", "onAuthenticationError $errorCode: $errString")
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
            Log.d("PwCrypt", "onAuthenticationFailed")
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

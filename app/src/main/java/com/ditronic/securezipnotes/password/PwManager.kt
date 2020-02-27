package com.ditronic.securezipnotes.password

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.ditronic.securezipnotes.zip.CryptoZip
import net.lingala.zip4j.io.ZipInputStream
import net.lingala.zip4j.model.FileHeader

sealed class PwResult {
    class Success(val inputStream: ZipInputStream?,
                  val password: String): PwResult()
    object Failure : PwResult()
}

class PwRequest(
        val fileHeader: FileHeader,
        val continuation: (res: PwResult.Success) -> Unit
)

object PwManager {

    private var cachedPw: String? = null

    val cachedPassword
        get() = cachedPw


    private fun onRetrievedPassword(ac: FragmentActivity, pwRequest: PwRequest, pw: String?) {
        if (pw == null) {
            showPasswordDialog(ac, pwRequest = pwRequest)
            return
        }
        val zipStream = CryptoZip.instance(ac).isPasswordValid(pwRequest.fileHeader, pw)
        if (zipStream != null) {
            cachedPw = pw // Assign password before running any callback!
            pwRequest.continuation(PwResult.Success(inputStream = zipStream, password = pw)) // Password valid, run success callback.
        } else {
            Log.d(TAG, "Outdated password, invalidate preferences and show password dialog")
            cachedPw = null
            clearPrivatePrefs(ac)
            // Ask the user for the right password, which runs the callback later on.
            showPasswordDialog(ac, pwRequest = pwRequest)
        }
    }


    fun retrievePasswordAsync(ac: FragmentActivity, fileHeader: FileHeader, continuation: (res: PwResult.Success) -> Unit) {
        val pwRequest = PwRequest(fileHeader = fileHeader, continuation = continuation)
        val cachedPassword = cachedPassword
        if (cachedPassword != null) {
            onRetrievedPassword(ac, pwRequest = pwRequest, pw = cachedPassword) // Synchronous case: Password already present
            return
        }

        if (Build.VERSION.SDK_INT < 23) {
            val pw = getOldApiPw(ac)
            onRetrievedPassword(ac, pwRequest = pwRequest, pw = pw) // Synchronous case: Old API
            return
        }

        val cipherToUnlock = initDecryptCipher(ac)
        if (cipherToUnlock == null) {
            onRetrievedPassword(ac, pwRequest = pwRequest, pw = null) // Synchronous case: Cipher failure
            return
        }

        unlockCipherWithBiometricPrompt(ac, cipherToUnlock) { unlockedCipher ->
            val pw = if (unlockedCipher != null) {
                decryptPassword(ac, unlockedCipher)
            } else {
                null
            }
            onRetrievedPassword(ac, pwRequest = pwRequest, pw = pw) // Asynchronous case
        }
    }


    private fun showPasswordDialog(ac: FragmentActivity, pwRequest: PwRequest) {
        PwDialog.show(activity = ac, pwRequest = pwRequest)
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
}

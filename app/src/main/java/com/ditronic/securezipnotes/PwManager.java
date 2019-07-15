package com.ditronic.securezipnotes;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricConstants;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.ditronic.securezipnotes.util.Boast;

import net.lingala.zip4j.model.FileHeader;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import static android.content.Context.MODE_PRIVATE;

public class PwManager {

    private static PwManager instance_;

    private PwManager() {
    }

    public static PwManager instance() {
        if (instance_ == null) {
            instance_ = new PwManager();
        }
        return instance_;
    }

    private String password;

    private static final String SEC_ALIAS = "test_pw_enc_key_alias_v8";

    private static final String PREF_FILE = "pref_private_no_backup";
    private static final String PREF_ENC_PW = "pref_enc_pw" + SEC_ALIAS;
    private static final String PREF_ENC_PW_IV = "pref_enc_pw_iv" + SEC_ALIAS;
    private static final String PREF_LOW_API_PW = "pref_low_api_pw" + SEC_ALIAS;

    @RequiresApi(23)
    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC;
    @RequiresApi(23)
    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7;
    @RequiresApi(23)
    private static final String PW_ENCRYPT_ALGORITHM = "AES/" + BLOCK_MODE + "/" + PADDING;

    private static final String KEY_STORE = "AndroidKeyStore";
    private static final String TAG = PwManager.class.getName();


    private static @Nullable byte[] getEncPw(final Context cx) {
        final SharedPreferences prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        if (!prefs.contains(PREF_ENC_PW)) {
            return null;
        }
        final String encPw = prefs.getString(PREF_ENC_PW, "");
        return Base64.decode(encPw, Base64.NO_WRAP);
    }

    private static @Nullable byte[] getEncPwIv(final Context cx) {
        final SharedPreferences prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        if (!prefs.contains(PREF_ENC_PW_IV)) {
            return null;
        }
        final String encPwIv = prefs.getString(PREF_ENC_PW_IV, "");
        return Base64.decode(encPwIv, Base64.NO_WRAP);
    }


    private static @Nullable SecretKey getPwEncKey() {
        try {
            final KeyStore ks = KeyStore.getInstance(KEY_STORE);
            ks.load(null);
            return (SecretKey) ks.getKey(SEC_ALIAS, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve KeyStore key", e);
            return null;
        }
    }


    private static @Nullable String getOldApiPw(final Context cx) {
        if (Build.VERSION.SDK_INT >= 23) {
            return null;
        }
        final SharedPreferences prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        return prefs.getString(PREF_LOW_API_PW, null);
    }


    public @Nullable char[] getPasswordFast() {
        if (password == null) {
            return null;
        } else {
            return password.toCharArray();
        }
    }


    private interface BiometricAuthCb {
        void onBiometricPromptFinished(@Nullable Cipher unlockedCipher);
    }


    @RequiresApi(23)
    private static void unlockCipherWithBiometricPrompt(final FragmentActivity ac, final Cipher cipherToUnlock, final BiometricAuthCb authCallback) {

        // Here we should use BiometricPrompt.Builder.setDeviceCredentialAllowed(true).
        // However, setDeviceCredentialAllowed is not yet available within the compat lib.

        final Executor executor = ContextCompat.getMainExecutor(ac);
        final BiometricPrompt biometricPrompt = new BiometricPrompt(ac, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Log.d(TAG, "onAuthenticationError " + errorCode + ": " + errString);
                if (errorCode != BiometricConstants.ERROR_USER_CANCELED &&
                    errorCode != BiometricConstants.ERROR_CANCELED &&
                    errorCode != BiometricConstants.ERROR_NEGATIVE_BUTTON) {
                    // Use password dialog fallback mode in case of too many tries
                    Toast.makeText(ac, "Authentication failed: " + errString, Toast.LENGTH_LONG).show();
                    authCallback.onBiometricPromptFinished(null);
                }
            }
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                final BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                authCallback.onBiometricPromptFinished(cryptoObject != null ? cryptoObject.getCipher() : null);
            }
            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Log.d(TAG, "onAuthenticationFailed");
            }
        });
        final BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Encryption Key")
                //.setSubtitle("Subtitle")
                //.setDescription("Description")
                .setNegativeButtonText(ac.getString(android.R.string.cancel))
                .build();

        final BiometricPrompt.CryptoObject cryptoObject = new BiometricPrompt.CryptoObject(cipherToUnlock);
        biometricPrompt.authenticate(promptInfo, cryptoObject);
    }


    private @Nullable String decryptPassword(final Context cx, final Cipher cipher) {
        try {
            final byte[] encPw = getEncPw(cx);
            if (encPw == null) {
                return null;
            }
            return new String(cipher.doFinal(encPw), "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "doFinal failed to decrypt the password", e);
            return null;
        }
    }


    private void onRetrievedPassword(final FragmentActivity ac, final FileHeader fileHeader, final Runnable cb) {
        if (password == null) {
            showPasswordDialog(ac, fileHeader, cb);
            return;
        }
        if (CryptoZip.instance(ac).isPasswordValid(fileHeader, password)) {
            cb.run(); // Password valid, run success callback.
        } else {
            Log.d(TAG, "Outdated password, invalidate preferences and show password dialog");
            password = null;
            ac.getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit().clear().apply();
            // Ask the user for the right password, which runs the callback later on.
            showPasswordDialog(ac, fileHeader, cb);
        }
    }


    public void retrievePasswordAsync(final FragmentActivity ac, final FileHeader fileHeader, final Runnable cb) {

        final boolean aSync = retrievePasswordInternal(ac, fileHeader, cb);
        if (!aSync) {
            onRetrievedPassword(ac, fileHeader, cb);
        }
    }
    

    private boolean retrievePasswordInternal(final FragmentActivity ac, final FileHeader fileHeader, final Runnable cb) {

        if (password != null) {
            return false; // Password already present.
        }

        if (Build.VERSION.SDK_INT < 23) {
            password = getOldApiPw(ac);
            return false;
        }

        final SecretKey secretKey = getPwEncKey();
        final byte[] pwEncIv = getEncPwIv(ac);
        if (secretKey == null || pwEncIv == null) {
            return false; // Password material not available.
        }

        final Cipher cipherToUnlock;
        try {
            cipherToUnlock = Cipher.getInstance(PW_ENCRYPT_ALGORITHM);
            cipherToUnlock.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(pwEncIv));
        } catch (Exception e) {
            Log.d(TAG, "Failed to init decrypt cipher", e);
            return false;
        }

        unlockCipherWithBiometricPrompt(ac, cipherToUnlock, unlockedCipher -> {
            if (unlockedCipher != null) {
                password = decryptPassword(ac, unlockedCipher);
            }
            onRetrievedPassword(ac, fileHeader, cb);
        });
        return true; // Asynchronous case
    }


    private void showPasswordDialog(final FragmentActivity ac, final FileHeader fileHeader, final Runnable cb) {

        // Ask the user for the password (asynchronously)
        final AlertDialog.Builder builder = new AlertDialog.Builder(ac);
        builder.setTitle("Master password:");
        final EditText input = new EditText(ac);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Master password");
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Ignored
            }
        }).setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        final android.app.AlertDialog dialog = builder.create();
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onPosBtnClick(ac, input, fileHeader, cb, dialog);
                    return true;
                }
                return false;
            }
        });
        final Window window = dialog.getWindow();
        if (window != null) {
            // Show keyboard automatically
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
        final Button posBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        posBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onPosBtnClick(ac, input, fileHeader, cb, dialog);
            }
        });
    }


    private void onPosBtnClick(final FragmentActivity ac, final EditText input, final FileHeader fileHeader, final Runnable cb, final AlertDialog dialog) {
        final String typedPassword = input.getText().toString();
        if (CryptoZip.instance(ac).isPasswordValid(fileHeader, typedPassword)) {
            input.setError(null);
            saveUserProvidedPassword(ac, typedPassword, cb);
            dialog.dismiss();
        } else {
            input.setError("Wrong password");
        }
    }

    public void saveUserProvidedPassword(final FragmentActivity ac, @NonNull final String password, final Runnable cb) {
        
        final boolean aSync = savePasswordInternal(ac, password, cb);
        if (!aSync) {
            cb.run();
        }
    }


    private boolean savePasswordInternal(final FragmentActivity ac, @NonNull final String pw, final Runnable cb) {
        
        password = pw; // This should be assigned before any callback is executed.

        // The salt must be different for each file since this ZIP format uses counter mode with a constant IV!
        // Therefore we cannot simply store a key that is derived via PBKDF2. Instead, we encrypt the password via KeyStore.

        if (Build.VERSION.SDK_INT < 23) {
            // Low API levels do not support AndroidKeystore with symmetric encryption.
            // Or they might even not support AndroidKeystore at all.
            final SharedPreferences prefs = ac.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
            prefs.edit().putString(PREF_LOW_API_PW, pw).apply();
            return false;
        }

        SecretKey secretKey = null;
        try {
            secretKey = tryGenerateKeystoreKey(true);
        } catch (Exception e1) {
            Log.d(TAG, "Failed to generate key with UserAuthenticationRequired", e1);
            try {
                secretKey = tryGenerateKeystoreKey(false);
            } catch (Exception e2) {
                Log.d(TAG, "Failed the second attempt to generate a keystore key", e2);
            }
        }
        if (secretKey == null) {
            return false; // Failure
        }

        final Cipher cipherToUnlock;
        try {
            cipherToUnlock = Cipher.getInstance(PW_ENCRYPT_ALGORITHM);
            cipherToUnlock.init(Cipher.ENCRYPT_MODE, secretKey);
        } catch (Exception e) {
            Log.d(TAG, "Failed to init encrypt cipher", e);
            return false;
        }

        // Unlock the freshly created key in order to encrypt the password.
        unlockCipherWithBiometricPrompt(ac, cipherToUnlock, unlockedCipher ->  {
            final boolean success = finalizePwEncryption(ac, pw, cipherToUnlock);
            if (!success) {
                Toast.makeText(ac, "Failed to encrypt the password", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(ac, "Password configured successfully", Toast.LENGTH_LONG).show();
            }
            cb.run(); // Callback must be executed regardless of success or failure.
        });
        return true; // Asynchronous case
    }


    @RequiresApi(23)
    private static SecretKey tryGenerateKeystoreKey(final boolean userAuthenticationRequired)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {

        // Try to generate a symmetric key within the AndroidKeyStore.
        final KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", KEY_STORE);
        final KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(SEC_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(PADDING)
                .setUserAuthenticationRequired(userAuthenticationRequired)
                .build();
        keyGenerator.init(keyGenParameterSpec);
        return keyGenerator.generateKey();
    }


    private static boolean finalizePwEncryption(final Context cx, final String password, final @Nullable Cipher unlockedCipher) {
        if (unlockedCipher == null) {
            // Should never happen
            return false;
        }
        final byte[] encPwIv = unlockedCipher.getIV();
        final byte[] encPw;
        try {
            encPw = unlockedCipher.doFinal(password.getBytes("UTF-8"));
        } catch (Exception e) {
            Log.e(TAG, "doFinal failed to encrypt the password", e);
            return false;
        }
        saveEncPw(cx, encPw, encPwIv);
        return true;
    }


    private static void saveEncPw(final Context cx, final byte[] encPw, final byte[] encPwIv) {
        final SharedPreferences prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(PREF_ENC_PW, Base64.encodeToString(encPw, Base64.NO_WRAP));
        edit.putString(PREF_ENC_PW_IV, Base64.encodeToString(encPwIv, Base64.NO_WRAP));
        edit.apply();
        Boast.makeText(cx, "Password correct").show();
    }
}

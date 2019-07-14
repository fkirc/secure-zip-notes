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

    private static final String PW_ENCRYPT_ALGORITHM = "AES/CBC/PKCS7Padding";
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
            throw new RuntimeException(e);
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
        void onBiometricPromptSuccess(@Nullable Cipher unlockedCipher);
    }

    // TODO: Check in advance whether BiometricPrompt is available

    @RequiresApi(23)
    private void unlockCipherWithBiometricPrompt(final FragmentActivity ac, final Cipher cipherToUnlock, final BiometricAuthCb authCallback) {

        // Here we should use BiometricPrompt.Builder.setDeviceCredentialAllowed(true).
        // However, setDeviceCredentialAllowed is not yet available within the compat lib.

        final Executor executor = ContextCompat.getMainExecutor(ac);
        final BiometricPrompt biometricPrompt = new BiometricPrompt(ac, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Log.d(TAG, "onAuthenticationError " + errorCode + ": " + errString);
                // Important message in case of too many tries
                if (errorCode != BiometricConstants.ERROR_USER_CANCELED &&
                    errorCode != BiometricConstants.ERROR_CANCELED &&
                    errorCode != BiometricConstants.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(ac, "Authentication failed: " + errString, Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                final BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                authCallback.onBiometricPromptSuccess(cryptoObject != null ? cryptoObject.getCipher() : null);
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
            throw new RuntimeException(e);
        }
    }


    private void onRetrievedPassword(final FragmentActivity ac, final FileHeader fileHeader, final Runnable cb) {
        if (password == null) {
            throw new IllegalStateException();
        }
        if (CryptoZip.instance(ac).isPasswordValid(fileHeader, password)) {
            cb.run(); // Password valid, run success callback.
        } else {
            Log.d(TAG, "Outdated password, invalidate preferences and show password dialog");
            password = null;
            ac.getSharedPreferences(PREF_FILE, MODE_PRIVATE).edit().clear().apply();
            // Ask the user for the right password.
            showPasswordDialog(ac, fileHeader, cb);
        }
    }


    public void retrievePasswordAsync(final FragmentActivity ac, final FileHeader fileHeader, final Runnable cb) {

        if (password != null) {
            onRetrievedPassword(ac, fileHeader, cb);
            return;
        }

        if (Build.VERSION.SDK_INT < 23) {
            password = getOldApiPw(ac);
            if (password != null) {
                onRetrievedPassword(ac, fileHeader, cb);
            } else {
                showPasswordDialog(ac, fileHeader, cb);
            }
            return;
        }

        final SecretKey secretKey = getPwEncKey();
        final byte[] pwEncIv = getEncPwIv(ac);
        if (secretKey == null || pwEncIv == null) {
            showPasswordDialog(ac, fileHeader, cb);
            return;
        }

        final Cipher cipherToUnlock;
        try {
            cipherToUnlock = Cipher.getInstance(PW_ENCRYPT_ALGORITHM);
            cipherToUnlock.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(pwEncIv));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        unlockCipherWithBiometricPrompt(ac, cipherToUnlock, unlockedCipher -> {
                if (unlockedCipher != null) {
                    password = decryptPassword(ac, unlockedCipher);
                }
                if (password != null) {
                    onRetrievedPassword(ac, fileHeader, cb);
                } else {
                    showPasswordDialog(ac, fileHeader, cb);
                }
        });
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
            saveUserProvidedPassword(ac, typedPassword);
            cb.run();
            dialog.dismiss();
        } else {
            input.setError("Wrong password");
        }
    }


    public void saveUserProvidedPassword(final FragmentActivity ac, final String pwd) {
        try {
            savePasswordUnchecked(ac, pwd);
            password = pwd;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void savePasswordUnchecked(final FragmentActivity ac, final String password)
            throws InvalidAlgorithmParameterException, NoSuchProviderException, NoSuchAlgorithmException {

        // The salt must be different for each file since this ZIP format uses counter mode with a constant IV!
        // Therefore we cannot simply store a key that is derived via PBKDF2. Instead, we encrypt the password via KeyStore.

        if (Build.VERSION.SDK_INT < 23) {
            // Low API levels do not support AndroidKeystore with symmetric encryption.
            // Or they might even not support AndroidKeystore at all.
            final SharedPreferences prefs = ac.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
            prefs.edit().putString(PREF_LOW_API_PW, password).apply();
            return;
        }

        // Generate a symmetric key within the AndroidKeyStore.
        final KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", KEY_STORE);
        final KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(SEC_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                //.setUserAuthenticationValidityDurationSeconds(5) // Repeat authentication if app is force-closed
                .build();
        keyGenerator.init(keyGenParameterSpec);
        final SecretKey secretKey = keyGenerator.generateKey();

        final Cipher cipherToUnlock;
        try {
            cipherToUnlock = Cipher.getInstance(PW_ENCRYPT_ALGORITHM);
            cipherToUnlock.init(Cipher.ENCRYPT_MODE, secretKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Unlock the freshly created key in order to encrypt the password.
        unlockCipherWithBiometricPrompt(ac, cipherToUnlock, unlockedCipher ->  {
            if (unlockedCipher == null) {
                // Should never happen
                Toast.makeText(ac, "Failed to encrypt the password", Toast.LENGTH_LONG).show();
                return;
            }
            final byte[] encPwIv = unlockedCipher.getIV();
            final byte[] encPw;
            try {
                encPw = unlockedCipher.doFinal(password.getBytes("UTF-8"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            saveEncPw(ac, encPw, encPwIv);
        });
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

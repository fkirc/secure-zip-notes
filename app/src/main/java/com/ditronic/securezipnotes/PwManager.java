package com.ditronic.securezipnotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.security.keystore.UserNotAuthenticatedException;
import android.text.InputType;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import net.lingala.zip4j.model.FileHeader;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.MODE_PRIVATE;
import static com.ditronic.securezipnotes.activities.MainActivity.REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS;

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
    private Runnable onAcResultCallback;

    private static final String PREF_FILE = "pref_private_no_backup";
    private static final String PREF_ENC_PW = "pref_enc_pw";
    private static final String PREF_ENC_PW_IV = "pref_enc_pw_iv";
    private static final String PREF_LOW_API_PW = "pref_low_api_pw";

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

    private static void saveEncPw(final Context cx, final byte[] encPw, final byte[] encPwIv) {
        final SharedPreferences prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(PREF_ENC_PW, Base64.encodeToString(encPw, Base64.NO_WRAP));
        edit.putString(PREF_ENC_PW_IV, Base64.encodeToString(encPwIv, Base64.NO_WRAP));
        edit.apply();
    }

    private static final String PW_ENCRYPT_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;

    private static final String TAG = PwManager.class.getName();

    private static final String KEY_STORE = "AndroidKeyStore";

    private static KeyStore getKeyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        final KeyStore ks = KeyStore.getInstance(KEY_STORE);
        ks.load(null);
        return ks;
    }

    private static @Nullable SecretKey getPwEncKey() throws KeyStoreException, CertificateException,
            IOException, NoSuchAlgorithmException, UnrecoverableEntryException {
        KeyStore ks = getKeyStore();
        if (!ks.containsAlias(SEC_ALIAS)) {
            return null;
        }
        final KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry)ks.getEntry(SEC_ALIAS, null);
        return secretKeyEntry.getSecretKey();
    }

    private static final String SEC_ALIAS = "pw_derived_key_alias";

    private static final int AES_KEY_LEN = 256;

    private static @Nullable String decryptPasswordUnchecked(final Context cx)
            throws NoSuchAlgorithmException, NoSuchPaddingException, IOException, InvalidKeyException,
            BadPaddingException, IllegalBlockSizeException, InvalidAlgorithmParameterException,
            KeyStoreException, CertificateException, UnrecoverableEntryException {

        if (Build.VERSION.SDK_INT < 23) {
            final SharedPreferences prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
            return prefs.getString(PREF_LOW_API_PW, null);
        }

        final byte[] encPw = getEncPw(cx);
        if (encPw == null) {
            return null;
        }
        final byte[] encPwIv = getEncPwIv(cx);
        if (encPwIv == null) {
            return null;
        }
        final SecretKey secretKey = getPwEncKey();
        if (secretKey == null) {
            return null;
        }

        final Cipher cipher = Cipher.getInstance(PW_ENCRYPT_ALGORITHM);
        final GCMParameterSpec spec = new GCMParameterSpec(128, encPwIv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return new String(cipher.doFinal(encPw), "UTF-8");
    }

    private static void savePasswordUnchecked(final Context cx, final String password)
            throws NoSuchAlgorithmException, KeyStoreException, NoSuchPaddingException, InvalidKeyException,
            IOException, BadPaddingException, CertificateException, IllegalBlockSizeException {

        if (Build.VERSION.SDK_INT < 23) {
            // Low API levels do not support AndroidKeystore with symmetric encryption.
            // Or they might even not support AndroidKeystore at all.
            final SharedPreferences prefs = cx.getSharedPreferences(PREF_FILE, MODE_PRIVATE);
            prefs.edit().putString(PREF_LOW_API_PW, password).apply();
            return;
        }

        // The salt must be different for each file since this ZIP format uses counter mode with a constant IV!
        // Therefore we cannot simply store a key that is derived via PBKDF2. Instead, we encrypt the password via KeyStore.
        KeyStore ks = getKeyStore();

        final KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_LEN);
        final SecretKey pwEncKey = keyGenerator.generateKey();

        final KeyProtection keyProtection = new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(5) // Repeat authentication if app is force-closed
                .build();
        ks.setEntry(SEC_ALIAS, new KeyStore.SecretKeyEntry(pwEncKey), keyProtection);

        // Use the key outside of secure hardware for the first encryption to prevent a useless authentication screen right after typing in the password.
        final Cipher cipher = Cipher.getInstance(PW_ENCRYPT_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, pwEncKey);
        final byte[] encPwIv = cipher.getIV();
        final byte[] encPw = cipher.doFinal(password.getBytes("UTF-8"));
        saveEncPw(cx, encPw, encPwIv);
    }

    public @Nullable char[] getPasswordFast() {
        if (password == null) {
            return null;
        } else {
            return password.toCharArray();
        }
    }


    public void saveUserProvidedPassword(final Activity cx, final String pwd) {
        try {
            savePasswordUnchecked(cx, pwd);
            password = pwd;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            if (resultCode == RESULT_OK) {
                if (onAcResultCallback != null) {
                    onAcResultCallback.run();
                    onAcResultCallback = null;
                }
            }
        }
    }


    private boolean onRetrievedPassword(final Context cx, final FileHeader fileHeader, final Runnable cb) {
        if (password == null) {
            return false;
        }
        if (CryptoZip.instance(cx).isPasswordValid(fileHeader, password)) {
            cb.run(); // Password valid, run success callback.
            return true;
        } else {
            password = null; // Outdated password, invalidate it.
            return false;
        }
    }

    @RequiresApi(23)
    private void showAuthenticationScreen(final Activity cx, final Runnable onAcResCb) {
        final KeyguardManager keyguardManager = (KeyguardManager) cx.getSystemService(Context.KEYGUARD_SERVICE);
        final String appName = cx.getResources().getString(R.string.app_name);
        final Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(appName, "User confirmation required to decrypt the Zip file");
        if (intent != null) {
            onAcResultCallback = onAcResCb;
            cx.startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
        }
    }

    public void retrievePasswordAsync(final Activity cx, final FileHeader fileHeader, final Runnable cb) {
        if (onRetrievedPassword(cx, fileHeader, cb)) {
            return; // Return immediately if we already have the right password
        }
        try {
            // Try to retrieve an already encrypted password
            password = decryptPasswordUnchecked(cx);
            if (onRetrievedPassword(cx, fileHeader, cb)) {
                return;
            }
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= 23 && e instanceof UserNotAuthenticatedException) {
                showAuthenticationScreen(cx, new Runnable() {
                    @Override
                    public void run() {
                        retrievePasswordAsync(cx, fileHeader, cb);
                    }
                });
                return; // This method should be called again in onActivityResultOauthSignIn, after getting user confirmation
            } else {
                throw new RuntimeException(e);
            }
        }

        // We have to ask the user for the password (asynchronously)
        final AlertDialog.Builder builder = new AlertDialog.Builder(cx);
        builder.setTitle("Master password:");
        final EditText input = new EditText(cx);
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
                    onPosBtnClick(cx, input, fileHeader, cb, dialog);
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
                onPosBtnClick(cx, input, fileHeader, cb, dialog);
            }
        });
    }

    private void onPosBtnClick(final Activity cx, final EditText input, final FileHeader fileHeader, final Runnable cb, final AlertDialog dialog) {
        final String typedPassword = input.getText().toString();
        if (CryptoZip.instance(cx).isPasswordValid(fileHeader, typedPassword)) {
            //Boast.makeText(cx, "Password correct").show();
            input.setError(null);
            saveUserProvidedPassword(cx, typedPassword);
            cb.run();
            dialog.dismiss();
        } else {
            input.setError("Wrong password");
        }
    }
}

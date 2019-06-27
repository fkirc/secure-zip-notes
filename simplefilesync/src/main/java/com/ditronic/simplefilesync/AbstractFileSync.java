package com.ditronic.simplefilesync;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.ditronic.simplefilesync.util.SSyncResult;

public abstract class AbstractFileSync extends AsyncTask<Object, String, SSyncResult> {

    public interface SSyncCallback {
        void onSyncCompleted(SSyncResult res);
    }

    public AbstractFileSync(Context cx, File localFile, String remoteFileName, SSyncCallback cb) {
        this.context = cx;
        this.localFile = localFile;
        this.remoteFileName = remoteFileName;
        this.cb = cb;
    }

    // This leaks the context reference, but we expect to terminate this task within a few seconds.
    // WeakReferences did not work because they lost their reference too early.
    @SuppressLint("StaticFieldLeak")
    protected final Context context;
    protected final File localFile;
    protected final String remoteFileName;

    @SuppressLint("StaticFieldLeak")
    private SSyncCallback cb;
    private ProgressDialog progressDialog;

    private static final String BASE_SYNC_PREF_FILE = "pref_file_sync";
    private static final String PREF_CURRENT_SYNC_BACKEND = "pref_current_sync_backend";

    private static boolean syncTriggeredByUser = false;

    @Override
    protected void onProgressUpdate(final String... userMessage) {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        progressDialog = new ProgressDialog(context);
        progressDialog.setIndeterminate(true);
        progressDialog.setMessage(userMessage[0]);
        progressDialog.show();
    }

    @Override
    protected void onPostExecute(final @NonNull SSyncResult res) {

        if (progressDialog != null) {
            progressDialog.dismiss();
        }

        if (syncBackendStillCorrect()) {
            res.setSyncTriggeredByUser(syncTriggeredByUser);
            syncTriggeredByUser = false;
        }
        cb.onSyncCompleted(res);
    }

    private String TAG() {
        return getClass().getName();
    }

    protected boolean localFileNonEmpty() {
        if (!localFile.exists()) {
            Log.d(TAG(), localFile.getAbsolutePath() + " does not exist");
            return false;
        }
        if (localFile.length() == 0) {
            Log.d(TAG(), localFile.getAbsolutePath() + " is empty");
            return false;
        }
        return true;
    }

    protected boolean timestampsEqualToSavedTimestamps(final long serverModified) {
        final long clientModified = localFile.lastModified();

        final SharedPreferences prefs = context.getSharedPreferences(BASE_SYNC_PREF_FILE, Context.MODE_PRIVATE);
        final long serverModifiedSaved = prefs.getLong(remoteFileName, 0);
        final long clientModifiedSaved = prefs.getLong(localFile.getAbsolutePath(), 0);

        if (serverModified == 0 || clientModified == 0) {
            return false;
        }
        return ((serverModified == serverModifiedSaved) && (clientModified == clientModifiedSaved));
    }

    protected void saveLastModifiedOfEqualFiles(final long serverModified) {
        final long clientModified = localFile.lastModified();
        SharedPreferences prefs = context.getSharedPreferences(BASE_SYNC_PREF_FILE, Context.MODE_PRIVATE);
        prefs.edit().putLong(remoteFileName, serverModified)
                .putLong(localFile.getAbsolutePath(), clientModified).apply();
    }

    protected static MessageDigest getDigestInstance(final String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean syncBackendStillCorrect() {
        final String syncBackend = getCurrentSyncBackend(context);
        if (syncBackend == null) {
            return false;
        }
        return syncBackend.equals(getClass().getSimpleName());
    }

    public static @Nullable String getCurrentSyncBackend(final Context cx) {
        final SharedPreferences prefs = cx.getSharedPreferences(BASE_SYNC_PREF_FILE, Context.MODE_PRIVATE);
        return prefs.getString(PREF_CURRENT_SYNC_BACKEND, null);
    }

    protected static void setCurrentSyncBackend(final Context cx, Class<?> cls) {
        syncTriggeredByUser = true;
        SharedPreferences prefs = cx.getSharedPreferences(BASE_SYNC_PREF_FILE, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_CURRENT_SYNC_BACKEND, cls.getSimpleName()).apply();
    }
}

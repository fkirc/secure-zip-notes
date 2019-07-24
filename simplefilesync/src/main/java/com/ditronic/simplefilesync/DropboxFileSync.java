package com.ditronic.simplefilesync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.ditronic.simplefilesync.util.DropboxContentHasher;
import com.ditronic.simplefilesync.util.FilesUtil;
import com.ditronic.simplefilesync.util.ResultCode;
import com.ditronic.simplefilesync.util.SSyncResult;
import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.android.AuthActivity;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class DropboxFileSync extends AbstractFileSync {

    private static final String TAG = DropboxFileSync.class.getName();
    private static final String DROPBOX_PREF_FILE = "pref_dropbox_file_sync";
    private static final String PREF_DROPBOX_ACCESS_TOKEN = "dropbox_access_token";

    public DropboxFileSync(Context cx, File localFile, String remoteFileName, SSyncCallback cb) {
        super(cx, localFile, remoteFileName, cb);
    }

    private String getDbxPath() {
        // We are storing to the app-specific folder, without any additional sub-folders
        return "/" + remoteFileName;
    }

    @WorkerThread
    private SSyncResult dbxDownload(final DbxClientV2 dbxClient) {
        publishProgress("Download from Dropbox...");
        final SSyncResult res = new SSyncResult(ResultCode.DOWNLOAD_SUCCESS);
        try {
            final File tmpDownloadFile = File.createTempFile("stream2file", ".tmp");
            res.setTmpDownloadFile(tmpDownloadFile);
            tmpDownloadFile.deleteOnExit();
            final OutputStream os = new FileOutputStream(tmpDownloadFile);

            final DbxDownloader<FileMetadata> downloader = dbxClient.files().download(getDbxPath());
            downloader.download(os);
            downloader.close();
            return res;
        } catch (Exception e) {
            Log.e(TAG, "Failed to download", e);
            return new SSyncResult(ResultCode.CONNECTION_FAILURE);
        }
    }

    @WorkerThread
    private SSyncResult dbxUpload(final DbxClientV2 dbxClient) {
        publishProgress("Upload to Dropbox...");
        try {
            InputStream inputStream = new FileInputStream(localFile);
            dbxClient.files().uploadBuilder(getDbxPath())
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream);
            Log.d(TAG, "Upload succeeded");
            return new SSyncResult(ResultCode.UPLOAD_SUCCESS);
        } catch (DbxException e) {
            Log.e(TAG, "Upload failed", e);
        } catch (IOException e) {
            Log.e(TAG, "Upload failed", e);
        }
        return new SSyncResult(ResultCode.CONNECTION_FAILURE);
    }


    @Override
    @WorkerThread
    protected @NonNull
    SSyncResult doInBackground(Object[] params) {

        final String oauthToken = getOauthToken(context);
        if (oauthToken == null) {
            Log.d(TAG, "No oauthtoken found. Cannot instantiate DbxClientV2.");
            return new SSyncResult(ResultCode.NO_CREDENTIALS_FAILURE);
        }

        final DbxRequestConfig config = DbxRequestConfig.newBuilder("com.dropbox.core:dropbox-core-sdk").build();
        final DbxClientV2 dbxClient = new DbxClientV2(config, oauthToken);

        FileMetadata remoteMeta;
        try {
            remoteMeta = (FileMetadata)dbxClient.files().getMetadata(getDbxPath());
            Log.d(TAG, "Fetched metadata: " + remoteMeta);
        } catch (GetMetadataErrorException e) {
            remoteMeta = null; // This means that the remote file does not exist, but the connection to Dropbox worked.
        } catch (DbxException e) {
            Log.d(TAG, "Failed to fetch metadata. Probably due to an invalid token or missing Internet connectivity.", e);
            return new SSyncResult(ResultCode.CONNECTION_FAILURE); // Return immediately in case of a connectivity failure
        }

        final boolean localNonEmpty = localFileNonEmpty();

        if (!localNonEmpty && remoteMeta == null) {
            Log.d(TAG, "Connection succeeded, but neither local nor remote is non-empty");
            return new SSyncResult(ResultCode.FILES_NOT_EXIST_OR_EMPTY);
        }
        if (remoteMeta == null) {
            Log.d(TAG, "Only local exists, try to upload it");
            return dbxUpload(dbxClient);
        }
        if (!localNonEmpty) {
            Log.d(TAG, "Only remote is non-empty, try to download it");
            return dbxDownload(dbxClient);
        }

        final long serverModified = remoteMeta.getServerModified().getTime();
        final long clientModified = localFile.lastModified();
        if (timestampsEqualToSavedTimestamps(serverModified)) {
            Log.d(TAG, "Time stamps did not change since the last hash comparison");
            return new SSyncResult(ResultCode.REMOTE_EQUALS_LOCAL);
        }

        final String remoteHash = remoteMeta.getContentHash();
        final String localHash = FilesUtil.hashFromFile(localFile, new DropboxContentHasher());
        if (remoteHash.equalsIgnoreCase(localHash)) {
            Log.d(TAG, "Remote hash matches local hash, no need to upload or download");
            saveLastModifiedOfEqualFiles(serverModified);
            return new SSyncResult(ResultCode.REMOTE_EQUALS_LOCAL);
        }

        if (serverModified > clientModified) {
            Log.d(TAG, "Remote is newer than local, try to download it");
            return dbxDownload(dbxClient);
        } else {
            Log.d(TAG, "Local is newer than remote, try to upload it");
            return dbxUpload(dbxClient);
        }
    }

    private static @Nullable String getOauthToken(final Context cx) {
        final SharedPreferences prefs = cx.getSharedPreferences(DROPBOX_PREF_FILE, Context.MODE_PRIVATE);
        return prefs.getString(PREF_DROPBOX_ACCESS_TOKEN, null);
    }


    public static void storeNewOauthToken(@NonNull final String oauthToken, Context cx) {
        // This can potentially overwrite an old oauthtoken, which is fine.
        Log.d(TAG, "Retrieved a new oauthtoken");
        SharedPreferences prefs = cx.getSharedPreferences(DROPBOX_PREF_FILE, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_DROPBOX_ACCESS_TOKEN, oauthToken).apply();

        // This ensures that we run this code only once for each new oauth token
        AuthActivity.result = null;
        setCurrentSyncBackend(cx, DropboxFileSync.class);
    }


    public static void onResumeFetchOAuthToken(final Context cx) {

        // Try to fetch a newly retrieved oauthtoken.
        final String oauthToken = Auth.getOAuth2Token();

        if (oauthToken != null) {
            storeNewOauthToken(oauthToken, cx);
        }
    }

    public static void launchInitialOauthActivity(final Context cx) {
        Log.d(TAG, "Initiate Dropbox oauth flow");
        // Here we should use getApplicationContext() to prevent the Dropbox core client from keeping a static activity reference.
        // However, the application context led to a crash in certain cases where a failure dialog is shown by the Dropbox core client.
        Auth.startOAuth2Authentication(cx, cx.getString(R.string.dropbox_app_key));
    }
}

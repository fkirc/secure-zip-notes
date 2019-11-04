package com.ditronic.simplefilesync;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import com.ditronic.simplefilesync.util.FilesUtil;
import com.ditronic.simplefilesync.util.ResultCode;
import com.ditronic.simplefilesync.util.SSyncResult;

public class DriveFileSync extends AbstractFileSync {

	public DriveFileSync(Context cx, java.io.File localFile, String remoteFileName, SSyncCallback cb) {
		super(cx, localFile, remoteFileName, cb);
	}

	private Drive driveService;

	private static final String TAG = DriveFileSync.class.getName();
	public static final int REQUEST_CODE_GOOGLE_SIGN_IN = 45235;

	private boolean instantiateDriveService() {
		if (driveService != null) {
			return true; // Already present
		}
		final GoogleSignInAccount googleAccount = GoogleSignIn.getLastSignedInAccount(context);
		if (googleAccount == null) {
			Log.d(TAG, "Failed to instantiate Drive service - the user is not signed in to Google");
			return false;
		}
		Log.d(TAG, "Signed in as " + googleAccount.getEmail());
		// Use the authenticated account to sign in to the Drive service.
		GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
						context, Collections.singleton(DriveScopes.DRIVE_FILE));
		credential.setSelectedAccount(googleAccount.getAccount());
		driveService = new Drive.Builder(
				AndroidHttp.newCompatibleTransport(),
				new GsonFactory(),
				credential)
				.setApplicationName("Secure Zip Notes")
				.build();
		return true;
	}


	@WorkerThread
	private SSyncResult driveCreateNewRemote(final java.io.File localFile) {

		publishProgress("Upload to Google Drive...");

		File metadata = new File()
				.setParents(Collections.singletonList("root"))
				.setMimeType("application/zip")
				.setName(remoteFileName);

		FileContent localMediaContent = new FileContent("application/zip", localFile);
		try {
			final File googleFile = driveService.files().create(metadata, localMediaContent).execute();
			if (googleFile == null) {
				Log.d(TAG, "Null result when requesting file creation.");
				return new SSyncResult(ResultCode.CONNECTION_FAILURE);
			}
			Log.d(TAG, "Created file " + googleFile);
			return new SSyncResult(ResultCode.UPLOAD_SUCCESS);
		} catch (IOException e) {
			Log.e(TAG, "Failed to create file", e);
			return new SSyncResult(ResultCode.CONNECTION_FAILURE);
		}
	}


	@WorkerThread
	private SSyncResult driveDownload(final File remoteFile) {

		publishProgress("Download from Google Drive...");

		try {
			SSyncResult res = new SSyncResult(ResultCode.DOWNLOAD_SUCCESS);
			final java.io.File tmpDownloadFile = java.io.File.createTempFile("stream2file", ".tmp");
			res.setTmpDownloadFile(tmpDownloadFile);
			tmpDownloadFile.deleteOnExit();
			final OutputStream os = new FileOutputStream(tmpDownloadFile);
			driveService.files().get(remoteFile.getId()).executeMediaAndDownloadTo(os);
			os.close();
			return res;
		} catch (IOException e) {
			Log.e(TAG, "Download failed", e);
			return new SSyncResult(ResultCode.CONNECTION_FAILURE);
		}
	}

	@WorkerThread
	private SSyncResult driveUpload(final File remoteFile, final java.io.File localFile) {

		publishProgress("Upload to Google Drive...");

		try {
			final File metaData = new File().setName(remoteFileName);
			driveService.files().update(remoteFile.getId(), metaData, new FileContent("application/zip", localFile)).execute();
			Log.d(TAG, "Updated remote file " + remoteFile);
			return new SSyncResult(ResultCode.UPLOAD_SUCCESS);
		} catch (IOException e) {
			Log.e(TAG, "Failed to update remote file", e);
			return new SSyncResult(ResultCode.CONNECTION_FAILURE);
		}
	}

	@WorkerThread
	private @Nullable File retrieveMetaData(final File remoteFile) {
		try {
			return driveService.files().get(remoteFile.getId())
					.setFields("md5Checksum,size,modifiedTime,trashed").execute();
		} catch (IOException e) {
			Log.e(TAG, "Failed to retrieve metadata", e);
			return null;
		}
	}

	@WorkerThread
	private @Nullable File retrieveDriveFileByName() throws IOException {
		// We want to find a backup file with a specific name that is not located within the trash folder.
		// Other than not being in trash, we do not care about the folder location of the backup file.
		final String queryString = "name='" + remoteFileName + "'  and trashed=false";

		final FileList fileList = driveService.files().list().setQ(queryString).execute();

		Log.d(TAG, "Fetched fileList: " + fileList);
		File remoteFile = null;
		final List<File> files = fileList.getFiles();
		if (files.size() > 0) {
			remoteFile = files.get(0);
		}
		return remoteFile;
	}

	@Override
	@WorkerThread
	protected @NonNull SSyncResult doInBackground(Object[] params) {

		if (!instantiateDriveService()) {
			return new SSyncResult(ResultCode.NO_CREDENTIALS_FAILURE);
		}

		final File remoteFile;
		try {
			remoteFile = retrieveDriveFileByName();
		} catch (IOException e) {
			Log.d(TAG, "Failed to connect to Drive: " + e.getMessage());
			return new SSyncResult(ResultCode.CONNECTION_FAILURE);
		}
		final boolean localNonEmpty = localFileNonEmpty();

		if (!localNonEmpty && remoteFile == null) {
			Log.d(TAG, "Connection succeeded, but neither local nor remote is non-empty");
			return new SSyncResult(ResultCode.FILES_NOT_EXIST_OR_EMPTY);
		}
		if (remoteFile == null) {
			Log.d(TAG, "Only local exists, try to upload it");
			return driveCreateNewRemote(localFile);
		}
		if (!localNonEmpty) {
			Log.d(TAG, "Only remote is non-empty, try to download it");
			return driveDownload(remoteFile);
		}

		// We have to make an additional GET request to retrieve meta data for our file ID
		final File remoteMetaData = retrieveMetaData(remoteFile);
		if (remoteMetaData == null) {
			return new SSyncResult(ResultCode.CONNECTION_FAILURE);
		}

		final long serverModified = remoteMetaData.getModifiedTime().getValue();
		final long clientModified = localFile.lastModified();
		if (timestampsEqualToSavedTimestamps(serverModified)) {
			Log.d(TAG, "Time stamps did not change since the last hash comparison");
			return new SSyncResult(ResultCode.REMOTE_EQUALS_LOCAL);
		}

		final String remoteHash = remoteMetaData.getMd5Checksum();
		final String localHash = FilesUtil.hashFromFile(localFile, getDigestInstance("MD5"));
		if (remoteHash.equalsIgnoreCase(localHash)) {
			Log.d(TAG, "Remote hash matches local hash, no need to upload or download");
			saveLastModifiedOfEqualFiles(serverModified);
			return new SSyncResult(ResultCode.REMOTE_EQUALS_LOCAL);
		}

		if (serverModified > clientModified) {
			Log.d(TAG, "Remote is newer than local, try to download it");
			return driveDownload(remoteFile);
		} else {
			Log.d(TAG, "Local is newer than remote, try to upload it");
			return driveUpload(remoteFile, localFile);
		}
	}


	public static void onActivityResultOauthSignIn(final Activity ac, int requestCode, int resultCode, final Intent resultData,
												   final Runnable driveSignInSuccessCallback) {
		if (requestCode != REQUEST_CODE_GOOGLE_SIGN_IN) {
			return;
		}
		if (resultCode == Activity.RESULT_OK && resultData != null) {
			GoogleSignIn.getSignedInAccountFromIntent(resultData)
					.addOnSuccessListener(new OnSuccessListener<GoogleSignInAccount>() {
						@Override
						public void onSuccess(GoogleSignInAccount googleAccount) {
							Log.d(TAG, "Oauth flow completed successfully for " + googleAccount.getEmail());
							setCurrentSyncBackend(ac, DriveFileSync.class); // Must be set before running the callback
							driveSignInSuccessCallback.run();
						}
					})
					.addOnFailureListener(new OnFailureListener() {
						@Override
						public void onFailure(@NonNull Exception e) {
							Log.e(TAG, "Unable to sign in", e);
						}
					});
		} else {
			Log.d(TAG, "Oauth flow failed or rejected by the user");
		}
	}

	public static void launchInitialOauthActivity(final Activity ac) {
		Log.d(TAG, "Initiate Google oauth flow");

		GoogleSignInOptions signInOptions =
				new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
						.requestEmail()
						.requestScopes(new Scope(DriveScopes.DRIVE_FILE))
						.build();
		GoogleSignInClient client = GoogleSignIn.getClient(ac, signInOptions);

		ac.startActivityForResult(client.getSignInIntent(), REQUEST_CODE_GOOGLE_SIGN_IN);
	}
}

# Deprecation Notice

This library does not reflect good Android-practices.
Instead, I recommend to use Kotlin-Coroutines and ViewModels for file synchronization.
This helps to prevent numerous memory leaks and lifecycle bugs.
Or alternatively, I recommend to abandon native app-development and switch to cross-platform frameworks like https://capacitorjs.com/.

____

# Simple File Sync

``SimpleFileSync`` is an Android library that enables to easily synchronize individual files with Google Drive or Dropbox.

``SimpleFileSync`` provides synchronization in both directions, that is: It either uploads or downloads files.
The direction depends on the timestamps.
Moreover, ``SimpleFileSync`` compares a hash of the local file with the remote file to avoid unnecessary uploads or downloads.


``SimpleFileSync`` is a small wrapper on top of the following official client libraries:

- Dropbox Core SDK: `com.dropbox.core:dropbox-core-sdk`.
The Dropbox Core SDK is open-sourced at https://github.com/dropbox/dropbox-sdk-java.


- Play Services Drive library: `com.google.android.gms:play-services-drive`.
The Play Services Drive library is probably not open-source, but the API is documented at https://developers.google.com/drive/api/v3/.
Besides, there exists a demo app at https://github.com/gsuitedevs/android-samples/.

## Dropbox Integration
Integrating ``SimpleFileSync`` into your app involves the following steps:

1. Copy the `simplefilesync` folder into your project.
Then add a gradle dependency to your app-level `build.gradle`:
```Groovy
dependencies {
    ....
    implementation project(path: ':simplefilesync')
}
```
Furthermore, add `simplefilesync` to your root `settings.gradle`:
```Groovy
include ':app', ':simplefilesync'
```

2. Add the following to your `AndroidManifest.xml`:
```XML
<activity
            android:name="com.dropbox.core.android.AuthActivity"
            android:configChanges="orientation|keyboard"
            android:launchMode="singleTask">
            <intent-filter>
                <data android:scheme="@string/dropbox_app_key_db" />
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.BROWSABLE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
</activity>
```

3. Initiate the oauth2 flow at an appropriate place in your code, e.g. when the user clicks a button:
```Java
DropboxFileSync.launchInitialOauthActivity(context);
```

4. Insert the following call into the `onResume` of your activity:
```Java
DropboxFileSync.onResumeFetchOAuthToken(this);
```

5. To do the actual file synchronization, instantiate an instance of `DropboxFileSync` and execute it.
A good place for this might be the `onResume` of your activity:
```Java
new DropboxFileSync(this, myLocalFile, myRemoteFileName,
                    new AbstractFileSync.SSyncCallback() {
                        @Override
                        public void onSyncCompleted(SSyncResult res) {
                            // TODO: Check the result code and notify the user about success or failure.
                            // TODO: If the result is DOWNLOAD_SUCCESS, then you will probably need to copy the temporary download file to a permanent storage location.
                        }
                    }).execute();
```

## Dropbox Production Integration

For debugging purposes, this library includes an app key that is linked to a Dropbox sample app.
For a production app, you need to register a Dropbox app according to the Dropbox developer documentation: https://www.dropbox.com/developers/.
After registering your Dropbox app, add your app key to `strings.xml`. 
Replace the `MY_APP_KEY` entries with the app key from your Dropbox developer dashboard:
```XML
<string name="dropbox_app_key">MY_APP_KEY</string>
<string name="dropbox_app_key_db">db-MY_APP_KEY</string>
```

## Google Drive Integration

- Register an oauth2 app according to the Google Cloud developer documentation:
https://console.cloud.google.com/.
Among other things, you will need to specify the package name of your app
and the SHA-1 fingerprint of your app signing key store (providing different SHA-1 fingerprints for your debug key store and your production key store).
Unlike Dropbox, you do not get an app key that you can simply copy into your app.

- The code integration is mostly the same as for Dropbox, except that you use `DriveFileSync` instead of `DropboxFileSync`.
Unlike Dropbox, however, you need to call `DriveFileSync.onActivityResultOauthSignIn` from the `onActivityResult` of your Activity.

## Potential Issues

#### Optional Auto Backup Configuration
It might be a good idea to disable the Android Auto Backup feature.
This can be done by setting `android:allowBackup="false"` in your application `AndroidManifest.xml`.
Alternatively, you might disable Auto Backup for a selected folder where your local sync files are stored.
Otherwise, the Auto Backup might mess up the synchronization with Dropbox or Google Drive.


#### Build Failure about META_INF/DEPENDENCIES
If you get a message like `More than one file was found with OS independent path 'META-INF/DEPENDENCIES'`,
then you should be able to fix it by adding the following entry to your app-level `build.gradle`:
```
android {
    ....
    packagingOptions {
            exclude 'META-INF/DEPENDENCIES'
    }
}
```


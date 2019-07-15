package com.ditronic.securezipnotes;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.MenuItem;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import com.ditronic.simplefilesync.DriveFileSync;
import com.ditronic.simplefilesync.DropboxFileSync;
import com.ditronic.simplefilesync.util.FilesUtil;
import com.ditronic.securezipnotes.util.Boast;

public class MenuOptions {


    private static String getExportFileName() {
        final String dateString = new SimpleDateFormat("yyyy-MM-dd-HH:mm", Locale.getDefault()).format(Calendar.getInstance().getTimeInMillis());
        return dateString + "_securezipnotes.aeszip";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void exportZipFile(final Activity ac) {

        final File zipNotes = CryptoZip.getMainFilePath(ac);
        if (!zipNotes.exists()) {
            Boast.makeText(ac, "Notes empty, nothing to export").show();
            return;
        }

        final File tmpShareFile = new File(ac.getCacheDir(), getExportFileName());
        tmpShareFile.delete();
        tmpShareFile.deleteOnExit();
        try {
            FilesUtil.copyFile(zipNotes, tmpShareFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        final Uri shareUri = FileProvider.getUriForFile(ac, ac.getApplicationContext().getPackageName() + ".provider", tmpShareFile);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_STREAM, shareUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ac.startActivity(Intent.createChooser(intent, null)); // This provides a better menu than startActivity(intent)
    }

    public static boolean onOptionsSharedItemSelected(final MenuItem item, final Activity ac) {
        switch (item.getItemId()) {
            case android.R.id.home:
                ac.onBackPressed();
                return true;
            case R.id.action_export_zip_file:
                exportZipFile(ac);
                return true;
            case R.id.action_sync_dropbox:
                DropboxFileSync.launchInitialOauthActivity(ac);
                return true;
            case R.id.action_sync_drive:
                DriveFileSync.launchInitialOauthActivity(ac);
                return true;
            default:
                return false;
        }
    }
}

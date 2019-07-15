package com.ditronic.securezipnotes.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ditronic.securezipnotes.CryptoZip;
import com.ditronic.securezipnotes.MenuOptions;
import com.ditronic.securezipnotes.NotesImport;
import com.ditronic.securezipnotes.PwManager;
import com.ditronic.securezipnotes.R;
import com.ditronic.securezipnotes.adapters.NoteSelectAdapter;
import com.ditronic.securezipnotes.util.BannerAds;
import com.ditronic.securezipnotes.util.Boast;
import com.ditronic.securezipnotes.util.DeleteDialog;
import com.ditronic.securezipnotes.util.OnThrottleClickListener;
import com.ditronic.securezipnotes.util.OnThrottleItemClickListener;
import com.ditronic.simplefilesync.AbstractFileSync;
import com.ditronic.simplefilesync.DriveFileSync;
import com.ditronic.simplefilesync.DropboxFileSync;
import com.ditronic.simplefilesync.util.ResultCode;
import com.ditronic.simplefilesync.util.SSyncResult;

import net.lingala.zip4j.model.FileHeader;

import java.io.ByteArrayInputStream;

public class MainActivity extends AppCompatActivity {

    private static final String INTENT_NEW_NOTE = "intent_new_note";

    public static void launchCleanWithNewNote(final Context cx) {
        final Intent intent = new Intent(cx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(INTENT_NEW_NOTE, true);
        cx.startActivity(intent);
    }

    private NoteSelectAdapter noteSelectAdapter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
        toolbar.setLogo(R.mipmap.ic_launcher);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name_main_activity);
        }

        noteSelectAdapter = new NoteSelectAdapter(this);
        final ListView notesListView = findViewById(R.id.list_view_notes);
        notesListView.setAdapter(noteSelectAdapter);

        registerForContextMenu(notesListView);

        notesListView.setOnItemClickListener(new OnThrottleItemClickListener() {
            @Override
            public void onThrottleItemClick(AdapterView<?> parent, View view, int position, long id) {
                final FileHeader fileHeader = (FileHeader)noteSelectAdapter.getItem(position);
                PwManager.instance().retrievePasswordAsync(MainActivity.this, fileHeader, () -> NoteEditActivity.launch(MainActivity.this, fileHeader.getFileName()));
            }
        });
        notesListView.setEmptyView(findViewById(R.id.list_view_empty));

        findViewById(R.id.btn_create_new_note).setOnClickListener(new OnThrottleClickListener() {
            @Override
            public void onThrottleClick(View v) {
                btnNewNote();
            }
        });
        findViewById(R.id.btn_import_existing_notes).setOnClickListener(new OnThrottleClickListener() {
            @Override
            public void onThrottleClick(View v) {
                btnImportExistingNotes();
            }
        });
        findViewById(R.id.btn_sync_dropbox).setOnClickListener(new OnThrottleClickListener() {
            @Override
            public void onThrottleClick(View v) {
                DropboxFileSync.launchInitialOauthActivity(MainActivity.this);
            }
        });
        findViewById(R.id.btn_sync_drive).setOnClickListener(new OnThrottleClickListener() {
            @Override
            public void onThrottleClick(View v) {
                DriveFileSync.launchInitialOauthActivity(MainActivity.this);
            }
        });

        final Bundle bundle = getIntent().getExtras();
        if (bundle != null && bundle.getBoolean(INTENT_NEW_NOTE)) {
            btnNewNote();
        }

        BannerAds.loadBottomAdsBanner(this);
    }

    private void btnImportExistingNotes() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE_RES_CODE);
    }


    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_noteselect_longclick, menu);
    }

    private void askNoteDelete(final FileHeader fileHeader) {
        DeleteDialog.showDeleteQuestion("Delete " + CryptoZip.getDisplayName(fileHeader) + "?", this, new DeleteDialog.DialogActions() {
            @Override
            public void onPositiveClick() {
                CryptoZip.instance(MainActivity.this).removeFile(MainActivity.this, fileHeader);
                MainActivity.this.noteSelectAdapter.notifyDataSetChanged();
            }
            @Override
            public void onNegativeClick() {}
        });
    }


    private void renameFileDialog(final FileHeader fileHeader) {

        // Retrieving the password for renames should not be necessary, but this is the current implementation
        PwManager.instance().retrievePasswordAsync(MainActivity.this, fileHeader, () -> {
            final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Rename " + CryptoZip.getDisplayName(fileHeader));
            final EditText input = new EditText(MainActivity.this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setText(CryptoZip.getDisplayName(fileHeader));
            builder.setView(input);
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                final String newName = input.getText().toString();
                if (newName.isEmpty()) {
                    Boast.makeText(MainActivity.this, "Name must not be empty").show();
                    return;
                }

                CryptoZip.instance(MainActivity.this).renameFile(fileHeader, newName);
                MainActivity.this.noteSelectAdapter.notifyDataSetChanged();
            });

            builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
            final AlertDialog dialog = builder.create();
            final Window window = dialog.getWindow();
            if (window != null) {
                // Show keyboard automatically
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            dialog.show();
            input.requestFocus();
        });

    }


    @Override
    public boolean onContextItemSelected(final MenuItem item){
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        final FileHeader fileHeader = (FileHeader)noteSelectAdapter.getItem(info.position);
        switch (item.getItemId()) {
            case R.id.long_click_delete:
                askNoteDelete(fileHeader);
                return true;
            case R.id.long_click_rename:
                renameFileDialog(fileHeader);
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_noteselect, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {

        final MenuItem menuSyncDropbox = menu.findItem(R.id.action_sync_dropbox);
        final MenuItem menuSyncDrive = menu.findItem(R.id.action_sync_drive);
        menuSyncDropbox.setCheckable(false);
        menuSyncDrive.setCheckable(false);

        final String syncBackend = AbstractFileSync.getCurrentSyncBackend(this);
        if (syncBackend != null && syncBackend.equals(DropboxFileSync.class.getSimpleName())) {
            menuSyncDropbox.setCheckable(true).setChecked(true);
        } else if (syncBackend != null && syncBackend.equals(DriveFileSync.class.getSimpleName())) {
            menuSyncDrive.setCheckable(true).setChecked(true);
        }
        return true;
    }


    private static final int REQUEST_CODE_IMPORT_FILE_RES_CODE = 1;

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        DriveFileSync.onActivityResultOauthSignIn(this, requestCode, resultCode, data,
                this::initiateFileSync);

        if (requestCode == REQUEST_CODE_IMPORT_FILE_RES_CODE && resultCode == RESULT_OK) {
            final Uri importUri = data.getData();
            NotesImport.importFromUri(this, importUri);
            noteSelectAdapter.notifyDataSetChanged();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        noteSelectAdapter.notifyDataSetChanged();

        // This has to be called before the actual Dropbox sync
        DropboxFileSync.onResumeFetchOAuthToken(this);

        initiateFileSync();
    }

    private void initiateFileSync() {
        final java.io.File localFile = CryptoZip.getMainFilePath(this);
        final String REMOTE_BACKUP_FILE_NAME = "autosync_securezipnotes.aeszip";

        final String syncBackend = AbstractFileSync.getCurrentSyncBackend(this);
        if (syncBackend == null) {
            return;
        }

        if (syncBackend.equals(DropboxFileSync.class.getSimpleName())) {
            new DropboxFileSync(this, localFile, REMOTE_BACKUP_FILE_NAME,
                    res -> MainActivity.this.onSyncCompleted(res, "Dropbox")).execute();
        } else if (syncBackend.equals(DriveFileSync.class.getSimpleName())) {
            new DriveFileSync(this, localFile, REMOTE_BACKUP_FILE_NAME,
                    res -> MainActivity.this.onSyncCompleted(res, "Google Drive")).execute();
        }
    }

    private void onSyncCompleted(final SSyncResult res, final String cloudBackend) {

        if (res.getResultCode() == ResultCode.CONNECTION_FAILURE) {
            Boast.makeText(MainActivity.this, "Failed to connect to " + cloudBackend, Toast.LENGTH_LONG).show();
        } else if (res.getResultCode() == ResultCode.DOWNLOAD_SUCCESS) {
            NotesImport.importFromFile(MainActivity.this, res.getTmpDownloadFile(), "Downloaded Zip notes from " + cloudBackend);
            noteSelectAdapter.notifyDataSetChanged();
        } else if (res.getResultCode() == ResultCode.FILES_NOT_EXIST_OR_EMPTY
                && res.isSyncTriggeredByUser()) {
            // Special case for new users that click "Dropbox sync" without having any data.
            Boast.makeText(MainActivity.this, "Could not find a " + cloudBackend + " backup - Creating new Zip file...", Toast.LENGTH_LONG).show();
            btnNewNote();
        } else if (res.getResultCode() == ResultCode.REMOTE_EQUALS_LOCAL
                && res.isSyncTriggeredByUser()) {
            // Give the user some feedback if he started this sync explicitly
            // since REMOTE_EQUALS_LOCAL did not show the ProgressDialog on its own.
            Boast.makeText(MainActivity.this, cloudBackend + " synchronized").show();
        }
    }

    private void createNewNote() {
        final String displayName = "Note " + (1 + CryptoZip.instance(this).getNumFileHeaders());
        final String innerFileName = CryptoZip.instance(MainActivity.this).addStream(displayName, new ByteArrayInputStream(new byte[0]));
        noteSelectAdapter.notifyDataSetChanged();
        NoteEditActivity.launch(MainActivity.this, innerFileName);
    }

    private void btnNewNote() {

        if (CryptoZip.instance(this).getNumFileHeaders() == 0) {
            if (PwManager.instance().getPasswordFast() == null) {
                final Intent intent = new Intent(this, NewPasswordActivity.class);
                startActivity(intent);
            } else {
                createNewNote();
            }
        } else {
            final FileHeader fileHeader = CryptoZip.instance(this).getFileHeadersFast().get(0); // We use this to ensure password consistency accross the zip file
            PwManager.instance().retrievePasswordAsync(this, fileHeader, this::createNewNote);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        if (MenuOptions.onOptionsSharedItemSelected(item, this)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.action_add_note:
                btnNewNote();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}

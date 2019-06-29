package com.ditronic.securezipnotes.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ditronic.securezipnotes.CryptoZip;
import com.ditronic.securezipnotes.R;
import com.ditronic.securezipnotes.util.BannerAds;
import com.ditronic.securezipnotes.util.Boast;
import com.ditronic.securezipnotes.zip4j.model.FileHeader;

public class NoteEditActivity extends AppCompatActivity {

    private static final String INNER_FILE_NAME = "inner_file_name";

    public static void launch(final Context cx, final String innerFileName) {
        final Intent intent = new Intent(cx, NoteEditActivity.class);
        intent.putExtra(INNER_FILE_NAME, innerFileName);
        cx.startActivity(intent);
    }

    private boolean editMode;
    private EditText editTextMain;
    private EditText editTextTitle;
    private String secretContent;
    private String innerFileName;

    private static final int MIN_API_COPY_READ_ONLY = 23;

    void applyEditMode(final boolean enable) {
        editMode = enable;

        // Rather simple procedure for title edit text
        editTextTitle.setCursorVisible(editMode);
        editTextTitle.setClickable(editMode);
        editTextTitle.setFocusable(editMode);
        editTextTitle.setLongClickable(editMode);
        editTextTitle.setTextIsSelectable(editMode);
        editTextTitle.setLongClickable(editMode);


        // Complicated procedure for the main edit text
        if (Build.VERSION.SDK_INT >= MIN_API_COPY_READ_ONLY) { // 21
            editTextMain.setShowSoftInputOnFocus(editMode);
            editTextMain.setCursorVisible(editMode);
        } else {
            editTextMain.setCursorVisible(editMode);
            editTextMain.setClickable(editMode);
            editTextMain.setFocusable(editMode);
            editTextMain.setLongClickable(editMode);
            editTextMain.setTextIsSelectable(editMode);
            editTextMain.setLongClickable(editMode);
        }

        if (!editMode) {
            editTextMain.setCustomSelectionActionModeCallback(new CustomSelectionActionModeCallback());
            if (Build.VERSION.SDK_INT >= MIN_API_COPY_READ_ONLY) { // 23
                editTextMain.setCustomInsertionActionModeCallback(new CustomInsertionActionModeCallback());
            }
            // Close keyboard
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editTextMain.getWindowToken(), 0);
        } else {
            editTextMain.setCustomSelectionActionModeCallback(null);
            if (Build.VERSION.SDK_INT >= MIN_API_COPY_READ_ONLY) { // 23
                editTextMain.setCustomInsertionActionModeCallback(null);
            }
            // Open keyboard
            editTextMain.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(editTextMain, InputMethodManager.SHOW_IMPLICIT);
        }

        invalidateOptionsMenu();
    }

    private static class CustomSelectionActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return true;
        }
        @Override
        public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
            try {
                MenuItem copyItem = menu.findItem(android.R.id.copy);
                CharSequence title = copyItem.getTitle();
                menu.clear(); // We only want copy functionality, no paste, no cut.
                menu.add(0, android.R.id.copy, 0, title);
            }
            catch (Exception e) {
            }
            return true;
        }
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }
        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    }

    private static class CustomInsertionActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return false;
        }
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }
        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    }

    FileHeader getFileHeader() {
        return CryptoZip.instance(this).getFileHeader(innerFileName);
    }

    void saveContent() {
        if (!editMode) {
            return;
        }
        final String newContent = editTextMain.getText().toString();
        String newFileName = editTextTitle.getText().toString();
        if (newContent.equals(secretContent) &&
                newFileName.equals(getFileHeader().getDisplayName())) {
            return; // Nothing to save, text unchanged
        }
        if (newFileName.isEmpty()) {
            // Silently keep old file name if this is empty
            newFileName = getFileHeader().getDisplayName();
            editTextTitle.setText(getFileHeader().getDisplayName());
        }
        secretContent = newContent;
        innerFileName = CryptoZip.instance(this).updateStream(getFileHeader(), newFileName, secretContent);
        Boast.makeText(this, "Saved " + getFileHeader().getDisplayName()).show();
    }

    void saveClick() {
        saveContent();
        applyEditMode(false);
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(INNER_FILE_NAME, innerFileName);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);
        final Toolbar toolbar = findViewById(R.id.tool_bar_edit);
        setSupportActionBar(toolbar);

        editTextTitle = findViewById(R.id.edit_text_title);
        editTextMain = findViewById(R.id.edit_text_main);

        if (savedInstanceState != null) {
            innerFileName = savedInstanceState.getString(INNER_FILE_NAME);
        } else {
            innerFileName = getIntent().getExtras().getString(INNER_FILE_NAME);
        }

        final FileHeader fileHeader = CryptoZip.instance(this).getFileHeader(innerFileName);

        if (getSupportActionBar() != null) { // add back arrow to toolbar
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // We do not want a "title" since the EditText consumes all the space
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        secretContent = CryptoZip.instance(this).extractFileString(fileHeader);
        if (secretContent == null) {
            finish(); // Should almost never happen
            return;
        }
        applyEditMode(secretContent.isEmpty());
        editTextTitle.setText(fileHeader.getDisplayName());
        editTextMain.setText(secretContent);

        // Required to make links clickable
        //editTextMain.setMovementMethod(LinkMovementMethod.getInstance());

        BannerAds.loadBottomAdsBanner(this);
    }

    @Override
    public boolean onPrepareOptionsMenu (final Menu menu) {
        menu.findItem(R.id.action_edit_note).setVisible(!editMode);
        menu.findItem(R.id.action_save_note).setVisible(editMode);
        return true;
    }
    
    @Override
    protected void onPause() {
        super.onPause ();
        // This needs to happen in onPause.
        // onStop is already too late because the onResume of the previous activity might be called before onStop.
        saveContent();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_noteedit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_edit_note:
                applyEditMode(true);
                return true;
            case R.id.action_save_note:
                saveClick();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}

package com.ditronic.securezipnotes.noteedit

import android.content.Context
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager


fun NoteEditActivity.applyEditMode(enable: Boolean) {
    editMode = enable

    // Rather simple procedure for title edit text
    editTextTitle.isCursorVisible = editMode
    editTextTitle.isClickable = editMode
    editTextTitle.isFocusable = editMode
    editTextTitle.isLongClickable = editMode
    editTextTitle.setTextIsSelectable(editMode)
    editTextTitle.isLongClickable = editMode


    // Complicated procedure for the main edit text
    if (Build.VERSION.SDK_INT >= NoteEditActivity.MIN_API_COPY_READ_ONLY) { // 21
        editTextMain.showSoftInputOnFocus = editMode
        editTextMain.isCursorVisible = editMode
    } else {
        editTextMain.isCursorVisible = editMode
        editTextMain.isClickable = editMode
        editTextMain.isFocusable = editMode
        editTextMain.isLongClickable = editMode
        editTextMain.setTextIsSelectable(editMode)
        editTextMain.isLongClickable = editMode
    }

    if (!editMode) {
        editTextMain.customSelectionActionModeCallback = CustomSelectionActionModeCallback()
        if (Build.VERSION.SDK_INT >= NoteEditActivity.MIN_API_COPY_READ_ONLY) { // 23
            editTextMain.customInsertionActionModeCallback = CustomInsertionActionModeCallback()
        }
        // Close keyboard
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editTextMain.windowToken, 0)
    } else {
        editTextMain.customSelectionActionModeCallback = null
        if (Build.VERSION.SDK_INT >= NoteEditActivity.MIN_API_COPY_READ_ONLY) { // 23
            editTextMain.customInsertionActionModeCallback = null
        }
        // Open keyboard
        editTextMain.requestFocus()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editTextMain, InputMethodManager.SHOW_IMPLICIT)
    }

    invalidateOptionsMenu()
}


internal class CustomSelectionActionModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        try {
            val copyItem = menu.findItem(android.R.id.copy)
            val title = copyItem.title
            menu.clear() // We only want copy functionality, no paste, no cut.
            menu.add(0, android.R.id.copy, 0, title)
        } catch (ignored: Exception) {
        }

        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}

internal class CustomInsertionActionModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}

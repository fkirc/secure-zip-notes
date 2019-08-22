package com.ditronic.securezipnotes.common

import android.text.InputType
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.ditronic.securezipnotes.testutils.clickBottomCenter // TODO: Move this to class or module (instead of individual imports)
import com.ditronic.securezipnotes.testutils.click_dialogOK
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.testutils.pressBack

// We follow Jake Wharton's "robot pattern", which is an excellent pattern for testing Android apps.

const val TESTPASSWORD = "testpassword"

// Main menu actions ----------------------------------------------------------------------

private fun main_typeMasterPassword(password: String = TESTPASSWORD) {
    Espresso.onView(ViewMatchers.withInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
            .inRoot(RootMatchers.isDialog()).perform(ViewActions.replaceText(password))
    click_dialogOK()
}

fun main_clickNote(noteName: String, password: String? = null) {
    Espresso.onData(matchesFileHeader(noteName))
            .inAdapterView(ViewMatchers.withId(R.id.list_view_notes))
            //.onChildView(ViewMatchers.withText(noteName))
            .perform(ViewActions.click())
    if (password != null) {
        main_typeMasterPassword(password)
    }
}

fun main_clickAssertCloseNote(noteName: String, secretContent: String, password: String? = null) {
    main_clickNote(noteName, password)
    noteEdit_assertState(noteTitle = noteName, secretContent = secretContent, editMode = secretContent.isEmpty())
    pressBack()
}

private fun main_longClickNote(entryName: String) {
    Espresso.onData(matchesFileHeader(entryName))
            .inAdapterView(ViewMatchers.withId(R.id.list_view_notes))
            .perform(ViewActions.longClick())
}

fun main_clickOptionsMenu(resourceId: Int) {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    Espresso.openActionBarOverflowOrOptionsMenu(targetContext)
    Espresso.onView(ViewMatchers.withText(resourceId)).perform(ViewActions.click())
}

fun main_addNewNote(typePassword: Boolean = false) {
    Espresso.onView(ViewMatchers.withId(R.id.action_add_note)).perform(ViewActions.click())
    if (typePassword) {
        main_typeMasterPassword()
    }
}

fun main_renameNote(oldName: String, newName: String, typePassword: Boolean = false) {
    main_longClickNote(oldName)
    Espresso.onView(ViewMatchers.withText("Rename")).perform(ViewActions.click())
    if (typePassword) {
        main_typeMasterPassword()
    }
    Espresso.onView(ViewMatchers.withText(oldName)).inRoot(RootMatchers.isDialog()).perform(ViewActions.replaceText(newName))
    Espresso.onView(ViewMatchers.withText("OK")).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
}

fun main_deleteNote(entryName: String) {
    main_longClickNote(entryName)
    Espresso.onView(ViewMatchers.withText("Delete")).perform(ViewActions.click())
    Espresso.onView(ViewMatchers.withText("OK")).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
}


// Initial setup actions ----------------------------------------------------------------------

fun init_onViewPassword() : ViewInteraction {
    return Espresso.onView(ViewMatchers.withId(R.id.input_password))
}

fun init_createNewZipFile() {
    Espresso.onView(ViewMatchers.withId(R.id.btn_create_new_note)).perform(ViewActions.click())
}

fun init_importExistingNotes() {
    Espresso.onView(ViewMatchers.withId(R.id.btn_import_existing_notes)).perform(ViewActions.click())
}

fun init_typeNewPassword(newPw: String) {
    init_onViewPassword().perform(ViewActions.replaceText(newPw))
    init_chooseNewPassword()
}

fun init_chooseNewPassword() {
    Espresso.onView(ViewMatchers.withId(R.id.btn_next)).perform(clickBottomCenter())
}

fun init_genRandomPassword() {
    Espresso.onView(ViewMatchers.withId(R.id.btn_generate_master_password)).perform(ViewActions.click())
}

fun init_confirmNewPassword(newPw: String) {
    // PasswordConfirmActivity
    Espresso.onView(ViewMatchers.withId(R.id.input_password_confirm)).perform(ViewActions.replaceText(newPw))
    Espresso.onView(ViewMatchers.withId(R.id.btn_confirm_master_password)).perform(clickBottomCenter())
}


// Note edit actions -----------------------------------------------------------------------

fun noteEdit_typeText(noteText: String) {
    Espresso.onView(ViewMatchers.withId(R.id.edit_text_main)).perform(ViewActions.typeText(noteText), ViewActions.closeSoftKeyboard())
}

fun noteEdit_rename(oldName: String, newName: String, editMode: Boolean = false) {
    Espresso.onView(ViewMatchers.withId(R.id.edit_text_title)).check(ViewAssertions.matches(ViewMatchers.withText(oldName)))
    if (!editMode) {
        Espresso.onView(ViewMatchers.withId(R.id.action_edit_note)).perform(ViewActions.click())
    }
    Espresso.onView(ViewMatchers.withId(R.id.edit_text_title))
            .perform(ViewActions.click(),
                    ViewActions.clearText(),
                    ViewActions.typeText(newName))
    Espresso.onView(ViewMatchers.withId(R.id.action_save_note)).perform(ViewActions.click())
}

package com.ditronic.securezipnotes.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ditronic.securezipnotes.noteedit.NoteEditViewModel

@Suppress("UNCHECKED_CAST")
class ViewModelFactory(private val environment: AppEnvironment) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(NoteEditViewModel::class.java) -> NoteEditViewModel(
                appContext = environment.context
            ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel - Maybe forgot to add in ViewModelFactory?")
        }
    }
}

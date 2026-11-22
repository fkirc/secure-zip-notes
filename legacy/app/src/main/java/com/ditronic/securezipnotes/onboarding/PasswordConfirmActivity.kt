package com.ditronic.securezipnotes.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.ditronic.securezipnotes.databinding.ActivityPasswordConfirmBinding
import com.ditronic.securezipnotes.noteselect.MainActivity
import com.ditronic.securezipnotes.password.PwManager
import com.ditronic.securezipnotes.password.PwResult
import com.ditronic.securezipnotes.util.OnThrottleClickListener

class PasswordConfirmActivity : AppCompatActivity() {

    private lateinit var password: String
    private lateinit var binding: ActivityPasswordConfirmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        password = intent.extras!!.getString(INTENT_PASSWORD)!!

        val toolbar = binding.toolBar
        setSupportActionBar(toolbar)

        binding.btnConfirmMasterPassword.setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                savePassword()
            }
        })

        binding.inputPasswordConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                savePassword()
                return@setOnEditorActionListener true
            }
            false
        }

        // add back arrow to toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "Confirm Master Password"

        val window = window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.inputPasswordConfirm.requestFocus()
    }

    private fun savePassword() {

        val confirmedPassword = binding.inputPasswordConfirm.text.toString()
        if (confirmedPassword != password) {
            binding.inputPasswordConfirm.error = "Passwords do not match"
            return
        }
        binding.inputPasswordConfirm.error = null

        PwManager.saveUserProvidedPassword(this, PwResult.Success(password = confirmedPassword, inputStream = null)) {
            MainActivity.launchCleanWithNewNote(this)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {

        private const val INTENT_PASSWORD = "intent_password"

        fun launch(cx: Context, password: String) {
            val intent = Intent(cx, PasswordConfirmActivity::class.java)
            intent.putExtra(INTENT_PASSWORD, password)
            cx.startActivity(intent)
        }
    }
}

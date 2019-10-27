package com.ditronic.securezipnotes.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.ditronic.securezipnotes.password.PwManager
import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.noteselect.MainActivity
import com.ditronic.securezipnotes.util.OnThrottleClickListener
import java.util.*

class PasswordConfirmActivity : AppCompatActivity() {

    private var password: String? = null
    private var confirmPasswordText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_confirm)
        val toolbar = findViewById<Toolbar>(R.id.tool_bar)
        setSupportActionBar(toolbar)

        password = Objects.requireNonNull(intent.extras).getString(INTENT_PASSWORD)
        confirmPasswordText = findViewById(R.id.input_password_confirm)

        findViewById<View>(R.id.btn_confirm_master_password).setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                savePassword()
            }
        })

        confirmPasswordText!!.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                savePassword()
                return@setOnEditorActionListener true
            }
            false
        }

        if (supportActionBar != null) { // add back arrow to toolbar
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
            supportActionBar!!.title = "Confirm Master Password"
        }

        val window = window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        confirmPasswordText!!.requestFocus()
    }

    private fun savePassword() {

        val confirmedPassword = confirmPasswordText!!.text.toString()
        if (confirmedPassword != password) {
            confirmPasswordText!!.error = "Passwords do not match"
            return
        }
        confirmPasswordText!!.error = null

        PwManager.instance().saveUserProvidedPassword(this, password = confirmedPassword, zipStream = null) {
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

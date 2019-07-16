package com.ditronic.securezipnotes.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

import com.ditronic.securezipnotes.R
import com.ditronic.securezipnotes.util.OnThrottleClickListener

import java.security.SecureRandom


class NewPasswordActivity : AppCompatActivity() {

    private var passwordText: EditText? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_password)
        val toolbar = findViewById<Toolbar>(R.id.tool_bar)
        setSupportActionBar(toolbar)

        passwordText = findViewById(R.id.input_password)

        passwordText!!.setText(generatePassword())

        findViewById<View>(R.id.btn_generate_master_password).setOnClickListener { v -> passwordText!!.setText(generatePassword()) }

        findViewById<View>(R.id.btn_next).setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                btnNext()
            }
        })

        passwordText!!.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnNext()
                return@setOnEditorActionListener true
            }
            false
        }

        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
            supportActionBar!!.setTitle("New Master Password")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun btnNext() {

        val password = passwordText!!.text.toString()

        if (password.isEmpty() || password.length < MIN_PW_LEN) {
            passwordText!!.error = "Minimum length: $MIN_PW_LEN characters"
            return
        }
        passwordText!!.error = null

        PasswordConfirmActivity.launch(this, password)
    }

    companion object {
        private val TAG = NewPasswordActivity::class.java.name

        private val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

        private val DEFAULT_PW_LEN = 20 // This is sufficient against offline attacks
        private val MIN_PW_LEN = 8 // This is too short, but we won't hinder the user from shooting themselves

        private fun generatePassword(): String {
            val length = DEFAULT_PW_LEN
            val secureRandom = SecureRandom()
            val stringBuilder = StringBuilder(length)
            for (i in 0 until length) {
                stringBuilder.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
            }
            return stringBuilder.toString()
        }
    }
}

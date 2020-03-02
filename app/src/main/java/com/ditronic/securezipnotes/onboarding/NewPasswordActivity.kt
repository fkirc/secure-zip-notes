package com.ditronic.securezipnotes.onboarding

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.ditronic.securezipnotes.databinding.ActivityNewPasswordBinding
import com.ditronic.securezipnotes.util.OnThrottleClickListener
import java.security.SecureRandom


class NewPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewPasswordBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.toolBar
        setSupportActionBar(toolbar)

        binding.inputPassword.setText(generatePassword())

        binding.btnGenerateMasterPassword.setOnClickListener { binding.inputPassword.setText(generatePassword()) }

        binding.btnNext.setOnClickListener(object : OnThrottleClickListener() {
            public override fun onThrottleClick(v: View) {
                btnNext()
            }
        })

        binding.inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnNext()
                true
            } else {
                false
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = "New Master Password"
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

    private fun btnNext() {

        val password = binding.inputPassword.text.toString()

        if (password.isEmpty() || password.length < MIN_PW_LEN) {
            binding.inputPassword.error = "Minimum length: $MIN_PW_LEN characters"
            return
        }
        binding.inputPassword.error = null
        PasswordConfirmActivity.launch(this, password)
    }

    companion object {
        private val TAG = NewPasswordActivity::class.java.name

        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

        private const val DEFAULT_PW_LEN = 20 // This is sufficient against offline attacks
        private const val MIN_PW_LEN = 8 // This is too short, but we won't hinder the user from shooting themselves

        private fun generatePassword(): String {
            val length = DEFAULT_PW_LEN
            val secureRandom = SecureRandom()
            val stringBuilder = StringBuilder(length)
            repeat(times = length) {
                stringBuilder.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)])
            }
            return stringBuilder.toString()
        }
    }
}

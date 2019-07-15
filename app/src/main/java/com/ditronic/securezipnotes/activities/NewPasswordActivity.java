package com.ditronic.securezipnotes.activities;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ditronic.securezipnotes.R;
import com.ditronic.securezipnotes.util.OnThrottleClickListener;

import java.security.SecureRandom;


public class NewPasswordActivity extends AppCompatActivity {
    private static final String TAG = NewPasswordActivity.class.getName();

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int DEFAULT_PW_LEN = 20; // This is sufficient against offline attacks
    private static final int MIN_PW_LEN = 8; // This is too short, but we won't hinder the user from shooting themselves

    private static String generatePassword() {
        final int length = DEFAULT_PW_LEN;
        final SecureRandom secureRandom = new SecureRandom();
        StringBuilder stringBuilder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            stringBuilder.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return stringBuilder.toString();
    }

    private EditText passwordText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_password);
        final Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);

        passwordText = findViewById(R.id.input_password);

        passwordText.setText(generatePassword());

        findViewById(R.id.btn_generate_master_password).setOnClickListener(v -> passwordText.setText(generatePassword()));

        findViewById(R.id.btn_next).setOnClickListener(new OnThrottleClickListener() {
            @Override
            public void onThrottleClick(View v) {
                btnNext();
            }
        });

        passwordText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                btnNext();
                return true;
            }
            return false;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("New Master Password");
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void btnNext() {

        final String password = passwordText.getText().toString();

        if (password.isEmpty() || password.length() < MIN_PW_LEN) {
            passwordText.setError("Minimum length: " + MIN_PW_LEN + " characters");
            return;
        }
        passwordText.setError(null);

        PasswordConfirmActivity.launch(this, password);
    }
}

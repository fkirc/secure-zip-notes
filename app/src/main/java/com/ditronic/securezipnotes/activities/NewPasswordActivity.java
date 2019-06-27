package com.ditronic.securezipnotes.activities;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.security.SecureRandom;

import com.ditronic.securezipnotes.R;
import com.ditronic.securezipnotes.util.OnThrottleClickListener;


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

        passwordText = findViewById(R.id.input_password);

        passwordText.setText(generatePassword());

        findViewById(R.id.btn_generate_master_password).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passwordText.setText(generatePassword());
            }
        });

        findViewById(R.id.btn_next).setOnClickListener(new OnThrottleClickListener() {
            @Override
            public void onThrottleClick(View v) {
                btnNext();
            }
        });

        passwordText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    btnNext();
                    return true;
                }
                return false;
            }
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

    void btnNext() {

        final String password = passwordText.getText().toString();

        if (password.isEmpty() || password.length() < MIN_PW_LEN) {
            passwordText.setError("Minimum length: " + MIN_PW_LEN + " characters");
            return;
        }
        passwordText.setError(null);

        PasswordConfirmActivity.launch(this, password);
    }
}

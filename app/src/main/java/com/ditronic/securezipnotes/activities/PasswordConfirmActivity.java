package com.ditronic.securezipnotes.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ditronic.securezipnotes.PwManager;
import com.ditronic.securezipnotes.R;
import com.ditronic.securezipnotes.util.Boast;
import com.ditronic.securezipnotes.util.OnThrottleClickListener;

public class PasswordConfirmActivity extends AppCompatActivity {

    private static final String INTENT_PASSWORD = "intent_password";

    public static void launch(final Context cx, final String password) {
        final Intent intent = new Intent(cx, PasswordConfirmActivity.class);
        intent.putExtra(INTENT_PASSWORD, password);
        cx.startActivity(intent);
    }

    private String password;
    private EditText confirmPasswordText;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_confirm);
        final Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);

        password = getIntent().getExtras().getString(INTENT_PASSWORD);
        confirmPasswordText = findViewById(R.id.input_password_confirm);

        findViewById(R.id.btn_confirm_master_password).setOnClickListener(new OnThrottleClickListener() {
            @Override
            public void onThrottleClick(View v) {
                savePassword();
            }
        });

        confirmPasswordText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    savePassword();
                    return true;
                }
                return false;
            }
        });

        if (getSupportActionBar() != null) { // add back arrow to toolbar
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Confirm Master Password");
        }

        final Window window = getWindow();
        if (window != null) {
            // Show keyboard automatically
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        confirmPasswordText.requestFocus();
    }

    void savePassword() {

        final String confirmedPassword = confirmPasswordText.getText().toString();
        if (!confirmedPassword.equals(password)) {
            confirmPasswordText.setError("Passwords do not match");
            return;
        }
        confirmPasswordText.setError(null);

        PwManager.instance().saveUserProvidedPassword(this, confirmedPassword);
        Boast.makeText(this, "Password configured successfully", Toast.LENGTH_LONG).show();
        MainActivity.launchCleanWithNewNote(this); // TODO: Fix the return to MainActivity, API level 29
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
}

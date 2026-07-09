package com.wall.mob;

import android.content.Context;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.appbar.MaterialToolbar;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;

public class ForgetActivity extends BaseActivity {

    private EditText emailInput;
    private Button sendResetButton;
    private TextView backToLoginText;
private MaterialToolbar toolbar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.forget);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ThemeUtils.applySystemBars(this);
        }

        mAuth = FirebaseAuth.getInstance();

        initViews();
        setClickListeners();
    }

    private void initViews() {
    toolbar = findViewById(R.id.toolbar);
    emailInput = findViewById(R.id.emailInput);
    sendResetButton = findViewById(R.id.sendResetButton);
    backToLoginText = findViewById(R.id.backToLoginText);
}

    private void setClickListeners() {
        sendResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPasswordReset();
            }
        });

        backToLoginText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        toolbar.setNavigationOnClickListener(
        v -> getOnBackPressedDispatcher().onBackPressed()
);

       
    }

    private void sendPasswordReset() {
        String email = emailInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError(getString(R.string.email_required));
            emailInput.requestFocus();
            return;
        }

        if (!isValidEmail(email)) {
            emailInput.setError(getString(R.string.email_invalid));
            emailInput.requestFocus();
            return;
        }

        sendResetButton.setEnabled(false);
        sendResetButton.setText(getString(R.string.sending));

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(ForgetActivity.this, getString(R.string.password_reset_sent, email), Toast.LENGTH_LONG).show();
                            simulateEmailSent(email);
                        } else {
                            emailInput.setError(getString(R.string.email_not_found));
                            emailInput.requestFocus();
                            sendResetButton.setEnabled(true);
                            sendResetButton.setText(getString(R.string.send_reset_link));
                        }
                    }
                });
    }

    private void simulateEmailSent(String email) {
        sendResetButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendResetButton.setEnabled(true);
                sendResetButton.setText(getString(R.string.send_reset_link));
                emailInput.setText("");
            }
        }, 3000);
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
// test

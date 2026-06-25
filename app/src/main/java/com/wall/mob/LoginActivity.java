package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends BaseActivity {


    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button guestButton;
    private TextView forgotPasswordText;
    private TextView registerText;
    private ProgressBar progressBar;
    private View progressOverlay;

    // Firebase Database reference
    private DatabaseReference databaseReference;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Install the Splash Screen MUST be called before super.onCreate()
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        // 2. Initialize SessionManager early
        sessionManager = new SessionManager(this);

        // 3. Check if it's a genuine first-time install vs upgrade
        SharedPreferences prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE);
        if (!prefs.contains("isFirstRun")) {
            // Preference doesn't exist — this is either a fresh install or an upgrade.
            // Distinguish by checking for an existing session.
            if (!sessionManager.isLoggedIn()) {
                // No session + no pref = genuine first-time install → show onboarding
                startActivity(new Intent(this, WelcomeActivity.class));
                finish();
                return;
            }
            // Existing session exists = upgrading user → mark as non-first-run
            prefs.edit().putBoolean("isFirstRun", false).apply();
        }

        // 4. Check if user is already logged in BEFORE setting the content view
        if (sessionManager.isLoggedIn()) {
            // Keep the splash screen frozen on the screen while we transition to MainActivity
            splashScreen.setKeepOnScreenCondition(() -> true); 
            
            redirectToMainActivity();
            return; // Exit onCreate early so the Login UI never renders
        }

        // 4. If the user is NOT logged in, set the content view to show the login screen
        // The splash screen will automatically fade away revealing this layout
        setContentView(R.layout.login);

        ThemeUtils.applySystemBars(this);

        // Initialize Firebase Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseReference = database.getReference("users");

        initViews();
        setClickListeners();
    }

    private void initViews() {
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        guestButton = findViewById(R.id.guestButton);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        registerText = findViewById(R.id.registerText);
        progressBar = findViewById(R.id.loginProgressBar);
        progressOverlay = findViewById(R.id.loadingOverlay);

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (progressOverlay != null) {
            progressOverlay.setVisibility(View.GONE);
        }
    }

    private void setClickListeners() {
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { performLogin(); }
        });

        guestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { loginAsGuest(); }
        });

        forgotPasswordText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, ForgetActivity.class);
                startActivity(intent);
            }
        });

        registerText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
    }

    private void checkLoginStatus() {
        // Check if user is already logged in (handled in onCreate)
    }

    private void performLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Validate inputs
        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }

        if (!isValidEmail(email)) {
            emailInput.setError("Please enter a valid email");
            emailInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters");
            passwordInput.requestFocus();
            return;
        }

        // Show progress and disable UI
        showLoading(true);

        // Check credentials in Firebase
        databaseReference.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        // Hide progress regardless of outcome
                        showLoading(false);

                        if (dataSnapshot.exists()) {
                            for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                                User user = userSnapshot.getValue(User.class);
                                if (user != null && user.getPassword().equals(password)) {
                                    // Login successful
                                    Toast.makeText(LoginActivity.this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show();

                                    // Create login session
                                    sessionManager.createLoginSession(user.getEmail(), user.getFullName(), false);

                                    redirectToMainActivity();
                                    finish();
                                    return;
                                }
                            }
                            // Password doesn't match
                            Toast.makeText(LoginActivity.this, getString(R.string.invalid_email_password), Toast.LENGTH_SHORT).show();
                        } else {
                            // Email not found
                            Toast.makeText(LoginActivity.this, getString(R.string.invalid_email_password), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        // Hide progress and re-enable UI
                        showLoading(false);
                        Toast.makeText(LoginActivity.this, getString(R.string.database_error, databaseError.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loginAsGuest() {
        // Show a brief loading state for guest login
        showLoading(true);
        Toast.makeText(this, getString(R.string.welcome_guest), Toast.LENGTH_SHORT).show();

        // Create guest session
        sessionManager.createLoginSession("", "Guest", true);

        showLoading(false);
        redirectToMainActivity();
        finish();
    }

    private void redirectToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (progressOverlay != null) {
            progressOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        // Disable/enable inputs while loading
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        loginButton.setEnabled(!loading);
        guestButton.setEnabled(!loading);
        registerText.setEnabled(!loading);
        forgotPasswordText.setEnabled(!loading);
    }

    @Override
    protected void onResume() {
        super.onResume();
        emailInput.setText("");
        passwordInput.setText("");
        // Ensure loading UI is reset
        showLoading(false);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
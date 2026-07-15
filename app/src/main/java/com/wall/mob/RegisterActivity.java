package com.wall.mob;

import android.content.Context;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.Query;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;

// Remove the inner User class and import the new one
import com.wall.mob.User;

public class RegisterActivity extends BaseActivity {

    private EditText fullNameInput;
    private EditText emailInput;
    private EditText phoneInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private CheckBox termsCheckbox;
    private Button registerButton;
    private TextView loginText;
    private MaterialToolbar toolbar;
    // Firebase Database reference
    private DatabaseReference databaseReference;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);
        
        ThemeUtils.applySystemBars(this);

        // Initialize Firebase Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseReference = database.getReference("users");
        firebaseAuth = FirebaseAuth.getInstance();

        initViews();
        setClickListeners();
    }

    private void initViews() {
        fullNameInput = findViewById(R.id.fullNameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        termsCheckbox = findViewById(R.id.termsCheckbox);
        registerButton = findViewById(R.id.registerButton);
        loginText = findViewById(R.id.loginText);
        toolbar = findViewById(R.id.toolbar);
    }

    private void setClickListeners() {
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performRegistration();
            }
        });

        loginText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        toolbar.setNavigationOnClickListener(
        v -> getOnBackPressedDispatcher().onBackPressed()
);
    }

    private void performRegistration() {
        String fullName = fullNameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // Validate inputs (same as before)
        if (TextUtils.isEmpty(fullName)) {
            fullNameInput.setError("Full name is required");
            fullNameInput.requestFocus();
            return;
        }

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

        if (TextUtils.isEmpty(phone)) {
            phoneInput.setError("Phone number is required");
            phoneInput.requestFocus();
            return;
        }

        if (!isValidPhone(phone)) {
            phoneInput.setError("Please enter a valid phone number");
            phoneInput.requestFocus();
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

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordInput.setError("Please confirm your password");
            confirmPasswordInput.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords do not match");
            confirmPasswordInput.requestFocus();
            return;
        }

        if (!termsCheckbox.isChecked()) {
            Toast.makeText(this, getString(R.string.accept_terms_required), Toast.LENGTH_SHORT).show();
            return;
        }

        // Proceed with secure registration using FirebaseAuth only (do not store plaintext password in RTDB)
        registerUser(fullName, email, phone, password);
    }

    private void checkEmailExistsAndRegister(String email, String fullName, String phone, String password) {
        // Deprecated: we now rely on FirebaseAuth to detect existing emails; keep for legacy if needed
        registerUser(fullName, email, phone, password);
    }

    private void registerUser(String fullName, String email, String phone, String password) {
        showLoading(true);
        // Create user in FirebaseAuth. Do NOT store password in Realtime Database.
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(authTask -> {
                    showLoading(false);
                    if (authTask.isSuccessful()) {
                        String uid = authTask.getResult().getUser().getUid();
                        User profile = new User(uid, fullName, email, phone);
                        profile.setPhotoUrl("");
                        databaseReference.child(uid).setValue(profile)
                                .addOnCompleteListener(dbTask -> {
                                    Toast.makeText(RegisterActivity.this, getString(R.string.registration_successful), Toast.LENGTH_LONG).show();
                                    Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                    startActivity(intent);
                                    finish();
                                });
                    } else {
                        // Registration failed (email may already exist or invalid password)
                        Toast.makeText(RegisterActivity.this, getString(R.string.registration_failed, authTask.getException() != null ? authTask.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPhone(String phone) {
        return phone.matches("\\d{10}") || phone.matches("\\+\\d{10,15}");
    }

    @Override
    public void onBackPressed() {
        // Go back to previous activity instead of closing app
        super.onBackPressed();
        finish();
    }

}

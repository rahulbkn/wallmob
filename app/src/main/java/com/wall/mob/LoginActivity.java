package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import java.util.Arrays;

public class LoginActivity extends BaseActivity {


    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button guestButton;
    private TextView forgotPasswordText;
    private TextView registerText;
    private TextView appTitle;
    private TextView appSubtitle;
    private ProgressBar progressBar;
    private View progressOverlay;

    private Button googleLoginButton;
    private Button facebookLoginButton;

    private static final int RC_GOOGLE_SIGN_IN = 1001;

    private DatabaseReference databaseReference;
    private SessionManager sessionManager;

    private GoogleSignInClient googleSignInClient;
    private CallbackManager facebookCallbackManager;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);

        SharedPreferences prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE);
        if (!prefs.contains("isFirstRun")) {
            if (!sessionManager.isLoggedIn()) {
                startActivity(new Intent(this, WelcomeActivity.class));
                finish();
                return;
            }
            prefs.edit().putBoolean("isFirstRun", false).apply();
        }

        if (sessionManager.isLoggedIn()) {
            splashScreen.setKeepOnScreenCondition(() -> true);

            redirectToMainActivity();
            return;
        }

        setContentView(R.layout.login);

        ThemeUtils.applySystemBars(this);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseReference = database.getReference("users");
        firebaseAuth = FirebaseAuth.getInstance();

        initViews();
        setupGoogleButtonGradient();  
        setClickListeners();
        initGoogleSignIn();
        initFacebookLogin();
    }

    private void initViews() {
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        guestButton = findViewById(R.id.guestButton);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        registerText = findViewById(R.id.registerText);
        appTitle = findViewById(R.id.appTitle);
        appSubtitle = findViewById(R.id.appSubtitle);
        progressBar = findViewById(R.id.loginProgressBar);
        progressOverlay = findViewById(R.id.loadingOverlay);
        googleLoginButton = findViewById(R.id.googleLoginButton);
        facebookLoginButton = findViewById(R.id.facebookLoginButton);

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (progressOverlay != null) {
            progressOverlay.setVisibility(View.GONE);
        }
    }
    
    
private void setupGoogleButtonGradient() {
        int[] googleColors = new int[] {
            Color.parseColor("#FF4641"), // red
            Color.parseColor("#FFCC00"), // yellow
            Color.parseColor("#0EBC5F"), // green
            Color.parseColor("#3186FF")  // blue
        };

        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            googleColors
        );
        gradient.setCornerRadius(dpToPx(this, 27));

        googleLoginButton.setBackground(gradient);
    }

    public static int dpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }


    private void initGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void initFacebookLogin() {
        Log.d("FacebookLogin", "initFacebookLogin called");
        facebookCallbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(facebookCallbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        Log.d("FacebookLogin", "onSuccess - token: " + loginResult.getAccessToken().getToken());
                        Log.d("FacebookLogin", "onSuccess - userId: " + loginResult.getAccessToken().getUserId());
                        Log.d("FacebookLogin", "onSuccess - granted permissions: " + loginResult.getRecentlyGrantedPermissions());
                        handleFacebookAccessToken(loginResult.getAccessToken());
                    }

                    @Override
                    public void onCancel() {
                        Log.d("FacebookLogin", "onCancel - user cancelled Facebook login");
                        showLoading(false);
                    }

                    @Override
                    public void onError(FacebookException exception) {
                        Log.e("FacebookLogin", "onError - Facebook login error", exception);
                        showLoading(false);
                        Toast.makeText(LoginActivity.this, "Facebook login failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
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

        googleLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInWithGoogle();
            }
        });

        facebookLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signInWithFacebook();
            }
        });
    }

    private void signInWithGoogle() {
        showLoading(true);
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    private void signInWithFacebook() {
        Log.d("FacebookLogin", "signInWithFacebook button clicked");
        showLoading(true);
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                showLoading(false);
                Log.e("GoogleSignIn", "Status code: " + e.getStatusCode() + ", message: " + e.getMessage());
                Toast.makeText(this, "Google sign in failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d("FacebookLogin", "onActivityResult - requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + data);
            facebookCallbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";
                            String name = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User";
                            String photoUrl = firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "";
                            createOrGetUserInDatabase(email, name, photoUrl);
                        }
                    } else {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleFacebookAccessToken(AccessToken token) {
        Log.d("FacebookLogin", "handleFacebookAccessToken - token: " + token.getToken());
        Log.d("FacebookLogin", "handleFacebookAccessToken - userId: " + token.getUserId());
        Log.d("FacebookLogin", "handleFacebookAccessToken - expires: " + token.getExpires());
        AuthCredential credential = com.google.firebase.auth.FacebookAuthProvider.getCredential(token.getToken());
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d("FacebookLogin", "Firebase signInWithCredential succeeded");
                        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";
                            String name = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "User";
                            String photoUrl = firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "";
                            Log.d("FacebookLogin", "Firebase user - email: " + email + ", name: " + name + ", photoUrl: " + photoUrl);
                            createOrGetUserInDatabase(email, name, photoUrl);
                        } else {
                            Log.e("FacebookLogin", "Firebase signInWithCredential succeeded but getCurrentUser returned null");
                        }
                    } else {
                        Log.e("FacebookLogin", "Firebase signInWithCredential failed", task.getException());
                        showLoading(false);
                        Toast.makeText(LoginActivity.this, "Facebook authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createOrGetUserInDatabase(String email, String name, String photoUrl) {
        if (TextUtils.isEmpty(email)) {
            showLoading(false);
            sessionManager.createLoginSession("", name, false, photoUrl);
            Toast.makeText(LoginActivity.this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show();
            redirectToMainActivity();
            return;
        }

        databaseReference.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        showLoading(false);

                        if (dataSnapshot.exists()) {
                            for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                                User user = userSnapshot.getValue(User.class);
                                if (user != null) {
                                    String existingPhotoUrl = user.getPhotoUrl();
                                    if ((existingPhotoUrl == null || existingPhotoUrl.isEmpty()) && !photoUrl.isEmpty()) {
                                        userSnapshot.getRef().child("photoUrl").setValue(photoUrl);
                                        existingPhotoUrl = photoUrl;
                                    }
                                    sessionManager.createLoginSession(user.getEmail(), user.getFullName(), false, existingPhotoUrl);
                                } else {
                                    sessionManager.createLoginSession(email, name, false, photoUrl);
                                }
                            }
                        } else {
                            String userId = databaseReference.push().getKey();
                            if (userId != null) {
                                User newUser = new User(userId, name, email, "", "");
                                newUser.setPhotoUrl(photoUrl);
                                databaseReference.child(userId).setValue(newUser);
                            }
                            sessionManager.createLoginSession(email, name, false, photoUrl);
                        }

                        Toast.makeText(LoginActivity.this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show();
                        redirectToMainActivity();
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        showLoading(false);
                        sessionManager.createLoginSession(email, name, false, photoUrl);
                        Toast.makeText(LoginActivity.this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show();
                        redirectToMainActivity();
                    }
                });
    }

    private void checkLoginStatus() {
    }

    private void performLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

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

        showLoading(true);

        databaseReference.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        showLoading(false);

                        if (dataSnapshot.exists()) {
                            for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                                User user = userSnapshot.getValue(User.class);
                                if (user != null && user.getPassword().equals(password)) {
                                    Toast.makeText(LoginActivity.this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show();

                                    String photoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl() : "";
                                    sessionManager.createLoginSession(user.getEmail(), user.getFullName(), false, photoUrl);

                                    redirectToMainActivity();
                                    finish();
                                    return;
                                }
                            }
                            Toast.makeText(LoginActivity.this, getString(R.string.invalid_email_password), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(LoginActivity.this, getString(R.string.invalid_email_password), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this, getString(R.string.database_error, databaseError.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loginAsGuest() {
        showLoading(true);
        Toast.makeText(this, getString(R.string.welcome_guest), Toast.LENGTH_SHORT).show();

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
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        loginButton.setEnabled(!loading);
        guestButton.setEnabled(!loading);
        googleLoginButton.setEnabled(!loading);
        facebookLoginButton.setEnabled(!loading);
        registerText.setEnabled(!loading);
        forgotPasswordText.setEnabled(!loading);
    }

    @Override
    protected void onResume() {
        super.onResume();
        emailInput.setText("");
        passwordInput.setText("");
        showLoading(false);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}

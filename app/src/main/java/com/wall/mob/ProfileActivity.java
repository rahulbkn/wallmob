package com.wall.mob;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import com.wall.mob.User;

public class ProfileActivity extends AppCompatActivity {

    private TextView welcomeText;
    private TextView userInfoText;
    private Button logoutButton;
    private ImageView backButton;
    private LinearLayout settingsButton;
    // Firebase Database reference
    private DatabaseReference databaseReference;
    private SessionManager sessionManager;
    
    private boolean isGuest;
    private String currentEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile);
        
        // Initialize SessionManager
        sessionManager = new SessionManager(this);
        
        // Check if user is not logged in, redirect to LoginActivity
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            finish();
            return;
        }
        
        // Get user data from session
        isGuest = sessionManager.isGuest();
        currentEmail = sessionManager.getEmail();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            window.setNavigationBarColor(ContextCompat.getColor(this, android.R.color.white));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int flags = window.getDecorView().getSystemUiVisibility();
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                window.getDecorView().setSystemUiVisibility(flags);
            }
        }

        // Initialize Firebase Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseReference = database.getReference("users");

        initViews();
        setUserInfo();
        setClickListeners();
        
        
        }


    private void initViews() {
        welcomeText = findViewById(R.id.welcomeText);
        userInfoText = findViewById(R.id.userInfoText);
        logoutButton = findViewById(R.id.logoutButton);
        backButton = findViewById(R.id.backButton);
        settingsButton = findViewById(R.id.settingsButton);
    }

    private void setUserInfo() {
        if (isGuest) {
            welcomeText.setText("Welcome, Guest!");
            userInfoText.setText("You are browsing as a guest user.\nSome features may be limited.");
        } else {
            // Fetch user details from Firebase
            databaseReference.orderByChild("email").equalTo(currentEmail)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {
                                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                                    User user = userSnapshot.getValue(User.class);
                                    if (user != null) {
                                        welcomeText.setText("Welcome back, " + user.getFullName() + "!");
                                        userInfoText.setText("Email: " + user.getEmail() + 
                                                           "\nPhone: " + user.getPhone() +
                                                           "\nYou have full access to all features.");
                                    }
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            welcomeText.setText("Welcome back!");
                            userInfoText.setText("Email: " + currentEmail + "\nCould not load full profile details.");
                        }
                    });
        }
    }

   private void setClickListeners() {
    logoutButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            logout();
        }
    });

    backButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    });

    settingsButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(ProfileActivity.this, SettingsActivity.class);
            startActivity(intent);
        }
    });


    
}
    private void logout() {
        sessionManager.logoutUser();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        
        redirectToLogin();
    }
    
    private void redirectToLogin() {
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
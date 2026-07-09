package com.wall.mob;

import android.content.Context;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.wall.mob.User;

public class ProfileActivity extends BaseActivity {


    private TextView welcomeText;
    private TextView userInfoText;
    private Button logoutButton;
    private ImageView avatarImage;
    private LinearLayout settingsButton;
    private MaterialToolbar toolbar;
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
            // Use ThemeUtils to apply system bar colors according to current theme (handles night mode)
            ThemeUtils.applySystemBars(this);
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
        settingsButton = findViewById(R.id.settingsButton);
        avatarImage = findViewById(R.id.imageview8);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        loadAvatarImage();
    }

    private void loadAvatarImage() {
        String photoUrl = sessionManager.getPhotoUrl();
        if (photoUrl != null && !photoUrl.isEmpty() && avatarImage != null) {
            avatarImage.setImageTintList(null);
            avatarImage.setColorFilter(null);
            avatarImage.setPadding(0, 0, 0, 0);
            Glide.with(this)
                    .load(photoUrl)
                    .transform(new CircleCrop())
                    .into(avatarImage);
        }
    }

    private void setUserInfo() {
        if (isGuest) {
            welcomeText.setText(R.string.welcome_guest);
            userInfoText.setText(R.string.guest_user_info);
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
                                        welcomeText.setText(getString(R.string.welcome_back_name, user.getFullName()));
                                        userInfoText.setText(getString(R.string.profile_full_access, user.getEmail(), user.getPhone()));
                                    }
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            welcomeText.setText(R.string.welcome_back);
                            userInfoText.setText(getString(R.string.profile_email_failed, currentEmail));
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

     toolbar.setNavigationOnClickListener(new View.OnClickListener() {
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
        Toast.makeText(this, getString(R.string.logged_out_successfully), Toast.LENGTH_SHORT).show();
        
        redirectToLogin();
    }
    
    private void redirectToLogin() {
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
// test

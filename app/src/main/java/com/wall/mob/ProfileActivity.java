package com.wall.mob;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Color;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;

import org.json.JSONObject;

public class ProfileActivity extends BaseActivity {

    private static final String UPLOAD_URL = "https://api-server.rahulkumarbknv.workers.dev/profile";
    
    // Constants for scroll behavior
    private static final float TOOLBAR_FADE_START = 0.01f;
    private static final float TOOLBAR_FADE_RANGE = 0.2f;
    private static final float ALPHA_THRESHOLD = 0.99f;

    private TextView welcomeText;
    private TextView userInfoText;
    private ImageView avatarImage;
    private ImageView uploadPhotoButton;
    private ProgressBar uploadProgressBar;
    private LinearLayout settingsButton;
    private LinearLayout dashboardButton;
    private MaterialToolbar toolbar;
    private RecyclerView uploadedWallpapersGrid;
    private DatabaseReference databaseReference;
    private SessionManager sessionManager;
    
    // Toolbar views
    private LinearLayout toolbarLeftContent;
    private ImageView toolbarAvatar;
    private TextView toolbarUserName;
    private ImageView toolbarLogout;
    private LinearLayout profileHeader;
    private AppBarLayout appBarLayout;
    
    // Logout button
    private MaterialButton logoutButton;
    
    private WallpaperAdapter adapter;
    private List<Wallpaper> uploadedWallpapers = new ArrayList<>();
    private boolean isGuest;
    private String currentEmail;

    // Cached values for scroll behavior
    private int cachedHeaderHeight;
    private int cachedToolbarHeight;
    private int cachedTotalScrollRange;
    private AppBarLayout.OnOffsetChangedListener offsetListener;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uploadImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile);
        
        sessionManager = new SessionManager(this);
        
        if (!sessionManager.isLoggedIn()) {
            redirectToLogin();
            finish();
            return;
        }
        
        isGuest = sessionManager.isGuest();
        currentEmail = sessionManager.getEmail();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ThemeUtils.applySystemBars(this);
        }

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseReference = database.getReference("users");

        initViews();
        setUserInfo();
        setClickListeners();
        fetchUploadedWallpapers();
        loadAvatarImage();
        setupAppBarScrollBehavior();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up listener to prevent memory leaks
        if (appBarLayout != null && offsetListener != null) {
            appBarLayout.removeOnOffsetChangedListener(offsetListener);
            offsetListener = null;
        }
    }

    private void initViews() {
        welcomeText = findViewById(R.id.welcomeText);
        userInfoText = findViewById(R.id.userInfoText);
        settingsButton = findViewById(R.id.settingsButton);
        dashboardButton = findViewById(R.id.dashboardButton);
        avatarImage = findViewById(R.id.avatarImage);
        uploadPhotoButton = findViewById(R.id.uploadPhotoButton);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        toolbar = findViewById(R.id.toolbar);
        uploadedWallpapersGrid = findViewById(R.id.uploadedWallpapersGrid);
        
        // Toolbar views
        toolbarLeftContent = findViewById(R.id.toolbarLeftContent);
        toolbarAvatar = findViewById(R.id.toolbarAvatar);
        toolbarUserName = findViewById(R.id.toolbarUserName);
        toolbarLogout = findViewById(R.id.toolbarLogout);
        profileHeader = findViewById(R.id.profileHeader);
        appBarLayout = findViewById(R.id.appBarLayout);
        
        // Logout button in profile header
        logoutButton = findViewById(R.id.logoutButton);
        
        setSupportActionBar(toolbar);
        
        // Setup RecyclerView
        int spanCount = 3;
        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        uploadedWallpapersGrid.setLayoutManager(layoutManager);
        
        adapter = new WallpaperAdapter(this, uploadedWallpapers, 
            new WallpaperAdapter.OnWallpaperClickListener() {
                @Override
                public void onWallpaperClick(Wallpaper wallpaper) {
                    Toast.makeText(ProfileActivity.this, "Wallpaper clicked", Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
                    // Handle long click
                }
            });
        uploadedWallpapersGrid.setAdapter(adapter);
    }

    private void setupAppBarScrollBehavior() {
    // Cache dimensions after layout
    appBarLayout.post(() -> {
        cachedHeaderHeight = profileHeader.getHeight();
        cachedToolbarHeight = toolbar.getHeight();
        cachedTotalScrollRange = appBarLayout.getTotalScrollRange();
    });

    offsetListener = new AppBarLayout.OnOffsetChangedListener() {
        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            // Use cached values or get current values
            int headerHeight = cachedHeaderHeight > 0 ? cachedHeaderHeight : profileHeader.getHeight();
            int totalScrollRange = cachedTotalScrollRange > 0 ? cachedTotalScrollRange : appBarLayout.getTotalScrollRange();
            
            // Calculate scroll progress
            float scrollPercentage = totalScrollRange > 0 ? Math.abs(verticalOffset) / (float) totalScrollRange : 0f;
            
            // Calculate header progress based on actual header visibility
            float headerProgress = headerHeight > 0 ? Math.min(1f, Math.abs(verticalOffset) / (float) headerHeight) : 0f;
            
            // Handle profile header fade animation
            updateProfileHeaderVisibility(headerProgress);
            
            // Handle toolbar content fade animation
            float toolbarAlpha = calculateToolbarAlpha(scrollPercentage);
            updateToolbarContentVisibility(toolbarAlpha);
            
            // FIX: Set toolbar background based on scroll state
            updateToolbarBackground(scrollPercentage);
        }
    };
    
    appBarLayout.addOnOffsetChangedListener(offsetListener);
}

/**
 * Updates profile header visibility and alpha based on scroll progress
 */
private void updateProfileHeaderVisibility(float headerProgress) {
    if (headerProgress < ALPHA_THRESHOLD) {
        if (profileHeader.getVisibility() != View.VISIBLE) {
            profileHeader.setVisibility(View.VISIBLE);
        }
        profileHeader.setAlpha(1f - headerProgress);
    } else {
        if (profileHeader.getVisibility() != View.INVISIBLE) {
            profileHeader.setVisibility(View.INVISIBLE);
        }
        profileHeader.setAlpha(0f);
    }
}

/**
 * Calculates toolbar content alpha based on scroll percentage
 */
private float calculateToolbarAlpha(float scrollPercentage) {
    if (scrollPercentage <= TOOLBAR_FADE_START) {
        return 0f;
    }
    return Math.min(1f, (scrollPercentage - TOOLBAR_FADE_START) / TOOLBAR_FADE_RANGE);
}

/**
 * Updates toolbar content (left content and logout icon) visibility and alpha
 */
private void updateToolbarContentVisibility(float alpha) {
    boolean isVisible = alpha > 0f;
    
    // Handle toolbar left content (avatar + name)
    if (isVisible) {
        if (toolbarLeftContent.getVisibility() != View.VISIBLE) {
            toolbarLeftContent.setVisibility(View.VISIBLE);
        }
        toolbarLeftContent.setAlpha(alpha);
    } else {
        if (toolbarLeftContent.getVisibility() != View.GONE) {
            toolbarLeftContent.setVisibility(View.GONE);
        }
        toolbarLeftContent.setAlpha(0f);
    }
    
    // Handle toolbar logout icon (fully opaque once visible)
    if (isVisible) {
        if (toolbarLogout.getVisibility() != View.VISIBLE) {
            toolbarLogout.setVisibility(View.VISIBLE);
        }
        toolbarLogout.setAlpha(1f);
    } else {
        if (toolbarLogout.getVisibility() != View.GONE) {
            toolbarLogout.setVisibility(View.GONE);
        }
    }
}

/**
 * Updates toolbar background based on scroll state
 */
private void updateToolbarBackground(float scrollPercentage) {
    if (scrollPercentage > TOOLBAR_FADE_START) {
        // When collapsed, set solid background color to toolbar
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar_background));
        toolbar.setElevation(getResources().getDimension(R.dimen.toolbar_elevation));
    } else {
        // When expanded, make toolbar transparent
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setElevation(0f);
    }
}

    private void fetchUploadedWallpapers() {
        FirebaseDatabase.getInstance().getReference("wallpapers/newly_added")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    uploadedWallpapers.clear();
                    String userEmail = sessionManager.getEmail();
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        Wallpaper w = ds.getValue(Wallpaper.class);
                        if (w != null && userEmail != null && userEmail.equals(w.getUploaderId())) {
                            uploadedWallpapers.add(w);
                        }
                    }
                    adapter.updateData(uploadedWallpapers);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle error
                }
            });
    }

    private void loadAvatarImage() {
        String photoUrl = sessionManager.getPhotoUrl();
        
        // Load main avatar
        if (photoUrl != null && !photoUrl.isEmpty() && avatarImage != null) {
            avatarImage.setImageTintList(null);
            avatarImage.setColorFilter(null);
            avatarImage.setPadding(0, 0, 0, 0);
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .transform(new CircleCrop())
                    .into(avatarImage);
        }
        
        // Load toolbar avatar (same image, smaller)
        if (photoUrl != null && !photoUrl.isEmpty() && toolbarAvatar != null) {
            Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .transform(new CircleCrop())
                    .into(toolbarAvatar);
        }
    }

    private void setUserInfo() {
        String displayName = "User";
        
        if (isGuest) {
            displayName = getString(R.string.welcome_guest);
            welcomeText.setText(R.string.welcome_guest);
            userInfoText.setText(R.string.guest_user_info);
            toolbarUserName.setText("Guest");
        } else {
            databaseReference.orderByChild("email").equalTo(currentEmail)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {
                                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                                    User user = userSnapshot.getValue(User.class);
                                    if (user != null) {
                                        String fullName = user.getFullName();
                                        welcomeText.setText(getString(R.string.welcome_back_name, fullName));
                                        userInfoText.setText(getString(R.string.profile_full_access, 
                                            user.getEmail(), user.getPhone()));
                                        toolbarUserName.setText(fullName);
                                    }
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            welcomeText.setText(R.string.welcome_back);
                            userInfoText.setText(getString(R.string.profile_email_failed, currentEmail));
                            toolbarUserName.setText("User");
                        }
                    });
        }
    }

    private void setClickListeners() {
        toolbar.setNavigationOnClickListener(v -> finish());

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        dashboardButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, AdminActivity.class);
            startActivity(intent);
        });

        uploadPhotoButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        
        // Logout button in profile header
        logoutButton.setOnClickListener(v -> showLogoutConfirmationDialog());
        
        // Toolbar logout icon
        toolbarLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
    }

    /**
     * Shows a confirmation dialog before logging out the user
     */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setIcon(R.drawable.ic_exit)
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Performs the actual logout operation
     */
    private void performLogout() {
        sessionManager.logoutUser();
        Toast.makeText(this, getString(R.string.logged_out_successfully), Toast.LENGTH_SHORT).show();
        redirectToLogin();
    }

    private void uploadImage(Uri imageUri) {
        String boundary = "BOUNDARY-" + System.currentTimeMillis();
        String oldPhotoUrl = sessionManager.getPhotoUrl();
        String email = sessionManager.getEmail();

        uploadProgressBar.setVisibility(View.VISIBLE);
        uploadPhotoButton.setEnabled(false);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                if (inputStream == null) {
                    runOnUiThread(() -> {
                        uploadProgressBar.setVisibility(View.GONE);
                        uploadPhotoButton.setEnabled(true);
                        Toast.makeText(ProfileActivity.this, "Failed to read image", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                byte[] imageBytes = byteBuffer.toByteArray();
                inputStream.close();

                HttpURLConnection connection = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                OutputStream os = connection.getOutputStream();
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"profile.jpg\"\r\n").getBytes());
                os.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
                os.write(imageBytes);

                if (email != null && !email.isEmpty()) {
                    os.write(("\r\n--" + boundary + "\r\n").getBytes());
                    os.write(("Content-Disposition: form-data; name=\"email\"\r\n\r\n").getBytes());
                    os.write(email.getBytes());
                }

                if (oldPhotoUrl != null && !oldPhotoUrl.isEmpty()) {
                    os.write(("\r\n--" + boundary + "\r\n").getBytes());
                    os.write(("Content-Disposition: form-data; name=\"oldPhotoUrl\"\r\n\r\n").getBytes());
                    os.write(oldPhotoUrl.getBytes());
                }

                os.write(("\r\n--" + boundary + "--\r\n").getBytes());
                os.flush();
                os.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = connection.getInputStream();
                    ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        responseBuffer.write(buf, 0, n);
                    }
                    String responseBody = responseBuffer.toString();
                    is.close();

                    JSONObject json = new JSONObject(responseBody);
                    if (json.getBoolean("success")) {
                        String photoUrl = json.getString("url");
                        runOnUiThread(() -> onUploadSuccess(photoUrl));
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(ProfileActivity.this, "Upload failed: server error", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(ProfileActivity.this, "Upload failed: HTTP " + responseCode, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ProfileActivity.this, "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                runOnUiThread(() -> {
                    uploadProgressBar.setVisibility(View.GONE);
                    uploadPhotoButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void onUploadSuccess(String photoUrl) {
        sessionManager.savePhotoUrl(photoUrl);

        if (!isGuest && currentEmail != null && !currentEmail.isEmpty()) {
            databaseReference.orderByChild("email").equalTo(currentEmail)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            if (dataSnapshot.exists()) {
                                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                                    userSnapshot.getRef().child("photoUrl").setValue(photoUrl);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            // Handle error
                        }
                    });
        }

        loadAvatarImage();
        Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show();
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
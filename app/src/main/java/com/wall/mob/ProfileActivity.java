package com.wall.mob;

import android.content.Context;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Toast;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.wall.mob.User;

import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.ArrayList;
import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProfileActivity extends BaseActivity {

    private static final String UPLOAD_URL = "https://api-server.rahulkumarbknv.workers.dev/upload";
    private static final String BOUNDARY = "Boundary-" + System.currentTimeMillis();

    private TextView welcomeText;
    private TextView userInfoText;
    private Button logoutButton;
    private ImageView avatarImage;
    private ImageView uploadPhotoButton;
    private ProgressBar uploadProgressBar;
    private LinearLayout settingsButton;
    private MaterialToolbar toolbar;
    private DatabaseReference databaseReference;
    private SessionManager sessionManager;
    
    private boolean isGuest;
    private String currentEmail;

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
        fetchUploadedWallpapers();
        
        }


    private TextView welcomeText;
    private TextView userInfoText;
    private ImageView avatarImage;
    private ImageView uploadPhotoButton;
    private ProgressBar uploadProgressBar;
    private MaterialToolbar toolbar;
    private RecyclerView uploadedWallpapersGrid;
    private WallpaperAdapter adapter;
    private List<Wallpaper> uploadedWallpapers = new ArrayList<>();

    private void initViews() {
        welcomeText = findViewById(R.id.welcomeText);
        userInfoText = findViewById(R.id.userInfoText);
        logoutButton = findViewById(R.id.logoutButton);
        settingsButton = findViewById(R.id.settingsButton);
        avatarImage = findViewById(R.id.imageview8);
        uploadPhotoButton = findViewById(R.id.uploadPhotoButton);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        loadAvatarImage();

        uploadedWallpapersGrid = findViewById(R.id.uploadedWallpapersGrid);
        adapter = new WallpaperAdapter(this, uploadedWallpapers, new WallpaperAdapter.OnWallpaperClickListener() {
            @Override
            public void onWallpaperClick(Wallpaper wallpaper) {
                // Handle click if needed
            }
            @Override
            public void onWallpaperLongClick(Wallpaper wallpaper, int position) {
                // Handle long click
            }
        });
        uploadedWallpapersGrid.setAdapter(adapter);
    }

    private void fetchUploadedWallpapers() {
        FirebaseDatabase.getInstance().getReference("wallpapers/trending")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    uploadedWallpapers.clear();
                    String userEmail = sessionManager.getEmail();
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        Wallpaper w = ds.getValue(Wallpaper.class);
                        if (w != null && userEmail.equals(w.getUploaderId())) {
                            uploadedWallpapers.add(w);
                        }
                    }
                    adapter.updateData(uploadedWallpapers);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
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

     uploadPhotoButton.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View v) {
             pickImageLauncher.launch("image/*");
         }
     });
 }
    private void uploadImage(Uri imageUri) {
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
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
                connection.setDoOutput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                OutputStream os = connection.getOutputStream();
                os.write(("--" + BOUNDARY + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"profile.jpg\"\r\n").getBytes());
                os.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
                os.write(imageBytes);
                os.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes());
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
                        }
                    });
        }

        loadAvatarImage();
        Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show();
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

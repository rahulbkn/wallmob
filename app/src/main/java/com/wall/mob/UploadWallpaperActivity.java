package com.wall.mob;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UploadWallpaperActivity extends BaseActivity {

    private static final String UPLOAD_URL = "https://api-server.rahulkumarbknv.workers.dev/upload-wallpaper";
    private static final String BOUNDARY = "Boundary-" + System.currentTimeMillis();

    private ImageView previewImage;
    private TextView selectImageHint;
    private TextInputEditText titleInput;
    private TextInputEditText categoryInput;
    private Button uploadButton;
    private ProgressBar uploadProgressBar;
    private MaterialToolbar toolbar;
    private View imagePreviewContainer;

    private Uri selectedImageUri;
    private DatabaseReference firebaseRef;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    previewImage.setPadding(0, 0, 0, 0);
                    previewImage.setImageTintList(null);
                    Glide.with(this).load(uri).into(previewImage);
                    selectImageHint.setVisibility(View.GONE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.upload_wallpaper);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ThemeUtils.applySystemBars(this);
        }

        firebaseRef = FirebaseDatabase.getInstance().getReference("wallpapers/trending");

        initViews();
        setClickListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer);
        previewImage = findViewById(R.id.previewImage);
        selectImageHint = findViewById(R.id.selectImageHint);
        titleInput = findViewById(R.id.titleInput);
        categoryInput = findViewById(R.id.categoryInput);
        uploadButton = findViewById(R.id.uploadButton);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);

        setSupportActionBar(toolbar);
    }

    private void setClickListeners() {
        toolbar.setNavigationOnClickListener(v -> finish());

        imagePreviewContainer.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        uploadButton.setOnClickListener(v -> {
            if (selectedImageUri == null) {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadWallpaper();
        });
    }

    private void uploadWallpaper() {
        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String category = categoryInput.getText() != null ? categoryInput.getText().toString().trim() : "";
        final String effectiveTitle = title.isEmpty() ? "Untitled" : title;
        final String effectiveCategory = category.isEmpty() ? "General" : category;

        uploadProgressBar.setVisibility(View.VISIBLE);
        uploadButton.setEnabled(false);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                if (inputStream == null) {
                    runOnUiThread(() -> {
                        uploadProgressBar.setVisibility(View.GONE);
                        uploadButton.setEnabled(true);
                        Toast.makeText(UploadWallpaperActivity.this, "Failed to read image", Toast.LENGTH_SHORT).show();
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
                // Photo field
                os.write(("--" + BOUNDARY + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"wallpaper.jpg\"\r\n").getBytes());
                os.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
                os.write(imageBytes);
                os.write(("\r\n").getBytes());
                // Title field
                os.write(("--" + BOUNDARY + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"title\"\r\n\r\n").getBytes());
                os.write((effectiveTitle + "\r\n").getBytes());
                // Category field
                os.write(("--" + BOUNDARY + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"category\"\r\n\r\n").getBytes());
                os.write((effectiveCategory + "\r\n").getBytes());
                os.write(("--" + BOUNDARY + "--\r\n").getBytes());
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
                        String imageUrl = json.getString("imageUrl");
                        String thumbnailUrl = json.getString("thumbnailUrl");
                        String returnedTitle = json.optString("title", effectiveTitle);
                        String returnedCategory = json.optString("category", effectiveCategory);
                        saveToFirebase(imageUrl, thumbnailUrl, returnedTitle, returnedCategory);
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(UploadWallpaperActivity.this, "Upload failed: server error", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(UploadWallpaperActivity.this, "Upload failed: HTTP " + responseCode, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(UploadWallpaperActivity.this, "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                runOnUiThread(() -> {
                    uploadProgressBar.setVisibility(View.GONE);
                    uploadButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void saveToFirebase(String imageUrl, String thumbnailUrl, String title, String category) {
        DatabaseReference newRef = firebaseRef.push();
        String wallpaperId = newRef.getKey();

        Wallpaper wallpaper = new Wallpaper(
            wallpaperId,
            imageUrl,
            thumbnailUrl,
            title,
            category,
            "User Uploaded",
            null,
            true
        );
        wallpaper.setAddedAt(System.currentTimeMillis());

        newRef.setValue(wallpaper)
            .addOnSuccessListener(aVoid -> runOnUiThread(() -> {
                Toast.makeText(UploadWallpaperActivity.this, "Wallpaper uploaded successfully!", Toast.LENGTH_LONG).show();
                finish();
            }))
            .addOnFailureListener(e -> runOnUiThread(() -> {
                Toast.makeText(UploadWallpaperActivity.this, "Failed to save to database", Toast.LENGTH_SHORT).show();
            }));
    }
}

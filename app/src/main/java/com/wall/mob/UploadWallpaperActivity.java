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
    private SessionManager sessionManager;

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

        sessionManager = new SessionManager(this);

        initViews();
        setClickListeners();
        checkHealthAndEnableUpload();
    }

    private void checkHealthAndEnableUpload() {
        uploadButton.setEnabled(false);
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            boolean isHealthy = false;
            
            while (System.currentTimeMillis() - startTime < 60000) {
                if (isFinishing() || isDestroyed()) return;

                try {
                    java.net.HttpURLConnection urlConnection = (java.net.HttpURLConnection) new java.net.URL("https://tool-veyr.onrender.com/health").openConnection();
                    urlConnection.setRequestMethod("GET");
                    urlConnection.setConnectTimeout(3000);
                    urlConnection.setReadTimeout(3000);
                    int responseCode = urlConnection.getResponseCode();
                    urlConnection.disconnect();

                    if (responseCode == 200) {
                        isHealthy = true;
                        break;
                    }
                } catch (Exception e) {
                    // Ignore and continue polling
                }
                
                try {
                    Thread.sleep(4000); 
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (isFinishing() || isDestroyed()) return;

            if (isHealthy) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    uploadButton.setEnabled(true);
                });
            } else {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(UploadWallpaperActivity.this, "Server unavailable, please try again later", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
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
                
                // Photographer field
                os.write(("--" + BOUNDARY + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"photographer\"\r\n\r\n").getBytes());
                os.write(("User Uploaded\r\n").getBytes());

                // NAYA: Uploader ID field send karna Cloudflare ko
                String uEmail = sessionManager.getEmail();
                if (uEmail == null) uEmail = "";
                android.util.Log.d("UploadWallpaper", "Uploading wallpaper with uploaderId: " + uEmail);
                os.write(("--" + BOUNDARY + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"uploader_id\"\r\n\r\n").getBytes());
                os.write((uEmail + "\r\n").getBytes());

                os.write(("--" + BOUNDARY + "--\r\n").getBytes());
                os.flush();
                os.close();

                int responseCode = connection.getResponseCode();

                InputStream errorStream = null;
                try {
                    errorStream = connection.getErrorStream();
                } catch (Exception ignored) {}

                String errorBody = null;
                if (errorStream != null) {
                    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = errorStream.read(buf)) != -1) {
                        errBuf.write(buf, 0, n);
                    }
                    errorBody = errBuf.toString();
                    errorStream.close();
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = connection.getInputStream();
                    ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
                    byte[] buff = new byte[4096];
                    int m;
                    while ((m = is.read(buff)) != -1) {
                        responseBuffer.write(buff, 0, m);
                    }
                    String responseBody = responseBuffer.toString();
                    is.close();

                    JSONObject json = new JSONObject(responseBody);
                    if (json.getBoolean("success")) {
                        
                        // NAYA: Ab worker database handle kar raha hai, so no saveToFirebase call.
                        runOnUiThread(() -> {
                            Toast.makeText(UploadWallpaperActivity.this, "Wallpaper uploaded successfully!", Toast.LENGTH_LONG).show();
                            finish(); // Activity close
                        });

                    } else {
                        final String errMsg = json.optString("message", "server error");
                        runOnUiThread(() -> {
                            Toast.makeText(UploadWallpaperActivity.this, "Upload failed: " + errMsg, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else if (responseCode == 422) {
                    String reason = "Image contains objectionable content";
                    if (errorBody != null) {
                        try {
                            JSONObject errJson = new JSONObject(errorBody);
                            reason = errJson.optString("message", reason);
                        } catch (Exception ignored) {}
                    }
                    final String displayReason = reason;
                    runOnUiThread(() -> {
                        Toast.makeText(UploadWallpaperActivity.this, displayReason, Toast.LENGTH_LONG).show();
                    });
                } else {
                    final String detail = errorBody != null ? errorBody : "HTTP " + responseCode;
                    runOnUiThread(() -> {
                        Toast.makeText(UploadWallpaperActivity.this, "Upload failed: " + detail, Toast.LENGTH_SHORT).show();
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
}

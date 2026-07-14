package com.wall.mob;

import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
    private View selectImageHint;
    private TextInputEditText titleInput;
    private TextInputEditText categoryInput;
    private MaterialButton uploadButton;
    private CircularProgressIndicator uploadProgressBar;
    private TextView uploadProgressText;
    private TextView uploadStatusLabel;
    private MaterialToolbar toolbar;
    private View imagePreviewContainer;
    private View uploadOverlay;

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

        if (!sessionManager.isLoggedIn() || sessionManager.isGuest()) {
            Toast.makeText(this, "Please login to upload wallpapers", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setClickListeners();
        checkHealthAndEnableUpload();
    }

    // Theme attr color helper — sirf AppTheme me defined attrs (colorPrimary, colorOnPrimary,
    // colorSurface, colorOnSurface, colorControlNormal) DayNight ke saath safe hain
    private int themeColor(int attr) {
        return MaterialColors.getColor(this, attr, 0);
    }

    private void checkHealthAndEnableUpload() {
        setUploadButtonDisabledAppearance();

        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            boolean isHealthy = false;

            while (System.currentTimeMillis() - startTime < 60000) {
                if (isFinishing() || isDestroyed()) return;

                try {
                    HttpURLConnection urlConnection = (HttpURLConnection) new URL("https://tool-veyr.onrender.com/health").openConnection();
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

            final boolean finalIsHealthy = isHealthy;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalIsHealthy) {
                    setUploadingAppearance(false);
                } else {
                    Toast.makeText(UploadWallpaperActivity.this, getString(R.string.server_unavailable), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        // NAYA: upload ke dauraan back press ignore — user ko beech me activity chhodne se roko
        if (uploadOverlay != null && uploadOverlay.getVisibility() == View.VISIBLE) {
            return;
        }
        super.onBackPressed();
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
        uploadProgressText = findViewById(R.id.uploadProgressText);
        uploadStatusLabel = findViewById(R.id.uploadStatusLabel);
        uploadOverlay = findViewById(R.id.uploadOverlay);

        setSupportActionBar(toolbar);
    }

    private void setClickListeners() {
        toolbar.setNavigationOnClickListener(v -> {
            // NAYA: upload chal rahe time back navigation block — overlay visible = upload in progress
            if (uploadOverlay.getVisibility() == View.VISIBLE) return;
            finish();
        });

        imagePreviewContainer.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        uploadButton.setOnClickListener(v -> {
            if (selectedImageUri == null) {
                Toast.makeText(this, getString(R.string.select_image_first), Toast.LENGTH_SHORT).show();
                return;
            }
            uploadWallpaper();
        });
    }

    // Disabled state: muted grey, using theme's colorControlNormal + textColorSecondary
    private void setUploadButtonDisabledAppearance() {
        uploadButton.setEnabled(false);
        uploadButton.setBackgroundTintList(ColorStateList.valueOf(
                themeColor(com.google.android.material.R.attr.colorControlNormal)));
        int secondaryText = themeColor(android.R.attr.textColorSecondary);
        if (secondaryText == 0) secondaryText = ContextCompat.getColor(this, R.color.gray_dark);
        uploadButton.setTextColor(secondaryText);
        uploadButton.setIconTint(ColorStateList.valueOf(secondaryText));
    }

    // Enabled state: uses AppTheme's colorPrimary / colorOnPrimary (already DayNight-aware)
    private void setUploadButtonEnabledAppearance() {
        uploadButton.setEnabled(true);
        uploadButton.setBackgroundTintList(ColorStateList.valueOf(
                themeColor(com.google.android.material.R.attr.colorPrimary)));
        int onPrimary = themeColor(com.google.android.material.R.attr.colorOnPrimary);
        uploadButton.setTextColor(onPrimary);
        uploadButton.setIconTint(ColorStateList.valueOf(onPrimary));
    }

    private void setUploadingAppearance(boolean isUploading) {
        if (isUploading) {
            uploadProgressBar.setIndeterminate(false);
            uploadProgressBar.setProgress(0);
            uploadProgressText.setText("0%");
            uploadStatusLabel.setText(getString(R.string.uploading_label));
            uploadOverlay.setVisibility(View.VISIBLE);
            setUploadButtonDisabledAppearance();
        } else {
            uploadOverlay.setVisibility(View.GONE);
            setUploadButtonEnabledAppearance();
        }
    }

    private byte[] buildTextPart(String fieldName, String value) {
        return ("--" + BOUNDARY + "\r\n" +
                "Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n" +
                value + "\r\n").getBytes();
    }

    private void uploadWallpaper() {
        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String category = categoryInput.getText() != null ? categoryInput.getText().toString().trim() : "";
        final String effectiveTitle = title.isEmpty() ? getString(R.string.default_title) : title;
        final String effectiveCategory = category.isEmpty() ? getString(R.string.default_category) : category;

        setUploadingAppearance(true);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                if (inputStream == null) {
                    runOnUiThread(() -> {
                        setUploadingAppearance(false);
                        Toast.makeText(UploadWallpaperActivity.this, getString(R.string.failed_to_read_image), Toast.LENGTH_SHORT).show();
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

                // Saare multipart parts pehle hi bana lo taaki exact Content-Length pata ho
                byte[] photoHeader = ("--" + BOUNDARY + "\r\n" +
                        "Content-Disposition: form-data; name=\"photo\"; filename=\"wallpaper.jpg\"\r\n" +
                        "Content-Type: image/jpeg\r\n\r\n").getBytes();
                byte[] photoFooter = "\r\n".getBytes();
                byte[] titlePart = buildTextPart("title", effectiveTitle);
                byte[] categoryPart = buildTextPart("category", effectiveCategory);
                byte[] photographerPart = buildTextPart("photographer", "User Uploaded");

                String uEmail = sessionManager.getEmail();
                if (uEmail == null) uEmail = "";
                android.util.Log.d("UploadWallpaper", "Uploading wallpaper with uploaderId: " + uEmail);
                byte[] uploaderPart = buildTextPart("uploader_id", uEmail);

                byte[] idTokenPart = null;
                FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                if (firebaseUser != null) {
                    try {
                        String idToken = com.google.android.gms.tasks.Tasks.await(firebaseUser.getIdToken(false)).getToken();
                        idTokenPart = buildTextPart("idToken", idToken);
                    } catch (Exception ignored) {}
                }

                byte[] closingBoundary = ("--" + BOUNDARY + "--\r\n").getBytes();

                long totalContentLength = photoHeader.length + imageBytes.length + photoFooter.length
                        + titlePart.length + categoryPart.length + photographerPart.length
                        + uploaderPart.length + closingBoundary.length;
                if (idTokenPart != null) {
                    totalContentLength += idTokenPart.length;
                }

                HttpURLConnection connection = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
                connection.setDoOutput(true);
                connection.setConnectTimeout(30000);
                // NAYA: 30s bahut kam tha — server-side moderation (NudeNet) + Telegram upload +
                // Firebase write chain kabhi kabhi isse zyada le leti hai, isliye badha diya
                connection.setReadTimeout(120000);

                // NAYA: fixed-length streaming mode — isse HttpURLConnection poora body memory me
                // buffer nahi karta. Har write() seedha socket pe jaata hai (TCP backpressure ke
                // saath), so progress bar REAL network upload speed dikhayega, fake nahi.
                connection.setFixedLengthStreamingMode(totalContentLength);

                OutputStream os = connection.getOutputStream();

                final long[] bytesWritten = {0};

                os.write(photoHeader);
                bytesWritten[0] += photoHeader.length;
                publishProgress(bytesWritten[0], totalContentLength);

                // Photo ko chunks me likho — har write() network pe jaata hai, isliye progress real hai
                int chunkSize = 8192;
                int totalImageSize = imageBytes.length;
                for (int i = 0; i < totalImageSize; i += chunkSize) {
                    int length = Math.min(chunkSize, totalImageSize - i);
                    os.write(imageBytes, i, length);
                    bytesWritten[0] += length;
                    publishProgress(bytesWritten[0], totalContentLength);
                }

                os.write(photoFooter);
                bytesWritten[0] += photoFooter.length;

                os.write(titlePart);
                bytesWritten[0] += titlePart.length;

                os.write(categoryPart);
                bytesWritten[0] += categoryPart.length;

                os.write(photographerPart);
                bytesWritten[0] += photographerPart.length;

                os.write(uploaderPart);
                bytesWritten[0] += uploaderPart.length;

                if (idTokenPart != null) {
                    os.write(idTokenPart);
                    bytesWritten[0] += idTokenPart.length;
                }

                os.write(closingBoundary);
                bytesWritten[0] += closingBoundary.length;
                publishProgress(bytesWritten[0], totalContentLength);

                os.flush();
                os.close();

                // NAYA: yahan tak client ka kaam khatam — poora body network pe ja chuka hai.
                // Ab server moderation + Telegram upload + Firebase write kar raha hai, jisme
                // kuch second lag sakte hain. Isliye "100% frozen" dikhne ki jagah spinner +
                // "Processing" state dikhao, taaki user ko pata rahe kuch ho raha hai
                switchToProcessingState();

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

                        // Worker database handle kar raha hai, so no saveToFirebase call.
                        runOnUiThread(() -> {
                            setUploadingAppearance(false);
                            Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_success), Toast.LENGTH_LONG).show();
                            finish(); // Activity close
                        });

                    } else {
                        final String errMsg = json.optString("message", getString(R.string.server_error));
                        runOnUiThread(() -> {
                            setUploadingAppearance(false);
                            Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_failed_with_reason, errMsg), Toast.LENGTH_SHORT).show();
                        });
                    }
                } else if (responseCode == 422) {
                    String reason = getString(R.string.image_objectionable_content);
                    if (errorBody != null) {
                        try {
                            JSONObject errJson = new JSONObject(errorBody);
                            reason = errJson.optString("message", reason);
                        } catch (Exception ignored) {}
                    }
                    final String displayReason = reason;
                    runOnUiThread(() -> {
                        setUploadingAppearance(false);
                        Toast.makeText(UploadWallpaperActivity.this, displayReason, Toast.LENGTH_LONG).show();
                    });
                } else {
                    final String detail = errorBody != null ? errorBody : "HTTP " + responseCode;
                    runOnUiThread(() -> {
                        setUploadingAppearance(false);
                        Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_failed_with_reason, detail), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (java.net.SocketTimeoutException e) {
                // NAYA: timeout ko alag se handle — generic exception message se zyada helpful
                runOnUiThread(() -> {
                    setUploadingAppearance(false);
                    Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_timeout), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setUploadingAppearance(false);
                    Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_error_with_reason, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // Real byte count se UI progress push karta hai — network write ke turant baad call hota hai
    private void publishProgress(long written, long total) {
        int progress = total > 0 ? (int) ((written * 100) / total) : 0;
        runOnUiThread(() -> {
            uploadProgressBar.setProgress(progress);
            uploadProgressText.setText(progress + "%");
        });
    }

    // Body fully sent hone ke baad determinate % se indeterminate spinner me switch —
    // ab server-side processing ka wait hai, byte-progress ka nahi
    private void switchToProcessingState() {
        runOnUiThread(() -> {
            uploadProgressText.setText(getString(R.string.processing_percent));
            uploadStatusLabel.setText(getString(R.string.processing_label));
            uploadProgressBar.setIndeterminate(true);
        });
    }
}

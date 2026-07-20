package com.wall.mob;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.wall.mob.reels.ReelsRepository;
import com.wall.mob.reels.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UploadWallpaperActivity extends BaseActivity {

    public static final String EXTRA_UPLOAD_TYPE = "upload_type";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";

    private static final String UPLOAD_URL = "https://api-server.rahulkumarbknv.workers.dev/upload-wallpaper";
    private static final String BOUNDARY = "Boundary-" + System.currentTimeMillis();
    private static final String TAG = "UploadWallpaper";

    private ImageView previewImage;
    private LinearLayout selectImageHint;
    private TextView selectMediaHintText;
    private ImageView selectMediaIcon;
    private TextView selectedFileName;
    private TextInputEditText titleInput;
    private TextInputEditText categoryInput;
    private TextInputEditText descriptionInput;
    private TextInputEditText hashtagsInput;
    private TextInputLayout descriptionInputLayout;
    private TextInputLayout hashtagsInputLayout;
    private MaterialButton uploadButton;
    private MaterialButtonToggleGroup uploadTypeToggle;
    private CircularProgressIndicator uploadProgressBar;
    private TextView uploadProgressText;
    private TextView uploadStatusLabel;
    private MaterialToolbar toolbar;
    private View imagePreviewContainer;
    private View uploadOverlay;

    private Uri selectedMediaUri;
    private String selectedDisplayName;
    private SessionManager sessionManager;
    private ReelsRepository reelsRepository;

    private final ActivityResultLauncher<String> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    onMediaSelected(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.upload_wallpaper);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn() || sessionManager.isGuest()) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        reelsRepository = new ReelsRepository(getApplicationContext());
        initViews();
        setClickListeners();

        checkHealthAndEnableUpload();
    }

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
                    String healthUrl = "https://tool-veyr.onrender.com/health";
                    HttpURLConnection urlConnection = (HttpURLConnection) new URL(healthUrl).openConnection();
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
        selectMediaHintText = findViewById(R.id.selectMediaHintText);
        selectMediaIcon = findViewById(R.id.selectMediaIcon);
        selectedFileName = findViewById(R.id.selectedFileName);
        titleInput = findViewById(R.id.titleInput);
        categoryInput = findViewById(R.id.categoryInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        hashtagsInput = findViewById(R.id.hashtagsInput);
        descriptionInputLayout = findViewById(R.id.descriptionInputLayout);
        hashtagsInputLayout = findViewById(R.id.hashtagsInputLayout);
        uploadButton = findViewById(R.id.uploadButton);
        uploadTypeToggle = findViewById(R.id.uploadTypeToggle);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        uploadProgressText = findViewById(R.id.uploadProgressText);
        uploadStatusLabel = findViewById(R.id.uploadStatusLabel);
        uploadOverlay = findViewById(R.id.uploadOverlay);

        setSupportActionBar(toolbar);
    }

    private void setClickListeners() {
        toolbar.setNavigationOnClickListener(v -> {
            if (uploadOverlay.getVisibility() == View.VISIBLE) return;
            finish();
        });

        imagePreviewContainer.setOnClickListener(v -> {
            pickMediaLauncher.launch("image/*");
        });

        uploadButton.setOnClickListener(v -> {
            if (selectedMediaUri == null) {
                Toast.makeText(this, getString(R.string.select_media_first), Toast.LENGTH_SHORT).show();
                return;
            }
            uploadWallpaper();
        });
    }

    private void clearMediaSelection() {
        selectedMediaUri = null;
        selectedDisplayName = null;
        previewImage.setImageDrawable(null);
        previewImage.setVisibility(View.GONE);
        selectImageHint.setVisibility(View.VISIBLE);
        selectedFileName.setVisibility(View.GONE);
        selectedFileName.setText("");
    }

    private void onMediaSelected(Uri uri) {
        selectedMediaUri = uri;
        selectedDisplayName = resolveDisplayName(uri);

        previewImage.setImageURI(uri);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewImage.setVisibility(View.VISIBLE);
        selectImageHint.setVisibility(View.GONE);
        selectedFileName.setText(selectedDisplayName != null
                ? selectedDisplayName
                : getString(R.string.image_selected));
        selectedFileName.setVisibility(View.VISIBLE);
    }

    private String resolveDisplayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }
        String path = uri.getLastPathSegment();
        return path != null ? path : "selected_file";
    }

    private void setUploadButtonDisabledAppearance() {
        uploadButton.setEnabled(false);
        uploadButton.setBackgroundTintList(ColorStateList.valueOf(
                themeColor(com.google.android.material.R.attr.colorControlNormal)));
        int secondaryText = themeColor(android.R.attr.textColorSecondary);
        if (secondaryText == 0) secondaryText = ContextCompat.getColor(this, R.color.gray_dark);
        uploadButton.setTextColor(secondaryText);
        uploadButton.setIconTint(ColorStateList.valueOf(secondaryText));
    }

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

    private File copyUriToCache(Uri uri, String prefix, String suffix) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;
            File tempFile = File.createTempFile(prefix, suffix, getCacheDir());
            tempFile.deleteOnExit();
            try (FileOutputStream output = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int len;
                long written = 0;
                long estimated = estimateSize(uri);
                while ((len = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                    written += len;
                    if (estimated > 0) {
                        publishProgress(written, estimated);
                    }
                }
            }
            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "copyUriToCache failed: " + e.getMessage(), e);
            return null;
        }
    }

    private long estimateSize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) {
                    return cursor.getLong(index);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void uploadWallpaper() {
        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String category = categoryInput.getText() != null ? categoryInput.getText().toString().trim() : "";
        final String effectiveTitle = title.isEmpty() ? getString(R.string.default_title) : title;
        final String effectiveCategory = category.isEmpty() ? getString(R.string.default_category) : category;

        Log.d(TAG, "uploadWallpaper: title=" + effectiveTitle + " category=" + effectiveCategory + " uri=" + selectedMediaUri);
        setUploadingAppearance(true);

        new Thread(() -> {
            try {
                Log.d(TAG, "Opening input stream");
                InputStream inputStream = getContentResolver().openInputStream(selectedMediaUri);
                if (inputStream == null) {
                    Log.e(TAG, "inputStream is null");
                    runOnUiThread(() -> {
                        setUploadingAppearance(false);
                        Toast.makeText(UploadWallpaperActivity.this, getString(R.string.failed_to_read_image), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                Log.d(TAG, "Reading image bytes");
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                byte[] imageBytes = byteBuffer.toByteArray();
                inputStream.close();
                Log.d(TAG, "Image read: " + imageBytes.length + " bytes");

                Log.d(TAG, "Building multipart parts");
                byte[] photoHeader = ("--" + BOUNDARY + "\r\n" +
                        "Content-Disposition: form-data; name=\"photo\"; filename=\"wallpaper.jpg\"\r\n" +
                        "Content-Type: image/jpeg\r\n\r\n").getBytes();
                byte[] photoFooter = "\r\n".getBytes();
                byte[] titlePart = buildTextPart("title", effectiveTitle);
                byte[] categoryPart = buildTextPart("category", effectiveCategory);
                byte[] photographerPart = buildTextPart("photographer", "User Uploaded");

                String uEmail = sessionManager.getEmail();
                if (uEmail == null) uEmail = "";
                Log.d(TAG, "uploader_id=" + uEmail);
                byte[] uploaderPart = buildTextPart("uploader_id", uEmail);
                byte[] emailPart = buildTextPart("email", uEmail);

                byte[] idTokenPart = null;
                FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                Log.d(TAG, "FirebaseAuth user=" + (firebaseUser != null ? firebaseUser.getEmail() : "null"));
                if (firebaseUser != null) {
                    try {
                        String idToken = com.google.android.gms.tasks.Tasks.await(firebaseUser.getIdToken(false)).getToken();
                        idTokenPart = buildTextPart("idToken", idToken);
                        Log.d(TAG, "idToken obtained, length=" + idToken.length());
                    } catch (Exception e) {
                        Log.w(TAG, "idToken fetch failed: " + e.getMessage());
                    }
                }

                byte[] closingBoundary = ("--" + BOUNDARY + "--\r\n").getBytes();

                long totalContentLength = photoHeader.length + imageBytes.length + photoFooter.length
                        + titlePart.length + categoryPart.length + photographerPart.length
                        + uploaderPart.length + emailPart.length + closingBoundary.length;
                if (idTokenPart != null) {
                    totalContentLength += idTokenPart.length;
                }
                Log.d(TAG, "totalContentLength=" + totalContentLength);

                Log.d(TAG, "Opening HTTP connection to " + UPLOAD_URL);
                HttpURLConnection connection = (HttpURLConnection) new URL(UPLOAD_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
                connection.setDoOutput(true);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(120000);
                connection.setFixedLengthStreamingMode(totalContentLength);

                OutputStream os = connection.getOutputStream();

                final long[] bytesWritten = {0};

                os.write(photoHeader);
                bytesWritten[0] += photoHeader.length;
                publishProgress(bytesWritten[0], totalContentLength);

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
                os.write(emailPart);
                bytesWritten[0] += emailPart.length;
                if (idTokenPart != null) {
                    os.write(idTokenPart);
                    bytesWritten[0] += idTokenPart.length;
                }
                os.write(closingBoundary);
                bytesWritten[0] += closingBoundary.length;
                publishProgress(bytesWritten[0], totalContentLength);

                os.flush();
                os.close();
                Log.d(TAG, "Body sent: " + bytesWritten[0] + " bytes");

                switchToProcessingState();

                Log.d(TAG, "Reading response code...");
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);

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
                    Log.e(TAG, "Error body: " + errorBody);
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Upload OK, reading response");
                    InputStream is = connection.getInputStream();
                    ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
                    byte[] buff = new byte[4096];
                    int m;
                    while ((m = is.read(buff)) != -1) {
                        responseBuffer.write(buff, 0, m);
                    }
                    String responseBody = responseBuffer.toString();
                    is.close();
                    Log.d(TAG, "Response: " + responseBody);

                    JSONObject json = new JSONObject(responseBody);
                    if (json.getBoolean("success")) {
                        runOnUiThread(() -> {
                            setUploadingAppearance(false);
                            Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_success), Toast.LENGTH_LONG).show();
                            finish();
                        });
                    } else {
                        final String errMsg = json.optString("message", getString(R.string.server_error));
                        Log.e(TAG, "Server success=false: " + errMsg);
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
                    Log.e(TAG, "Rejected 422: " + displayReason);
                    runOnUiThread(() -> {
                        setUploadingAppearance(false);
                        Toast.makeText(UploadWallpaperActivity.this, displayReason, Toast.LENGTH_LONG).show();
                    });
                } else {
                    final String detail = errorBody != null ? errorBody : "HTTP " + responseCode;
                    Log.e(TAG, "Failed " + responseCode + ": " + detail);
                    runOnUiThread(() -> {
                        setUploadingAppearance(false);
                        Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_failed_with_reason, detail), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (java.net.SocketTimeoutException e) {
                Log.e(TAG, "SocketTimeout: " + e.getMessage());
                runOnUiThread(() -> {
                    setUploadingAppearance(false);
                    Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_timeout), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Exception: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    setUploadingAppearance(false);
                    Toast.makeText(UploadWallpaperActivity.this, getString(R.string.upload_error_with_reason, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void publishProgress(long written, long total) {
        int progress = total > 0 ? (int) ((written * 100) / total) : 0;
        runOnUiThread(() -> {
            uploadProgressBar.setIndeterminate(false);
            uploadProgressBar.setProgress(Math.min(progress, 100));
            uploadProgressText.setText(Math.min(progress, 100) + "%");
        });
    }

    private void switchToProcessingState() {
        runOnUiThread(() -> {
            uploadProgressText.setText(getString(R.string.processing_percent));
            uploadStatusLabel.setText(getString(R.string.processing_label));
            uploadProgressBar.setIndeterminate(true);
        });
    }
}

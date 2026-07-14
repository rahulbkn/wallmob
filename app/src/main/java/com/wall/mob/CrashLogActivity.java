package com.wall.mob;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class CrashLogActivity extends BaseActivity {
    
    private TextView crashLogTextView;
    private ScrollView scrollView;
    private Button copyButton;
    private Button restartButton;
    private Button viewAllLogsButton;
    private String crashLog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_log);
        
        // Initialize views
        crashLogTextView = findViewById(R.id.crash_log_text);
        scrollView = findViewById(R.id.crash_log_scroll);
        copyButton = findViewById(R.id.btn_copy_log);
        restartButton = findViewById(R.id.btn_restart_app);
        viewAllLogsButton = findViewById(R.id.btn_view_all_logs);
        
        // Get crash log from intent
        crashLog = getIntent().getStringExtra("crash_log");
        
        if (crashLog != null && !crashLog.isEmpty()) {
            crashLogTextView.setText(crashLog);
        } else {
            crashLogTextView.setText("No crash log available");
        }
        
        // Setup button listeners
        setupButtons();
    }
    
    private void setupButtons() {
        // Copy log to clipboard
        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Crash Log", crashLog);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        
        // Restart app
        restartButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        
        // View all crash logs
        viewAllLogsButton.setOnClickListener(v -> {
            showAllCrashLogs();
        });
    }
    
    /**
     * Show all saved crash logs
     */
    private void showAllCrashLogs() {
        File crashDir = CrashHandler.getCrashLogsDir(this);
        File[] logFiles = crashDir.listFiles();
        
        if (logFiles == null || logFiles.length == 0) {
            Toast.makeText(this, "No crash logs found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Sort by last modified (newest first)
        java.util.Arrays.sort(logFiles, (f1, f2) -> 
            Long.compare(f2.lastModified(), f1.lastModified()));
        
        // Build list of all logs
        StringBuilder allLogs = new StringBuilder();
        allLogs.append("=== ALL CRASH LOGS (").append(logFiles.length).append(" total) ===\n\n");
        
        for (int i = 0; i < logFiles.length; i++) {
            File logFile = logFiles[i];
            allLogs.append("--- Log ").append(i + 1).append(" (")
                   .append(logFile.getName()).append(") ---\n");
            
            try {
                String content = readFile(logFile);
                allLogs.append(content);
            } catch (Exception e) {
                allLogs.append("Error reading log: ").append(e.getMessage());
            }
            
            allLogs.append("\n\n");
        }
        
        crashLogTextView.setText(allLogs.toString());
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
    }
    
    /**
     * Read file content
     */
    private String readFile(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.FileReader(file));
        
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        
        return content.toString();
    }
    
    @Override
    public void onBackPressed() {
        restartButton.performClick();
        super.onBackPressed();
    }
}

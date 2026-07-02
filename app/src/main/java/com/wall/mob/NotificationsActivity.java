package com.wall.mob;

import android.content.Context;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;
import android.os.AsyncTask;

public class NotificationsActivity extends BaseActivity {


    private static final String TAG = "NotificationsActivity";
    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private View emptyView;
    private List<NotificationItem> notifications;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notifications);
        
        // Status bar setup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ThemeUtils.applySystemBars(this);
        }

        initializeViews();
        setupRecyclerView();
        
        // Load notifications asynchronously
        new LoadNotificationsTask().execute();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progressBar);
        
        // Back button
        findViewById(R.id.back_button).setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });
        
        // Clear all button
        findViewById(R.id.clear_all_button).setOnClickListener(v -> clearAllNotifications());
    }

    private void setupRecyclerView() {
        notifications = new ArrayList<>();
        adapter = new NotificationAdapter(notifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    // AsyncTask to load notifications in background
    private class LoadNotificationsTask extends AsyncTask<Void, Void, List<NotificationItem>> {
        
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
        }
        
        @Override
        protected List<NotificationItem> doInBackground(Void... voids) {
            List<NotificationItem> loadedNotifications = new ArrayList<>();
            
            try {
                SharedPreferences prefs = getSharedPreferences("notifications", MODE_PRIVATE);
                Map<String, ?> allNotifications = prefs.getAll();
                
                Log.d(TAG, "Found " + allNotifications.size() + " stored notifications");
                
                for (Map.Entry<String, ?> entry : allNotifications.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue().toString();
                    
                    // Parse notification data
                    String[] parts = value.split("\\|", 5); // Limit split to 5 parts
                    if (parts.length >= 3) {
                        try {
                            String title = parts[0];
                            String message = parts[1];
                            long timestamp = Long.parseLong(parts[2]);
                            String type = parts.length > 3 ? parts[3] : "unknown";
                            String extraData = parts.length > 4 ? parts[4] : "";
                            
                            NotificationItem item = new NotificationItem(
                                title, message, timestamp, type, extraData
                            );
                            loadedNotifications.add(item);
                            
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing timestamp for: " + key, e);
                        }
                    }
                }
                
                // Sort by timestamp (newest first)
                Collections.sort(loadedNotifications, (a, b) -> Long.compare(b.timestamp, a.timestamp));
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading notifications", e);
            }
            
            return loadedNotifications;
        }
        
        @Override
        protected void onPostExecute(List<NotificationItem> result) {
            notifications.clear();
            notifications.addAll(result);
            
            progressBar.setVisibility(View.GONE);
            updateUI();
        }
    }

    private void updateUI() {
        if (notifications.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
        
        Log.d(TAG, "Updated UI with " + notifications.size() + " notifications");
    }

    private void clearAllNotifications() {
        try {
            SharedPreferences prefs = getSharedPreferences("notifications", MODE_PRIVATE);
            prefs.edit().clear().apply();
            
            notifications.clear();
            updateUI();
            
            Log.d(TAG, "All notifications cleared");
            
        } catch (Exception e) {
            Log.e(TAG, "Error clearing notifications", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh notifications when returning to activity
        new LoadNotificationsTask().execute();
    }
}
// test

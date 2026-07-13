package com.wall.mob;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminActivity extends AppCompatActivity {

    private static final String TAG = "AdminActivity";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;
    private Toolbar toolbar;

    private AdminAdapter adapter;
    private List<WallpaperEntry> wallpaperList = new ArrayList<>();
    private DatabaseReference newlyAddedRef;
    private String currentUserEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        SessionManager sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn() || sessionManager.isGuest()) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserEmail = sessionManager.getEmail();

        initializeViews();
        loadWallpapers();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progress_bar);
        emptyState = findViewById(R.id.empty_state);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminAdapter();
        recyclerView.setAdapter(adapter);

        newlyAddedRef = FirebaseDatabase.getInstance().getReference("wallpapers/newly_added");
    }

    private void loadWallpapers() {
        showLoading();

        newlyAddedRef.orderByChild("addedAt").limitToLast(200).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                wallpaperList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        Map<String, Object> map = (Map<String, Object>) child.getValue();
                        if (map == null) continue;

                        String uploaderId = (String) map.get("uploaderId");
                        if (uploaderId == null || !uploaderId.equals(currentUserEmail)) continue;

                        WallpaperEntry entry = new WallpaperEntry();
                        entry.firebaseKey = child.getKey();
                        entry.imageUrl = (String) map.get("imageUrl");
                        entry.thumbnailUrl = (String) map.get("thumbnailUrl");
                        entry.title = (String) map.get("title");
                        entry.category = (String) map.get("category");
                        entry.photographer = (String) map.get("photographer");
                        entry.source = (String) map.get("source");
                        entry.telegramFileId = (String) map.get("telegramFileId");
                        entry.fileUniqueId = (String) map.get("fileUniqueId");
                        Object premiumObj = map.get("premium");
                        entry.premium = premiumObj instanceof Boolean && (Boolean) premiumObj;
                        Object widthObj = map.get("width");
                        entry.width = widthObj instanceof Long ? ((Long) widthObj).intValue() : 0;
                        Object heightObj = map.get("height");
                        entry.height = heightObj instanceof Long ? ((Long) heightObj).intValue() : 0;
                        Object addedAtObj = map.get("addedAt");
                        entry.addedAt = addedAtObj instanceof Long ? (Long) addedAtObj : 0;
                        entry.uploaderId = uploaderId;
                        entry.chatId = (String) map.get("chatId");
                        entry.categorized = map.containsKey("categorized") ? Boolean.TRUE.equals(map.get("categorized")) : true;

                        wallpaperList.add(0, entry);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing: " + child.getKey(), e);
                    }
                }
                adapter.notifyDataSetChanged();
                hideLoading();

                if (wallpaperList.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed: " + error.getMessage());
                hideLoading();
                Toast.makeText(AdminActivity.this, "Failed to load: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteWallpaper(WallpaperEntry entry, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Wallpaper")
                .setMessage("Delete \"" + (entry.title != null ? entry.title : "untitled") + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    String key = entry.firebaseKey;
                    if (key == null) return;

                    newlyAddedRef.child(key).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                if (entry.fileUniqueId != null) {
                                    DatabaseReference fileIndexRef = FirebaseDatabase.getInstance()
                                            .getReference("wallpapers/file_index/" + entry.fileUniqueId);
                                    fileIndexRef.removeValue();
                                }
                                wallpaperList.remove(position);
                                adapter.notifyItemRemoved(position);
                                Toast.makeText(AdminActivity.this, "Deleted", Toast.LENGTH_SHORT).show();

                                if (wallpaperList.isEmpty()) {
                                    emptyState.setVisibility(View.VISIBLE);
                                    recyclerView.setVisibility(View.GONE);
                                }
                            })
                            .addOnFailureListener(e -> Toast.makeText(AdminActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog(WallpaperEntry entry, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Wallpaper");

        View view = getLayoutInflater().inflate(R.layout.dialog_edit_wallpaper, null);
        EditText etTitle = view.findViewById(R.id.et_edit_title);
        EditText etCategory = view.findViewById(R.id.et_edit_category);
        EditText etPhotographer = view.findViewById(R.id.et_edit_photographer);
        EditText etSource = view.findViewById(R.id.et_edit_source);

        etTitle.setText(entry.title != null ? entry.title : "");
        etCategory.setText(entry.category != null ? entry.category : "");
        etPhotographer.setText(entry.photographer != null ? entry.photographer : "");
        etSource.setText(entry.source != null ? entry.source : "");

        builder.setView(view);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String rawTitle = etTitle.getText().toString().trim();
            String rawCategory = etCategory.getText().toString().trim();
            String rawPhotographer = etPhotographer.getText().toString().trim();
            String rawSource = etSource.getText().toString().trim();

            final String finalTitle = TextUtils.isEmpty(rawTitle) ? "Untitled" : rawTitle;
            final String finalCategory = TextUtils.isEmpty(rawCategory) ? "General" : rawCategory;
            final String finalPhotographer = rawPhotographer;
            final String finalSource = rawSource;

            Map<String, Object> updates = new HashMap<>();
            updates.put("title", finalTitle);
            updates.put("category", finalCategory);
            updates.put("photographer", finalPhotographer);
            updates.put("source", finalSource);

            String key = entry.firebaseKey;
            if (key != null) {
                int pos = position;
                newlyAddedRef.child(key).updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            WallpaperEntry e = wallpaperList.get(pos);
                            e.title = finalTitle;
                            e.category = finalCategory;
                            e.photographer = finalPhotographer;
                            e.source = finalSource;
                            adapter.notifyItemChanged(pos);
                            Toast.makeText(AdminActivity.this, "Updated", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(AdminActivity.this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    private static class WallpaperEntry {
        String firebaseKey;
        String imageUrl;
        String thumbnailUrl;
        String title;
        String category;
        String photographer;
        String source;
        String telegramFileId;
        String fileUniqueId;
        boolean premium;
        int width;
        int height;
        long addedAt;
        String uploaderId;
        String chatId;
        boolean categorized;
    }

    private class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(AdminActivity.this).inflate(R.layout.item_admin_wallpaper, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            WallpaperEntry entry = wallpaperList.get(position);

            String displayTitle = entry.title != null ? entry.title : "Untitled";
            if (entry.premium) displayTitle += " ✦";

            holder.title.setText(displayTitle);
            holder.category.setText(entry.category != null ? entry.category : "General");
            holder.photographer.setText(entry.photographer != null ? entry.photographer : "Unknown");

            String imgUrl = entry.thumbnailUrl != null ? entry.thumbnailUrl : entry.imageUrl;
            Glide.with(AdminActivity.this)
                    .load(imgUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg)
                    .error(R.drawable.error_image)
                    .into(holder.image);

            holder.editBtn.setOnClickListener(v -> showEditDialog(entry, position));
            holder.deleteBtn.setOnClickListener(v -> deleteWallpaper(entry, position));
            holder.itemView.setOnClickListener(v -> showEditDialog(entry, position));
        }

        @Override
        public int getItemCount() {
            return wallpaperList.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView image, editBtn, deleteBtn;
            TextView title, category, photographer;

            VH(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.admin_image);
                editBtn = itemView.findViewById(R.id.admin_edit);
                deleteBtn = itemView.findViewById(R.id.admin_delete);
                title = itemView.findViewById(R.id.admin_title);
                category = itemView.findViewById(R.id.admin_category);
                photographer = itemView.findViewById(R.id.admin_photographer);
            }
        }
    }
}

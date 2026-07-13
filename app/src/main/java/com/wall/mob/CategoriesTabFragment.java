package com.wall.mob;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CategoriesTabFragment extends Fragment implements CategoryGridAdapter.OnCategoryClickListener {

    private static final String TAG = "CategoriesTabFragment";

    private RecyclerView recyclerView;
    private CategoryGridAdapter categoryAdapter;
    private List<CategoryItem> categoryList = new ArrayList<>();
    private ProgressBar progressBar;
    private View emptyState;
    private DatabaseReference firebaseRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_category, container, false);
        initializeViews(view);
        loadCategories();
        return view;
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyState = view.findViewById(R.id.empty_state);

        firebaseRef = FirebaseDatabase.getInstance().getReference("wallpapers/newly_added");

        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        recyclerView.setLayoutManager(layoutManager);
        categoryAdapter = new CategoryGridAdapter(requireContext(), categoryList, this);
        recyclerView.setAdapter(categoryAdapter);
    }

    private void loadCategories() {
        showLoading();

        firebaseRef.orderByChild("addedAt").limitToLast(200).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, String> categoryImageMap = new LinkedHashMap<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        Map<String, Object> map = (Map<String, Object>) child.getValue();
                        if (map == null) continue;

                        String category = (String) map.get("category");
                        String imageUrl = (String) map.get("imageUrl");

                        if (category != null && !category.trim().isEmpty() && imageUrl != null) {
                            if (!categoryImageMap.containsKey(category)) {
                                categoryImageMap.put(category, imageUrl);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing snapshot", e);
                    }
                }

                categoryList.clear();
                for (Map.Entry<String, String> entry : categoryImageMap.entrySet()) {
                    categoryList.add(new CategoryItem(entry.getKey(), entry.getValue()));
                }
                categoryAdapter.updateData(categoryList);
                hideLoading();

                if (categoryList.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load categories: " + error.getMessage());
                hideLoading();
                emptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onCategoryClick(CategoryItem category) {
        if (category != null && isAdded()) {
            CategoryActivity.start(requireContext(), category.getName(), category.getImageUrl());
        }
    }

    public void refreshData() {
        loadCategories();
    }
}

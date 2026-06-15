package com.wall.mob;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.FrameLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

public class MenuBottomSheetDialog extends BottomSheetDialog {

    private final Context context;

    public MenuBottomSheetDialog(Context context) {
        super(context, R.style.BottomSheetDialogTheme);
        this.context = context;
    }
@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bottom_sheet);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && getWindow() != null) {
            getWindow().setNavigationBarColor(Color.WHITE);
        }

        // --- THE MAGIC FIX: Wait until the dialog shows, then force the width! ---
        setOnShowListener(dialogInterface -> {
    BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
    FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);

    if (bottomSheet != null) {
        android.view.ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
        lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        bottomSheet.setLayoutParams(lp);
        bottomSheet.setBackgroundResource(android.R.color.transparent);

        com.google.android.material.bottomsheet.BottomSheetBehavior<FrameLayout> behavior =
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
        behavior.setMaxWidth(-1);
    }
});
        // --------------------------------------------------------------------------

        // Initialize your views
        LinearLayout websiteItem = findViewById(R.id.menu_website);
        LinearLayout updateItem = findViewById(R.id.menu_update);
        LinearLayout telegramItem = findViewById(R.id.menu_telegram);
        LinearLayout contactItem = findViewById(R.id.menu_contact);
        LinearLayout privacyItem = findViewById(R.id.menu_privacy);
        LinearLayout termsItem = findViewById(R.id.menu_terms);

        if (websiteItem != null) {
            websiteItem.setOnClickListener(v -> {
                openWebsite("https://wallmob.pages.dev/");
                dismiss();
            });
        }

        if (updateItem != null) {
            updateItem.setOnClickListener(v -> {
                Toast.makeText(context, "Already using latest version", Toast.LENGTH_SHORT).show();
                dismiss();
            });
        }

        if (telegramItem != null) {
            telegramItem.setOnClickListener(v -> {
                openTelegram("https://t.me/wallmobofficial");
                dismiss();
            });
        }

        if (contactItem != null) {
            contactItem.setOnClickListener(v -> {
                contactUs();
                dismiss();
            });
        }

        if (privacyItem != null) {
            privacyItem.setOnClickListener(v -> {
                openWebsite("https://wallmob.pages.dev/privacy_policy");
                dismiss();
            });
        }

        if (termsItem != null) {
            termsItem.setOnClickListener(v -> {
                openWebsite("https://wallmob.pages.dev/terms_of_service");
                dismiss();
            });
        }
    }

    private void openWebsite(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTelegram(String telegramUrl) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl));
            intent.setPackage("org.telegram.messenger");
            context.startActivity(intent);
        } catch (Exception e) {
            openWebsite(telegramUrl);
        }
    }

    private void contactUs() {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:rahulkumarbknv@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "App Support");
            context.startActivity(Intent.createChooser(intent, "Contact Us"));
        } catch (Exception e) {
            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
protected void onStart() {
    super.onStart();
  if (getWindow() != null) {
    getWindow().setLayout(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT
    );
    getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
    
    // White nav bar with black icons
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        getWindow().setNavigationBarColor(Color.WHITE);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        android.view.View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            decorView.getSystemUiVisibility() | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }}
}
}
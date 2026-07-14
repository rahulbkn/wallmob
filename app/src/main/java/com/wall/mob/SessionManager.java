package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

public class SessionManager {
    private static final String PREF_NAME = "UserSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_IS_GUEST = "isGuest";
    private static final String KEY_PHOTO_URL = "photoUrl";

    private final SharedPreferences pref;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void createLoginSession(String email, String fullName, boolean isGuest) {
        pref.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_EMAIL, email)
                .putString(KEY_FULL_NAME, fullName)
                .putBoolean(KEY_IS_GUEST, isGuest)
                .putString(KEY_PHOTO_URL, null)
                .apply();
    }

    public void createLoginSession(String email, String fullName, boolean isGuest, String photoUrl) {
        pref.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_EMAIL, email)
                .putString(KEY_FULL_NAME, fullName)
                .putBoolean(KEY_IS_GUEST, isGuest)
                .putString(KEY_PHOTO_URL, photoUrl)
                .apply();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getEmail() {
        return pref.getString(KEY_EMAIL, null);
    }

    public String getFullName() {
        return pref.getString(KEY_FULL_NAME, null);
    }

    public boolean isGuest() {
        return pref.getBoolean(KEY_IS_GUEST, false);
    }

    public String getPhotoUrl() {
        return pref.getString(KEY_PHOTO_URL, null);
    }

    public String getIdToken() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            try {
                Task<GetTokenResult> task = user.getIdToken(false);
                Tasks.await(task);
                return task.getResult().getToken();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public boolean hasPhotoUrl() {
        String url = getPhotoUrl();
        return url != null && !url.isEmpty();
    }

    public void savePhotoUrl(String photoUrl) {
        pref.edit().putString(KEY_PHOTO_URL, photoUrl).apply();
    }

    public void logoutUser() {
        pref.edit().clear().apply();
    }
}

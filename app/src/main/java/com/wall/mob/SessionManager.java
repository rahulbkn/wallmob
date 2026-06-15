package com.wall.mob;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "UserSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_IS_GUEST = "isGuest";
    
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Context context;
    
    public SessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }
    
    public void createLoginSession(String email, String fullName, boolean isGuest) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putBoolean(KEY_IS_GUEST, isGuest);
        editor.commit();
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
    
    public void logoutUser() {
        editor.clear();
        editor.commit();
    }
}
package com.example.aftersalesmobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

public final class SessionManager {
    private static final String PREFS = "after_sales_session";
    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(JSONObject login) {
        preferences.edit()
                .putString("token", login.optString("token"))
                .putLong("userId", login.optLong("userId"))
                .putString("username", login.optString("username"))
                .putString("realName", login.optString("realName"))
                .putString("roles", login.optJSONArray("roles") == null ? "[]" : login.optJSONArray("roles").toString())
                .apply();
    }

    public String token() { return preferences.getString("token", ""); }
    public String username() { return preferences.getString("username", ""); }
    public String realName() { return preferences.getString("realName", ""); }
    public boolean isLoggedIn() { return !token().isEmpty(); }

    public JSONArray roles() {
        try { return new JSONArray(preferences.getString("roles", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    public boolean hasRole(String role) {
        JSONArray roles = roles();
        for (int i = 0; i < roles.length(); i++) {
            if (role.equals(roles.optString(i))) return true;
        }
        return false;
    }

    public void clear() { preferences.edit().clear().apply(); }
}

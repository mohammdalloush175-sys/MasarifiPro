package com.example.masarifipro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AppLockManager {
    public static final String PREF_NAME = "app_prefs";
    public static final String KEY_APP_LOCK_ENABLED = "app_lock_enabled";
    public static final String KEY_APP_LOCK_PIN_HASH = "app_lock_pin_hash";
    public static final String KEY_APP_LOCK_BIOMETRIC_ENABLED = "app_lock_biometric_enabled";
    public static final String KEY_APP_LOCK_SESSION_UNLOCKED = "app_lock_session_unlocked";
    private static final String KEY_IGNORE_LOCK_UNTIL = "app_lock_ignore_lock_until";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAppLockEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_APP_LOCK_ENABLED, false);
    }

    public static void setAppLockEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply();
    }

    public static boolean isBiometricEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_APP_LOCK_BIOMETRIC_ENABLED, false);
    }

    public static void setBiometricEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_APP_LOCK_BIOMETRIC_ENABLED, enabled).apply();
    }

    public static boolean hasPin(Context context) {
        String pinHash = getPrefs(context).getString(KEY_APP_LOCK_PIN_HASH, "");
        return pinHash != null && !pinHash.isEmpty();
    }

    public static void savePin(Context context, String pin) {
        String hash = hashPin(pin);
        if (hash != null) {
            getPrefs(context).edit()
                    .putString(KEY_APP_LOCK_PIN_HASH, hash)
                    .putBoolean(KEY_APP_LOCK_ENABLED, true)
                    .apply();
        }
    }

    public static boolean verifyPin(Context context, String pin) {
        String savedHash = getPrefs(context).getString(KEY_APP_LOCK_PIN_HASH, null);
        String currentHash = hashPin(pin);
        return savedHash != null && savedHash.equals(currentHash);
    }

    public static void disableLock(Context context) {
        getPrefs(context).edit()
                .putBoolean(KEY_APP_LOCK_ENABLED, false)
                .putBoolean(KEY_APP_LOCK_BIOMETRIC_ENABLED, false)
                .remove(KEY_APP_LOCK_PIN_HASH)
                .apply();
    }

    public static boolean isSessionUnlocked(Context context) {
        boolean unlocked = getPrefs(context).getBoolean(KEY_APP_LOCK_SESSION_UNLOCKED, false);
        Log.d("APP_LOCK", "sessionUnlocked=" + unlocked);
        return unlocked;
    }

    public static void setSessionUnlocked(Context context, boolean unlocked) {
        if (!unlocked) {
            Log.d("APP_LOCK", "app moved to background -> lock session");
        } else {
            Log.d("APP_LOCK", "unlock success -> session unlocked");
        }
        getPrefs(context).edit().putBoolean(KEY_APP_LOCK_SESSION_UNLOCKED, unlocked).apply();
    }

    public static void markUnlocked(Context context) {
        getPrefs(context).edit()
                .putBoolean(KEY_APP_LOCK_SESSION_UNLOCKED, true)
                .apply();
    }

    public static void markLocked(Context context) {
        getPrefs(context).edit()
                .putBoolean(KEY_APP_LOCK_SESSION_UNLOCKED, false)
                .apply();
    }

    public static void ignoreLockTemporarily(Context context) {
        long until = System.currentTimeMillis() + 5000;
        getPrefs(context).edit()
                .putLong(KEY_IGNORE_LOCK_UNTIL, until)
                .apply();
    }

    public static boolean shouldIgnoreLockTemporarily(Context context) {
        long until = getPrefs(context).getLong(KEY_IGNORE_LOCK_UNTIL, 0L);
        return System.currentTimeMillis() < until;
    }

    private static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(pin.getBytes());
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e("AppLockManager", "Hashing error", e);
            return null;
        }
    }
}

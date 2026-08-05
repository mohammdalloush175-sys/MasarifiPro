package com.example.masarifipro.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.masarifipro.activities.MainActivity;
import com.example.masarifipro.database.AppDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserSessionManager {
    private static final String PREFS_NAME = "app_prefs";
    public static final String KEY_CURRENT_USER_UID = "current_user_uid";
    private static final String TAG = "UserSessionManager";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void handleUserChanged(Context context, String newUid) {
        if (newUid == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedUid = prefs.getString(KEY_CURRENT_USER_UID, null);

        if (savedUid == null) {
            // First login or no previous UID saved
            prefs.edit().putString(KEY_CURRENT_USER_UID, newUid).apply();
            Log.d(TAG, "First time saving UID: " + newUid);
        } else if (!savedUid.equals(newUid)) {
            // UID changed! Clear local data
            Log.d(TAG, "User changed from " + savedUid + " to " + newUid + ". Clearing local data...");
            clearUserLocalData(context, savedUid, newUid);
        } else {
            Log.d(TAG, "User UID is the same. No action needed.");
        }
    }

    private static void clearUserLocalData(Context context, String oldUid, String newUid) {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(context);
                
                // 1. Clear all Room tables
                db.transactionDao().deleteAllTransactions();
                db.categoryDao().deleteAllCategories(oldUid != null ? oldUid : "guest");
                db.currencyDao().deleteAllCurrencies();
                db.reminderDao().deleteAll();
                db.debtDao().deleteAll();
                db.syncOperationDao().deleteAll();
                db.sharedTripDao().deleteAll();
                db.sharedTripMemberDao().deleteAll();
                db.sharedTripExpenseDao().deleteAll();
                db.monthlyBudgetDao().deleteAllMonthlyBudgets();
                db.userDao().deleteAll();
                db.sharedAccountDao().deleteAll();

                // 2. Clear user-specific SharedPreferences
                // But KEEP general settings (Language, etc.)
                SharedPreferences appPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                
                // Keep app_language and possibly SYP format
                String lang = appPrefs.getString("app_language", "ar");
                boolean isSypShort = appPrefs.getBoolean("syp_short_format", false);
                
                // Clear app_prefs
                appPrefs.edit().clear().apply();
                
                // Restore essential settings and save new UID
                appPrefs.edit()
                        .putString("app_language", lang)
                        .putBoolean("syp_short_format", isSypShort)
                        .putString(KEY_CURRENT_USER_UID, newUid)
                        .apply();
                
                // Clear other potential user-specific prefs
                context.getSharedPreferences("pending_invite_prefs", Context.MODE_PRIVATE).edit().clear().apply();

                Log.d(TAG, "Local data for previous user cleared successfully.");

                // 3. Redirect to MainActivity to refresh UI and start fresh sync for new user
                Intent intent = new Intent(context, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
                
            } catch (Exception e) {
                Log.e(TAG, "Error clearing local data", e);
            }
        });
    }
}

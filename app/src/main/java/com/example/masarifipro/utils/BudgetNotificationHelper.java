package com.example.masarifipro.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.masarifipro.R;
import com.example.masarifipro.activities.MonthlyBudgetActivity;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.MonthlyBudget;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BudgetNotificationHelper {

    private static final String CHANNEL_ID = "budget_alerts";
    private static final String PREFS_NAME = "budget_alert_prefs";
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static void checkAndNotifyBudgetExceeded(Context context, String currencyCode, String categoryName, long transactionDate) {
        final String finalCategoryName = (categoryName == null || categoryName.isEmpty()) ? "ALL" : categoryName;
        
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(transactionDate);
            int month = cal.get(Calendar.MONTH) + 1;
            int year = cal.get(Calendar.YEAR);

            List<MonthlyBudget> budgets = db.monthlyBudgetDao().getBudgetsForMonthCurrency(month, year, currencyCode);
            if (budgets == null || budgets.isEmpty()) {
                return;
            }

            // Calculate start and end of month
            Calendar monthCal = Calendar.getInstance();
            monthCal.set(Calendar.YEAR, year);
            monthCal.set(Calendar.MONTH, month - 1);
            monthCal.set(Calendar.DAY_OF_MONTH, 1);
            monthCal.set(Calendar.HOUR_OF_DAY, 0);
            monthCal.set(Calendar.MINUTE, 0);
            monthCal.set(Calendar.SECOND, 0);
            monthCal.set(Calendar.MILLISECOND, 0);
            long startDate = monthCal.getTimeInMillis();

            monthCal.set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            monthCal.set(Calendar.HOUR_OF_DAY, 23);
            monthCal.set(Calendar.MINUTE, 59);
            monthCal.set(Calendar.SECOND, 59);
            monthCal.set(Calendar.MILLISECOND, 999);
            long endDate = monthCal.getTimeInMillis();

            for (MonthlyBudget budget : budgets) {
                String budgetCategory = budget.getCategoryName();
                
                // Check if this budget applies: it's "ALL" or it matches the transaction category
                if (budgetCategory.equals("ALL") || budgetCategory.equals(finalCategoryName)) {
                    double spent;
                    if (budgetCategory.equals("ALL")) {
                        spent = db.transactionDao().getMonthlyExpenseTotal(currencyCode, startDate, endDate);
                    } else {
                        spent = db.transactionDao().getMonthlyExpenseTotalByCategory(currencyCode, budgetCategory, startDate, endDate);
                    }

                    if (spent >= budget.getBudgetAmount()) {
                        String prefKey = "budget_exceeded_" + year + "_" + month + "_" + currencyCode + "_" + budgetCategory.replace(" ", "_") + "_" + budget.getBudgetAmount();
                        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        
                        if (!prefs.getBoolean(prefKey, false)) {
                            sendNotification(context, currencyCode, budgetCategory, budget.getBudgetAmount(), spent, month, year);
                            prefs.edit().putBoolean(prefKey, true).apply();
                        }
                    }
                }
            }
        });
    }

    private static void sendNotification(Context context, String currencyCode, String categoryName, double budgetAmount, double spent, int month, int year) {
        createNotificationChannel(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Intent intent = new Intent(context, MonthlyBudgetActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        String title = context.getString(R.string.mb_notification_exceeded_title);
        String body;
        if (categoryName.equals("ALL")) {
            body = context.getString(R.string.mb_notification_exceeded_body, 
                    currencyCode, 
                    NumberFormatter.formatAmount(context, budgetAmount, currencyCode), 
                    NumberFormatter.formatAmount(context, spent, currencyCode));
        } else {
            body = context.getString(R.string.mb_notification_exceeded_body_category, 
                    currencyCode, 
                    categoryName,
                    NumberFormatter.formatAmount(context, budgetAmount, currencyCode), 
                    NumberFormatter.formatAmount(context, spent, currencyCode));
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        // Unique ID per month, currency, category
        int notificationId = (year + month + currencyCode + categoryName).hashCode();
        notificationManager.notify(notificationId, builder.build());
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.mb_notification_channel_name);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}

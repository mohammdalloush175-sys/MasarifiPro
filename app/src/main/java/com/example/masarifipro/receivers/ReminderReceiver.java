package com.example.masarifipro.receivers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.masarifipro.R;
import com.example.masarifipro.activities.CalendarActivity;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.Reminder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "reminders_channel";
    private static final String CHANNEL_NAME = "تذكيرات مالية";

    @Override
    public void onReceive(Context context, Intent intent) {
        int reminderId = intent.getIntExtra("reminder_id", -1);
        if (reminderId == -1) return;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            Reminder reminder = AppDatabase.getDatabase(context).reminderDao().getReminderById(reminderId);
            if (reminder != null && !reminder.isDone()) {
                showNotification(context, reminder);
            }
        });
    }

    private void showNotification(Context context, Reminder reminder) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, CalendarActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, reminder.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String content = reminder.getDescription();
        if (reminder.getAmount() > 0) {
            content = reminder.getTitle() + " - " + String.format("%.2f", reminder.getAmount()) + " " + reminder.getCurrencyCode();
        } else {
            content = reminder.getTitle();
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("تذكير مالي")
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(reminder.getId(), builder.build());
    }
}

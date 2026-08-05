package com.example.masarifipro.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.Reminder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                List<Reminder> activeReminders = AppDatabase.getDatabase(context).reminderDao().getAllActiveReminders();
                for (Reminder reminder : activeReminders) {
                    if (reminder.getReminderTime() > System.currentTimeMillis()) {
                        scheduleNotification(context, reminder);
                    }
                }
            });
        }
    }

    private void scheduleNotification(Context context, Reminder reminder) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("reminder_id", reminder.getId());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, reminder.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.getReminderTime(), pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.getReminderTime(), pendingIntent);
        }
    }
}

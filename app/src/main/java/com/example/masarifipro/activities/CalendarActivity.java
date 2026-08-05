package com.example.masarifipro.activities;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.adapters.ReminderAdapter;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.CurrencyAccount;
import com.example.masarifipro.models.Reminder;
import com.example.masarifipro.receivers.ReminderReceiver;
import com.example.masarifipro.utils.LocaleHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalendarActivity extends AppCompatActivity {

    private static final String TAG = "REMINDER_DEBUG";
    private CalendarView calendarView;
    private TextView tvSelectedDate;
    private RecyclerView rvReminders;
    private TextView tvEmpty;
    private ChipGroup cgFilters;
    private ReminderAdapter adapter;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String selectedDateText;
    private Calendar selectedCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        db = AppDatabase.getDatabase(this);
        
        initViews();
        setupBottomNavigation();
        setupRecyclerView();
        
        selectedDateText = dateFormat.format(new Date());
        tvSelectedDate.setText(getString(R.string.reminders) + ": " + selectedDateText);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedCalendar.set(year, month, dayOfMonth);
            selectedDateText = String.format(Locale.getDefault(), "%02d-%02d-%d", dayOfMonth, month + 1, year);
            tvSelectedDate.setText(getString(R.string.reminders) + ": " + selectedDateText);
            loadReminders();
        });

        cgFilters.setOnCheckedChangeListener((group, checkedId) -> {
            loadReminders();
        });

        findViewById(R.id.fabAddReminder).setOnClickListener(v -> showAddReminderDialog());

        loadReminders();
        checkNotificationPermission();
    }

    private void applyTheme() {
        SharedPreferences themePrefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        boolean isDarkMode = themePrefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void initViews() {
        calendarView = findViewById(R.id.calendarView);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        rvReminders = findViewById(R.id.rvReminders);
        tvEmpty = findViewById(R.id.tvEmpty);
        cgFilters = findViewById(R.id.cgFilters);
    }

    private void setupRecyclerView() {
        rvReminders.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReminderAdapter(new ArrayList<>(), new ReminderAdapter.OnReminderClickListener() {
            @Override
            public void onDeleteClick(Reminder reminder) {
                deleteReminder(reminder);
            }

            @Override
            public void onStatusChange(Reminder reminder, boolean isDone) {
                updateReminderStatus(reminder, isDone);
            }
        });
        rvReminders.setAdapter(adapter);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        applyBottomNavDirection(bottomNavigationView);
        bottomNavigationView.setSelectedItemId(R.id.nav_calendar);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                openActivity(MainActivity.class);
                return true;
            } else if (itemId == R.id.nav_statistics) {
                openActivity(StatisticsActivity.class);
                return true;
            } else if (itemId == R.id.nav_debts) {
                openActivity(DebtActivity.class);
                return true;
            } else if (itemId == R.id.nav_calendar) {
                return true;
            } else if (itemId == R.id.nav_settings) {
                openActivity(SettingsActivity.class);
                return true;
            }
            return false;
        });
    }

    private void applyBottomNavDirection(BottomNavigationView bottomNav) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String lang = prefs.getString("app_language", "ar");

        if ("en".equals(lang)) {
            bottomNav.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            bottomNav.setTextDirection(View.TEXT_DIRECTION_LTR);
        } else {
            bottomNav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            bottomNav.setTextDirection(View.TEXT_DIRECTION_RTL);
        }

        bottomNav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
    }

    private void openActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void loadReminders() {
        executorService.execute(() -> {
            List<Reminder> reminders = db.reminderDao().getRemindersForDate(selectedDateText);
            Log.d(TAG, "selectedDateText=" + selectedDateText);
            Log.d(TAG, "loaded reminders count=" + reminders.size());
            List<Reminder> filteredList = filterReminders(reminders);
            
            runOnUiThread(() -> {
                adapter.setReminders(filteredList);
                if (filteredList.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
            });
        });
    }

    private List<Reminder> filterReminders(List<Reminder> reminders) {
        int checkedId = cgFilters.getCheckedChipId();
        long currentTime = System.currentTimeMillis();
        List<Reminder> filtered = new ArrayList<>();

        for (Reminder r : reminders) {
            if (checkedId == R.id.chipUpcoming) {
                if (r.getReminderTime() > currentTime && !r.isDone()) {
                    filtered.add(r);
                }
            } else if (checkedId == R.id.chipExpired) {
                if (r.getReminderTime() <= currentTime && !r.isDone()) {
                    filtered.add(r);
                }
            } else if (checkedId == R.id.chipCompleted) {
                if (r.isDone()) {
                    filtered.add(r);
                }
            } else {
                // الكل
                filtered.add(r);
            }
        }
        return filtered;
    }

    private void showAddReminderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null);
        builder.setView(view);

        // Apply layout direction to custom view
        if (LocaleHelper.getLanguage(this).equals("ar")) {
            view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        } else {
            view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

        EditText etTitle = view.findViewById(R.id.etReminderTitle);
        EditText etDesc = view.findViewById(R.id.etReminderDesc);
        EditText etAmount = view.findViewById(R.id.etReminderAmount);
        Spinner spinnerCurrency = view.findViewById(R.id.spinnerCurrency);
        Button btnPickDate = view.findViewById(R.id.btnPickDate);
        Button btnPickTime = view.findViewById(R.id.btnPickTime);
        TextView tvDateTime = view.findViewById(R.id.tvSelectedDateTime);
        Button btnSave = view.findViewById(R.id.btnSaveReminder);

        final Calendar reminderCalendar = (Calendar) selectedCalendar.clone();
        // Default time: current time + 5 minutes
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MINUTE, 5);
        reminderCalendar.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
        reminderCalendar.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
        reminderCalendar.set(Calendar.SECOND, 0);
        reminderCalendar.set(Calendar.MILLISECOND, 0);

        updateDateTimeText(tvDateTime, reminderCalendar);

        btnPickDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                reminderCalendar.set(Calendar.YEAR, year);
                reminderCalendar.set(Calendar.MONTH, month);
                reminderCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateDateTimeText(tvDateTime, reminderCalendar);
            }, reminderCalendar.get(Calendar.YEAR), reminderCalendar.get(Calendar.MONTH), reminderCalendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnPickTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view1, hourOfDay, minute) -> {
                reminderCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                reminderCalendar.set(Calendar.MINUTE, minute);
                updateDateTimeText(tvDateTime, reminderCalendar);
            }, reminderCalendar.get(Calendar.HOUR_OF_DAY), reminderCalendar.get(Calendar.MINUTE), false).show();
        });

        executorService.execute(() -> {
            List<CurrencyAccount> activeCurrencies = db.currencyDao().getActiveCurrencies();
            List<String> codes = new ArrayList<>();
            for (CurrencyAccount c : activeCurrencies) codes.add(c.getCurrencyCode());
            runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, codes);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCurrency.setAdapter(adapter);
            });
        });

        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            Log.d(TAG, "Save clicked");
            String title = etTitle.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim();
            double amount = amountStr.isEmpty() ? 0 : Double.parseDouble(amountStr);
            String currency = spinnerCurrency.getSelectedItem() != null ? spinnerCurrency.getSelectedItem().toString() : "";

            Log.d(TAG, "title=" + title);

            if (title.isEmpty()) {
                Toast.makeText(this, R.string.enter_title, Toast.LENGTH_SHORT).show();
                return;
            }

            if (reminderCalendar.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, R.string.reminder_future_error, Toast.LENGTH_SHORT).show();
                return;
            }

            String dateText = dateFormat.format(reminderCalendar.getTime());
            Log.d(TAG, "dateText=" + dateText);
            long reminderTime = reminderCalendar.getTimeInMillis();
            Log.d(TAG, "reminderTime=" + reminderTime);

            Reminder reminder = new Reminder(title, desc, reminderTime, dateText, amount, currency);
            
            executorService.execute(() -> {
                try {
                    long id = db.reminderDao().insert(reminder);
                    Log.d(TAG, "inserted id=" + id);
                    reminder.setId((int) id);
                    scheduleNotification(reminder);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(this, R.string.reminder_saved, Toast.LENGTH_SHORT).show();
                        // Reset filter to All to ensure new reminder is seen if it was hidden by current filter
                        cgFilters.check(R.id.chipAll);
                        loadReminders();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Insert failed", e);
                    runOnUiThread(() -> {
                        Toast.makeText(this, R.string.reminder_save_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        dialog.show();
    }

    private void updateDateTimeText(TextView tv, Calendar calendar) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault());
        tv.setText(getString(R.string.date_time_label) + ": " + sdf.format(calendar.getTime()));
    }

    private void scheduleNotification(Reminder reminder) {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, ReminderReceiver.class);
            intent.putExtra("reminder_id", reminder.getId());
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, reminder.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.getReminderTime(), pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.getReminderTime(), pendingIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling notification", e);
            runOnUiThread(() -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.notification_permission_warning, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void deleteReminder(Reminder reminder) {
        executorService.execute(() -> {
            db.reminderDao().delete(reminder);
            cancelNotification(reminder);
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.reminder_deleted, Toast.LENGTH_SHORT).show();
                loadReminders();
            });
        });
    }

    private void cancelNotification(Reminder reminder) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, reminder.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void updateReminderStatus(Reminder reminder, boolean isDone) {
        executorService.execute(() -> {
            db.reminderDao().markDone(reminder.getId(), isDone);
            if (isDone) {
                cancelNotification(reminder);
            } else if (reminder.getReminderTime() > System.currentTimeMillis()) {
                scheduleNotification(reminder);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.reminder_updated, Toast.LENGTH_SHORT).show();
                loadReminders();
            });
        });
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            applyBottomNavDirection(bottomNavigationView);
            bottomNavigationView.setSelectedItemId(R.id.nav_calendar);
        }
    }
}

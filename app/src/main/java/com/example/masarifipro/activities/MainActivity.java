package com.example.masarifipro.activities;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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
import com.example.masarifipro.adapters.TransactionAdapter;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.CurrencyAccount;
import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.utils.AppLockManager;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.NumberFormatter;
import com.example.masarifipro.utils.SharedTripsSyncManager;
import com.example.masarifipro.utils.SyncManager;
import com.example.masarifipro.utils.TransactionBalanceCalculator;
import com.example.masarifipro.utils.TransactionSyncManager;
import com.example.masarifipro.utils.UserSessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.messaging.FirebaseMessaging;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TX_SYNC";
    private static final String PREFS_NAME = "app_prefs";
    private static final String ACCOUNT_PREFS = "account_prefs";
    private static final String SAVED_ACCOUNTS = "saved_accounts";
    private static final String KEY_GUEST_MODE = "guest_mode";
    private static final String KEY_GUEST_WARNING_SHOWN = "guest_warning_shown";
    private static final String ANNOUNCEMENT_PREFS = "announcement_prefs";
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;

    private TextView tvNetBalance, tvTotalIncome, tvTotalExpense, tvSelectedCurrency;
    private ImageButton btnHideBalance;
    private MaterialButton btnSwitchAccount;
    private ImageView btnSyncStatus;
    private View cardSharedTrips;
    private RecyclerView rvTransactions;
    private TransactionAdapter adapter;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private String selectedCurrency = "USD";
    private boolean isBalanceHidden = false;
    private double totalBalance = 0, totalIncome = 0, totalExpense = 0;

    // Filter fields
    private long filterFromDate = 0;
    private long filterToDate = 0;
    private String filterType = "all";
    private String filterCategory = "";
    private Double filterFromAmount = null;
    private Double filterToAmount = null;

    private FirebaseAuth mAuth;
    private boolean isAnnouncementDialogShowing = false;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private FloatingActionButton fabAddTransaction;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    private void checkAppLock() {
        if (AppLockManager.shouldIgnoreLockTemporarily(this)) {
            Log.d("APP_LOCK", "skip unlock screen because internal change");
            AppLockManager.markUnlocked(this);
            return;
        }

        boolean lockEnabled = AppLockManager.isAppLockEnabled(this);
        boolean hasPin = AppLockManager.hasPin(this);
        boolean sessionUnlocked = AppLockManager.isSessionUnlocked(this);

        if (lockEnabled && hasPin && !sessionUnlocked) {
            Log.d("APP_LOCK", "redirecting to UnlockActivity");
            Intent intent = new Intent(this, UnlockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        checkAppLock();

        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        db = AppDatabase.getDatabase(this);

        SharedPreferences appPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedCurrency = appPrefs.getString("current_currency", "USD");

        initViews();
        setupBottomSheet();
        setupRecyclerView();
        setupListeners();
        setupBottomNavigation();
        checkUserAuth();
        setupFCM();
        requestNotificationPermission();
        // Removed checkForAnnouncements() to hide hints/announcements on MainActivity

        TransactionSyncManager.setSyncCallback(() -> runOnUiThread(this::loadData));
        
        SyncManager.getInstance(this).setStatusListener((pendingCount, errorCount) -> {
            runOnUiThread(() -> {
                if (errorCount > 0) {
                    btnSyncStatus.setColorFilter(Color.parseColor("#F44336")); // Red
                } else if (pendingCount > 0) {
                    btnSyncStatus.setColorFilter(Color.parseColor("#FF9800")); // Orange
                } else {
                    btnSyncStatus.setColorFilter(Color.parseColor("#4CAF50")); // Green
                }
            });
        });
        
        // Initial Sync check
        SyncManager.getInstance(this).sync();
        SharedTripsSyncManager.getInstance(this).sync();
    }

    @Override
    protected void onStart() {
        super.onStart();
        TransactionSyncManager.startListening(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TransactionSyncManager.setSyncCallback(null);
        TransactionSyncManager.stopListening();
    }

    private void setupFCM() {
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM_DEBUG", "Subscribed to all_users");
                    } else {
                        Log.e("FCM_DEBUG", "Subscribe failed", task.getException());
                    }
                });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted");
            } else {
                Log.d(TAG, "Notification permission denied");
            }
        }
    }

    private void applyTheme() {
        SharedPreferences themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        boolean isDarkMode = themePrefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAppLock();

        Log.d("BALANCE_DEBUG", "refresh onResume");
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedCurrency = prefs.getString("current_currency", "USD");
        loadActiveCurrencies();
        if (bottomNavigationView != null) {
            applyBottomNavDirection(bottomNavigationView);
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
        // Removed checkForAnnouncements() from onResume
        SyncManager.getInstance(this).sync();
        SharedTripsSyncManager.getInstance(this).sync();
    }

    private void checkForAnnouncements() {
        // Disabled as per user request to remove hints from MainActivity
    }

    private void showAnnouncementDialog(String id, String title, String body, String seenKey) {
        if (isAnnouncementDialogShowing) return;
        isAnnouncementDialogShowing = true;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(body)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, which) -> {
                    isAnnouncementDialogShowing = false;
                    SharedPreferences prefs = getSharedPreferences(ANNOUNCEMENT_PREFS, MODE_PRIVATE);
                    prefs.edit().putString(seenKey, id).apply();
                })
                .setOnDismissListener(d -> isAnnouncementDialogShowing = false)
                .create();

        dialog.setOnShowListener(d -> {
            if (LocaleHelper.getLanguage(this).equals("ar")) {
                dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            } else {
                dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
        });

        dialog.show();
    }

    private void initViews() {
        tvNetBalance = findViewById(R.id.tvNetBalance);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpense = findViewById(R.id.tvTotalExpense);
        tvSelectedCurrency = findViewById(R.id.tvSelectedCurrency);
        btnHideBalance = findViewById(R.id.btnHideBalance);
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount);
        btnSyncStatus = findViewById(R.id.btnSyncStatus);
        
        if (btnSwitchAccount != null) {
            btnSwitchAccount.setAllCaps(false);
            btnSwitchAccount.setLetterSpacing(0f);
            btnSwitchAccount.setIncludeFontPadding(true);
            btnSwitchAccount.setTypeface(Typeface.DEFAULT_BOLD);
        }

        cardSharedTrips = findViewById(R.id.cardSharedTrips);
        rvTransactions = findViewById(R.id.rvTransactions);
        fabAddTransaction = findViewById(R.id.fabAddTransaction);

        if (fabAddTransaction != null) {
            fabAddTransaction.setVisibility(View.VISIBLE);
            fabAddTransaction.setAlpha(1f);
            fabAddTransaction.bringToFront();
            fabAddTransaction.setElevation(40f);
            fabAddTransaction.setTranslationZ(40f);
        }
    }

    private void setupBottomSheet() {
        View latestTransactionsSheet = findViewById(R.id.latestTransactionsSheet);
        if (latestTransactionsSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(latestTransactionsSheet);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int peekHeight;

            if (screenHeight < dpToPx(720)) {
                peekHeight = dpToPx(240);
            } else if (screenHeight < dpToPx(820)) {
                peekHeight = dpToPx(270);
            } else {
                peekHeight = dpToPx(300);
            }

            bottomSheetBehavior.setPeekHeight(peekHeight);
            Log.d("MAIN_LAYOUT", "screenHeight=" + screenHeight);
            Log.d("MAIN_LAYOUT", "peekHeight=" + peekHeight);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private boolean isGuestMode() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_GUEST_MODE, false);
    }

    private void checkUserAuth() {
        if (isGuestMode()) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean warningShown = prefs.getBoolean(KEY_GUEST_WARNING_SHOWN, false);
            if (!warningShown) {
                Toast.makeText(this, R.string.guest_mode_warning, Toast.LENGTH_LONG).show();
                prefs.edit().putBoolean(KEY_GUEST_WARNING_SHOWN, true).apply();
            }
            if (btnSwitchAccount != null) btnSwitchAccount.setVisibility(View.GONE);
            if (btnSyncStatus != null) btnSyncStatus.setVisibility(View.GONE);
            loadData();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } else {
            saveCurrentAccount(user);
            UserSessionManager.handleUserChanged(this, user.getUid());
            syncFromFirebase();
            TransactionSyncManager.startListening(this);
        }
    }

    private void saveCurrentAccount(FirebaseUser user) {
        if (user == null || user.getEmail() == null) return;
        String email = user.getEmail();
        String provider = "unknown";

        for (UserInfo profile : user.getProviderData()) {
            String providerId = profile.getProviderId();
            if (providerId.equals("google.com")) {
                provider = "google";
                break;
            } else if (profile.getProviderId().equals("password")) {
                provider = "password";
            }
        }

        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE);
        String saved = prefs.getString(SAVED_ACCOUNTS, "");
        List<String> accounts = new ArrayList<>();
        if (!saved.isEmpty()) {
            accounts.addAll(Arrays.asList(saved.split(",")));
        }

        // Remove duplicates/old format
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).startsWith(email + "|") || accounts.get(i).equals(email)) {
                accounts.remove(i);
                break;
            }
        }

        accounts.add(email + "|" + provider);
        prefs.edit().putString(SAVED_ACCOUNTS, String.join(",", accounts)).apply();
    }

    private void setupRecyclerView() {
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        rvTransactions.setAdapter(adapter);

        adapter.setOnItemClickListener(transaction -> {
            Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
            intent.putExtra("transaction_id", transaction.getId());
            intent.putExtra("selected_currency", transaction.getCurrencyCode());
            startActivity(intent);
        });

        adapter.setOnTransactionDeleteListener(this::deleteTransaction);
    }

    private void setupListeners() {
        if (fabAddTransaction != null) {
            fabAddTransaction.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
                intent.putExtra("selected_currency", selectedCurrency);
                startActivity(intent);
            });
        }

        View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> openActivity(SettingsActivity.class));
        }

        View btnFilter = findViewById(R.id.btnFilter);
        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> showFilterDialog());
        }

        btnHideBalance.setOnClickListener(v -> toggleBalanceVisibility());

        if (btnSwitchAccount != null) {
            btnSwitchAccount.setOnClickListener(v -> showAccountSwitcherDialog());
        }

        if (btnSyncStatus != null) {
            btnSyncStatus.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, PendingSyncActivity.class);
                startActivity(intent);
            });
        }

        if (cardSharedTrips != null) {
            cardSharedTrips.setOnClickListener(v -> {
                // Unified: Always go to SharedTripsActivity
                Intent intent = new Intent(MainActivity.this, SharedTripsActivity.class);
                startActivity(intent);
            });
        }
    }

    private void showLoginRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.login_required_title)
                .setMessage(R.string.shared_expenses_login_msg)
                .setPositiveButton(R.string.login, (dialog, which) -> {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_GUEST_MODE, false).apply();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAccountSwitcherDialog() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_switcher, null);
        bottomSheetDialog.setContentView(dialogView);

        if (LocaleHelper.getLanguage(this).equals("ar")) {
            dialogView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        } else {
            dialogView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        String currentEmail = currentUser != null ? currentUser.getEmail() : "";

        // Set Current Account Item
        View currentItemView = dialogView.findViewById(R.id.currentAccountItem);
        TextView tvCurrentEmail = currentItemView.findViewById(R.id.tvAccountEmail);
        TextView tvCurrentLabel = currentItemView.findViewById(R.id.tvCurrentLabel);
        TextView tvCurrentProvider = currentItemView.findViewById(R.id.tvProviderType);

        tvCurrentEmail.setText(currentEmail);
        tvCurrentLabel.setVisibility(View.VISIBLE);

        if (currentUser != null) {
            String provider = "unknown";
            for (UserInfo profile : currentUser.getProviderData()) {
                if (profile.getProviderId().equals("google.com")) {
                    provider = "google";
                    break;
                } else if (profile.getProviderId().equals("password")) {
                    provider = "password";
                }
            }
            setProviderText(tvCurrentProvider, provider);
        }

        currentItemView.setOnClickListener(v -> {
            Toast.makeText(this, R.string.already_logged_in, Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        // Set Saved Accounts List
        LinearLayout llSavedAccounts = dialogView.findViewById(R.id.llSavedAccounts);
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE);
        String saved = prefs.getString(SAVED_ACCOUNTS, "");
        List<String> accounts = new ArrayList<>();
        if (!saved.isEmpty()) {
            accounts.addAll(Arrays.asList(saved.split(",")));
        }

        for (String entry : accounts) {
            String email, provider;
            if (entry.contains("|")) {
                String[] parts = entry.split("\\|");
                email = parts[0];
                provider = parts.length > 1 ? parts[1] : "unknown";
            } else {
                email = entry;
                provider = "unknown";
            }

            if (email.equals(currentEmail)) continue;

            View itemView = LayoutInflater.from(this).inflate(R.layout.item_account_switcher, llSavedAccounts, false);
            TextView tvEmail = itemView.findViewById(R.id.tvAccountEmail);
            TextView tvProvider = itemView.findViewById(R.id.tvProviderType);

            tvEmail.setText(email);
            setProviderText(tvProvider, provider);

            itemView.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                handleAccountSelection(email, provider);
            });

            llSavedAccounts.addView(itemView);
        }

        // Add Account Button
        View btnAddAccount = dialogView.findViewById(R.id.btnAddAccount);
        btnAddAccount.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            addAnotherAccount();
        });

        // Cancel Button
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }

    private void setProviderText(TextView textView, String provider) {
        textView.setVisibility(View.VISIBLE);
        if ("google".equals(provider)) {
            textView.setText("Google");
        } else if ("password".equals(provider)) {
            textView.setText(R.string.password_label);
        } else {
            textView.setText(R.string.unknown_provider);
        }
    }

    private void handleAccountSelection(String email, String provider) {
        if ("google".equals(provider)) {
            switchAccountGoogle();
        } else if ("password".equals(provider)) {
            switchAccountPassword(email);
        } else {
            showProviderChoiceDialog(email);
        }
    }

    private void showProviderChoiceDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_provider)
                .setItems(new String[]{getString(R.string.sign_in_google), getString(R.string.sign_in_password)}, (dialog, which) -> {
                    if (which == 0) {
                        switchAccountGoogle();
                    } else {
                        switchAccountPassword(email);
                    }
                })
                .show();
    }

    private void switchAccountGoogle() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.putExtra("trigger_google_sign_in", true);
        mAuth.signOut();
        startActivity(intent);
        finish();
    }

    private void switchAccountPassword(String email) {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.putExtra("selected_email", email);
        mAuth.signOut();
        startActivity(intent);
        finish();
    }

    private void addAnotherAccount() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.putExtra("add_account_mode", true);
        mAuth.signOut();
        startActivity(intent);
        finish();
    }

    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        applyBottomNavDirection(bottomNavigationView);
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_statistics) {
                openActivity(StatisticsActivity.class);
                return true;
            } else if (itemId == R.id.nav_debts) {
                openActivity(DebtActivity.class);
                return true;
            } else if (itemId == R.id.nav_calendar) {
                openActivity(CalendarActivity.class);
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

    private void loadActiveCurrencies() {
        executorService.execute(() -> {
            // Ensure initial currencies if missing
            if (db.currencyDao().getCurrencyCount() == 0) {
                List<CurrencyAccount> initialList = new ArrayList<>();
                initialList.add(new CurrencyAccount("USD", "US Dollar", "$", 0, null, true));
                initialList.add(new CurrencyAccount("EUR", "Euro", "€", 0, null, true));
                initialList.add(new CurrencyAccount("SAR", "Saudi Riyal", "SR", 0, null, true));
                initialList.add(new CurrencyAccount("AED", "UAE Dirham", "AED", 0, null, true));
                initialList.add(new CurrencyAccount("KWD", "Kuwaiti Dinar", "KD", 0, null, true));
                initialList.add(new CurrencyAccount("SYP", "Syrian Pound", "SYP", 0, null, true));
                initialList.add(new CurrencyAccount("LBP", "Lebanese Pound", "LBP", 0, null, true));

                for (CurrencyAccount c : initialList) {
                    db.currencyDao().insert(c);
                }
            }

            List<CurrencyAccount> activeCurrencies = db.currencyDao().getActiveCurrencies();
            Log.d("CURRENCY_DEBUG", "active count = " + activeCurrencies.size());

            if (activeCurrencies.isEmpty()) {
                CurrencyAccount usd = db.currencyDao().getCurrencyByCode("USD");
                if (usd != null) {
                    usd.setActive(true);
                    db.currencyDao().update(usd);
                    activeCurrencies.add(usd);
                }
            }

            final List<CurrencyAccount> finalActive = activeCurrencies;
            runOnUiThread(() -> {
                ChipGroup chipGroupCurrencies = findViewById(R.id.chipGroupCurrencies);
                if (chipGroupCurrencies == null) return;

                chipGroupCurrencies.removeAllViews();
                boolean currentIsActive = false;

                for (CurrencyAccount currency : finalActive) {
                    Log.d("CURRENCY_DEBUG", "currency active = " + currency.getCurrencyCode());
                    Chip chip = new Chip(this);
                    chip.setText(currency.getCurrencyCode());
                    chip.setCheckable(true);
                    chip.setClickable(true);
                    chip.setId(View.generateViewId());

                    if (currency.getCurrencyCode().equals(selectedCurrency)) {
                        chip.setChecked(true);
                        currentIsActive = true;
                    }

                    chip.setOnClickListener(v -> {
                        selectedCurrency = currency.getCurrencyCode();
                        saveCurrentCurrency(selectedCurrency);
                        loadData();
                    });

                    chipGroupCurrencies.addView(chip);
                }

                if (!currentIsActive && !finalActive.isEmpty()) {
                    selectedCurrency = finalActive.get(0).getCurrencyCode();
                    saveCurrentCurrency(selectedCurrency);
                    if (chipGroupCurrencies.getChildCount() > 0) {
                        ((Chip) chipGroupCurrencies.getChildAt(0)).setChecked(true);
                    }
                }

                loadData();
            });
        });
    }

    private void saveCurrentCurrency(String currencyCode) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString("current_currency", currencyCode)
                .apply();
    }

    private void showFilterDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_filter, null);
        dialog.setContentView(dialogView);

        if (LocaleHelper.getLanguage(this).equals("ar")) {
            dialogView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        } else {
            dialogView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

        TextView tvFromDate = dialogView.findViewById(R.id.tvFromDate);
        TextView tvToDate = dialogView.findViewById(R.id.tvToDate);
        RadioGroup rgTypeFilter = dialogView.findViewById(R.id.rgTypeFilter);
        MaterialAutoCompleteTextView etFilterCategory = dialogView.findViewById(R.id.etFilterCategory);
        EditText etFromAmount = dialogView.findViewById(R.id.etFromAmount);
        EditText etToAmount = dialogView.findViewById(R.id.etToAmount);
        Button btnApplyFilter = dialogView.findViewById(R.id.btnApplyFilter);
        Button btnClearFilter = dialogView.findViewById(R.id.btnClearFilter);
        ImageButton btnClose = dialogView.findViewById(R.id.btnClose);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        if (filterFromDate != 0) tvFromDate.setText(sdf.format(filterFromDate));
        if (filterToDate != 0) tvToDate.setText(sdf.format(filterToDate));
        if (!filterCategory.isEmpty()) etFilterCategory.setText(filterCategory);
        
        if (filterFromAmount != null) etFromAmount.setText(String.valueOf(filterFromAmount));
        if (filterToAmount != null) etToAmount.setText(String.valueOf(filterToAmount));

        if ("income".equals(filterType)) rgTypeFilter.check(R.id.rbIncome);
        else if ("expense".equals(filterType)) rgTypeFilter.check(R.id.rbExpense);
        else rgTypeFilter.check(R.id.rbAll);

        tvFromDate.setOnClickListener(v -> showDatePicker(tvFromDate, true));
        tvToDate.setOnClickListener(v -> showDatePicker(tvToDate, false));
        
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        // Category suggestions
        etFilterCategory.setThreshold(0);
        executorService.execute(() -> {
            List<String> suggestions = db.transactionDao().getAllCategorySuggestions();
            runOnUiThread(() -> {
                ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, R.layout.item_category_suggestion, R.id.tvSuggestion, suggestions);
                etFilterCategory.setAdapter(catAdapter);
            });
        });

        etFilterCategory.setOnClickListener(v -> {
            if (etFilterCategory.getAdapter() != null && etFilterCategory.getAdapter().getCount() > 0) {
                etFilterCategory.post(() -> etFilterCategory.showDropDown());
            }
        });

        etFilterCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && etFilterCategory.getAdapter() != null && etFilterCategory.getAdapter().getCount() > 0) {
                etFilterCategory.post(() -> etFilterCategory.showDropDown());
            }
        });

        btnApplyFilter.setOnClickListener(v -> {
            int checkedId = rgTypeFilter.getCheckedRadioButtonId();
            if (checkedId == R.id.rbIncome) filterType = "income";
            else if (checkedId == R.id.rbExpense) filterType = "expense";
            else filterType = "all";

            filterCategory = etFilterCategory.getText().toString().trim();
            
            String fromStr = etFromAmount.getText().toString().trim().replace(",", "");
            String toStr = etToAmount.getText().toString().trim().replace(",", "");

            try {
                Double fromVal = fromStr.isEmpty() ? null : Double.parseDouble(fromStr);
                Double toVal = toStr.isEmpty() ? null : Double.parseDouble(toStr);

                if (fromVal != null && toVal != null && fromVal > toVal) {
                    Toast.makeText(this, R.string.amount_range_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                filterFromAmount = fromVal;
                filterToAmount = toVal;
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.invalid_number_format, Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d("FILTER_DEBUG", "Selected Filters: FromDate=" + filterFromDate +
                    ", ToDate=" + filterToDate + ", Type=" + filterType + ", Category=" + filterCategory +
                    ", FromAmount=" + filterFromAmount + ", ToAmount=" + filterToAmount);

            loadData();
            dialog.dismiss();
        });

        btnClearFilter.setOnClickListener(v -> {
            filterFromDate = 0;
            filterToDate = 0;
            filterType = "all";
            filterCategory = "";
            filterFromAmount = null;
            filterToAmount = null;
            loadData();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDatePicker(TextView textView, boolean isFromDate) {
        Calendar calendar = Calendar.getInstance();
        if (isFromDate && filterFromDate != 0) calendar.setTimeInMillis(filterFromDate);
        if (!isFromDate && filterToDate != 0) calendar.setTimeInMillis(filterToDate);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth);
            if (isFromDate) {
                selected.set(Calendar.HOUR_OF_DAY, 0);
                selected.set(Calendar.MINUTE, 0);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);
                filterFromDate = selected.getTimeInMillis();
            } else {
                selected.set(Calendar.HOUR_OF_DAY, 23);
                selected.set(Calendar.MINUTE, 59);
                selected.set(Calendar.SECOND, 59);
                selected.set(Calendar.MILLISECOND, 999);
                filterToDate = selected.getTimeInMillis();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            textView.setText(sdf.format(selected.getTimeInMillis()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void loadData() {
        executorService.execute(() -> {
            List<Transaction> allTransactions = db.transactionDao().getTransactionsByCurrency(selectedCurrency);
            Log.d("BALANCE_DEBUG", "fresh transactions count=" + allTransactions.size());
            List<Transaction> filteredTransactions = new ArrayList<>();

            for (Transaction t : allTransactions) {
                boolean matches = true;

                // Filter by type
                if (!"all".equals(filterType) && !filterType.equals(t.getType())) {
                    matches = false;
                }

                // Filter by date
                if (matches && filterFromDate != 0 && t.getDate() < filterFromDate) {
                    matches = false;
                }
                if (matches && filterToDate != 0 && t.getDate() > filterToDate) {
                    matches = false;
                }

                // Filter by category
                if (matches && !filterCategory.isEmpty()) {
                    if (t.getCategoryName() == null || !t.getCategoryName().toLowerCase().contains(filterCategory.toLowerCase())) {
                        matches = false;
                    }
                }
                
                // Filter by amount
                if (matches && filterFromAmount != null && t.getAmount() < filterFromAmount) {
                    matches = false;
                }
                if (matches && filterToAmount != null && t.getAmount() > filterToAmount) {
                    matches = false;
                }

                if (matches) {
                    filteredTransactions.add(t);
                }
            }

            Log.d("FILTER_DEBUG", "Result size: " + filteredTransactions.size());

            calculateTotals(filteredTransactions);
            runOnUiThread(() -> {
                adapter.setTransactions(filteredTransactions);
                updateUI();
                
                if (filteredTransactions.isEmpty() && (!"all".equals(filterType) || filterFromDate != 0 || filterToDate != 0 || !filterCategory.isEmpty() || filterFromAmount != null || filterToAmount != null)) {
                    Toast.makeText(this, R.string.no_matching_results, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void calculateTotals(List<Transaction> transactions) {
        totalIncome = 0;
        totalExpense = 0;
        Log.d("BALANCE_DEBUG", "selectedCurrency=" + selectedCurrency);
        Log.d("BALANCE_DEBUG", "transactions count=" + (transactions != null ? transactions.size() : 0));

        if (transactions != null) {
            for (Transaction t : transactions) {
                String type = t.getType();
                double amount = t.getAmount();
                String currency = t.getCurrencyCode();

                Log.d("BALANCE_DEBUG", "type=" + type);
                Log.d("BALANCE_DEBUG", "currency=" + currency);
                Log.d("BALANCE_DEBUG", "amount=" + amount);

                if ("income".equals(type) || getString(R.string.income).equals(type)) {
                    totalIncome += amount;
                } else if ("expense".equals(type) || getString(R.string.expense).equals(type)) {
                    totalExpense += amount;
                }
            }
        }
        totalBalance = totalIncome - totalExpense;
        Log.d("BALANCE_DEBUG", "totalIncome=" + totalIncome);
        Log.d("BALANCE_DEBUG", "totalExpense=" + totalExpense);
        Log.d("BALANCE_DEBUG", "netBalance=" + totalBalance);
    }

    private void updateUI() {
        if (tvSelectedCurrency != null) {
            String title = getString(R.string.remaining_balance) + " (" + selectedCurrency + ")";
            tvSelectedCurrency.setText(title);
        }

        if (isBalanceHidden) {
            tvNetBalance.setText("****");
            tvTotalIncome.setText("****");
            tvTotalExpense.setText("****");
        } else {
            tvNetBalance.setText(NumberFormatter.formatAmount(this, totalBalance, selectedCurrency));
            tvTotalIncome.setText(NumberFormatter.formatAmount(this, totalIncome, selectedCurrency));
            tvTotalExpense.setText(NumberFormatter.formatAmount(this, totalExpense, selectedCurrency));
        }
    }

    private void toggleBalanceVisibility() {
        isBalanceHidden = !isBalanceHidden;
        updateUI();
    }

    private void deleteTransaction(Transaction transaction) {
        executorService.execute(() -> {
            try {
                if (!isGuestMode()) {
                    TransactionSyncManager.deleteTransaction(this, transaction);
                }

                db.transactionDao().delete(transaction);
                TransactionBalanceCalculator.recalculateAll(db);

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, R.string.transaction_deleted, Toast.LENGTH_SHORT).show();
                    loadData();
                });

            } catch (Exception e) {
                Log.e(TAG, "Delete transaction error", e);
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, R.string.transaction_delete_failed, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void syncFromFirebase() {
        TransactionSyncManager.refreshFromServer(this, this::loadData);
    }
}

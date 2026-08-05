package com.example.masarifipro.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.masarifipro.R;
import com.example.masarifipro.activities.CategoryManagementActivity;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.CurrencyAccount;
import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.utils.AppLockManager;
import com.example.masarifipro.utils.CsvExporter;
import com.example.masarifipro.utils.CsvImportManager;
import com.example.masarifipro.utils.ExcelExporter;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.PdfExporter;
import com.example.masarifipro.utils.TransactionBalanceCalculator;
import com.example.masarifipro.utils.TransactionSyncManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_SYP_SHORT_FORMAT = "syp_short_format";
    private static final String THEME_PREFS = "theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_GUEST_MODE = "guest_mode";
    private static final int PICK_CSV_FILE = 1001;

    private static final int EXPORT_TYPE_PDF = 0;
    private static final int EXPORT_TYPE_CSV = 1;
    private static final int EXPORT_TYPE_EXCEL = 2;
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initBottomNavigation();
        checkAdminStatus();

        // Account / Profile Button
        findViewById(R.id.btnProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });

        // Dark Mode Switch
        SwitchMaterial switchDarkMode = findViewById(R.id.switchDarkMode);
        SharedPreferences themePrefs = getSharedPreferences(THEME_PREFS, MODE_PRIVATE);
        boolean isDarkMode = themePrefs.getBoolean(KEY_DARK_MODE, false);
        switchDarkMode.setChecked(isDarkMode);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            themePrefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppLockManager.ignoreLockTemporarily(this);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // Language Switcher
        findViewById(R.id.btnLanguage).setOnClickListener(v -> showLanguageDialog());

        // SYP Short Format Switch
        SwitchMaterial switchSypShortFormat = findViewById(R.id.switchSypShortFormat);
        SharedPreferences appPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isSypShort = appPrefs.getBoolean(KEY_SYP_SHORT_FORMAT, false);
        switchSypShortFormat.setChecked(isSypShort);

        switchSypShortFormat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPrefs.edit().putBoolean(KEY_SYP_SHORT_FORMAT, isChecked).apply();
            Toast.makeText(this, R.string.transaction_updated, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnCurrencyManagement).setOnClickListener(v -> {
            startActivity(new Intent(this, CurrencyActivity.class));
        });

        // Category Management Button
        View btnCategoryManagement = findViewById(R.id.btnCategoryManagement);
        if (btnCategoryManagement != null) {
            btnCategoryManagement.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, CategoryManagementActivity.class);
                startActivity(intent);
            });
        }

        findViewById(R.id.btnSharedAccounts).setOnClickListener(v -> {
            if (isGuestMode()) {
                showLoginRequiredDialog();
            } else {
                startActivity(new Intent(this, SharedAccountActivity.class));
            }
        });

        // Monthly Budget Button
        View btnMonthlyBudget = findViewById(R.id.btnMonthlyBudget);
        if (btnMonthlyBudget != null) {
            btnMonthlyBudget.setOnClickListener(v -> {
                startActivity(new Intent(SettingsActivity.this, MonthlyBudgetActivity.class));
            });
        }

        findViewById(R.id.btnSyncCenter).setOnClickListener(v -> {
            startActivity(new Intent(this, SyncCenterActivity.class));
        });

        findViewById(R.id.btnAppUpdates).setOnClickListener(v -> {
            startActivity(new Intent(this, UpdatesActivity.class));
        });

        findViewById(R.id.btnAppLock).setOnClickListener(v -> {
            startActivity(new Intent(this, AppLockSettingsActivity.class));
        });

        findViewById(R.id.btnImportCsv).setOnClickListener(v -> openFilePicker());

        findViewById(R.id.btnExportExcel).setOnClickListener(v -> showCsvOrExcelDialog());

        findViewById(R.id.btnExportPDF).setOnClickListener(v -> showImprovedExportDialog(EXPORT_TYPE_PDF));

        findViewById(R.id.btnAdminPanel).setOnClickListener(v -> {
            startActivity(new Intent(this, AdminNotificationActivity.class));
        });

        Button btnLogout = findViewById(R.id.btnLogout);
        if (isGuestMode()) {
            btnLogout.setText(getString(R.string.login));
        }

        btnLogout.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_GUEST_MODE, false).apply();
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Developer Info Section
        TextView tvPhone = findViewById(R.id.tvDeveloperPhone);
        tvPhone.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:0937956657"));
            startActivity(intent);
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/csv", "text/comma-separated-values", "application/csv"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, PICK_CSV_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CSV_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Log.d("CSV_IMPORT", "IMPORT_CSV file selected: " + uri.getPath());
                startImportFlow(uri);
            }
        }
    }

    private void startImportFlow(Uri uri) {
        executorService.execute(() -> {
            CsvImportManager.CsvData csvData = CsvImportManager.readCsv(this, uri);
            if (csvData.headers.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, R.string.invalid_file, Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> showMappingDialog(csvData));
        });
    }

    private void showMappingDialog(CsvImportManager.CsvData csvData) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_csv_mapping, null);
        Spinner spDate = dialogView.findViewById(R.id.spinnerDate);
        Spinner spAmount = dialogView.findViewById(R.id.spinnerAmount);
        Spinner spType = dialogView.findViewById(R.id.spinnerType);
        Spinner spCurrency = dialogView.findViewById(R.id.spinnerCurrency);
        Spinner spCategory = dialogView.findViewById(R.id.spinnerCategory);
        Spinner spDescription = dialogView.findViewById(R.id.spinnerDescription);
        
        LinearLayout layoutDefaultType = dialogView.findViewById(R.id.layoutDefaultType);
        Spinner spDefaultType = dialogView.findViewById(R.id.spinnerDefaultType);
        
        LinearLayout layoutDefaultCurrency = dialogView.findViewById(R.id.layoutDefaultCurrency);
        Spinner spDefaultCurrency = dialogView.findViewById(R.id.spinnerDefaultCurrency);

        List<String> spinnerOptions = new ArrayList<>();
        spinnerOptions.add(getString(R.string.select_column));
        spinnerOptions.addAll(csvData.headers);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spDate.setAdapter(adapter);
        spAmount.setAdapter(adapter);
        spType.setAdapter(adapter);
        spCurrency.setAdapter(adapter);
        spCategory.setAdapter(adapter);
        spDescription.setAdapter(adapter);

        // Default Type Spinner
        String[] types = {getString(R.string.expense), getString(R.string.income)};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        spDefaultType.setAdapter(typeAdapter);

        // Default Currency Spinner
        String[] currencies = {"SYP", "USD", "EUR", "SAR", "AED", "KWD"};
        ArrayAdapter<String> currAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        spDefaultCurrency.setAdapter(currAdapter);

        // Auto-detect
        spDate.setSelection(CsvImportManager.detectColumn(csvData.headers, CsvImportManager.DATE_KEYWORDS) + 1);
        spAmount.setSelection(CsvImportManager.detectColumn(csvData.headers, CsvImportManager.AMOUNT_KEYWORDS) + 1);
        spType.setSelection(CsvImportManager.detectColumn(csvData.headers, CsvImportManager.TYPE_KEYWORDS) + 1);
        spCurrency.setSelection(CsvImportManager.detectColumn(csvData.headers, CsvImportManager.CURRENCY_KEYWORDS) + 1);
        spCategory.setSelection(CsvImportManager.detectColumn(csvData.headers, CsvImportManager.CATEGORY_KEYWORDS) + 1);
        spDescription.setSelection(CsvImportManager.detectColumn(csvData.headers, CsvImportManager.DESCRIPTION_KEYWORDS) + 1);

        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutDefaultType.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                layoutDefaultCurrency.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.mapping_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.import_btn, (dialog, which) -> {
                    if (spDate.getSelectedItemPosition() == 0 || spAmount.getSelectedItemPosition() == 0) {
                        Toast.makeText(this, R.string.select_date_amount_error, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    CsvImportManager.ColumnMapping mapping = new CsvImportManager.ColumnMapping();
                    mapping.dateIndex = spDate.getSelectedItemPosition() - 1;
                    mapping.amountIndex = spAmount.getSelectedItemPosition() - 1;
                    mapping.typeIndex = spType.getSelectedItemPosition() - 1;
                    mapping.currencyIndex = spCurrency.getSelectedItemPosition() - 1;
                    mapping.categoryIndex = spCategory.getSelectedItemPosition() - 1;
                    mapping.descriptionIndex = spDescription.getSelectedItemPosition() - 1;
                    mapping.defaultType = spDefaultType.getSelectedItemPosition() == 0 ? "expense" : "income";
                    mapping.defaultCurrency = (String) spDefaultCurrency.getSelectedItem();

                    Log.d("CSV_IMPORT", "IMPORT_CSV selected date column: " + spDate.getSelectedItem());
                    Log.d("CSV_IMPORT", "IMPORT_CSV selected amount column: " + spAmount.getSelectedItem());

                    processCsvData(csvData, mapping);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void processCsvData(CsvImportManager.CsvData csvData, CsvImportManager.ColumnMapping mapping) {
        executorService.execute(() -> {
            List<Transaction> validTransactions = new ArrayList<>();
            List<Integer> invalidRowNumbers = new ArrayList<>();
            AppDatabase roomDb = AppDatabase.getDatabase(this);
            
            for (int i = 0; i < csvData.rows.size(); i++) {
                Transaction t = CsvImportManager.parseRow(csvData.rows.get(i), mapping);
                if (t != null) {
                    // Check for duplicate
                    Transaction duplicate = roomDb.transactionDao().findDuplicate(t.getDate(), t.getAmount(), t.getType(), t.getCurrencyCode(), t.getDescription());
                    if (duplicate == null) {
                        validTransactions.add(t);
                    } else {
                        invalidRowNumbers.add(i + 2); // Duplicate treated as skip/invalid for count
                    }
                } else {
                    invalidRowNumbers.add(i + 2); // +2 because CSV is 1-indexed and has header
                }
            }

            Log.d("CSV_IMPORT", "IMPORT_CSV valid rows: " + validTransactions.size());
            Log.d("CSV_IMPORT", "IMPORT_CSV invalid rows: " + invalidRowNumbers.size());

            runOnUiThread(() -> showPreviewDialog(validTransactions, invalidRowNumbers));
        });
    }

    private void showPreviewDialog(List<Transaction> validTransactions, List<Integer> invalidRows) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_preview, null);
        TextView tvValid = dialogView.findViewById(R.id.tvValidCount);
        TextView tvInvalid = dialogView.findViewById(R.id.tvInvalidCount);
        TextView tvPreview = dialogView.findViewById(R.id.tvPreviewData);
        TextView tvInvalidRows = dialogView.findViewById(R.id.tvInvalidRows);

        tvValid.setText(getString(R.string.valid_rows, validTransactions.size()));
        tvInvalid.setText(getString(R.string.invalid_rows, invalidRows.size()));

        StringBuilder previewText = new StringBuilder();
        int previewCount = Math.min(validTransactions.size(), 10);
        for (int i = 0; i < previewCount; i++) {
            Transaction t = validTransactions.get(i);
            previewText.append(t.getType().equals("income") ? "+" : "-")
                    .append(t.getAmount()).append(" ").append(t.getCurrencyCode())
                    .append(" (").append(t.getCategoryName()).append(")\n");
        }
        tvPreview.setText(previewText.toString());

        if (!invalidRows.isEmpty()) {
            tvInvalidRows.setText(getString(R.string.invalid_rows) + ": " + invalidRows.toString());
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.import_preview)
                .setView(dialogView)
                .setPositiveButton(R.string.import_btn, (dialog, which) -> executeImport(validTransactions, invalidRows.size()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void executeImport(List<Transaction> transactions, int invalidCount) {
        if (transactions.isEmpty()) {
            Toast.makeText(this, R.string.no_transactions, Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            AppDatabase roomDb = AppDatabase.getDatabase(this);
            FirebaseUser user = mAuth.getCurrentUser();
            boolean guestMode = isGuestMode();
            String userId = (user != null) ? user.getUid() : "guest_user";
            
            int insertedCount = 0;
            int uploadedCount = 0;

            for (Transaction t : transactions) {
                t.setUserId(userId);
                t.setFirestoreId(UUID.randomUUID().toString());
                t.setBalanceSnapshotEnabled(true);
                t.setSynced(guestMode);
                t.setSyncStatus(guestMode
                        ? Transaction.STATUS_SYNCED
                        : Transaction.STATUS_PENDING);
                long id = roomDb.transactionDao().insert(t);
                t.setId(id);
                insertedCount++;
            }

            TransactionBalanceCalculator.recalculateAll(roomDb);

            if (!guestMode && user != null) {
                for (Transaction t : transactions) {
                    Transaction saved = roomDb.transactionDao().getTransactionById(t.getId());
                    TransactionSyncManager.uploadTransactionToFirestore(
                            getApplicationContext(), saved != null ? saved : t);
                    uploadedCount++;
                }
            }

            final int finalInserted = insertedCount;
            final int finalUploaded = uploadedCount;
            Log.d("CSV_IMPORT", "IMPORT_CSV inserted count: " + finalInserted);
            Log.d("CSV_IMPORT", "IMPORT_CSV uploaded count: " + finalUploaded);

            runOnUiThread(() -> {
                String message;
                if (invalidCount == 0) {
                    message = getString(R.string.import_success, finalInserted);
                } else {
                    message = getString(R.string.import_partial_success, finalInserted, invalidCount);
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showLanguageDialog() {
        String[] languages = {getString(R.string.arabic), getString(R.string.english)};
        int checkedItem = LocaleHelper.getLanguage(this).equals("ar") ? 0 : 1;

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_language)
                .setSingleChoiceItems(languages, checkedItem, (dialog, which) -> {
                    String lang = (which == 0) ? "ar" : "en";
                    if (!lang.equals(LocaleHelper.getLanguage(this))) {
                        AppLockManager.ignoreLockTemporarily(this);
                        LocaleHelper.setNewLocale(this, lang);
                        Toast.makeText(this, R.string.language_changed, Toast.LENGTH_SHORT).show();
                        
                        // Recreate activity to apply changes immediately
                        recreate();
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void checkAdminStatus() {
        if (isGuestMode()) {
            findViewById(R.id.layoutAdmin).setVisibility(View.GONE);
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).collection("profile").document("info")
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                Boolean isAdmin = document.getBoolean("isAdmin");
                                if (isAdmin != null && isAdmin) {
                                    findViewById(R.id.layoutAdmin).setVisibility(View.VISIBLE);
                                } else {
                                    findViewById(R.id.layoutAdmin).setVisibility(View.GONE);
                                }
                            } else {
                                findViewById(R.id.layoutAdmin).setVisibility(View.GONE);
                            }
                        } else {
                            findViewById(R.id.layoutAdmin).setVisibility(View.GONE);
                        }
                    });
        } else {
            findViewById(R.id.layoutAdmin).setVisibility(View.GONE);
        }
    }

    private boolean isGuestMode() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_GUEST_MODE, false);
    }

    private void showLoginRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.login)
                .setMessage(R.string.login_subtitle)
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

    private void initBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        applyBottomNavDirection(bottomNavigationView);
        bottomNavigationView.setSelectedItemId(R.id.nav_settings);
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
                openActivity(CalendarActivity.class);
                return true;
            } else if (itemId == R.id.nav_settings) {
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

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            applyBottomNavDirection(bottomNavigationView);
            bottomNavigationView.setSelectedItemId(R.id.nav_settings);
        }
        checkAdminStatus();
    }

    private long startDate = 0, endDate = 0;

    private void showCsvOrExcelDialog() {
        String[] options = {getString(R.string.export_csv_only), getString(R.string.export_excel_only)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.export_type_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showImprovedExportDialog(EXPORT_TYPE_CSV);
                    } else {
                        showImprovedExportDialog(EXPORT_TYPE_EXCEL);
                    }
                })
                .show();
    }

    private void showImprovedExportDialog(int exportType) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_export_options, null);
        Spinner spCurrency = view.findViewById(R.id.spExportCurrency);
        Spinner spType = view.findViewById(R.id.spExportType);
        Button btnPeriod = view.findViewById(R.id.btnExportPeriod);
        TextView tvPeriod = view.findViewById(R.id.tvSelectedPeriod);

        // Fetch active currencies dynamically
        executorService.execute(() -> {
            List<CurrencyAccount> activeCurrencies = AppDatabase.getDatabase(this).currencyDao().getActiveCurrencies();
            runOnUiThread(() -> {
                List<String> currencies = new ArrayList<>();
                currencies.add(getString(R.string.all_currencies));
                for (CurrencyAccount c : activeCurrencies) {
                    currencies.add(c.getCurrencyCode());
                }
                ArrayAdapter<String> currAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
                currAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spCurrency.setAdapter(currAdapter);
            });
        });

        // Types
        String[] types = {getString(R.string.all_types), getString(R.string.income_only), getString(R.string.expense_only)};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        spType.setAdapter(typeAdapter);

        startDate = 0; endDate = 0;
        btnPeriod.setOnClickListener(v -> {
            MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker().build();
            picker.addOnPositiveButtonClickListener(selection -> {
                startDate = selection.first;
                endDate = selection.second;
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
                tvPeriod.setText(sdf.format(new Date(startDate)) + " - " + sdf.format(new Date(endDate)));
            });
            picker.show(getSupportFragmentManager(), "DATE_PICKER");
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.export_options)
                .setView(view)
                .setPositiveButton(R.string.export_data, (dialog, which) -> {
                    String currency = spCurrency.getSelectedItemPosition() == 0 ? null : (String) spCurrency.getSelectedItem();
                    String type = null;
                    if (spType.getSelectedItemPosition() == 1) type = "income";
                    else if (spType.getSelectedItemPosition() == 2) type = "expense";

                    performExport(exportType, currency, type, startDate, endDate);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performExport(int exportType, String currency, String type, long start, long end) {
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            List<Transaction> transactions;

            if (currency == null) {
                transactions = db.transactionDao().getAllTransactions();
            } else {
                transactions = db.transactionDao().getTransactionsByCurrency(currency);
            }

            List<Transaction> filtered = new ArrayList<>();
            double totalIncome = 0, totalExpense = 0;
            for (Transaction t : transactions) {
                boolean matchType = (type == null || type.equals(t.getType()));
                boolean matchDate = (start == 0 || (t.getDate() >= start && t.getDate() <= end));
                if (matchType && matchDate) {
                    filtered.add(t);
                    if ("income".equals(t.getType())) totalIncome += t.getAmount();
                    else totalExpense += t.getAmount();
                }
            }

            final List<Transaction> finalTransactions = filtered;
            final double finalIncome = totalIncome;
            final double finalExpense = totalExpense;
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            String fileName = "Masarifi_" + sdf.format(new Date());
            String period = (start == 0) ? getString(R.string.all) : (new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date(start)) + " - " + new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(new Date(end)));

            runOnUiThread(() -> {
                if (exportType == EXPORT_TYPE_CSV) {
                    CsvExporter.exportTransactions(this, finalTransactions, fileName, new CsvExporter.ExportCallback() {
                        @Override public void onSuccess(Uri uri) { showPostExportActions(uri, "text/csv"); }
                        @Override public void onFailure(Exception e) { Toast.makeText(SettingsActivity.this, R.string.export_failed, Toast.LENGTH_SHORT).show(); }
                    });
                } else if (exportType == EXPORT_TYPE_EXCEL) {
                    ExcelExporter.exportTransactions(this, finalTransactions, fileName, new ExcelExporter.ExportCallback() {
                        @Override public void onSuccess(Uri uri) { 
                            Toast.makeText(SettingsActivity.this, R.string.export_success, Toast.LENGTH_SHORT).show();
                            showPostExportActions(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); 
                        }
                        @Override public void onFailure(Exception e) { Toast.makeText(SettingsActivity.this, R.string.export_failed, Toast.LENGTH_SHORT).show(); }
                    });
                } else {
                    PdfExporter.exportTransactions(this, finalTransactions, fileName, currency, finalIncome, finalExpense, period, new PdfExporter.ExportCallback() {
                        @Override public void onSuccess(Uri uri) { showPostExportActions(uri, "application/pdf"); }
                        @Override public void onFailure(Exception e) { Toast.makeText(SettingsActivity.this, R.string.pdf_export_failed, Toast.LENGTH_SHORT).show(); }
                    });
                }
            });
        });
    }

    private void showPostExportActions(Uri uri, String mimeType) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.export_success)
                .setItems(new String[]{getString(R.string.open_file), getString(R.string.share)}, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, mimeType);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, getString(R.string.open_file)));
                    } else {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType(mimeType);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, getString(R.string.share)));
                    }
                })
                .show();
    }
}

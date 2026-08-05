package com.example.masarifipro.activities;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.masarifipro.R;
import com.example.masarifipro.adapters.CategoryDropdownAdapter;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.Category;
import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.utils.BudgetNotificationHelper;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.NumberFormatter;
import com.example.masarifipro.utils.TransactionBalanceCalculator;
import com.example.masarifipro.utils.TransactionSyncManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AddTransactionActivity extends AppCompatActivity {

    private static final String TAG = "TX_SYNC";
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_GUEST_MODE = "guest_mode";

    private FirebaseAuth mAuth;
    private FirebaseFirestore dbFirestore;
    private AppDatabase db;

    private TextView tvTitle;
    private MaterialButtonToggleGroup toggleType;
    private TextInputEditText etAmount, etDescription;
    private AutoCompleteTextView etCategory;
    private ChipGroup chipGroupCurrency;
    private TextView tvDate;
    private MaterialButton btnSave;

    // Exchange features
    private LinearLayout layoutExchange, layoutExchangeSection;
    private CheckBox cbExchangeTransfer;
    private Spinner spSourceCurrency;
    private TextInputEditText etSourceAmount, etExchangeRate;
    private TextView tvExchangeResult;

    private final Calendar calendar = Calendar.getInstance();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String selectedType = "expense";
    private String selectedCurrency = "USD";
    private long editTransactionId = -1;
    private Transaction existingTransaction;
    private boolean isFormatting = false;
    private boolean isSaving = false;
    private CategoryDropdownAdapter categoryAdapter;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : "guest";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        // Fix layout direction
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lang = prefs.getString("app_language", "ar");
        View root = findViewById(R.id.addTransactionRoot);
        if (root != null) {
            root.setLayoutDirection("en".equals(lang) ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
            root.setTextDirection("en".equals(lang) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        }

        mAuth = FirebaseAuth.getInstance();
        dbFirestore = FirebaseFirestore.getInstance();
        db = AppDatabase.getDatabase(this);

        editTransactionId = getIntent().getLongExtra("transaction_id", -1);

        initViews();
        setupSpinner();
        setupListeners();
        setupAmountFormatting();
        setupExchangeCalculation();
        setupNavigationListeners();

        if (editTransactionId != -1) {
            loadTransactionData();
            if (tvTitle != null) tvTitle.setText(R.string.edit_transaction);
            btnSave.setText(R.string.save);
            layoutExchange.setVisibility(View.GONE);
        } else {
            updateDateLabel();
            handleDefaultCurrency();
            loadCategorySuggestions();
        }
    }

    private void handleDefaultCurrency() {
        String intentCurrency = getIntent().getStringExtra("selected_currency");
        if (intentCurrency != null && !intentCurrency.isEmpty()) {
            selectedCurrency = intentCurrency;
        } else {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            selectedCurrency = prefs.getString("current_currency", "USD");
        }
        selectCurrencyChip(selectedCurrency);
    }

    private void selectCurrencyChip(String currencyCode) {
        for (int i = 0; i < chipGroupCurrency.getChildCount(); i++) {
            View child = chipGroupCurrency.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.getText().toString().equalsIgnoreCase(currencyCode)) {
                    chip.setChecked(true);
                    selectedCurrency = currencyCode;
                    break;
                }
            }
        }
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvTitle);
        toggleType = findViewById(R.id.toggleType);
        etAmount = findViewById(R.id.etAmount);
        etCategory = findViewById(R.id.etCategory);
        etDescription = findViewById(R.id.etDescription);
        chipGroupCurrency = findViewById(R.id.chipGroupCurrency);
        tvDate = findViewById(R.id.tvDate);
        btnSave = findViewById(R.id.btnSave);

        layoutExchange = findViewById(R.id.layoutExchange);
        layoutExchangeSection = findViewById(R.id.layoutExchangeSection);
        cbExchangeTransfer = findViewById(R.id.cbExchangeTransfer);
        spSourceCurrency = findViewById(R.id.spSourceCurrency);
        etSourceAmount = findViewById(R.id.etSourceAmount);
        etExchangeRate = findViewById(R.id.etExchangeRate);
        tvExchangeResult = findViewById(R.id.tvExchangeResult);

        setupCategorySuggestions();
    }

    private void setupNavigationListeners() {
        etAmount.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etCategory.requestFocus();
                return true;
            }
            return false;
        });

        etCategory.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etDescription.requestFocus();
                return true;
            }
            return false;
        });

        etDescription.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setupCategorySuggestions() {
        categoryAdapter = new CategoryDropdownAdapter(this, new ArrayList<>());
        etCategory.setAdapter(categoryAdapter);
        
        // Show suggestions even without typing (threshold = 0)
        etCategory.setThreshold(0);
        
        // Use default dropdown background or a clean one if available
        // etCategory.setDropDownBackgroundResource(R.drawable.bg_dropdown_suggestions);

        // Show dropdown on click
        etCategory.setOnClickListener(v -> {
            if (!cbExchangeTransfer.isChecked() && categoryAdapter != null && !categoryAdapter.isEmpty()) {
                etCategory.showDropDown();
            }
        });

        // Show dropdown on focus
        etCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !cbExchangeTransfer.isChecked()) {
                etCategory.postDelayed(() -> {
                    if (etCategory.hasFocus() && !isFinishing()) {
                        etCategory.showDropDown();
                    }
                }, 300); // Small delay to ensure keyboard is up and layout adjusted
            }
        });
        
        // Close keyboard when item selected (improves UX)
        etCategory.setOnItemClickListener((parent, view, position, id) -> {
            etCategory.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etCategory.getWindowToken(), 0);
        });
    }

    private void loadCategorySuggestions() {
        String userId = getCurrentUserId();
        executorService.execute(() -> {
            try {
                // Modified: Category suggestions come only from categoryDao.getCategoriesByTypeAndCurrencyByUser
                List<Category> categories = db.categoryDao().getCategoriesByTypeAndCurrencyByUser(userId, selectedType, selectedCurrency);
                List<String> suggestions = new ArrayList<>();
                for (Category category : categories) {
                    if (category.getName() != null && !category.getName().trim().isEmpty()) {
                        suggestions.add(category.getName());
                    }
                }
                Collections.sort(suggestions, String.CASE_INSENSITIVE_ORDER);

                final List<String> finalSuggestions = suggestions;
                runOnUiThread(() -> {
                    if (categoryAdapter != null) {
                        categoryAdapter.clear();
                        categoryAdapter.addAll(finalSuggestions);
                        categoryAdapter.notifyDataSetChanged();
                        
                        // If current focused and has data, show it immediately
                        if (etCategory.hasFocus() && !cbExchangeTransfer.isChecked() && !finalSuggestions.isEmpty()) {
                            etCategory.showDropDown();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading categories", e);
            }
        });
    }

    private void setupSpinner() {
        String[] currencies = {"USD", "EUR", "SAR", "AED", "KWD", "SYP", "LBP"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSourceCurrency.setAdapter(adapter);
    }

    private boolean isGuestMode() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_GUEST_MODE, false);
    }

    private void setupListeners() {
        toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnIncome) {
                    selectedType = "income";
                    layoutExchange.setVisibility(View.VISIBLE);
                } else {
                    selectedType = "expense";
                    layoutExchange.setVisibility(View.GONE);
                    cbExchangeTransfer.setChecked(false);
                }
                loadCategorySuggestions();
            }
        });

        cbExchangeTransfer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutExchangeSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                etCategory.setText(R.string.exchange_category); 
                etCategory.setEnabled(false);
                if (etCategory.isPopupShowing()) etCategory.dismissDropDown();
                calculateExchange();
            } else {
                etCategory.setText("");
                etCategory.setEnabled(true);
                loadCategorySuggestions();
            }
        });

        chipGroupCurrency.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip chip = findViewById(checkedIds.get(0));
                selectedCurrency = chip.getText().toString();
                calculateExchange();
                loadCategorySuggestions();
            }
        });

        spSourceCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { calculateExchange(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        findViewById(R.id.cardDate).setOnClickListener(v -> showDatePicker());
        btnSave.setOnClickListener(v -> saveTransaction());
    }

    private void setupExchangeCalculation() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { calculateExchange(); }
        };
        etSourceAmount.addTextChangedListener(watcher);
        etExchangeRate.addTextChangedListener(watcher);
    }

    private String normalizeNumberInput(String input) {
        if (input == null) return "";
        String result = input.trim();
        result = result.replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
                       .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
                       .replace('٨', '8').replace('٩', '9');
        result = result.replace('۰', '0').replace('۱', '1').replace('۲', '2').replace('۳', '3')
                       .replace('۴', '4').replace('۵', '5').replace('۶', '6').replace('۷', '7')
                       .replace('٨', '8').replace('۹', '9');
        result = result.replace('٫', '.');
        result = result.replace("٬", "").replace(" ", "");
        if (result.contains(",") && !result.contains(".")) {
            int lastComma = result.lastIndexOf(",");
            int digitsAfter = result.length() - lastComma - 1;
            if (digitsAfter == 1 || digitsAfter == 2) result = result.replace(",", ".");
            else result = result.replace(",", "");
        } else result = result.replace(",", "");
        return result;
    }

    private void calculateExchange() {
        if (cbExchangeTransfer.isChecked()) {
            try {
                String srcAmtStr = etSourceAmount.getText().toString();
                String rateStr = etExchangeRate.getText().toString();
                String normalizedSrcAmt = normalizeNumberInput(srcAmtStr);
                String normalizedRate = normalizeNumberInput(rateStr);
                if (!TextUtils.isEmpty(normalizedSrcAmt) && !normalizedRate.isEmpty()) {
                    double srcAmt = Double.parseDouble(normalizedSrcAmt);
                    double rate = Double.parseDouble(normalizedRate);
                    double result = srcAmt * rate;
                    tvExchangeResult.setText(getString(R.string.exchange_result_prefix) + NumberFormatter.format(result, selectedCurrency));
                    isFormatting = true;
                    etAmount.setText(NumberFormatter.formatDecimal(result));
                    isFormatting = false;
                } else tvExchangeResult.setText(getString(R.string.exchange_result_prefix) + "0");
            } catch (Exception ignored) { tvExchangeResult.setText(getString(R.string.exchange_result_prefix) + "0"); }
        }
    }

    private void saveTransaction() {
        if (isSaving) return;
        isSaving = true;
        if (btnSave != null) btnSave.setEnabled(false);

        String amountText = etAmount.getText().toString();
        String normalizedAmount = normalizeNumberInput(amountText);
        String category = etCategory.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        if (normalizedAmount.isEmpty()) {
            showError(R.string.amount_required);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(normalizedAmount);
        } catch (NumberFormatException e) {
            showError(R.string.invalid_number_format);
            return;
        }

        if (amount <= 0) {
            showError(R.string.amount_must_be_positive);
            return;
        }

        if (TextUtils.isEmpty(selectedType) || TextUtils.isEmpty(selectedCurrency)) {
            showError(R.string.fill_required_fields);
            return;
        }

        boolean guestMode = isGuestMode();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null && !guestMode) {
            showError(R.string.login_required_title);
            return;
        }

        SharedPreferences accountPrefs = getSharedPreferences("account_prefs", MODE_PRIVATE);
        String sharedId = guestMode ? null : accountPrefs.getString("shared_account_id", null);
        long timestamp = calendar.getTimeInMillis();
        long now = System.currentTimeMillis();
        String userId = getCurrentUserId();

        if (cbExchangeTransfer.isChecked() && "income".equals(selectedType) && editTransactionId == -1) {
            handleExchangeSave(userId, sharedId, now, timestamp);
        } else {
            handleSingleSave(userId, sharedId, now, timestamp, amount, category, description);
        }
    }

    private void showError(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
        isSaving = false;
        if (btnSave != null) btnSave.setEnabled(true);
    }

    private void handleExchangeSave(String userId, String sharedId, long now, long timestamp) {
        String sourceCurrency = spSourceCurrency.getSelectedItem().toString();
        String srcAmtStr = etSourceAmount.getText().toString();
        String rateStr = etExchangeRate.getText().toString();
        String normalizedSrcAmt = normalizeNumberInput(srcAmtStr);
        String normalizedRate = normalizeNumberInput(rateStr);

        if (TextUtils.isEmpty(normalizedSrcAmt) || TextUtils.isEmpty(normalizedRate)) {
            showError(R.string.fill_required_fields);
            return;
        }

        double srcAmt, rate;
        try {
            srcAmt = Double.parseDouble(normalizedSrcAmt);
            rate = Double.parseDouble(normalizedRate);
        } catch (NumberFormatException e) {
            showError(R.string.invalid_number_format);
            return;
        }

        if (srcAmt <= 0 || rate <= 0 || sourceCurrency.equals(selectedCurrency)) {
            showError(R.string.enter_valid_numbers);
            return;
        }

        double targetAmount = srcAmt * rate;
        String groupId = UUID.randomUUID().toString();
        String formattedSrcAmt = NumberFormatter.formatDecimal(srcAmt);
        String formattedTargetAmt = NumberFormatter.formatDecimal(targetAmount);

        Transaction exp = createBaseTransaction(userId, sharedId, now, timestamp);
        exp.setFirestoreId(UUID.randomUUID().toString());
        exp.setBalanceSnapshotEnabled(true);
        markAsLocalMutation(exp);
        exp.setType("expense");
        exp.setAmount(srcAmt);
        exp.setCurrencyCode(sourceCurrency);
        exp.setCategoryName(getString(R.string.exchange_category));
        exp.setDescription(getString(R.string.exchange) + ": " + formattedSrcAmt + " " + sourceCurrency +
                "\n" + getString(R.string.rate) + ": " + rate +
                "\n" + getString(R.string.result) + ": " + formattedTargetAmt + " " + selectedCurrency);
        configureExchangeFields(exp, groupId, sourceCurrency, selectedCurrency, rate);

        Transaction inc = createBaseTransaction(userId, sharedId, now, timestamp);
        inc.setFirestoreId(UUID.randomUUID().toString());
        inc.setBalanceSnapshotEnabled(true);
        markAsLocalMutation(inc);
        inc.setType("income");
        inc.setAmount(targetAmount);
        inc.setCurrencyCode(selectedCurrency);
        inc.setCategoryName(getString(R.string.exchange_category));
        inc.setDescription(getString(R.string.exchange_result) + ": " + formattedSrcAmt + " " + sourceCurrency +
                "\n" + getString(R.string.rate) + ": " + rate);
        configureExchangeFields(inc, groupId, sourceCurrency, selectedCurrency, rate);

        saveAndSyncExchange(exp, inc);
    }

    private void handleSingleSave(String userId, String sharedId, long now, long timestamp, double amount, String category, String description) {
        Transaction transaction = (editTransactionId == -1) ? new Transaction() : existingTransaction;
        transaction.setUserId(userId);
        transaction.setSharedAccountId(sharedId);
        transaction.setAmount(amount);
        transaction.setCategoryName(category);
        transaction.setDescription(description);
        transaction.setCurrencyCode(selectedCurrency);
        transaction.setType(selectedType);
        transaction.setDate(timestamp);
        transaction.setUpdatedAt(now);

        if (editTransactionId == -1) {
            if (transaction.getFirestoreId() == null || transaction.getFirestoreId().isEmpty()) {
                transaction.setFirestoreId(UUID.randomUUID().toString());
            }
            transaction.setBalanceSnapshotEnabled(true);
            transaction.setCreatedAt(now);
            markAsLocalMutation(transaction);
            saveAndSyncSingle(transaction);
        } else {
            if (transaction.getFirestoreId() == null || transaction.getFirestoreId().isEmpty()) {
                transaction.setFirestoreId(UUID.randomUUID().toString());
            }
            markAsLocalMutation(transaction);
            updateAndSyncSingle(transaction);
        }
    }

    private void markAsLocalMutation(Transaction transaction) {
        boolean shouldSync = !isGuestMode() && mAuth.getCurrentUser() != null;
        transaction.setSynced(!shouldSync);
        transaction.setSyncStatus(shouldSync
                ? Transaction.STATUS_PENDING
                : Transaction.STATUS_SYNCED);
    }

    private Transaction createBaseTransaction(String uid, String sharedId, long now, long date) {
        Transaction t = new Transaction();
        t.setUserId(uid);
        t.setSharedAccountId(sharedId);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t.setDate(date);
        return t;
    }

    private void configureExchangeFields(Transaction t, String gid, String src, String tar, double rate) {
        t.setExchangeTransfer(true);
        t.setExchangeGroupId(gid);
        t.setSourceCurrency(src);
        t.setTargetCurrency(tar);
        t.setExchangeRate(rate);
    }

    private void saveAndSyncSingle(final Transaction t) {
        executorService.execute(() -> {
            try {
                long id = db.transactionDao().insert(t);
                t.setId(id);
                TransactionBalanceCalculator.recalculateAll(db);
                Transaction savedTransaction = db.transactionDao().getTransactionById(id);
                
                if (t.getType().equals("expense")) {
                    BudgetNotificationHelper.checkAndNotifyBudgetExceeded(this, t.getCurrencyCode(), t.getCategoryName(), t.getDate());
                }

                notifyAndFinish();

                checkAndInsertCategory(t.getUserId(), t.getCategoryName(), t.getType(), t.getCurrencyCode());
                TransactionSyncManager.uploadTransactionToFirestore(
                        getApplicationContext(), savedTransaction != null ? savedTransaction : t);
            } catch (Exception e) {
                Log.e(TAG, "Error saving single transaction", e);
                runOnUiThread(() -> showError(R.string.transaction_save_failed));
            }
        });
    }

    private void updateAndSyncSingle(final Transaction t) {
        executorService.execute(() -> {
            try {
                db.transactionDao().update(t);
                TransactionBalanceCalculator.recalculateAll(db);
                Transaction savedTransaction = db.transactionDao().getTransactionById(t.getId());
                
                if (t.getType().equals("expense")) {
                    BudgetNotificationHelper.checkAndNotifyBudgetExceeded(this, t.getCurrencyCode(), t.getCategoryName(), t.getDate());
                }

                notifyAndFinish();

                checkAndInsertCategory(t.getUserId(), t.getCategoryName(), t.getType(), t.getCurrencyCode());
                TransactionSyncManager.uploadTransactionToFirestore(
                        getApplicationContext(), savedTransaction != null ? savedTransaction : t);
            } catch (Exception e) {
                Log.e(TAG, "Error updating single transaction", e);
                runOnUiThread(() -> showError(R.string.transaction_update_failed));
            }
        });
    }

    private void checkAndInsertCategory(String userId, String name, String type, String currency) {
        try {
            if (name == null || name.trim().isEmpty()) return;
            if (name.equalsIgnoreCase(getString(R.string.exchange_category))) return;

            // Modified: Use countDuplicateCategoryByUser and setUserId as requested
            int count = db.categoryDao().countDuplicateCategoryByUser(userId, name, type, currency, 0);
            if (count == 0) {
                Category category = new Category(name, type, currency, null, userId);
                category.setUserId(userId);
                db.categoryDao().insert(category);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error auto-adding category", e);
        }
    }

    private void saveAndSyncExchange(final Transaction exp, final Transaction inc) {
        executorService.execute(() -> {
            try {
                exp.setId(db.transactionDao().insert(exp));
                inc.setId(db.transactionDao().insert(inc));
                TransactionBalanceCalculator.recalculateAll(db);
                Transaction savedExpense = db.transactionDao().getTransactionById(exp.getId());
                Transaction savedIncome = db.transactionDao().getTransactionById(inc.getId());
                
                BudgetNotificationHelper.checkAndNotifyBudgetExceeded(this, exp.getCurrencyCode(), exp.getCategoryName(), exp.getDate());

                notifyAndFinish();

                TransactionSyncManager.uploadTransactionToFirestore(
                        getApplicationContext(), savedExpense != null ? savedExpense : exp);
                TransactionSyncManager.uploadTransactionToFirestore(
                        getApplicationContext(), savedIncome != null ? savedIncome : inc);
            } catch (Exception e) {
                Log.e(TAG, "Error saving exchange", e);
                runOnUiThread(() -> showError(R.string.transaction_save_failed));
            }
        });
    }

    private void notifyAndFinish() {
        runOnUiThread(() -> {
            String msg = LocaleHelper.getLanguage(this).equals("ar") ? 
                    "تم حفظ العملية محلياً، وستتم مزامنتها تلقائياً لاحقاً" : 
                    "Transaction saved locally and will be synced automatically later.";
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });
    }

    private void setupAmountFormatting() {
        etAmount.addTextChangedListener(createCommaSeparator(etAmount));
        etSourceAmount.addTextChangedListener(createCommaSeparator(etSourceAmount));
        etExchangeRate.addTextChangedListener(createCommaSeparator(etExchangeRate));
    }

    private TextWatcher createCommaSeparator(final TextInputEditText editText) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isFormatting || s.toString().isEmpty()) return;
                isFormatting = true;
                try {
                    String clean = normalizeNumberInput(s.toString());
                    double parsed = Double.parseDouble(clean);
                    String formatted = NumberFormatter.formatDecimal(parsed);
                    editText.setText(formatted);
                    editText.setSelection(formatted.length());
                } catch (Exception ignored) {}
                isFormatting = false;
            }
        };
    }

    private void loadTransactionData() {
        executorService.execute(() -> {
            try {
                existingTransaction = db.transactionDao().getTransactionById(editTransactionId);
                if (existingTransaction != null) {
                    runOnUiThread(() -> {
                        etAmount.setText(NumberFormatter.formatDecimal(existingTransaction.getAmount()));
                        etCategory.setText(existingTransaction.getCategoryName());
                        etDescription.setText(existingTransaction.getDescription());
                        selectedType = existingTransaction.getType();
                        selectedCurrency = existingTransaction.getCurrencyCode();
                        calendar.setTimeInMillis(existingTransaction.getDate());
                        updateDateLabel();
                        toggleType.check("income".equals(selectedType) ? R.id.btnIncome : R.id.btnExpense);
                        selectCurrencyChip(selectedCurrency);
                        loadCategorySuggestions();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading transaction data", e);
            }
        });
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, y, m, d) -> {
            calendar.set(y, m, d);
            updateDateLabel();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel() {
        tvDate.setText(new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(calendar.getTime()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

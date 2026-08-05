package com.example.masarifipro.activities;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.masarifipro.R;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.Category;
import com.example.masarifipro.models.CurrencyAccount;
import com.example.masarifipro.models.MonthlyBudget;
import com.example.masarifipro.utils.BudgetNotificationHelper;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.NumberFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonthlyBudgetActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_prefs";
    private static final String HINT_BUDGET_SEEN = "hint_budget_seen";

    private Spinner spMonth, spYear, spCurrency, spCategory;
    private EditText etBudgetAmount;
    private TextView tvBudgetAmount, tvSpentAmount, tvRemainingAmount, tvUsagePercent, tvBudgetWarning, tvBudgetCategory;
    private ProgressBar progressBudget;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private final List<String> currencyCodes = new ArrayList<>();
    private final List<String> categoryNames = new ArrayList<>();
    private MonthlyBudget currentBudget;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, R.string.mb_enable_notifications_permission, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monthly_budget);

        db = AppDatabase.getDatabase(this);
        initViews();
        setupSpinners();
        checkNotificationPermission();
        loadData();
        checkHint();
    }

    private void checkHint() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean seen = prefs.getBoolean(HINT_BUDGET_SEEN, false);
        View hintCard = findViewById(R.id.hintCard);
        if (hintCard != null) {
            if (!seen) {
                hintCard.setVisibility(View.VISIBLE);
                hintCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.hint_enter));
                findViewById(R.id.btnDismissHint).setOnClickListener(v -> {
                    prefs.edit().putBoolean(HINT_BUDGET_SEEN, true).apply();
                    Animation exit = AnimationUtils.loadAnimation(this, R.anim.hint_exit);
                    exit.setAnimationListener(new Animation.AnimationListener() {
                        @Override public void onAnimationStart(Animation animation) {}
                        @Override public void onAnimationRepeat(Animation animation) {}
                        @Override
                        public void onAnimationEnd(Animation animation) {
                            hintCard.setVisibility(View.GONE);
                        }
                    });
                    hintCard.startAnimation(exit);
                });
            } else {
                hintCard.setVisibility(View.GONE);
            }
        }
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        spMonth = findViewById(R.id.spMonth);
        spYear = findViewById(R.id.spYear);
        spCurrency = findViewById(R.id.spCurrency);
        spCategory = findViewById(R.id.spCategory);
        etBudgetAmount = findViewById(R.id.etBudgetAmount);
        tvBudgetAmount = findViewById(R.id.tvBudgetAmount);
        tvSpentAmount = findViewById(R.id.tvSpentAmount);
        tvRemainingAmount = findViewById(R.id.tvRemainingAmount);
        tvUsagePercent = findViewById(R.id.tvUsagePercent);
        tvBudgetWarning = findViewById(R.id.tvBudgetWarning);
        tvBudgetCategory = findViewById(R.id.tvBudgetCategory);
        progressBudget = findViewById(R.id.progressBudget);

        findViewById(R.id.btnSaveBudget).setOnClickListener(v -> saveBudget());

        AdapterView.OnItemSelectedListener changeListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (parent.getId() == R.id.spCurrency) {
                    updateCategories();
                }
                loadData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spMonth.setOnItemSelectedListener(changeListener);
        spYear.setOnItemSelectedListener(changeListener);
        spCurrency.setOnItemSelectedListener(changeListener);
        spCategory.setOnItemSelectedListener(changeListener);
    }

    private void setupSpinners() {
        // Setup Months
        String[] months = new String[12];
        for (int i = 0; i < 12; i++) {
            int resId = getResources().getIdentifier("mb_month_" + (i + 1), "string", getPackageName());
            months[i] = getString(resId);
        }
        CustomDropdownAdapter monthAdapter = new CustomDropdownAdapter(this, Arrays.asList(months));
        spMonth.setAdapter(monthAdapter);

        // Setup Years
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        List<String> years = new ArrayList<>();
        for (int i = currentYear - 2; i <= currentYear + 5; i++) {
            years.add(String.valueOf(i));
        }
        CustomDropdownAdapter yearAdapter = new CustomDropdownAdapter(this, years);
        spYear.setAdapter(yearAdapter);

        // Select current month and year
        spMonth.setSelection(calendar.get(Calendar.MONTH));
        spYear.setSelection(years.indexOf(String.valueOf(currentYear)));

        // Setup Currencies
        executorService.execute(() -> {
            List<CurrencyAccount> activeCurrencies = db.currencyDao().getActiveCurrencies();
            runOnUiThread(() -> {
                currencyCodes.clear();
                for (CurrencyAccount c : activeCurrencies) {
                    currencyCodes.add(c.getCurrencyCode());
                }
                if (currencyCodes.isEmpty()) {
                    currencyCodes.add("USD");
                }
                CustomDropdownAdapter currAdapter = new CustomDropdownAdapter(this, currencyCodes);
                spCurrency.setAdapter(currAdapter);
                
                int sypIndex = currencyCodes.indexOf("SYP");
                if (sypIndex != -1) spCurrency.setSelection(sypIndex);
                
                updateCategories();
            });
        });
    }

    private void updateCategories() {
        if (spCurrency.getSelectedItem() == null) return;
        String currencyCode = spCurrency.getSelectedItem().toString();

        executorService.execute(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String currentUserId = user != null ? user.getUid() : "guest";
            List<Category> categories = db.categoryDao().getCategoriesByTypeAndCurrency(currentUserId, "expense", currencyCode);
            List<String> suggestions = db.transactionDao().getCategorySuggestions("expense", currencyCode);
            
            runOnUiThread(() -> {
                categoryNames.clear();
                categoryNames.add(getString(R.string.mb_all_categories));
                
                for (Category cat : categories) {
                    if (!categoryNames.contains(cat.getName())) {
                        categoryNames.add(cat.getName());
                    }
                }
                for (String name : suggestions) {
                    if (!categoryNames.contains(name)) {
                        categoryNames.add(name);
                    }
                }

                CustomDropdownAdapter catAdapter = new CustomDropdownAdapter(this, categoryNames);
                spCategory.setAdapter(catAdapter);
                spCategory.setSelection(0);
            });
        });
    }

    private void loadData() {
        if (spMonth.getSelectedItem() == null || spYear.getSelectedItem() == null || spCurrency.getSelectedItem() == null || spCategory.getSelectedItem() == null) {
            return;
        }

        int month = spMonth.getSelectedItemPosition() + 1;
        int year = Integer.parseInt(spYear.getSelectedItem().toString());
        String currencyCode = spCurrency.getSelectedItem().toString();
        String selectedCategory = spCategory.getSelectedItem().toString();
        String categoryName = selectedCategory.equals(getString(R.string.mb_all_categories)) ? "ALL" : selectedCategory;

        executorService.execute(() -> {
            MonthlyBudget budget = db.monthlyBudgetDao().getBudget(month, year, currencyCode, categoryName);
            
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startDate = cal.getTimeInMillis();

            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            long endDate = cal.getTimeInMillis();

            double spent;
            if (categoryName.equals("ALL")) {
                spent = db.transactionDao().getMonthlyExpenseTotal(currencyCode, startDate, endDate);
            } else {
                spent = db.transactionDao().getMonthlyExpenseTotalByCategory(currencyCode, categoryName, startDate, endDate);
            }

            runOnUiThread(() -> {
                currentBudget = budget;
                updateUI(budget, spent, currencyCode, categoryName);
            });
        });
    }

    private void updateUI(MonthlyBudget budget, double spent, String currencyCode, String categoryName) {
        String displayCategory = categoryName.equals("ALL") ? getString(R.string.mb_all_categories) : categoryName;
        tvBudgetCategory.setText(getString(R.string.mb_budget_category, displayCategory));

        if (budget != null) {
            etBudgetAmount.setText(formatForInput(budget.getBudgetAmount()));
            tvBudgetAmount.setText(NumberFormatter.formatAmount(this, budget.getBudgetAmount(), currencyCode));
            
            double budgetAmount = budget.getBudgetAmount();
            double remaining = budgetAmount - spent;
            double percent = budgetAmount > 0 ? (spent / budgetAmount) * 100 : 0;

            tvSpentAmount.setText(NumberFormatter.formatAmount(this, spent, currencyCode));
            tvRemainingAmount.setText(NumberFormatter.formatAmount(this, remaining, currencyCode));
            tvRemainingAmount.setTextColor(remaining >= 0 ? ContextCompat.getColor(this, R.color.teal_700) : Color.RED);
            
            tvUsagePercent.setText(String.format(Locale.US, "%.1f%%", percent));
            progressBudget.setProgress((int) Math.min(percent, 100));

            tvBudgetWarning.setVisibility(View.VISIBLE);
            if (percent >= 100) {
                tvBudgetWarning.setText(R.string.mb_over_budget_warning);
                tvBudgetWarning.setTextColor(Color.RED);
                tvBudgetWarning.setBackgroundColor(Color.parseColor("#FFF9F9"));
            } else if (percent >= 80) {
                tvBudgetWarning.setText(R.string.mb_close_to_limit_warning);
                tvBudgetWarning.setTextColor(Color.parseColor("#FFA500")); // Orange
                tvBudgetWarning.setBackgroundColor(Color.parseColor("#FFFDF0"));
            } else {
                tvBudgetWarning.setText(R.string.mb_budget_status_normal);
                tvBudgetWarning.setTextColor(ContextCompat.getColor(this, R.color.teal_700));
                tvBudgetWarning.setBackgroundColor(Color.parseColor("#F9FFFA"));
            }
        } else {
            etBudgetAmount.setText("");
            tvBudgetAmount.setText(getString(R.string.mb_no_budget_set));
            tvSpentAmount.setText(NumberFormatter.formatAmount(this, spent, currencyCode));
            tvRemainingAmount.setText("-");
            tvRemainingAmount.setTextColor(ContextCompat.getColor(this, R.color.teal_700));
            tvUsagePercent.setText("0%");
            progressBudget.setProgress(0);
            tvBudgetWarning.setVisibility(View.GONE);
        }
    }

    private String formatForInput(double amount) {
        if (amount == (long) amount) {
            return String.format(Locale.US, "%,d", (long) amount);
        } else {
            return String.format(Locale.US, "%,.2f", amount);
        }
    }

    private void saveBudget() {
        String amountStr = etBudgetAmount.getText().toString().replace(",", "").replace(" ", "");
        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(this, R.string.mb_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.mb_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount <= 0) {
            Toast.makeText(this, R.string.mb_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }

        int month = spMonth.getSelectedItemPosition() + 1;
        int year = Integer.parseInt(spYear.getSelectedItem().toString());
        String currencyCode = spCurrency.getSelectedItem().toString();
        String selectedCategory = spCategory.getSelectedItem().toString();
        String categoryName = selectedCategory.equals(getString(R.string.mb_all_categories)) ? "ALL" : selectedCategory;

        executorService.execute(() -> {
            long now = System.currentTimeMillis();
            if (currentBudget == null) {
                MonthlyBudget newBudget = new MonthlyBudget();
                newBudget.setMonth(month);
                newBudget.setYear(year);
                newBudget.setCurrencyCode(currencyCode);
                newBudget.setCategoryName(categoryName);
                newBudget.setBudgetAmount(amount);
                newBudget.setCreatedAt(now);
                newBudget.setUpdatedAt(now);
                db.monthlyBudgetDao().insert(newBudget);
            } else {
                currentBudget.setBudgetAmount(amount);
                currentBudget.setUpdatedAt(now);
                db.monthlyBudgetDao().update(currentBudget);
            }
            
            Calendar cal = Calendar.getInstance();
            cal.set(year, month - 1, 1);
            long dateMillis = cal.getTimeInMillis();
            
            BudgetNotificationHelper.checkAndNotifyBudgetExceeded(this, currencyCode, categoryName, dateMillis);
            
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.mb_budget_saved, Toast.LENGTH_SHORT).show();
                loadData();
            });
        });
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private class CustomDropdownAdapter extends ArrayAdapter<String> {
        public CustomDropdownAdapter(Context context, List<String> items) {
            super(context, android.R.layout.simple_spinner_item, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            view.setTextColor(ContextCompat.getColor(getContext(), R.color.purple_700));
            return view;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_mb_category_dropdown, parent, false);
            }
            TextView tv = convertView.findViewById(R.id.tvCategoryName);
            String item = getItem(position);
            tv.setText(item);
            
            if (position == 0 && item != null && (item.equals(getString(R.string.mb_all_categories)) || item.equals(getString(R.string.all)))) {
                tv.setTypeface(null, Typeface.BOLD);
            } else {
                tv.setTypeface(null, Typeface.NORMAL);
            }
            
            return convertView;
        }
    }
}

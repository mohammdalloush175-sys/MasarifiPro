package com.example.masarifipro.activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.adapters.CategoryManagementAdapter;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.Category;
import com.example.masarifipro.models.TransactionCategorySuggestion;
import com.example.masarifipro.utils.LocaleHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CategoryManagementActivity extends AppCompatActivity implements CategoryManagementAdapter.OnCategoryActionListener {

    private static final String TAG = "CategoryMgmt";
    private static final String PREF_FILE = "category_prefs";
    private static final String PREFS_NAME = "app_prefs";
    private static final String HINT_CATEGORIES_SEEN = "hint_categories_seen";
    private static final String KEY_DEFAULTS_SEEDED_PREFIX = "default_categories_seeded_v1_";
    private static final String KEY_IMPORTED_PREFIX = "transaction_categories_imported_v1_";
    private static final String KEY_FIXED_USERID_PREFIX = "categories_missing_userid_fixed_";

    private RecyclerView rvCategories;
    private CategoryManagementAdapter adapter;
    private View tvEmpty;
    private AutoCompleteTextView spFilterType, spFilterCurrency;
    private AppDatabase db;
    private ExecutorService executor;

    private final String[] currencies = {"USD", "SYP", "EUR", "AED", "SAR", "KWD", "LBP"};
    private String selectedFilterType = "ALL";
    private String selectedFilterCurrency = "ALL";
    
    private List<Category> currentCategories = new ArrayList<>();
    private String currentUserId;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    private String getCurrentUserIdFromFirebase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : "guest";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_management);

        db = AppDatabase.getDatabase(this);
        executor = Executors.newSingleThreadExecutor();
        
        currentUserId = getCurrentUserIdFromFirebase();

        initViews();
        setupFilters();
        checkHint();
        
        String seededKey = KEY_DEFAULTS_SEEDED_PREFIX + currentUserId;
        String importedKey = KEY_IMPORTED_PREFIX + currentUserId;
        String fixedUserIdKey = KEY_FIXED_USERID_PREFIX + currentUserId;

        executor.execute(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);

                // Fix missing userId for old categories (once per user)
                boolean fixed = prefs.getBoolean(fixedUserIdKey, false);
                if (!fixed) {
                    db.categoryDao().assignMissingUserIdToCategories(currentUserId);
                    prefs.edit().putBoolean(fixedUserIdKey, true).apply();
                }

                boolean seeded = prefs.getBoolean(seededKey, false);
                boolean imported = prefs.getBoolean(importedKey, false);

                boolean defaultAddedNow = false;

                if (!seeded) {
                    defaultAddedNow = addDefaultCategories(currentUserId);
                    if (defaultAddedNow) {
                        prefs.edit().putBoolean(seededKey, true).apply();
                    }
                }

                if (!imported) {
                    if (importCategoriesFromTransactions(currentUserId)) {
                        prefs.edit().putBoolean(importedKey, true).apply();
                    }
                }

                if (defaultAddedNow) {
                    runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_default_categories_added, Toast.LENGTH_SHORT).show());
                }

                loadCategoriesInternal();
            } catch (Exception e) {
                Log.e(TAG, "Error in initial category setup", e);
                runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_operation_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void checkHint() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean seen = prefs.getBoolean(HINT_CATEGORIES_SEEN, false);
        View hintCard = findViewById(R.id.hintCard);
        if (hintCard != null) {
            if (!seen) {
                hintCard.setVisibility(View.VISIBLE);
                hintCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.hint_enter));
                findViewById(R.id.btnDismissHint).setOnClickListener(v -> {
                    prefs.edit().putBoolean(HINT_CATEGORIES_SEEN, true).apply();
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
        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
        rvCategories = findViewById(R.id.rvCategories);
        tvEmpty = findViewById(R.id.tvEmptyCategories);
        spFilterType = findViewById(R.id.spFilterType);
        spFilterCurrency = findViewById(R.id.spFilterCurrency);

        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CategoryManagementAdapter(this, this);
        rvCategories.setAdapter(adapter);

        findViewById(R.id.btnAddCategory).setOnClickListener(v -> showCategoryDialog(null));
        
        findViewById(R.id.btnDeleteAllCategories).setOnClickListener(v -> showDeleteDisplayedConfirmation());
    }

    private void showDeleteDisplayedConfirmation() {
        List<Category> currentList = adapter.getCategories();
        if (currentList == null || currentList.isEmpty()) return;

        executor.execute(() -> {
            try {
                List<Category> unused = new ArrayList<>();
                List<Category> used = new ArrayList<>();

                for (Category cat : currentList) {
                    int count = db.transactionDao().countTransactionsByCategory(cat.getName(), cat.getType(), cat.getCurrencyCode());
                    if (count > 0) {
                        used.add(cat);
                    } else {
                        unused.add(cat);
                    }
                }

                runOnUiThread(() -> {
                    if (used.isEmpty()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_displayed_categories)
                                .setMessage(R.string.delete_all_categories_confirm)
                                .setPositiveButton(R.string.delete, (dialog, which) -> {
                                    deleteCategories(unused);
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.delete_categories_used_title)
                                .setMessage(R.string.delete_categories_used_message)
                                .setPositiveButton(R.string.delete_used_yes, (dialog, which) -> {
                                    List<Category> allToDelete = new ArrayList<>(unused);
                                    allToDelete.addAll(used);
                                    deleteCategories(allToDelete);
                                })
                                .setNeutralButton(R.string.delete_used_no, (dialog, which) -> {
                                    deleteCategories(unused);
                                })
                                .setNegativeButton(R.string.delete_cancel, null)
                                .show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error checking category usage", e);
                runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_operation_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteCategories(List<Category> categoriesToDelete) {
        if (categoriesToDelete == null || categoriesToDelete.isEmpty()) {
            return;
        }

        executor.execute(() -> {
            try {
                List<Long> deletedIds = new ArrayList<>();
                for (Category cat : categoriesToDelete) {
                    db.categoryDao().deleteByIdAndUser(currentUserId, cat.getId());
                    deletedIds.add(cat.getId());
                }
                
                runOnUiThread(() -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        currentCategories.removeIf(category -> deletedIds.contains(category.getId()));
                    } else {
                        List<Category> toRemove = new ArrayList<>();
                        for (Category c : currentCategories) {
                            if (deletedIds.contains(c.getId())) toRemove.add(c);
                        }
                        currentCategories.removeAll(toRemove);
                    }
                    
                    adapter.updateData(currentCategories);
                    tvEmpty.setVisibility(currentCategories.isEmpty() ? View.VISIBLE : View.GONE);
                    Toast.makeText(CategoryManagementActivity.this, R.string.categories_deleted, Toast.LENGTH_SHORT).show();
                    
                    loadCategories();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error deleting categories", e);
                runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.delete_error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setupFilters() {
        List<String> types = new ArrayList<>();
        types.add(getString(R.string.cm_all_types));
        types.add(getString(R.string.cm_income));
        types.add(getString(R.string.cm_expense));

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types);
        spFilterType.setAdapter(typeAdapter);
        spFilterType.setText(types.get(0), false);
        spFilterType.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) selectedFilterType = "ALL";
            else if (position == 1) selectedFilterType = "income";
            else selectedFilterType = "expense";
            loadCategories();
        });

        List<String> currencyOptions = new ArrayList<>();
        currencyOptions.add(getString(R.string.cm_all_currencies));
        for (String c : currencies) currencyOptions.add(c);

        ArrayAdapter<String> currAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, currencyOptions);
        spFilterCurrency.setAdapter(currAdapter);
        spFilterCurrency.setText(currencyOptions.get(0), false);
        spFilterCurrency.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) selectedFilterCurrency = "ALL";
            else selectedFilterCurrency = currencies[position - 1];
            loadCategories();
        });
    }

    private void loadCategories() {
        executor.execute(this::loadCategoriesInternal);
    }

    private void loadCategoriesInternal() {
        try {
            List<Category> list;
            if (selectedFilterType.equals("ALL") && selectedFilterCurrency.equals("ALL")) {
                list = db.categoryDao().getAllCategoriesByUser(currentUserId);
            } else if (!selectedFilterType.equals("ALL") && selectedFilterCurrency.equals("ALL")) {
                list = db.categoryDao().getCategoriesByTypeByUser(currentUserId, selectedFilterType);
            } else if (selectedFilterType.equals("ALL") && !selectedFilterCurrency.equals("ALL")) {
                list = db.categoryDao().getCategoriesByCurrencyByUser(currentUserId, selectedFilterCurrency);
            } else {
                list = db.categoryDao().getCategoriesByTypeAndCurrencyByUser(currentUserId, selectedFilterType, selectedFilterCurrency);
            }

            runOnUiThread(() -> {
                this.currentCategories = new ArrayList<>(list);
                adapter.setCategories(currentCategories);
                tvEmpty.setVisibility(currentCategories.isEmpty() ? View.VISIBLE : View.GONE);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error loading categories", e);
            runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_operation_failed, Toast.LENGTH_SHORT).show());
        }
    }

    private boolean importCategoriesFromTransactions(String userId) {
        List<TransactionCategorySuggestion> suggestions = db.transactionDao().getUniqueCategoriesFromTransactions();
        boolean anyAdded = false;
        for (TransactionCategorySuggestion suggestion : suggestions) {
            String name = suggestion.name;
            String type = suggestion.type;
            String currency = suggestion.currencyCode;

            if (name == null || name.trim().isEmpty()) continue;
            
            int count = db.categoryDao().countDuplicateCategoryByUser(userId, name, type, currency, -1);
            if (count == 0) {
                Category newCat = new Category(name, type, currency, null, userId);
                newCat.setUserId(userId);
                db.categoryDao().insert(newCat);
                anyAdded = true;
            }
        }
        return anyAdded;
    }

    private boolean addDefaultCategories(String userId) {
        if (db.categoryDao().getCategoryCount(userId) > 0) return false;

        String[] expenseCats = {"طعام", "مواصلات", "فواتير", "تسوق", "صحة", "تعليم", "ترفيه", "إيجار", "إنترنت", "مساعدات", "تعويض", "أخرى"};
        String[] incomeCats = {"راتب", "عمل حر", "هدية", "بيع", "تحويل", "تعويض", "أخرى"};
        String[] targetCurrencies = {"USD", "SYP", "EUR", "AED", "SAR"};

        for (String currency : targetCurrencies) {
            for (String cat : expenseCats) {
                Category c = new Category(cat, "expense", currency, null, userId);
                c.setUserId(userId);
                db.categoryDao().insert(c);
            }
            for (String cat : incomeCats) {
                Category c = new Category(cat, "income", currency, null, userId);
                c.setUserId(userId);
                db.categoryDao().insert(c);
            }
        }
        return true;
    }

    private void showCategoryDialog(Category categoryToEdit) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_category_form, null);
        TextInputEditText etName = view.findViewById(R.id.etCategoryName);
        AutoCompleteTextView spType = view.findViewById(R.id.spCategoryType);
        AutoCompleteTextView spCurrency = view.findViewById(R.id.spCategoryCurrency);

        String[] types = {getString(R.string.cm_income), getString(R.string.cm_expense)};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types);
        spType.setAdapter(typeAdapter);

        ArrayAdapter<String> currAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, currencies);
        spCurrency.setAdapter(currAdapter);

        if (categoryToEdit != null) {
            etName.setText(categoryToEdit.getName());
            spType.setText("income".equals(categoryToEdit.getType()) ? types[0] : types[1], false);
            spCurrency.setText(categoryToEdit.getCurrencyCode(), false);
        } else {
            spType.setText(types[1], false); // Default expense
            spCurrency.setText("USD", false);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(categoryToEdit == null ? R.string.cm_add_category : R.string.cm_edit_category)
                .setView(view)
                .setPositiveButton(R.string.cm_save, (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String type = spType.getText().toString().equals(types[0]) ? "income" : "expense";
                    String currency = spCurrency.getText().toString();

                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, R.string.cm_name_required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveCategory(categoryToEdit, name, type, currency);
                })
                .setNegativeButton(R.string.cm_cancel, null)
                .show();
    }

    private void saveCategory(Category existing, String name, String type, String currency) {
        executor.execute(() -> {
            try {
                long excludeId = existing != null ? existing.getId() : -1;
                int count = db.categoryDao().countDuplicateCategoryByUser(currentUserId, name, type, currency, excludeId);
                if (count > 0) {
                    runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_duplicate_category, Toast.LENGTH_SHORT).show());
                    return;
                }

                if (existing == null) {
                    Category newCat = new Category(name, type, currency, null, currentUserId);
                    newCat.setUserId(currentUserId);
                    db.categoryDao().insert(newCat);
                    runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_category_added, Toast.LENGTH_SHORT).show());
                } else {
                    existing.setName(name);
                    existing.setType(type);
                    existing.setCurrencyCode(currency);
                    existing.setUserId(currentUserId);
                    db.categoryDao().update(existing);
                    runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_category_updated, Toast.LENGTH_SHORT).show());
                }
                loadCategoriesInternal();
            } catch (Exception e) {
                Log.e(TAG, "Error saving category", e);
                runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_operation_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public void onEdit(Category category) {
        showCategoryDialog(category);
    }

    @Override
    public void onDelete(Category category) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cm_delete_category)
                .setMessage(R.string.cm_delete_category_message)
                .setPositiveButton(R.string.cm_delete, (dialog, which) -> {
                    executor.execute(() -> {
                        try {
                            int usageCount = db.transactionDao().countTransactionsByCategory(category.getName(), category.getType(), category.getCurrencyCode());
                            if (usageCount > 0) {
                                runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_category_used_cannot_delete, Toast.LENGTH_LONG).show());
                            } else {
                                db.categoryDao().deleteByIdAndUser(currentUserId, category.getId());
                                runOnUiThread(() -> {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                        currentCategories.removeIf(c -> c.getId() == category.getId());
                                    } else {
                                        for (int i = 0; i < currentCategories.size(); i++) {
                                            if (currentCategories.get(i).getId() == category.getId()) {
                                                currentCategories.remove(i);
                                                break;
                                            }
                                        }
                                    }
                                    adapter.updateData(currentCategories);
                                    tvEmpty.setVisibility(currentCategories.isEmpty() ? View.VISIBLE : View.GONE);
                                    Toast.makeText(CategoryManagementActivity.this, R.string.cm_category_deleted, Toast.LENGTH_SHORT).show();
                                    
                                    loadCategories();
                                });
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error deleting category", e);
                            runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this, R.string.cm_operation_failed, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.cm_cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

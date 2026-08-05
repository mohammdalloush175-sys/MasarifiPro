package com.example.masarifipro.activities;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.adapters.DebtAdapter;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.CurrencyAccount;
import com.example.masarifipro.models.Debt;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.NumberFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebtActivity extends AppCompatActivity {

    private static final String TAG = "DEBT_DEBUG";
    private static final String SYNC_TAG = "DEBT_SYNC";
    private RecyclerView rvDebts;
    private View emptyStateLayout;
    private DebtAdapter adapter;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String selectedCurrency = "USD";
    private TextView tvOwedToMe, tvOwedByMe;
    private Spinner spinnerCurrency;
    private ChipGroup chipGroupFilters;

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debt);

        db = AppDatabase.getDatabase(this);
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        selectedCurrency = prefs.getString("current_currency", "USD");

        initViews();
        setupRecyclerView();
        setupBottomNavigation();
        loadCurrencies();
        setupFilters();

        findViewById(R.id.fabAddDebt).setOnClickListener(v -> {
            Log.d(TAG, "FAB clicked");
            showAddDebtDialog(null);
        });

        // Initial load
        loadDebts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncDebtsFromFirestore();
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            applyBottomNavDirection(bottomNavigationView);
            bottomNavigationView.setSelectedItemId(R.id.nav_debts);
        }
    }

    private void initViews() {
        rvDebts = findViewById(R.id.rvDebts);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvOwedToMe = findViewById(R.id.tvOwedToMe);
        tvOwedByMe = findViewById(R.id.tvOwedByMe);
        spinnerCurrency = findViewById(R.id.spinnerCurrency);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
    }

    private void setupRecyclerView() {
        rvDebts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DebtAdapter();
        rvDebts.setAdapter(adapter);

        adapter.setOnDebtActionListener(new DebtAdapter.OnDebtActionListener() {
            @Override
            public void onEdit(Debt debt) {
                showAddDebtDialog(debt);
            }

            @Override
            public void onDelete(Debt debt) {
                new AlertDialog.Builder(DebtActivity.this)
                        .setTitle(R.string.confirm_delete)
                        .setMessage(R.string.confirm_delete_debt)
                        .setPositiveButton(R.string.delete, (dialog, which) -> {
                            executorService.execute(() -> {
                                db.debtDao().delete(debt);
                                deleteDebtFromFirestore(debt);
                                runOnUiThread(() -> {
                                    Toast.makeText(DebtActivity.this, R.string.debt_deleted, Toast.LENGTH_SHORT).show();
                                    loadDebts();
                                    updateSummary();
                                });
                            });
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }

            @Override
            public void onMarkPaid(Debt debt) {
                debt.setPaidAmount(debt.getAmount());
                debt.setStatus("paid");
                debt.setUpdatedAt(System.currentTimeMillis());
                executorService.execute(() -> {
                    db.debtDao().update(debt);
                    uploadDebtToFirestore(debt);
                    runOnUiThread(() -> {
                        Toast.makeText(DebtActivity.this, R.string.debt_updated, Toast.LENGTH_SHORT).show();
                        loadDebts();
                        updateSummary();
                    });
                });
            }

            @Override
            public void onPartialPayment(Debt debt) {
                showPartialPaymentDialog(debt);
            }
        });
    }

    private void showPartialPaymentDialog(Debt debt) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.partial_payment);
        final EditText input = new EditText(this);
        input.setHint(R.string.amount_paid_now);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);

        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String val = input.getText().toString().replace(",", "").trim();
            if (!val.isEmpty()) {
                try {
                    double extra = Double.parseDouble(val);
                    double totalPaid = debt.getPaidAmount() + extra;
                    if (totalPaid > debt.getAmount()) {
                        Toast.makeText(this, R.string.paid_more_than_total, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    debt.setPaidAmount(totalPaid);
                    if (debt.getPaidAmount() >= debt.getAmount()) {
                        debt.setStatus("paid");
                    } else {
                        debt.setStatus("partial");
                    }
                    debt.setUpdatedAt(System.currentTimeMillis());
                    executorService.execute(() -> {
                        db.debtDao().update(debt);
                        uploadDebtToFirestore(debt);
                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.debt_updated, Toast.LENGTH_SHORT).show();
                            loadDebts();
                            updateSummary();
                        });
                    });
                } catch (NumberFormatException e) {
                    Toast.makeText(this, R.string.invalid_number_format, Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            if (LocaleHelper.getLanguage(this).equals("ar")) {
                dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            } else {
                dialog.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
        });
        dialog.show();
    }

    private void loadCurrencies() {
        executorService.execute(() -> {
            List<CurrencyAccount> activeCurrencies = db.currencyDao().getActiveCurrencies();
            List<String> codes = new ArrayList<>();
            int selectedIndex = 0;
            for (int i = 0; i < activeCurrencies.size(); i++) {
                codes.add(activeCurrencies.get(i).getCurrencyCode());
                if (activeCurrencies.get(i).getCurrencyCode().equals(selectedCurrency)) {
                    selectedIndex = i;
                }
            }
            final int finalSelectedIndex = selectedIndex;
            runOnUiThread(() -> {
                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, codes);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCurrency.setAdapter(spinnerAdapter);
                spinnerCurrency.setSelection(finalSelectedIndex);
                spinnerCurrency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        selectedCurrency = codes.get(position);
                        loadDebts();
                        updateSummary();
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        });
    }

    private void setupFilters() {
        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            loadDebts();
        });
    }

    private void loadDebts() {
        executorService.execute(() -> {
            List<Debt> allDebts = db.debtDao().getDebtsByCurrency(selectedCurrency);
            Log.d(TAG, "loaded debts count=" + (allDebts != null ? allDebts.size() : 0));
            
            runOnUiThread(() -> {
                if (allDebts == null) return;
                List<Debt> filtered = new ArrayList<>();
                int checkedId = chipGroupFilters.getCheckedChipId();
                long now = System.currentTimeMillis();

                for (Debt d : allDebts) {
                    boolean matches = false;
                    if (checkedId == R.id.chipAll || checkedId == View.NO_ID || checkedId == -1) matches = true;
                    else if (checkedId == R.id.chipOwedToMe) matches = "owed_to_me".equals(d.getType());
                    else if (checkedId == R.id.chipOwedByMe) matches = "owed_by_me".equals(d.getType());
                    else if (checkedId == R.id.chipUnpaid) matches = "unpaid".equals(d.getStatus());
                    else if (checkedId == R.id.chipPartial) matches = "partial".equals(d.getStatus());
                    else if (checkedId == R.id.chipPaid) matches = "paid".equals(d.getStatus());
                    else if (checkedId == R.id.chipOverdue) matches = d.getDueDate() < now && !"paid".equals(d.getStatus());

                    if (matches) filtered.add(d);
                }
                adapter.setDebts(filtered);
                
                if (filtered.isEmpty()) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    rvDebts.setVisibility(View.GONE);
                } else {
                    emptyStateLayout.setVisibility(View.GONE);
                    rvDebts.setVisibility(View.VISIBLE);
                }

                updateSummary();
            });
        });
    }

    private void updateSummary() {
        executorService.execute(() -> {
            List<Debt> allDebts = db.debtDao().getDebtsByCurrency(selectedCurrency);
            double owedToMe = 0;
            double owedByMe = 0;

            if (allDebts != null) {
                for (Debt d : allDebts) {
                    if (!"paid".equals(d.getStatus())) {
                        if ("owed_to_me".equals(d.getType())) {
                            owedToMe += d.getRemainingAmount();
                        } else {
                            owedByMe += d.getRemainingAmount();
                        }
                    }
                }
            }

            final double finalOwedToMe = owedToMe;
            final double finalOwedByMe = owedByMe;
            runOnUiThread(() -> {
                tvOwedToMe.setText(NumberFormatter.formatAmount(this, finalOwedToMe, selectedCurrency));
                tvOwedByMe.setText(NumberFormatter.formatAmount(this, finalOwedByMe, selectedCurrency));
            });
        });
    }

    private void showAddDebtDialog(Debt existingDebt) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_debt, null);
        builder.setView(view);

        // Apply layout direction
        if (LocaleHelper.getLanguage(this).equals("ar")) {
            view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        } else {
            view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

        TextInputEditText etPersonName = view.findViewById(R.id.etPersonName);
        android.widget.RadioGroup rgDebtType = view.findViewById(R.id.rgDebtType);
        TextInputEditText etAmount = view.findViewById(R.id.etAmount);
        TextInputEditText etPaidAmount = view.findViewById(R.id.etPaidAmount);
        Spinner spinnerDialogCurrency = view.findViewById(R.id.spinnerDialogCurrency);
        TextInputEditText etDueDate = view.findViewById(R.id.etDueDate);
        TextInputEditText etNote = view.findViewById(R.id.etNote);
        Button btnSave = view.findViewById(R.id.btnSaveDebt);

        final Calendar calendar = Calendar.getInstance();
        if (existingDebt != null && existingDebt.getDueDate() > 0) {
            calendar.setTimeInMillis(existingDebt.getDueDate());
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        etDueDate.setText(sdf.format(calendar.getTime()));

        etDueDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                etDueDate.setText(sdf.format(calendar.getTime()));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        executorService.execute(() -> {
            List<CurrencyAccount> activeCurrencies = db.currencyDao().getActiveCurrencies();
            List<String> codes = new ArrayList<>();
            int selectedIdx = 0;
            for (int i = 0; i < activeCurrencies.size(); i++) {
                codes.add(activeCurrencies.get(i).getCurrencyCode());
                if (existingDebt != null && existingDebt.getCurrencyCode().equals(activeCurrencies.get(i).getCurrencyCode())) {
                    selectedIdx = i;
                } else if (existingDebt == null && activeCurrencies.get(i).getCurrencyCode().equals(selectedCurrency)) {
                    selectedIdx = i;
                }
            }
            final int finalSelectedIdx = selectedIdx;
            runOnUiThread(() -> {
                ArrayAdapter<String> sAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, codes);
                sAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerDialogCurrency.setAdapter(sAdapter);
                spinnerDialogCurrency.setSelection(finalSelectedIdx);
            });
        });

        if (existingDebt != null) {
            etPersonName.setText(existingDebt.getPersonName());
            if ("owed_by_me".equals(existingDebt.getType())) {
                rgDebtType.check(R.id.rbOwedByMe);
            } else {
                rgDebtType.check(R.id.rbOwedToMe);
            }
            etAmount.setText(String.valueOf(existingDebt.getAmount()));
            etPaidAmount.setText(String.valueOf(existingDebt.getPaidAmount()));
            etNote.setText(existingDebt.getNote());
        }

        AlertDialog dialog = builder.create();
        btnSave.setOnClickListener(v -> {
            Log.d(TAG, "Save clicked");
            String name = etPersonName.getText().toString().trim();
            String amountStr = etAmount.getText().toString().replace(",", "").trim();
            String paidStr = etPaidAmount.getText().toString().replace(",", "").trim();
            Object selectedCurObj = spinnerDialogCurrency.getSelectedItem();
            String cur = selectedCurObj != null ? selectedCurObj.toString() : selectedCurrency;
            String type = rgDebtType.getCheckedRadioButtonId() == R.id.rbOwedToMe ? "owed_to_me" : "owed_by_me";

            Log.d(TAG, "personName=" + name);
            Log.d(TAG, "amount=" + amountStr);
            Log.d(TAG, "paidAmount=" + paidStr);
            Log.d(TAG, "currencyCode=" + cur);
            Log.d(TAG, "type=" + type);

            if (name.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(this, R.string.fill_required_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double amount = Double.parseDouble(amountStr);
                double paid = paidStr.isEmpty() ? 0 : Double.parseDouble(paidStr);

                if (amount <= 0) {
                    Toast.makeText(this, R.string.amount_must_be_positive, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (paid < 0) {
                    Toast.makeText(this, R.string.paid_amount_negative, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (paid > amount) {
                    Toast.makeText(this, R.string.paid_more_than_total, Toast.LENGTH_SHORT).show();
                    return;
                }

                String status;
                if (paid == 0) status = "unpaid";
                else if (paid < amount) status = "partial";
                else status = "paid";
                Log.d(TAG, "status=" + status);

                Debt debt = existingDebt != null ? existingDebt : new Debt();
                debt.setPersonName(name);
                debt.setType(type);
                debt.setAmount(amount);
                debt.setPaidAmount(paid);
                debt.setCurrencyCode(cur);
                debt.setDueDate(calendar.getTimeInMillis());
                debt.setNote(etNote.getText().toString());
                debt.setStatus(status);
                debt.setUpdatedAt(System.currentTimeMillis());

                executorService.execute(() -> {
                    try {
                        if (debt.getFirestoreId() == null || debt.getFirestoreId().isEmpty()) {
                            debt.setFirestoreId(UUID.randomUUID().toString());
                        }

                        if (existingDebt == null) {
                            debt.setStartDate(System.currentTimeMillis());
                            db.debtDao().insert(debt);
                        } else {
                            db.debtDao().update(debt);
                        }
                        
                        uploadDebtToFirestore(debt);

                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.debt_saved, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadDebts();
                            updateSummary();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Insert debt failed", e);
                        runOnUiThread(() -> Toast.makeText(this, R.string.debt_save_failed, Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.enter_valid_numbers, Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void syncDebtsFromFirestore() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Log.d(SYNC_TAG, "Guest mode or not logged in, skipping sync");
            return;
        }

        Log.d(SYNC_TAG, "Starting sync from Firestore");
        firestore.collection("users").document(user.getUid()).collection("debts")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    executorService.execute(() -> {
                        List<Debt> firestoreDebts = queryDocumentSnapshots.toObjects(Debt.class);
                        Log.d(SYNC_TAG, "Downloaded " + firestoreDebts.size() + " debts from Firestore");
                        
                        for (Debt firestoreDebt : firestoreDebts) {
                            Debt localDebt = db.debtDao().getDebtByFirestoreId(firestoreDebt.getFirestoreId());
                            if (localDebt == null) {
                                db.debtDao().insert(firestoreDebt);
                                Log.d(SYNC_TAG, "Inserted new debt: " + firestoreDebt.getPersonName());
                            } else if (firestoreDebt.getUpdatedAt() > localDebt.getUpdatedAt()) {
                                firestoreDebt.setId(localDebt.getId());
                                db.debtDao().update(firestoreDebt);
                                Log.d(SYNC_TAG, "Updated debt: " + firestoreDebt.getPersonName());
                            }
                        }
                        runOnUiThread(this::loadDebts);
                    });
                })
                .addOnFailureListener(e -> Log.e(SYNC_TAG, "Failed to sync debts", e));
    }

    private void uploadDebtToFirestore(Debt debt) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.isAnonymous()) return;

        Log.d(SYNC_TAG, "Uploading debt to Firestore: " + debt.getPersonName());
        firestore.collection("users").document(user.getUid()).collection("debts")
                .document(debt.getFirestoreId())
                .set(debt)
                .addOnSuccessListener(aVoid -> Log.d(SYNC_TAG, "Debt uploaded successfully"))
                .addOnFailureListener(e -> Log.e(SYNC_TAG, "Failed to upload debt", e));
    }

    private void deleteDebtFromFirestore(Debt debt) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.isAnonymous() || debt.getFirestoreId() == null) return;

        Log.d(SYNC_TAG, "Deleting debt from Firestore: " + debt.getFirestoreId());
        firestore.collection("users").document(user.getUid()).collection("debts")
                .document(debt.getFirestoreId())
                .delete()
                .addOnSuccessListener(aVoid -> Log.d(SYNC_TAG, "Debt deleted from Firestore"))
                .addOnFailureListener(e -> Log.e(SYNC_TAG, "Failed to delete debt from Firestore", e));
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        applyBottomNavDirection(bottomNavigationView);
        bottomNavigationView.setSelectedItemId(R.id.nav_debts);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                openActivity(MainActivity.class);
                return true;
            } else if (itemId == R.id.nav_statistics) {
                openActivity(StatisticsActivity.class);
                return true;
            } else if (itemId == R.id.nav_debts) {
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
}

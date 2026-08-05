package com.example.masarifipro.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.adapters.CurrencyAdapter;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.CurrencyAccount;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CurrencyActivity extends AppCompatActivity {

    private RecyclerView rvCurrencies;
    private CurrencyAdapter adapter;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_currency_management);

        db = AppDatabase.getDatabase(this);
        initViews();
        setupRecyclerView();
        ensureInitialCurrencies();
    }

    private void initViews() {
        rvCurrencies = findViewById(R.id.rvCurrencies);
    }

    private void setupRecyclerView() {
        rvCurrencies.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CurrencyAdapter(currency -> {
            executorService.execute(() -> {
                db.currencyDao().updateCurrencyActive(currency.getCurrencyCode(), currency.isActive());
                runOnUiThread(() -> Toast.makeText(CurrencyActivity.this, "تم تحديث العملة", Toast.LENGTH_SHORT).show());
            });
        });
        rvCurrencies.setAdapter(adapter);
    }

    private void ensureInitialCurrencies() {
        executorService.execute(() -> {
            if (db.currencyDao().getCurrencyCount() == 0) {
                List<CurrencyAccount> initialList = new ArrayList<>();
                initialList.add(new CurrencyAccount("USD", "الدولار الأمريكي", "$", 0, null, true));
                initialList.add(new CurrencyAccount("EUR", "اليورو", "€", 0, null, true));
                initialList.add(new CurrencyAccount("SAR", "الريال السعودي", "ر.س", 0, null, true));
                initialList.add(new CurrencyAccount("AED", "الدرهم الإماراتي", "د.إ", 0, null, true));
                initialList.add(new CurrencyAccount("KWD", "الدينار الكويتي", "د.ك", 0, null, true));
                initialList.add(new CurrencyAccount("SYP", "الليرة السورية", "ل.س", 0, null, true));
                initialList.add(new CurrencyAccount("LBP", "الليرة اللبنانية", "ل.ل", 0, null, true));

                for (CurrencyAccount c : initialList) {
                    db.currencyDao().insert(c);
                }
            }
            loadCurrencies();
        });
    }

    private void loadCurrencies() {
        executorService.execute(() -> {
            List<CurrencyAccount> currencies = db.currencyDao().getAllCurrencies();
            runOnUiThread(() -> adapter.setCurrencies(currencies));
        });
    }
}

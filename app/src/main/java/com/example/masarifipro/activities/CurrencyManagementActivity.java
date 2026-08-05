package com.example.masarifipro.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

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

public class CurrencyManagementActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_prefs";
    private static final String HINT_CURRENCIES_SEEN = "hint_currencies_seen";

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
        checkHint();
    }

    private void checkHint() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean seen = prefs.getBoolean(HINT_CURRENCIES_SEEN, false);
        View hintCard = findViewById(R.id.hintCard);
        if (hintCard != null) {
            if (!seen) {
                hintCard.setVisibility(View.VISIBLE);
                hintCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.hint_enter));
                findViewById(R.id.btnDismissHint).setOnClickListener(v -> {
                    prefs.edit().putBoolean(HINT_CURRENCIES_SEEN, true).apply();
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
        rvCurrencies = findViewById(R.id.rvCurrencies);
    }

    private void setupRecyclerView() {
        rvCurrencies.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CurrencyAdapter(currency -> {
            executorService.execute(() -> db.currencyDao().update(currency));
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

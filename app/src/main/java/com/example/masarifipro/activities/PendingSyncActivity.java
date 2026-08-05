package com.example.masarifipro.activities;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.adapters.SyncOperationAdapter;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.SyncOperation;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.SyncManager;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PendingSyncActivity extends AppCompatActivity {

    private RecyclerView rvPendingOperations;
    private SyncOperationAdapter adapter;
    private TextView tvEmpty;
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable Edge-to-Edge to allow drawing behind status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_pending_sync);

        db = AppDatabase.getDatabase(this);
        initViews();
        loadOperations();
    }

    private void initViews() {
        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Handle system bar insets to fix toolbar title overlap and navigation bar overlap
        View root = findViewById(R.id.pendingSyncRoot);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                
                // Add top padding to AppBarLayout to move content below the status bar
                // while keeping the background extended behind it.
                if (appBarLayout != null) {
                    appBarLayout.setPadding(0, systemBars.top, 0, 0);
                }
                
                // Add bottom padding to the root layout to avoid button overlapping with the navigation bar
                v.setPadding(0, 0, 0, systemBars.bottom);
                
                return WindowInsetsCompat.CONSUMED;
            });
        }

        rvPendingOperations = findViewById(R.id.rvPendingOperations);
        tvEmpty = findViewById(R.id.tvEmpty);
        MaterialButton btnSyncAll = findViewById(R.id.btnSyncAll);

        rvPendingOperations.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SyncOperationAdapter();
        rvPendingOperations.setAdapter(adapter);

        adapter.setActionListener(new SyncOperationAdapter.OnOperationActionListener() {
            @Override
            public void onRetry(SyncOperation operation) {
                executor.execute(() -> {
                    operation.setStatus(SyncOperation.STATUS_PENDING);
                    db.syncOperationDao().update(operation);
                    SyncManager.getInstance(PendingSyncActivity.this).sync();
                    runOnUiThread(() -> Toast.makeText(PendingSyncActivity.this, R.string.sync_started, Toast.LENGTH_SHORT).show());
                });
            }

            @Override
            public void onDelete(SyncOperation operation) {
                executor.execute(() -> {
                    db.syncOperationDao().delete(operation);
                    loadOperations();
                });
            }
        });

        btnSyncAll.setOnClickListener(v -> {
            SyncManager.getInstance(this).sync();
            Toast.makeText(this, R.string.sync_started, Toast.LENGTH_SHORT).show();
        });

        SyncManager.getInstance(this).setStatusListener((pendingCount, errorCount) -> loadOperations());
    }

    private void loadOperations() {
        executor.execute(() -> {
            List<SyncOperation> operations = db.syncOperationDao().getAllPendingOperations();
            runOnUiThread(() -> {
                adapter.setOperations(operations);
                tvEmpty.setVisibility(operations.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

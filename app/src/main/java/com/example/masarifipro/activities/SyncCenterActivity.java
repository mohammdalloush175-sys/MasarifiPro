package com.example.masarifipro.activities;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.masarifipro.R;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.SharedTrip;
import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.SharedTripsSyncManager;
import com.example.masarifipro.utils.SyncManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncCenterActivity extends AppCompatActivity {

    private static final String TAG = "SYNC_CENTER";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    
    private TextView tvSyncedTx, tvPendingTx, tvFailedTx;
    private TextView tvSyncedGroups, tvPendingGroups, tvFailedGroups, tvLocalGroups;
    private TextView tvNetworkStatus;
    private View btnRetry, cardGuestWarning;
    private ProgressBar progressBar;
    private LinearLayout listPendingTx, listPendingGroups;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_center);

        db = AppDatabase.getDatabase(this);
        initViews();
        loadSyncData();
        checkNetworkStatus();
        
        btnRetry.setOnClickListener(v -> retrySync());
    }

    private void initViews() {
        tvSyncedTx = findViewById(R.id.tvSyncedTxCount);
        tvPendingTx = findViewById(R.id.tvPendingTxCount);
        tvFailedTx = findViewById(R.id.tvFailedTxCount);
        
        tvSyncedGroups = findViewById(R.id.tvSyncedGroupsCount);
        tvPendingGroups = findViewById(R.id.tvPendingGroupsCount);
        tvFailedGroups = findViewById(R.id.tvFailedGroupsCount);
        tvLocalGroups = findViewById(R.id.tvLocalGroupsCount);
        
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);
        btnRetry = findViewById(R.id.btnRetrySync);
        cardGuestWarning = findViewById(R.id.cardGuestWarning);
        progressBar = findViewById(R.id.progressBar);
        
        listPendingTx = findViewById(R.id.listPendingTransactions);
        listPendingGroups = findViewById(R.id.listPendingGroups);

        boolean isGuest = getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("guest_mode", false);
        if (isGuest) {
            Log.d(TAG, "guest mode active");
            cardGuestWarning.setVisibility(View.VISIBLE);
            btnRetry.setEnabled(false);
            btnRetry.setAlpha(0.5f);
        }
    }

    private void loadSyncData() {
        Log.d(TAG, "loading sync counts");
        executor.execute(() -> {
            int syncedTx = db.transactionDao().getCountBySyncStatus(Transaction.STATUS_SYNCED);
            int pendingTx = db.transactionDao().getCountBySyncStatus(Transaction.STATUS_PENDING);
            int syncingTx = db.transactionDao().getCountBySyncStatus(Transaction.STATUS_SYNCING);
            int failedTx = db.transactionDao().getCountBySyncStatus(Transaction.STATUS_FAILED);

            int syncedGr = db.sharedTripDao().getCountBySyncStatus(SharedTrip.STATUS_SYNCED);
            int pendingGr = db.sharedTripDao().getCountBySyncStatus(SharedTrip.STATUS_PENDING);
            int failedGr = db.sharedTripDao().getCountBySyncStatus(SharedTrip.STATUS_FAILED);
            int localGr = db.sharedTripDao().getCountBySyncStatus(SharedTrip.STATUS_LOCAL_ONLY);

            List<Transaction> pendingTxList = db.transactionDao().getTransactionsBySyncStatuses(Transaction.STATUS_PENDING, Transaction.STATUS_FAILED, 5);
            List<SharedTrip> pendingGrList = db.sharedTripDao().getTripsBySyncStatuses(SharedTrip.STATUS_PENDING, SharedTrip.STATUS_FAILED, 5);

            Log.d(TAG, "transaction pending count = " + (pendingTx + syncingTx));
            Log.d(TAG, "shared trips pending count = " + pendingGr);
            Log.d(TAG, "shared trips local only count = " + localGr);

            runOnUiThread(() -> {
                tvSyncedTx.setText(String.valueOf(syncedTx));
                tvPendingTx.setText(String.valueOf(pendingTx + syncingTx));
                tvFailedTx.setText(String.valueOf(failedTx));

                tvSyncedGroups.setText(String.valueOf(syncedGr));
                tvPendingGroups.setText(String.valueOf(pendingGr));
                tvFailedGroups.setText(String.valueOf(failedGr));
                tvLocalGroups.setText(String.valueOf(localGr));

                populatePendingTxList(pendingTxList);
                populatePendingGrList(pendingGrList);
            });
        });
    }

    private void populatePendingTxList(List<Transaction> list) {
        listPendingTx.removeAllViews();
        if (list.isEmpty()) {
            findViewById(R.id.tvLabelTxToSync).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.tvLabelTxToSync).setVisibility(View.VISIBLE);
        for (Transaction t : list) {
            View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, listPendingTx, false);
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);
            
            text1.setText(t.getCategoryName() + " - " + t.getAmount() + " " + t.getCurrencyCode());
            String status = t.getSyncStatus();
            String date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(t.getDate()));
            text2.setText(status + " | " + date);
            
            listPendingTx.addView(view);
        }
    }

    private void populatePendingGrList(List<SharedTrip> list) {
        listPendingGroups.removeAllViews();
        if (list.isEmpty()) {
            findViewById(R.id.tvLabelGroupsToSync).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.tvLabelGroupsToSync).setVisibility(View.VISIBLE);
        for (SharedTrip trip : list) {
            View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, listPendingGroups, false);
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);
            
            text1.setText(trip.getName());
            String status = trip.getSyncStatus();
            String date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(trip.getUpdatedAt()));
            text2.setText(status + " | " + date);
            
            listPendingGroups.addView(view);
        }
    }

    private void retrySync() {
        Log.d(TAG, "retry sync clicked");
        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.no_internet, Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnRetry.setEnabled(false);

        executor.execute(() -> {
            SyncManager.getInstance(this).sync();
            SharedTripsSyncManager.getInstance(this).sync();

            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

            runOnUiThread(() -> {
                Log.d(TAG, "retry completed");
                progressBar.setVisibility(View.GONE);
                btnRetry.setEnabled(true);
                loadSyncData();
                Toast.makeText(this, R.string.sync_attempted, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void checkNetworkStatus() {
        if (isNetworkAvailable()) {
            tvNetworkStatus.setText(R.string.connected);
            tvNetworkStatus.setTextColor(android.graphics.Color.WHITE);
        } else {
            tvNetworkStatus.setText(R.string.disconnected);
            tvNetworkStatus.setTextColor(android.graphics.Color.LTGRAY);
        }
    }
}

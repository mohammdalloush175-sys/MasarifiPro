package com.example.masarifipro.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.SyncOperation;
import com.example.masarifipro.models.Transaction;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final String WORK_NAME = "PeriodicSyncWork";
    private static final long OPERATION_TIMEOUT_SECONDS = 30L;

    private static SyncManager instance;
    private final Context context;
    private final AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();
    private boolean isSyncing = false;

    public interface SyncStatusListener {
        void onSyncStatusChanged(int pendingCount, int errorCount);
    }

    private SyncStatusListener statusListener;

    private SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getDatabase(this.context);
        registerNetworkCallback();
        schedulePeriodicSync();
    }

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context);
        }
        return instance;
    }

    public void setStatusListener(SyncStatusListener listener) {
        this.statusListener = listener;
        notifyStatusChanged();
    }

    public void enqueueOperation(String type, String collection, String docId, Object data) {
        if (docId == null || docId.trim().isEmpty()) {
            Log.e(TAG, "Refusing to enqueue an operation without a stable document ID");
            return;
        }

        executor.execute(() -> {
            try {
                String jsonData = data != null ? gson.toJson(data) : "";

                // Keep only the newest queued mutation for the same document.
                // Firestore writes use the stable document ID, so retries remain idempotent.
                db.syncOperationDao().deleteSupersededOperations(collection, docId);
                SyncOperation operation = new SyncOperation(type, collection, docId, jsonData);
                db.syncOperationDao().insert(operation);

                if ("transactions".equals(collection)) {
                    updateTransactionSyncStatus(docId, false, Transaction.STATUS_PENDING);
                }

                notifyStatusChangedInternal();
                syncInternal();
            } catch (Exception e) {
                Log.e(TAG, "Failed to enqueue operation", e);
            }
        });
    }

    public void sync() {
        executor.execute(this::syncInternal);
    }

    public boolean syncBlocking() {
        try {
            Future<Boolean> future = executor.submit(this::syncInternal);
            return future.get(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            Log.e(TAG, "Blocking sync failed", e);
            return false;
        }
    }

    private boolean syncInternal() {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping.");
            return true;
        }

        if (context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("guest_mode", false)) {
            return true;
        }

        if (!isNetworkAvailable()) {
            Log.d(TAG, "Network not available, skipping sync.");
            return false;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No user logged in, skipping sync.");
            return true;
        }

        boolean allSucceeded = true;
        isSyncing = true;
        try {
            List<SyncOperation> operations = db.syncOperationDao().getAllPendingOperations();
            Log.d(TAG, "Starting sync for " + operations.size() + " operations.");

            for (SyncOperation operation : operations) {
                if (!isNetworkAvailable()) {
                    allSucceeded = false;
                    break;
                }
                if (!processOperationBlocking(operation, user.getUid())) {
                    allSucceeded = false;
                }
            }
        } catch (Exception e) {
            allSucceeded = false;
            Log.e(TAG, "Sync process failed", e);
        } finally {
            isSyncing = false;
            notifyStatusChangedInternal();
        }
        return allSucceeded;
    }

    private boolean processOperationBlocking(SyncOperation operation, String uid) {
        try {
            operation.setStatus(SyncOperation.STATUS_SYNCING);
            db.syncOperationDao().update(operation);

            if ("transactions".equals(operation.getCollectionName())) {
                updateTransactionSyncStatus(
                        operation.getDocumentId(), false, Transaction.STATUS_SYNCING);
            }

            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            String path;
            if ("users".equals(operation.getCollectionName())
                    || "profile".equals(operation.getCollectionName())) {
                path = "users";
            } else {
                path = "users/" + uid + "/" + operation.getCollectionName();
            }

            Task<Void> task;
            if (SyncOperation.TYPE_DELETE.equals(operation.getOperationType())) {
                task = firestore.collection(path)
                        .document(operation.getDocumentId())
                        .delete();
            } else {
                Map<String, Object> data = gson.fromJson(
                        operation.getJsonData(),
                        new TypeToken<Map<String, Object>>() { }.getType());
                task = firestore.collection(path)
                        .document(operation.getDocumentId())
                        .set(data, SetOptions.merge());
            }

            // The old implementation returned before Firebase completed. That let
            // another sync pass resend the same queue while callbacks were pending.
            Tasks.await(task, OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if ("transactions".equals(operation.getCollectionName())
                    && !SyncOperation.TYPE_DELETE.equals(operation.getOperationType())) {
                updateTransactionSyncStatus(
                        operation.getDocumentId(), true, Transaction.STATUS_SYNCED);
            }
            db.syncOperationDao().delete(operation);
            Log.d(TAG, "Operation " + operation.getId() + " completed successfully.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Operation " + operation.getId() + " failed", e);
            handleSyncFailure(operation);
            return false;
        } finally {
            notifyStatusChangedInternal();
        }
    }

    private void handleSyncFailure(SyncOperation operation) {
        try {
            operation.setStatus(SyncOperation.STATUS_FAILED);
            operation.setRetryCount(operation.getRetryCount() + 1);
            db.syncOperationDao().update(operation);

            if ("transactions".equals(operation.getCollectionName())) {
                updateTransactionSyncStatus(
                        operation.getDocumentId(), false, Transaction.STATUS_FAILED);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to record sync failure", e);
        }
    }

    private void updateTransactionSyncStatus(String documentId, boolean synced, String status) {
        db.transactionDao().updateSyncStatus(documentId, synced, status);
        TransactionSyncManager.notifyLocalTransactionChanged();
    }

    private void notifyStatusChanged() {
        executor.execute(this::notifyStatusChangedInternal);
    }

    private void notifyStatusChangedInternal() {
        try {
            final int pending = db.syncOperationDao().getUnsyncedCount();
            final int errors = db.syncOperationDao().getErrorCount();

            new Handler(Looper.getMainLooper()).post(() -> {
                if (statusListener != null) {
                    statusListener.onSyncStatusChanged(pending, errors);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to notify status change", e);
        }
    }

    public boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                return capabilities != null
                        && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.d(TAG, "Network available, triggering sync.");
                    sync();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
        }
    }

    private void schedulePeriodicSync() {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest syncRequest =
                    new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                            .setConstraints(constraints)
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                            .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule periodic sync", e);
        }

        startAppRunningTimer();
    }

    private void startAppRunningTimer() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sync();
                handler.postDelayed(this, 5 * 60 * 1000L);
            }
        }, 5 * 60 * 1000L);
    }
}

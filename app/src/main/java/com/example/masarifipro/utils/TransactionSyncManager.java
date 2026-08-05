package com.example.masarifipro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.SyncOperation;
import com.example.masarifipro.models.Transaction;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TransactionSyncManager {
    private static final String TAG = "TX_SYNC";
    private static final String TRANSACTIONS_COLLECTION = "transactions";

    private static ListenerRegistration listenerRegistration;
    private static String currentListenerUid;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public interface SyncCallback {
        void onDataChanged();
    }

    private static SyncCallback syncCallback;

    private TransactionSyncManager() {
    }

    public static void setSyncCallback(SyncCallback callback) {
        syncCallback = callback;
    }

    public static synchronized void startListening(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("guest_mode", false)) {
            stopListening();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            stopListening();
            return;
        }

        String uid = user.getUid();
        if (listenerRegistration != null && uid.equals(currentListenerUid)) {
            return;
        }

        stopListening();
        currentListenerUid = uid;
        requeueUnsyncedTransactions(appContext);

        listenerRegistration = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection(TRANSACTIONS_COLLECTION)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Transaction listener failed", error);
                        return;
                    }
                    if (snapshots == null) {
                        return;
                    }

                    // A cache snapshot is useful for fast display, but it must not
                    // delete local rows because the cache may be incomplete/stale.
                    boolean authoritative = !snapshots.getMetadata().isFromCache();
                    applyRemoteSnapshot(appContext, snapshots, authoritative, null);
                });
    }

    public static void refreshFromServer(Context context, Runnable completion) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (prefs.getBoolean("guest_mode", false) || user == null) {
            if (completion != null) completion.run();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .collection(TRANSACTIONS_COLLECTION)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot ->
                        applyRemoteSnapshot(appContext, snapshot, true, completion))
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Authoritative transaction refresh failed", error);
                    if (completion != null) completion.run();
                });
    }

    private static void applyRemoteSnapshot(Context context, QuerySnapshot snapshot,
                                            boolean authoritative, Runnable completion) {
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            try {
                db.runInTransaction(() -> {
                    Set<String> remoteIds = new HashSet<>();
                    List<DocumentSnapshot> documents = snapshot.getDocuments();

                    for (DocumentSnapshot document : documents) {
                        Transaction remote = transactionFromFirestore(document);
                        if (remote == null || isBlank(remote.getFirestoreId())) {
                            continue;
                        }
                        remoteIds.add(remote.getFirestoreId());
                        upsertLocal(db, remote, authoritative);
                    }

                    if (authoritative) {
                        removeStaleSyncedRows(db, remoteIds);
                    }

                    TransactionBalanceCalculator.recalculateAll(db);
                });

                notifyDataChanged();
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply remote transaction snapshot", e);
            } finally {
                if (completion != null) completion.run();
            }
        });
    }

    public static Transaction transactionFromFirestore(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;

        try {
            Transaction transaction = new Transaction();
            // The Firestore document path is the canonical identity. Trusting a
            // mutable field here can create a second local row or delete the wrong one.
            transaction.setFirestoreId(doc.getId());
            transaction.setAmount(valueOrZero(doc.getDouble("amount")));
            transaction.setType(defaultString(doc.getString("type"), "expense"));

            String currencyCode = doc.getString("currencyCode");
            if (currencyCode == null) currencyCode = doc.getString("currency");
            transaction.setCurrencyCode(defaultString(currencyCode, "USD"));

            String category = doc.getString("category");
            if (category == null) category = doc.getString("categoryName");
            transaction.setCategoryName(defaultString(category, ""));

            String description = doc.getString("description");
            if (description == null) description = doc.getString("note");
            transaction.setDescription(defaultString(description, ""));

            Long date = doc.getLong("date");
            Long createdAt = doc.getLong("createdAt");
            Long updatedAt = doc.getLong("updatedAt");
            long safeCreatedAt = createdAt != null ? createdAt : System.currentTimeMillis();
            transaction.setDate(date != null ? date : safeCreatedAt);
            transaction.setCreatedAt(safeCreatedAt);
            transaction.setUpdatedAt(updatedAt != null ? updatedAt : safeCreatedAt);
            transaction.setSynced(true);
            transaction.setSyncStatus(Transaction.STATUS_SYNCED);

            transaction.setSharedAccountId(doc.getString("sharedAccountId"));
            transaction.setUserId(doc.getString("userId"));
            transaction.setUserName(doc.getString("userName"));

            Long categoryId = doc.getLong("categoryId");
            if (categoryId != null) transaction.setCategoryId(categoryId);
            transaction.setInvoiceImageUrl(doc.getString("invoiceImageUrl"));

            Boolean isExchange = doc.getBoolean("isExchangeTransfer");
            transaction.setExchangeTransfer(isExchange != null && isExchange);
            transaction.setExchangeGroupId(doc.getString("exchangeGroupId"));
            transaction.setSourceCurrency(doc.getString("sourceCurrency"));
            transaction.setTargetCurrency(doc.getString("targetCurrency"));
            Double rate = doc.getDouble("exchangeRate");
            if (rate != null) transaction.setExchangeRate(rate);

            Boolean snapshotEnabled = doc.getBoolean("balanceSnapshotEnabled");
            transaction.setBalanceSnapshotEnabled(snapshotEnabled != null && snapshotEnabled);
            transaction.setBalanceBefore(doc.getDouble("balanceBefore"));
            transaction.setBalanceAfter(doc.getDouble("balanceAfter"));

            return transaction;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse transaction " + doc.getId(), e);
            return null;
        }
    }

    private static void upsertLocal(AppDatabase db, Transaction remote,
                                    boolean authoritative) {
        String firestoreId = remote.getFirestoreId();

        // A local deletion must not be undone by a cached/older remote snapshot.
        if (db.syncOperationDao().countPendingDeletes(
                TRANSACTIONS_COLLECTION, firestoreId) > 0) {
            return;
        }

        Transaction existing = db.transactionDao().getTransactionByFirestoreId(firestoreId);
        Transaction legacyWithoutId = db.transactionDao().findLegacyWithoutFirestoreId(
                remote.getDate(),
                remote.getCreatedAt(),
                remote.getAmount(),
                remote.getType(),
                remote.getCurrencyCode(),
                remote.getDescription());

        if (existing != null) {
            // Cache snapshots are allowed to fill missing rows, never to roll an
            // already-present row back to an older cached version.
            if (!authoritative) {
                return;
            }

            // Local queued or not-yet-queued changes win until their idempotent
            // Firestore write completes. This closes the small insert-to-queue gap.
            boolean hasQueuedMutation = db.syncOperationDao().countOperationsForDocument(
                    TRANSACTIONS_COLLECTION, firestoreId) > 0;
            boolean hasLocalMutation = !existing.isSynced()
                    || Transaction.STATUS_FAILED.equals(existing.getSyncStatus())
                    || Transaction.STATUS_SYNCING.equals(existing.getSyncStatus());
            if (hasQueuedMutation || hasLocalMutation) {
                return;
            }

            remote.setId(existing.getId());
            remote.setLocalImagePath(existing.getLocalImagePath());
            db.transactionDao().insertOrReplace(remote);

            if (legacyWithoutId != null && legacyWithoutId.getId() != existing.getId()) {
                db.transactionDao().deleteById(legacyWithoutId.getId());
            }
            return;
        }

        // Repairs rows created by older builds that inserted locally before assigning
        // their Firestore ID. Reusing that row prevents the listener from adding a twin.
        if (legacyWithoutId != null) {
            remote.setId(legacyWithoutId.getId());
            remote.setLocalImagePath(legacyWithoutId.getLocalImagePath());
        }

        db.transactionDao().insertOrReplace(remote);
    }

    private static void removeStaleSyncedRows(AppDatabase db, Set<String> remoteIds) {
        List<Transaction> localTransactions = db.transactionDao().getAllTransactions();
        for (Transaction local : localTransactions) {
            String firestoreId = local.getFirestoreId();
            if (isBlank(firestoreId) || remoteIds.contains(firestoreId)) {
                continue;
            }

            boolean hasPendingMutation = db.syncOperationDao().countOperationsForDocument(
                    TRANSACTIONS_COLLECTION, firestoreId) > 0;
            boolean safeToRemove = local.isSynced()
                    || Transaction.STATUS_SYNCED.equals(local.getSyncStatus());

            if (!hasPendingMutation && safeToRemove) {
                db.transactionDao().deleteById(local.getId());
            }
        }
    }

    public static synchronized void stopListening() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        currentListenerUid = null;
    }

    public static void requeueUnsyncedTransactions(Context context) {
        Context appContext = context.getApplicationContext();
        executorService.execute(() -> {
            SharedPreferences prefs = appContext.getSharedPreferences(
                    "app_prefs", Context.MODE_PRIVATE);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (prefs.getBoolean("guest_mode", false) || user == null) {
                return;
            }

            AppDatabase db = AppDatabase.getDatabase(appContext);
            List<Transaction> unsynced = db.transactionDao()
                    .getUnsyncedTransactionsForUser(user.getUid());
            for (Transaction transaction : unsynced) {
                if (isBlank(transaction.getFirestoreId())) {
                    transaction.setFirestoreId(UUID.randomUUID().toString());
                    db.transactionDao().update(transaction);
                }
                uploadTransactionToFirestore(appContext, transaction);
            }
        });
    }

    public static void uploadTransactionToFirestore(Context context, Transaction transaction) {
        Context appContext = context.getApplicationContext();
        executorService.execute(() -> {
            SharedPreferences prefs = appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (prefs.getBoolean("guest_mode", false) || user == null) {
                return;
            }

            AppDatabase db = AppDatabase.getDatabase(appContext);
            String transactionId = transaction.getFirestoreId();
            if (isBlank(transactionId)) {
                transactionId = UUID.randomUUID().toString();
                transaction.setFirestoreId(transactionId);
                if (transaction.getId() > 0) {
                    db.transactionDao().update(transaction);
                }
            }

            Map<String, Object> data = toFirestoreMap(transaction, transactionId);
            SyncManager.getInstance(appContext).enqueueOperation(
                    SyncOperation.TYPE_UPDATE,
                    TRANSACTIONS_COLLECTION,
                    transactionId,
                    data
            );
        });
    }

    private static Map<String, Object> toFirestoreMap(Transaction transaction, String transactionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("firestoreId", transactionId);
        data.put("amount", transaction.getAmount());
        data.put("type", transaction.getType());
        data.put("currencyCode", transaction.getCurrencyCode());
        data.put("category", transaction.getCategoryName());
        data.put("description", transaction.getDescription());
        data.put("date", transaction.getDate());
        data.put("createdAt", transaction.getCreatedAt());
        data.put("updatedAt", transaction.getUpdatedAt());
        data.put("sharedAccountId", transaction.getSharedAccountId());
        data.put("userId", transaction.getUserId());
        data.put("userName", transaction.getUserName());
        data.put("categoryId", transaction.getCategoryId());
        data.put("invoiceImageUrl", transaction.getInvoiceImageUrl());
        data.put("isExchangeTransfer", transaction.isExchangeTransfer());
        data.put("exchangeGroupId", transaction.getExchangeGroupId());
        data.put("sourceCurrency", transaction.getSourceCurrency());
        data.put("targetCurrency", transaction.getTargetCurrency());
        data.put("exchangeRate", transaction.getExchangeRate());
        data.put("balanceSnapshotEnabled", transaction.isBalanceSnapshotEnabled());
        data.put("balanceBefore", transaction.getBalanceBefore());
        data.put("balanceAfter", transaction.getBalanceAfter());
        data.put("synced", true);
        return data;
    }

    public static void deleteTransaction(Context context, Transaction transaction) {
        boolean guestMode = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getBoolean("guest_mode", false);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String firestoreId = transaction.getFirestoreId();
        if (guestMode || user == null || isBlank(firestoreId)) {
            return;
        }

        SyncManager.getInstance(context.getApplicationContext()).enqueueOperation(
                SyncOperation.TYPE_DELETE,
                TRANSACTIONS_COLLECTION,
                firestoreId,
                null
        );
    }

    /**
     * Notifies the currently visible transaction screen after a local Room change.
     *
     * Firestore can acknowledge a local write using a metadata-only snapshot. Since
     * metadata changes are not included by the default listener, the UI must also be
     * refreshed when SyncManager changes the local status from PENDING/SYNCING to
     * SYNCED or FAILED.
     */
    public static void notifyLocalTransactionChanged() {
        notifyDataChanged();
    }

    private static void notifyDataChanged() {
        SyncCallback callback = syncCallback;
        if (callback != null) {
            callback.onDataChanged();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static double valueOrZero(Double value) {
        return value != null ? value : 0.0d;
    }
}

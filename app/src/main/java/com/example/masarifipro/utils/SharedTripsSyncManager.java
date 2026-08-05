package com.example.masarifipro.utils;

import android.content.Context;
import android.util.Log;

import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.SharedTrip;
import com.example.masarifipro.models.SharedTripExpense;
import com.example.masarifipro.models.SharedTripMember;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SharedTripsSyncManager {
    private static final String TAG = "SHARED_TRIPS_SYNC";
    private static SharedTripsSyncManager instance;
    private final Context context;
    private final AppDatabase db;
    private final FirebaseFirestore firestore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SharedTripsSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getDatabase(this.context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    public static synchronized SharedTripsSyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedTripsSyncManager(context);
        }
        return instance;
    }

    public void sync() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean isGuest = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("guest_mode", false);

        Log.d(TAG, "sync called. currentUser=" + (user != null ? user.getUid() : "null") + ", isGuest=" + isGuest);

        if (user == null || isGuest) {
            Log.d(TAG, "Sync skipped: user not logged in or in guest mode.");
            return;
        }

        if (!SyncManager.getInstance(context).isNetworkAvailable()) {
            Log.d(TAG, "Sync skipped: no network available.");
            return;
        }

        executor.execute(() -> {
            Log.d(TAG, "Starting background sync process...");
            uploadPendingTrips();
            uploadPendingExpenses();
            Log.d(TAG, "Background sync process finished.");
        });
    }

    public void uploadPendingTrips() {
        Log.d(TAG, "uploadPendingTrips called");
        List<SharedTrip> pendingTrips = db.sharedTripDao().getPendingTrips();
        Log.d(TAG, "pending trips count = " + pendingTrips.size());
        
        for (SharedTrip trip : pendingTrips) {
            uploadTripToFirestore(trip);
        }
    }

    public void uploadTripToFirestore(SharedTrip trip) {
        if (SharedTrip.STATUS_LOCAL_ONLY.equals(trip.getSyncStatus()) || "guest_user".equals(trip.getOwnerUid())) {
            Log.d(TAG, "Skipping upload for guest-owned or local-only trip: " + trip.getTripId());
            return;
        }

        if (!SyncManager.getInstance(context).isNetworkAvailable()) {
            Log.d(TAG, "Network not available, skipping upload for " + trip.getTripId());
            return;
        }

        Log.d(TAG, "SHARED_TRIPS_SYNC: upload started for trip " + trip.getTripId());

        executor.execute(() -> {
            List<SharedTripMember> members = db.sharedTripMemberDao().getMembersByTripId(trip.getTripId());
            List<String> uids = new ArrayList<>();
            for (SharedTripMember m : members) {
                if (m.getUid() != null && !m.isOffline()) {
                    uids.add(m.getUid());
                }
            }
            
            if (trip.getOwnerUid() != null && !uids.contains(trip.getOwnerUid())) {
                uids.add(trip.getOwnerUid());
            }
            
            trip.setMemberUids(uids);

            firestore.collection("shared_trips").document(trip.getTripId())
                    .set(trip)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "SHARED_TRIPS_SYNC: upload success -> SYNCED (" + trip.getName() + ")");
                        trip.setSyncStatus(SharedTrip.STATUS_SYNCED);
                        trip.setRemoteId(trip.getTripId());
                        executor.execute(() -> db.sharedTripDao().update(trip));
                        syncMembers(trip.getTripId());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "SHARED_TRIPS_SYNC: upload failed -> FAILED for " + trip.getName() + ": " + e.getMessage());
                        trip.setSyncStatus(SharedTrip.STATUS_FAILED);
                        executor.execute(() -> db.sharedTripDao().update(trip));
                    });
        });
    }

    public void retryFailedTrips() {
        Log.d(TAG, "retryFailedTrips called");
        sync();
    }

    private void syncMembers(String tripId) {
        executor.execute(() -> {
            List<SharedTripMember> members = db.sharedTripMemberDao().getMembersByTripId(tripId);
            WriteBatch batch = firestore.batch();
            for (SharedTripMember m : members) {
                batch.set(firestore.collection("shared_trips").document(tripId)
                        .collection("members").document(m.getUid()), m);
            }
            batch.commit().addOnSuccessListener(aVoid -> Log.d(TAG, "Members sub-collection synced successfully."));
        });
    }

    private void uploadPendingExpenses() {
        if (!SyncManager.getInstance(context).isNetworkAvailable()) return;

        List<SharedTripExpense> pendingExpenses = db.sharedTripExpenseDao().getPendingExpenses();
        for (SharedTripExpense expense : pendingExpenses) {
            SharedTrip trip = db.sharedTripDao().getTripById(expense.getTripId());
            if (trip == null || SharedTrip.STATUS_LOCAL_ONLY.equals(trip.getSyncStatus()) || "guest_user".equals(trip.getOwnerUid())) continue;

            firestore.collection("shared_trips").document(expense.getTripId())
                    .collection("expenses").document(expense.getExpenseId())
                    .set(expense)
                    .addOnSuccessListener(aVoid -> {
                        expense.setSyncStatus("SYNCED");
                        executor.execute(() -> db.sharedTripExpenseDao().update(expense));
                    })
                    .addOnFailureListener(e -> {
                        expense.setSyncStatus("FAILED");
                        executor.execute(() -> db.sharedTripExpenseDao().update(expense));
                    });
        }
    }
}

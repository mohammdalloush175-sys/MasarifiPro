package com.example.masarifipro.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.masarifipro.R;
import com.example.masarifipro.adapters.SharedTripAdapter;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.SharedTrip;
import com.example.masarifipro.models.SharedTripMember;
import com.example.masarifipro.utils.LocalSharedTripManager;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.SharedTripsSyncManager;
import com.example.masarifipro.utils.SyncManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SharedTripsActivity extends AppCompatActivity {

    private static final String TAG = "SHARED_TRIPS_SYNC";
    private static final String DELETE_TAG = "SHARED_TRIPS_DELETE";
    private static final String LINK_TAG = "SHARED_LINK";
    private static final String PREFS_NAME = "app_prefs";
    private static final String HINT_SHARED_EXPENSES_SEEN = "hint_shared_expenses_seen";

    private RecyclerView rvSharedTrips;
    private SharedTripAdapter adapter;
    private TextView tvEmptyState, tvGuestNote;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private boolean isGuestMode = false;
    private AppDatabase localDb;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextInputEditText pendingJoinCodeEditText;

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchQrScanner();
                } else {
                    Toast.makeText(this, R.string.qr_camera_permission_required, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String code = parseInviteCodeFromQr(result.getContents());
                    if (code != null && pendingJoinCodeEditText != null) {
                        pendingJoinCodeEditText.setText(code);
                        pendingJoinCodeEditText.setSelection(code.length());
                        Toast.makeText(this, R.string.qr_code_read_success, Toast.LENGTH_SHORT).show();
                    } else if (code == null) {
                        Toast.makeText(this, R.string.qr_invalid_code, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, R.string.qr_code_read_cancelled, Toast.LENGTH_SHORT).show();
                }
            });

    public interface ProfileNameCallback {
        void onNameLoaded(String name);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_trips);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        localDb = AppDatabase.getDatabase(this);

        isGuestMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("guest_mode", false);

        migrateLegacyDataIfNeeded();
        handleIncomingIntent(getIntent());

        if (currentUser == null && !isGuestMode) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initViews();
        setupRecyclerViews();
        loadTrips();
        checkHint();

        if (getIntent().hasExtra("invite_code")) {
            String code = getIntent().getStringExtra("invite_code");
            if (code != null && !code.isEmpty()) {
                if (isGuestMode) {
                    Toast.makeText(this, R.string.se_join_requires_login, Toast.LENGTH_LONG).show();
                } else {
                    loadCurrentUserProfileName(profileName -> joinTrip(code, profileName));
                }
            }
        }
        
        SharedTripsSyncManager.getInstance(this).sync();
    }

    private void checkHint() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean seen = prefs.getBoolean(HINT_SHARED_EXPENSES_SEEN, false);
        View hintCard = findViewById(R.id.hintCard);
        if (hintCard != null) {
            if (!seen) {
                hintCard.setVisibility(View.VISIBLE);
                hintCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.hint_enter));
                findViewById(R.id.btnDismissHint).setOnClickListener(v -> {
                    prefs.edit().putBoolean(HINT_SHARED_EXPENSES_SEEN, true).apply();
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

    private void migrateLegacyDataIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("shared_trips_migrated_to_room", false)) {
            executor.execute(() -> {
                List<SharedTrip> legacyTrips = LocalSharedTripManager.getAllTrips(this);
                for (SharedTrip trip : legacyTrips) {
                    localDb.sharedTripDao().insert(trip);
                    List<SharedTripMember> members = LocalSharedTripManager.getMembers(this, trip.getTripId());
                    for (SharedTripMember m : members) {
                        m.setTripId(trip.getTripId());
                        localDb.sharedTripMemberDao().insert(m);
                    }
                }
                prefs.edit().putBoolean("shared_trips_migrated_to_room", true).apply();
                runOnUiThread(this::loadTrips);
            });
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        
        Uri data = intent.getData();
        if (data != null && "masarifipro".equals(data.getScheme()) && "join-trip".equals(data.getHost())) {
            String code = data.getQueryParameter("code");
            Log.d(LINK_TAG, "received code=" + code);
            
            if (code != null && !code.isEmpty()) {
                if (currentUser == null && !isGuestMode) {
                    Log.d(LINK_TAG, "User not logged in, saving pending code=" + code);
                    SharedPreferences prefs = getSharedPreferences("pending_invite_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putString("pending_trip_code", code).apply();
                } else if (isGuestMode) {
                    Toast.makeText(this, R.string.se_join_requires_login, Toast.LENGTH_LONG).show();
                } else {
                    Log.d(LINK_TAG, "joining from deep link code=" + code);
                    loadCurrentUserProfileName(profileName -> joinTrip(code, profileName));
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        rvSharedTrips = findViewById(R.id.rvSharedTrips);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvGuestNote = findViewById(R.id.tvGuestNote);
        progressBar = findViewById(R.id.progressBar);

        MaterialButton btnCreateTrip = findViewById(R.id.btnCreateTrip);
        MaterialButton btnJoinTrip = findViewById(R.id.btnJoinTrip);

        if (isGuestMode) {
            tvGuestNote.setVisibility(View.VISIBLE);
            tvGuestNote.setText(R.string.se_guest_mode_note);
            btnCreateTrip.setText(R.string.se_create_local_group);
            btnJoinTrip.setVisibility(View.GONE);
        } else {
            tvGuestNote.setVisibility(View.GONE);
        }

        btnCreateTrip.setOnClickListener(v -> showCreateTripDialog());
        btnJoinTrip.setOnClickListener(v -> {
            if (isGuestMode) {
                Toast.makeText(this, R.string.se_join_requires_login, Toast.LENGTH_LONG).show();
            } else {
                showJoinTripDialog();
            }
        });
    }

    private void setupRecyclerViews() {
        rvSharedTrips.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SharedTripAdapter();
        if (isGuestMode) {
            adapter.setCurrentUid("guest_user");
        }
        rvSharedTrips.setAdapter(adapter);

        adapter.setOnItemClickListener(trip -> {
            Intent intent = new Intent(SharedTripsActivity.this, SharedTripDetailsActivity.class);
            intent.putExtra("trip", trip);
            startActivity(intent);
        });

        adapter.setOnDeleteLeaveClickListener(this::handleDeleteLeave);
    }

    private void handleDeleteLeave(SharedTrip trip) {
        if (isGuestMode) {
            confirmDeleteGroup(trip);
            return;
        }

        if (trip.getOwnerUid().equals(currentUser.getUid())) {
            confirmDeleteGroup(trip);
        } else {
            confirmLeaveGroup(trip);
        }
    }

    private void confirmDeleteGroup(SharedTrip trip) {
        String message = isGuestMode ? getString(R.string.se_delete_local_group_confirm) : getString(R.string.se_delete_group_confirm);
        new AlertDialog.Builder(this)
                .setTitle(R.string.se_delete_group)
                .setMessage(message)
                .setPositiveButton(R.string.se_delete, (dialog, which) -> deleteGroup(trip))
                .setNegativeButton(R.string.se_cancel, null)
                .show();
    }

    private void deleteGroup(SharedTrip trip) {
        executor.execute(() -> {
            localDb.sharedTripDao().delete(trip);
            localDb.sharedTripMemberDao().deleteByTripId(trip.getTripId());
            localDb.sharedTripExpenseDao().deleteByTripId(trip.getTripId());
            runOnUiThread(() -> {
                loadTrips();
                Toast.makeText(this, R.string.se_local_group_deleted, Toast.LENGTH_SHORT).show();
            });

            if (!isGuestMode && trip.getOwnerUid().equals(currentUser.getUid())) {
                Log.d(TAG, "Deleting group from Firestore: " + trip.getTripId());
                WriteBatch batch = db.batch();
                db.collection("shared_trips").document(trip.getTripId()).collection("expenses").get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) batch.delete(doc.getReference());
                        db.collection("shared_trips").document(trip.getTripId()).collection("members").get()
                            .addOnSuccessListener(memberSnapshots -> {
                                for (QueryDocumentSnapshot doc : memberSnapshots) batch.delete(doc.getReference());
                                batch.delete(db.collection("shared_trips").document(trip.getTripId()));
                                batch.commit().addOnSuccessListener(aVoid -> Log.d(TAG, "Group deleted from Firestore")).addOnFailureListener(e -> Log.e(TAG, "Firestore delete failed", e));
                            });
                    });
            }
        });
    }

    private void confirmLeaveGroup(SharedTrip trip) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.se_leave_group)
                .setMessage(R.string.se_leave_group_confirm)
                .setPositiveButton(R.string.se_leave, (dialog, which) -> leaveGroup(trip))
                .setNegativeButton(R.string.se_cancel, null)
                .show();
    }

    private void leaveGroup(SharedTrip trip) {
        String uid = currentUser.getUid();
        executor.execute(() -> {
            localDb.sharedTripDao().delete(trip);
            localDb.sharedTripMemberDao().deleteByTripId(trip.getTripId());
            localDb.sharedTripExpenseDao().deleteByTripId(trip.getTripId());
            runOnUiThread(() -> {
                loadTrips();
                Toast.makeText(this, R.string.se_leave_group_success, Toast.LENGTH_SHORT).show();
            });

            WriteBatch batch = db.batch();
            batch.delete(db.collection("shared_trips").document(trip.getTripId()).collection("members").document(uid));
            batch.update(db.collection("shared_trips").document(trip.getTripId()), "memberUids", FieldValue.arrayRemove(uid));
            batch.commit();
        });
    }

    private void loadTrips() {
        executor.execute(() -> {
            List<SharedTrip> trips = localDb.sharedTripDao().getAllTrips();
            runOnUiThread(() -> {
                adapter.setTrips(trips);
                tvEmptyState.setVisibility(trips.isEmpty() ? View.VISIBLE : View.GONE);
                progressBar.setVisibility(View.GONE);
            });
            
            if (!isGuestMode && currentUser != null) {
                fetchTripsFromFirestore();
            }
        });
    }

    private void fetchTripsFromFirestore() {
        String currentUid = currentUser.getUid();
        db.collection("shared_trips")
                .whereArrayContains("memberUids", currentUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    executor.execute(() -> {
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            SharedTrip trip = doc.toObject(SharedTrip.class);
                            if (trip != null) {
                                trip.setSyncStatus(SharedTrip.STATUS_SYNCED);
                                localDb.sharedTripDao().insert(trip);
                            }
                        }
                        List<SharedTrip> updatedTrips = localDb.sharedTripDao().getAllTrips();
                        runOnUiThread(() -> {
                            adapter.setTrips(updatedTrips);
                            tvEmptyState.setVisibility(updatedTrips.isEmpty() ? View.VISIBLE : View.GONE);
                        });
                    });
                });
    }

    private void loadCurrentUserProfileName(ProfileNameCallback callback) {
        if (isGuestMode) {
            callback.onNameLoaded(getString(R.string.se_you));
            return;
        }

        db.collection("users").document(currentUser.getUid()).collection("profile").document("info")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String name = documentSnapshot.getString("name");
                    if (name == null || name.isEmpty()) name = currentUser.getDisplayName();
                    if (name == null || name.isEmpty()) {
                        String email = currentUser.getEmail();
                        if (email != null && email.contains("@")) name = email.split("@")[0];
                        else name = getString(R.string.se_user);
                    }
                    callback.onNameLoaded(name);
                })
                .addOnFailureListener(e -> callback.onNameLoaded(getString(R.string.se_user)));
    }

    private void showCreateTripDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_create_trip, null);
        builder.setView(view);

        TextInputEditText etTripName = view.findViewById(R.id.etTripName);
        Spinner spinnerCurrency = view.findViewById(R.id.spinnerCurrency);

        String[] currencies = {"USD", "SYP", "SAR", "AED", "EUR", "KWD", "LBP"};
        ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCurrency.setAdapter(currencyAdapter);

        AlertDialog dialog = builder.create();
        view.findViewById(R.id.btnCreate).setOnClickListener(v -> {
            String name = etTripName.getText().toString().trim();
            String currency = spinnerCurrency.getSelectedItem().toString();
            if (name.isEmpty()) {
                etTripName.setError(getString(R.string.se_name_required));
                return;
            }
            loadCurrentUserProfileName(profileName -> createTrip(name, currency, profileName));
            dialog.dismiss();
        });
        dialog.show();
    }

    private void createTrip(String name, String currency, String ownerName) {
        Log.d(TAG, "SHARED_TRIPS_SYNC: create group local first");
        String tripId = UUID.randomUUID().toString();
        String inviteCode = generateInviteCode();
        long now = System.currentTimeMillis();
        String ownerUid = isGuestMode ? "guest_user" : currentUser.getUid();

        SharedTrip trip = new SharedTrip(tripId, name, inviteCode, ownerUid, ownerName, currency, now);
        
        if (isGuestMode) {
            trip.setSyncStatus(SharedTrip.STATUS_LOCAL_ONLY);
            Log.d(TAG, "SHARED_TRIPS_SYNC: guest mode = true, group saved as LOCAL_ONLY");
        } else {
            trip.setSyncStatus(SharedTrip.STATUS_PENDING);
            Log.d(TAG, "SHARED_TRIPS_SYNC: group saved as PENDING");
        }

        executor.execute(() -> {
            localDb.sharedTripDao().insert(trip);
            SharedTripMember owner = new SharedTripMember(ownerUid, tripId, ownerName, isGuestMode ? "" : currentUser.getEmail(), now);
            localDb.sharedTripMemberDao().insert(owner);

            runOnUiThread(() -> {
                loadTrips();
                if (isGuestMode) {
                    Toast.makeText(this, R.string.se_group_created_locally, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.se_group_saved_locally_syncing, Toast.LENGTH_LONG).show();
                }
                Intent intent = new Intent(this, SharedTripDetailsActivity.class);
                intent.putExtra("trip", trip);
                startActivity(intent);
            });

            if (!isGuestMode) {
                if (SyncManager.getInstance(this).isNetworkAvailable()) {
                    Log.d(TAG, "SHARED_TRIPS_SYNC: network available = true, calling sync manager");
                    SharedTripsSyncManager.getInstance(this).uploadTripToFirestore(trip);
                } else {
                    Log.d(TAG, "SHARED_TRIPS_SYNC: network available = false, staying as PENDING");
                }
            }
        });
    }

    private void showJoinTripDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_join_trip, null);
        builder.setView(view);

        pendingJoinCodeEditText = view.findViewById(R.id.etInviteCode);
        MaterialButton btnScanQr = view.findViewById(R.id.btnScanQr);

        btnScanQr.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchQrScanner();
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        AlertDialog dialog = builder.create();
        view.findViewById(R.id.btnJoin).setOnClickListener(v -> {
            String code = pendingJoinCodeEditText.getText().toString().trim().toUpperCase();
            if (code.isEmpty()) {
                pendingJoinCodeEditText.setError(getString(R.string.se_code_required));
                return;
            }
            loadCurrentUserProfileName(profileName -> joinTrip(code, profileName));
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> pendingJoinCodeEditText = null);
        dialog.show();
    }

    private void launchQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.qr_scan_prompt));
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        qrScanLauncher.launch(options);
    }

    private String parseInviteCodeFromQr(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        raw = raw.trim();
        
        // Example: TRIP-M4Y1
        if (raw.startsWith("TRIP-")) {
            return raw;
        }
        
        // Example: masarifipro://join-trip?code=TRIP-M4Y1
        if (raw.contains("code=")) {
            try {
                Uri uri = Uri.parse(raw);
                String code = uri.getQueryParameter("code");
                if (code != null && !code.isEmpty()) return code;
            } catch (Exception ignored) {}
        }
        
        // Fallback for simple alphanumeric codes
        if (raw.length() >= 4 && raw.length() <= 20) return raw;
        
        return null;
    }

    private void joinTrip(String code, String memberName) {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        db.collection("shared_trips").whereEqualTo("inviteCode", code).limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.se_group_not_found, Toast.LENGTH_SHORT).show();
                    } else {
                        SharedTrip trip = queryDocumentSnapshots.getDocuments().get(0).toObject(SharedTrip.class);
                        if (trip != null) {
                            trip.setSyncStatus(SharedTrip.STATUS_SYNCED);
                            executor.execute(() -> localDb.sharedTripDao().insert(trip));
                            
                            addMemberToTripFirestore(trip.getTripId(), memberName);
                            db.collection("shared_trips").document(trip.getTripId())
                                    .update("memberUids", FieldValue.arrayUnion(currentUser.getUid()), "updatedAt", System.currentTimeMillis())
                                    .addOnSuccessListener(aVoid -> {
                                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                                        Toast.makeText(this, R.string.se_joined_group_success, Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(this, SharedTripDetailsActivity.class);
                                        intent.putExtra("trip", trip);
                                        startActivity(intent);
                                    });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.se_join_group_failed_check_internet, Toast.LENGTH_SHORT).show();
                });
    }

    private void addMemberToTripFirestore(String tripId, String memberName) {
        SharedTripMember member = new SharedTripMember(currentUser.getUid(), tripId, memberName, currentUser.getEmail(), System.currentTimeMillis());
        db.collection("shared_trips").document(tripId).collection("members").document(currentUser.getUid()).set(member);
        executor.execute(() -> localDb.sharedTripMemberDao().insert(member));
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder("TRIP-");
        Random random = new Random();
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrips();
        SharedTripsSyncManager.getInstance(this).sync();
    }
}

package com.example.masarifipro.activities;

import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.masarifipro.R;
import com.example.masarifipro.adapters.SharedTripExpenseAdapter;
import com.example.masarifipro.models.SharedTrip;
import com.example.masarifipro.models.SharedTripExpense;
import com.example.masarifipro.models.SharedTripMember;
import com.example.masarifipro.utils.LocalSharedTripManager;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.NumberFormatter;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SharedTripDetailsActivity extends AppCompatActivity {

    private static final String TAG = "SHARED_TRIPS_DETAILS";
    private SharedTrip trip;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private boolean isGuestMode = false;

    private TextView tvHeaderTripName, tvInviteCode, tvTotalExpenses, tvYourShare, tvYourPaid, tvYourNet, tvNetInfo;
    private TextView tvMembersTitle, tvExpensesTitle, tvFilterStatus, tvNoExpenses;
    private View layoutEmptyExpenses;
    private RecyclerView rvMembers, rvExpenses;
    private SharedTripExpenseAdapter expenseAdapter;
    private List<SharedTripMember> membersList = new ArrayList<>();
    private List<SharedTripExpense> allExpensesList = new ArrayList<>();
    private List<SharedTripExpense> displayedExpensesList = new ArrayList<>();

    // Filter states
    private String filterPayerUid = "all";
    private String filterSearchTitle = "";
    private Double filterMinAmount = null;
    private Double filterMaxAmount = null;
    private Long filterStartDate = null;
    private Long filterEndDate = null;
    private boolean isFilterActive = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_trip_details);

        trip = (SharedTrip) getIntent().getSerializableExtra("trip");
        if (trip == null) {
            finish();
            return;
        }

        isGuestMode = getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("guest_mode", false);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null && !isGuestMode) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initViews();
        setupRecyclerViews();
        if (!isGuestMode) {
            updateProfileNameAndLoadData();
        } else {
            loadLocalData();
        }
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        tvHeaderTripName = findViewById(R.id.tvHeaderTripName);
        tvInviteCode = findViewById(R.id.tvInviteCode);
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        tvYourShare = findViewById(R.id.tvYourShare);
        tvYourPaid = findViewById(R.id.tvYourPaid);
        tvYourNet = findViewById(R.id.tvYourNet);
        tvNetInfo = findViewById(R.id.tvNetInfo);
        
        tvMembersTitle = findViewById(R.id.tvMembersTitle);
        tvExpensesTitle = findViewById(R.id.tvExpensesTitle);
        tvFilterStatus = findViewById(R.id.tvFilterStatus);
        tvNoExpenses = findViewById(R.id.tvNoExpenses);
        layoutEmptyExpenses = findViewById(R.id.layoutEmptyExpenses);

        tvHeaderTripName.setText(trip.getName());

        if (isGuestMode) {
            tvInviteCode.setText(R.string.se_guest_mode_local);
            findViewById(R.id.btnCopyCode).setVisibility(View.GONE);
            findViewById(R.id.btnShareCode).setVisibility(View.GONE);
            findViewById(R.id.btnShowQr).setVisibility(View.GONE);
        } else {
            tvInviteCode.setText(trip.getInviteCode());
            findViewById(R.id.btnCopyCode).setOnClickListener(v -> copyInviteCode());
            findViewById(R.id.btnShareCode).setOnClickListener(v -> shareInviteCode());
            findViewById(R.id.btnShowQr).setOnClickListener(v -> showQrDialog());
        }

        ImageButton btnEditTripName = findViewById(R.id.btnEditTripName);
        ImageButton btnDeleteLeave = findViewById(R.id.btnDeleteLeave);
        
        boolean isOwner = isGuestMode || (currentUser != null && trip.getOwnerUid().equals(currentUser.getUid()));
        
        if (isOwner) {
            btnEditTripName.setVisibility(View.VISIBLE);
            btnEditTripName.setOnClickListener(v -> showEditTripNameDialog());
            
            btnDeleteLeave.setImageResource(android.R.drawable.ic_menu_delete);
            btnDeleteLeave.setOnClickListener(v -> confirmDeleteGroup());
        } else {
            btnEditTripName.setVisibility(View.GONE);
            btnDeleteLeave.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            btnDeleteLeave.setOnClickListener(v -> confirmLeaveGroup());
        }

        findViewById(R.id.btnAddExpense).setOnClickListener(v -> showAddExpenseDialog());
        findViewById(R.id.btnSettle).setOnClickListener(v -> calculateSettlements());
        findViewById(R.id.btnFilter).setOnClickListener(v -> showFilterDialog());
        findViewById(R.id.btnAddMember).setOnClickListener(v -> showAddMemberDialog());
        
        if (!isOwner) {
            findViewById(R.id.btnAddMember).setVisibility(View.GONE);
        }
    }

    private void showQrDialog() {
        String inviteCode = trip.getInviteCode();
        if (inviteCode == null || inviteCode.isEmpty() || inviteCode.equalsIgnoreCase("null")) {
            Toast.makeText(this, R.string.qr_no_invite_code, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_trip_qr, null);
        builder.setView(view);

        ImageView ivQrCode = view.findViewById(R.id.ivQrCode);
        TextView tvQrInviteCode = view.findViewById(R.id.tvQrInviteCode);
        Button btnClose = view.findViewById(R.id.btnQrClose);

        tvQrInviteCode.setText(inviteCode);
        
        try {
            Bitmap qrBitmap = generateQrBitmap(inviteCode, 800);
            ivQrCode.setImageBitmap(qrBitmap);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate QR", e);
            Toast.makeText(this, "Error generating QR Code", Toast.LENGTH_SHORT).show();
        }

        AlertDialog dialog = builder.create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private Bitmap generateQrBitmap(String text, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private void showEditTripNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_trip_name, null);
        builder.setView(view);

        TextInputEditText etNewName = view.findViewById(R.id.etNewTripName);
        etNewName.setText(trip.getName());

        builder.setPositiveButton(R.string.se_save, (dialog, which) -> {
            String newName = etNewName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, R.string.se_group_name_required, Toast.LENGTH_SHORT).show();
                return;
            }

            if (isGuestMode) {
                trip.setName(newName);
                trip.setUpdatedAt(System.currentTimeMillis());
                LocalSharedTripManager.saveTrip(this, trip);
                tvHeaderTripName.setText(newName);
                Toast.makeText(this, R.string.se_group_name_updated, Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", newName);
            updates.put("updatedAt", System.currentTimeMillis());

            db.collection("shared_trips").document(trip.getTripId())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        trip.setName(newName);
                        tvHeaderTripName.setText(newName);
                        Toast.makeText(this, R.string.se_group_name_updated, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, R.string.se_group_name_update_failed, Toast.LENGTH_SHORT).show());
        });
        builder.setNegativeButton(R.string.se_cancel, null);
        builder.show();
    }

    private void showAddMemberDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_offline_member, null);
        builder.setView(view);

        TextInputEditText etName = view.findViewById(R.id.etOfflineMemberName);

        builder.setPositiveButton(R.string.se_save, (dialog, which) -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.se_person_name_required, Toast.LENGTH_SHORT).show();
                return;
            }

            addOfflineMember(name);
        });
        builder.setNegativeButton(R.string.se_cancel, null);
        builder.show();
    }

    private void addOfflineMember(String name) {
        String memberId = "offline_" + System.currentTimeMillis();
        String currentUid = isGuestMode ? "guest_user" : currentUser.getUid();
        SharedTripMember offlineMember = new SharedTripMember(memberId, name, "", System.currentTimeMillis(), true, currentUid);
        offlineMember.setTripId(trip.getTripId());

        if (isGuestMode) {
            membersList.add(offlineMember);
            LocalSharedTripManager.saveMembers(this, trip.getTripId(), membersList);
            if (trip.getMemberUids() == null) trip.setMemberUids(new ArrayList<>());
            trip.getMemberUids().add(memberId);
            trip.setUpdatedAt(System.currentTimeMillis());
            LocalSharedTripManager.saveTrip(this, trip);
            updateMembersUI();
            calculateTotals();
            Toast.makeText(this, R.string.se_person_added, Toast.LENGTH_SHORT).show();
            return;
        }

        WriteBatch batch = db.batch();
        batch.set(db.collection("shared_trips").document(trip.getTripId()).collection("members").document(memberId), offlineMember);
        batch.update(db.collection("shared_trips").document(trip.getTripId()), "memberUids", FieldValue.arrayUnion(memberId));
        batch.update(db.collection("shared_trips").document(trip.getTripId()), "updatedAt", System.currentTimeMillis());

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, R.string.se_person_added, Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, R.string.se_person_add_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private void confirmDeleteGroup() {
        String message = isGuestMode ? getString(R.string.se_delete_local_group_confirm) : getString(R.string.se_delete_group_confirm);
        new AlertDialog.Builder(this)
                .setTitle(R.string.se_delete_group)
                .setMessage(message)
                .setPositiveButton(R.string.se_delete, (dialog, which) -> deleteGroup())
                .setNegativeButton(R.string.se_cancel, null)
                .show();
    }

    private void deleteGroup() {
        if (isGuestMode) {
            LocalSharedTripManager.deleteTrip(this, trip.getTripId());
            Toast.makeText(this, R.string.se_group_deleted, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        WriteBatch batch = db.batch();

        db.collection("shared_trips").document(trip.getTripId()).collection("expenses").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.delete(doc.getReference());
                    }
                    db.collection("shared_trips").document(trip.getTripId()).collection("members").get()
                            .addOnSuccessListener(memberSnapshots -> {
                                for (QueryDocumentSnapshot doc : memberSnapshots) {
                                    batch.delete(doc.getReference());
                                }
                                batch.delete(db.collection("shared_trips").document(trip.getTripId()));

                                batch.commit().addOnSuccessListener(aVoid -> {
                                    Toast.makeText(SharedTripDetailsActivity.this, R.string.se_group_deleted, Toast.LENGTH_SHORT).show();
                                    finish();
                                }).addOnFailureListener(e -> {
                                    Log.e(TAG, "delete failed", e);
                                    Toast.makeText(SharedTripDetailsActivity.this, R.string.se_delete_group_failed, Toast.LENGTH_SHORT).show();
                                });
                            });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "delete failed", e);
                    Toast.makeText(this, R.string.se_delete_group_failed, Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmLeaveGroup() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.se_leave_group)
                .setMessage(R.string.se_leave_group_confirm)
                .setPositiveButton(R.string.se_leave, (dialog, which) -> leaveGroup())
                .setNegativeButton(R.string.se_cancel, null)
                .show();
    }

    private void leaveGroup() {
        String tripId = trip.getTripId();
        String uid = currentUser.getUid();

        WriteBatch batch = db.batch();
        batch.delete(db.collection("shared_trips").document(tripId).collection("members").document(uid));
        batch.update(db.collection("shared_trips").document(tripId), "memberUids", FieldValue.arrayRemove(uid));

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(SharedTripDetailsActivity.this, R.string.se_leave_group_success, Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "leave failed", e);
            Toast.makeText(SharedTripDetailsActivity.this, R.string.se_leave_group_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private void copyInviteCode() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Invite Code", trip.getInviteCode());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.se_invite_code_copied, Toast.LENGTH_SHORT).show();
    }

    private void setupRecyclerViews() {
        rvMembers = findViewById(R.id.rvMembers);
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        
        rvExpenses = findViewById(R.id.rvExpenses);
        rvExpenses.setLayoutManager(new LinearLayoutManager(this));
        expenseAdapter = new SharedTripExpenseAdapter();
        rvExpenses.setAdapter(expenseAdapter);
    }

    private void updateProfileNameAndLoadData() {
        db.collection("users").document(currentUser.getUid()).collection("profile").document("info")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String name = null;
                    if (documentSnapshot.exists()) {
                        name = documentSnapshot.getString("name");
                    }
                    if (name == null || name.isEmpty()) {
                        name = currentUser.getDisplayName();
                    }
                    if (name == null || name.isEmpty()) {
                        String email = currentUser.getEmail();
                        if (email != null && email.contains("@")) {
                            name = email.split("@")[0];
                        } else {
                            name = getString(R.string.se_user);
                        }
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("name", name);
                    if (currentUser.getEmail() != null) {
                        updates.put("email", currentUser.getEmail());
                    }

                    db.collection("shared_trips").document(trip.getTripId())
                            .collection("members").document(currentUser.getUid())
                            .update(updates);
                    
                    loadData();
                })
                .addOnFailureListener(e -> loadData());
    }

    private void loadData() {
        db.collection("shared_trips").document(trip.getTripId()).collection("members")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        membersList = value.toObjects(SharedTripMember.class);
                        updateMembersUI();
                        calculateTotals();
                    }
                });

        db.collection("shared_trips").document(trip.getTripId()).collection("expenses")
                .orderBy("createdAt")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        allExpensesList = value.toObjects(SharedTripExpense.class);
                        applyFilters();
                        calculateTotals();
                    }
                });

        db.collection("shared_trips").document(trip.getTripId())
                .addSnapshotListener((value, error) -> {
                    if (value != null && value.exists()) {
                        String newName = value.getString("name");
                        if (newName != null && !newName.equals(trip.getName())) {
                            trip.setName(newName);
                            tvHeaderTripName.setText(newName);
                        }
                    }
                });
    }

    private void loadLocalData() {
        membersList = LocalSharedTripManager.getMembers(this, trip.getTripId());
        allExpensesList = LocalSharedTripManager.getExpenses(this, trip.getTripId());
        updateMembersUI();
        applyFilters();
        calculateTotals();
    }

    private void updateMembersUI() {
        tvMembersTitle.setText(getString(R.string.se_members_count, membersList.size()));
        boolean isOwner = isGuestMode || (currentUser != null && trip.getOwnerUid().equals(currentUser.getUid()));

        rvMembers.setAdapter(new RecyclerView.Adapter<MemberViewHolder>() {
            @NonNull
            @Override
            public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_trip_member, parent, false);
                return new MemberViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
                SharedTripMember m = membersList.get(position);
                String name = m.getName();
                String currentUid = isGuestMode ? "guest_user" : (currentUser != null ? currentUser.getUid() : "");
                
                holder.tvName.setText(name);
                if (name != null && !name.isEmpty()) {
                    holder.tvAvatar.setText(name.substring(0, 1).toUpperCase());
                } else {
                    holder.tvAvatar.setText("?");
                }

                if (m.getUid().equals(currentUid)) {
                    holder.tvStatus.setText(R.string.se_you);
                } else if (m.isOffline()) {
                    holder.tvStatus.setText(R.string.se_offline);
                } else {
                    holder.tvStatus.setText(R.string.se_online);
                }

                if (isOwner && m.isOffline()) {
                    holder.btnDelete.setVisibility(View.VISIBLE);
                    holder.btnDelete.setOnClickListener(v -> confirmDeleteOfflineMember(m));
                } else {
                    holder.btnDelete.setVisibility(View.GONE);
                }
            }

            @Override
            public int getItemCount() {
                return membersList.size();
            }
        });
    }

    private void confirmDeleteOfflineMember(SharedTripMember member) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.se_delete_person)
                .setMessage(R.string.se_delete_person_confirm)
                .setPositiveButton(R.string.se_delete, (dialog, which) -> {
                    boolean hasExpenses = false;
                    for (SharedTripExpense e : allExpensesList) {
                        if (e.getPaidByUid().equals(member.getUid())) {
                            hasExpenses = true;
                            break;
                        }
                    }

                    if (hasExpenses) {
                        Toast.makeText(this, R.string.se_cannot_delete_person_has_expenses, Toast.LENGTH_LONG).show();
                    } else {
                        deleteOfflineMember(member);
                    }
                })
                .setNegativeButton(R.string.se_cancel, null)
                .show();
    }

    private void deleteOfflineMember(SharedTripMember member) {
        if (isGuestMode) {
            membersList.remove(member);
            LocalSharedTripManager.saveMembers(this, trip.getTripId(), membersList);
            if (trip.getMemberUids() != null) {
                trip.getMemberUids().remove(member.getUid());
            }
            LocalSharedTripManager.saveTrip(this, trip);
            updateMembersUI();
            calculateTotals();
            Toast.makeText(this, R.string.se_person_deleted, Toast.LENGTH_SHORT).show();
            return;
        }

        WriteBatch batch = db.batch();
        batch.delete(db.collection("shared_trips").document(trip.getTripId()).collection("members").document(member.getUid()));
        batch.update(db.collection("shared_trips").document(trip.getTripId()), "memberUids", FieldValue.arrayRemove(member.getUid()));
        batch.update(db.collection("shared_trips").document(trip.getTripId()), "updatedAt", System.currentTimeMillis());

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, R.string.se_person_deleted, Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, R.string.se_person_delete_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAvatar, tvStatus;
        ImageButton btnDelete;
        public MemberViewHolder(View itemView) { 
            super(itemView); 
            tvName = itemView.findViewById(R.id.tvMemberName);
            tvAvatar = itemView.findViewById(R.id.tvMemberAvatar);
            tvStatus = itemView.findViewById(R.id.tvMemberStatus);
            btnDelete = itemView.findViewById(R.id.btnDeleteMember);
        }
    }

    private void calculateTotals() {
        if (membersList.isEmpty()) return;

        double total = 0;
        double yourPaid = 0;
        String currentUid = isGuestMode ? "guest_user" : (currentUser != null ? currentUser.getUid() : "");
        for (SharedTripExpense e : allExpensesList) {
            total += e.getAmount();
            if (e.getPaidByUid().equals(currentUid)) {
                yourPaid += e.getAmount();
            }
        }

        double sharePerMember = total / membersList.size();
        double yourNet = yourPaid - sharePerMember;

        tvTotalExpenses.setText(NumberFormatter.formatAmount(this, total, trip.getCurrencyCode()));
        tvYourShare.setText(NumberFormatter.formatAmount(this, sharePerMember, trip.getCurrencyCode()));
        tvYourPaid.setText(NumberFormatter.formatAmount(this, yourPaid, trip.getCurrencyCode()));
        tvYourNet.setText(NumberFormatter.formatAmount(this, yourNet, trip.getCurrencyCode()));
        
        if (yourNet > 0.01) {
            tvYourNet.setTextColor(ContextCompat.getColor(this, R.color.shared_details_income));
            tvNetInfo.setText(getString(R.string.se_you_will_receive_amount, NumberFormatter.formatAmount(this, yourNet, trip.getCurrencyCode())));
        } else if (yourNet < -0.01) {
            tvYourNet.setTextColor(ContextCompat.getColor(this, R.color.shared_details_expense));
            tvNetInfo.setText(getString(R.string.se_you_should_pay_amount, NumberFormatter.formatAmount(this, Math.abs(yourNet), trip.getCurrencyCode())));
        } else {
            tvYourNet.setTextColor(ContextCompat.getColor(this, R.color.shared_details_text_primary));
            tvNetInfo.setText(R.string.se_your_share_fully_settled);
        }

        tvExpensesTitle.setText(getString(R.string.se_expenses_count, allExpensesList.size()));
        
        if (allExpensesList.isEmpty()) {
            layoutEmptyExpenses.setVisibility(View.VISIBLE);
        } else {
            layoutEmptyExpenses.setVisibility(View.GONE);
        }
    }

    private void showAddExpenseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_shared_expense, null);
        builder.setView(view);

        TextInputEditText etTitle = view.findViewById(R.id.etExpenseTitle);
        TextInputEditText etAmount = view.findViewById(R.id.etExpenseAmount);
        TextInputEditText etNote = view.findViewById(R.id.etExpenseNote);
        Spinner spinnerPaidBy = view.findViewById(R.id.spinnerPaidBy);

        List<String> memberNames = new ArrayList<>();
        String currentUid = isGuestMode ? "guest_user" : (currentUser != null ? currentUser.getUid() : "");
        for (SharedTripMember m : membersList) {
            String name = m.getName();
            if (m.getUid().equals(currentUid)) name += " (" + getString(R.string.se_you) + ")";
            else if (m.isOffline()) name += " (" + getString(R.string.se_offline) + ")";
            memberNames.add(name);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, memberNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaidBy.setAdapter(adapter);

        for (int i = 0; i < membersList.size(); i++) {
            if (membersList.get(i).getUid().equals(currentUid)) {
                spinnerPaidBy.setSelection(i);
                break;
            }
        }

        AlertDialog dialog = builder.create();
        view.findViewById(R.id.btnSaveExpense).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim().replace(",", "");
            int selectedMemberIdx = spinnerPaidBy.getSelectedItemPosition();

            if (title.isEmpty() || amountStr.isEmpty() || selectedMemberIdx == -1) {
                Toast.makeText(this, R.string.se_fill_required_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double amount = Double.parseDouble(amountStr);
                if (amount <= 0) {
                    Toast.makeText(this, R.string.se_amount_must_be_greater_than_zero, Toast.LENGTH_SHORT).show();
                    return;
                }

                SharedTripMember paidBy = membersList.get(selectedMemberIdx);
                addExpense(title, amount, paidBy, etNote.getText().toString());
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(this, R.string.se_invalid_amount, Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void addExpense(String title, double amount, SharedTripMember paidBy, String note) {
        String expenseId = isGuestMode ? "local_exp_" + System.currentTimeMillis() : db.collection("shared_trips").document(trip.getTripId()).collection("expenses").document().getId();
        SharedTripExpense expense = new SharedTripExpense(expenseId, trip.getTripId(), title, amount, trip.getCurrencyCode(),
                paidBy.getUid(), paidBy.getName(), System.currentTimeMillis(), note);

        if (isGuestMode) {
            allExpensesList.add(expense);
            LocalSharedTripManager.saveExpenses(this, trip.getTripId(), allExpensesList);
            trip.setUpdatedAt(System.currentTimeMillis());
            LocalSharedTripManager.saveTrip(this, trip);
            applyFilters();
            calculateTotals();
            Toast.makeText(this, R.string.se_expense_added, Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("shared_trips").document(trip.getTripId()).collection("expenses").document(expenseId).set(expense)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, R.string.se_expense_added, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, R.string.se_expense_add_failed, Toast.LENGTH_SHORT).show());
    }

    private void calculateSettlements() {
        if (membersList.isEmpty()) return;

        Map<String, Double> balances = new HashMap<>();
        double total = 0;
        for (SharedTripExpense e : allExpensesList) {
            total += e.getAmount();
            balances.put(e.getPaidByUid(), balances.getOrDefault(e.getPaidByUid(), 0.0) + e.getAmount());
        }

        double sharePerMember = total / membersList.size();
        List<MemberBalance> creditors = new ArrayList<>();
        List<MemberBalance> debtors = new ArrayList<>();

        for (SharedTripMember m : membersList) {
            double net = balances.getOrDefault(m.getUid(), 0.0) - sharePerMember;
            if (net > 0.01) {
                creditors.add(new MemberBalance(m, net));
            } else if (net < -0.01) {
                debtors.add(new MemberBalance(m, Math.abs(net)));
            }
        }

        Collections.sort(creditors, (a, b) -> Double.compare(b.balance, a.balance));
        Collections.sort(debtors, (a, b) -> Double.compare(b.balance, a.balance));

        StringBuilder result = new StringBuilder();
        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            MemberBalance debtor = debtors.get(i);
            MemberBalance creditor = creditors.get(j);

            double payment = Math.min(debtor.balance, creditor.balance);
            result.append(debtor.member.getName())
                    .append(" ")
                    .append(getString(R.string.se_pays))
                    .append(" ")
                    .append(NumberFormatter.formatAmount(this, payment, trip.getCurrencyCode()))
                    .append(" ")
                    .append(getString(R.string.se_to))
                    .append(" ")
                    .append(creditor.member.getName())
                    .append("\n");

            debtor.balance -= payment;
            creditor.balance -= payment;

            if (debtor.balance < 0.01) i++;
            if (creditor.balance < 0.01) j++;
        }

        if (result.length() == 0) {
            result.append(getString(R.string.se_everyone_settled));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.se_settlement_who_pays_whom);
        View settlementView = getLayoutInflater().inflate(R.layout.dialog_settlement_results, null);
        TextView tvResults = settlementView.findViewById(R.id.tvSettlementResults);
        tvResults.setText(result.toString());
        builder.setView(settlementView);
        builder.setPositiveButton(R.string.se_ok, null);
        builder.show();
    }

    private static class MemberBalance {
        SharedTripMember member;
        double balance;
        MemberBalance(SharedTripMember m, double b) { this.member = m; this.balance = b; }
    }

    private void showFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_filter_shared_expenses, null);
        builder.setView(view);

        TextInputEditText etTitle = view.findViewById(R.id.etFilterTitle);
        Spinner spinnerPayer = view.findViewById(R.id.spinnerFilterPayer);
        TextInputEditText etMin = view.findViewById(R.id.etMinAmount);
        TextInputEditText etMax = view.findViewById(R.id.etMaxAmount);
        Button btnStart = view.findViewById(R.id.btnStartDate);
        Button btnEnd = view.findViewById(R.id.btnEndDate);
        Button btnClear = view.findViewById(R.id.btnClearFilter);
        Button btnApply = view.findViewById(R.id.btnApplyFilter);

        // Setup Spinner
        List<String> payerOptions = new ArrayList<>();
        payerOptions.add(getString(R.string.se_all));
        for (SharedTripMember m : membersList) {
            payerOptions.add(m.getName());
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, payerOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPayer.setAdapter(spinnerAdapter);

        // Pre-fill current filter values
        etTitle.setText(filterSearchTitle);
        if (filterMinAmount != null) etMin.setText(String.valueOf(filterMinAmount));
        if (filterMaxAmount != null) etMax.setText(String.valueOf(filterMaxAmount));
        
        int selectedPayerIdx = 0;
        if (!filterPayerUid.equals("all")) {
            for (int i = 0; i < membersList.size(); i++) {
                if (membersList.get(i).getUid().equals(filterPayerUid)) {
                    selectedPayerIdx = i + 1;
                    break;
                }
            }
        }
        spinnerPayer.setSelection(selectedPayerIdx);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        final Long[] selectedStart = {filterStartDate};
        final Long[] selectedEnd = {filterEndDate};

        if (selectedStart[0] != null) btnStart.setText(sdf.format(new Date(selectedStart[0])));
        if (selectedEnd[0] != null) btnEnd.setText(sdf.format(new Date(selectedEnd[0])));

        btnStart.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedStart[0] != null) cal.setTimeInMillis(selectedStart[0]);
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                cal.set(year, month, dayOfMonth, 0, 0, 0);
                selectedStart[0] = cal.getTimeInMillis();
                btnStart.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnEnd.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedEnd[0] != null) cal.setTimeInMillis(selectedEnd[0]);
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                cal.set(year, month, dayOfMonth, 23, 59, 59);
                selectedEnd[0] = cal.getTimeInMillis();
                btnEnd.setText(sdf.format(cal.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = builder.create();

        btnClear.setOnClickListener(v -> {
            isFilterActive = false;
            filterSearchTitle = "";
            filterPayerUid = "all";
            filterMinAmount = null;
            filterMaxAmount = null;
            filterStartDate = null;
            filterEndDate = null;
            applyFilters();
            dialog.dismiss();
        });

        btnApply.setOnClickListener(v -> {
            filterSearchTitle = etTitle.getText().toString().trim();
            int pos = spinnerPayer.getSelectedItemPosition();
            if (pos == 0) filterPayerUid = "all";
            else filterPayerUid = membersList.get(pos - 1).getUid();

            String minStr = etMin.getText().toString().trim();
            try {
                filterMinAmount = minStr.isEmpty() ? null : Double.parseDouble(minStr);
            } catch (NumberFormatException e) { filterMinAmount = null; }
            String maxStr = etMax.getText().toString().trim();
            try {
                filterMaxAmount = maxStr.isEmpty() ? null : Double.parseDouble(maxStr);
            } catch (NumberFormatException e) { filterMaxAmount = null; }

            filterStartDate = selectedStart[0];
            filterEndDate = selectedEnd[0];

            isFilterActive = !filterSearchTitle.isEmpty() || !filterPayerUid.equals("all") || 
                             filterMinAmount != null || filterMaxAmount != null || 
                             filterStartDate != null || filterEndDate != null;
            
            applyFilters();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void applyFilters() {
        displayedExpensesList.clear();
        if (!isFilterActive) {
            displayedExpensesList.addAll(allExpensesList);
            tvFilterStatus.setVisibility(View.GONE);
        } else {
            for (SharedTripExpense e : allExpensesList) {
                boolean match = true;
                if (!filterSearchTitle.isEmpty() && !e.getTitle().toLowerCase().contains(filterSearchTitle.toLowerCase())) {
                    match = false;
                }
                if (match && !filterPayerUid.equals("all") && !e.getPaidByUid().equals(filterPayerUid)) {
                    match = false;
                }
                if (match && filterMinAmount != null && e.getAmount() < filterMinAmount) {
                    match = false;
                }
                if (match && filterMaxAmount != null && e.getAmount() > filterMaxAmount) {
                    match = false;
                }
                if (match && filterStartDate != null && e.getCreatedAt() < filterStartDate) {
                    match = false;
                }
                if (match && filterEndDate != null && e.getCreatedAt() > filterEndDate) {
                    match = false;
                }
                if (match) displayedExpensesList.add(e);
            }
            tvFilterStatus.setVisibility(View.VISIBLE);
        }

        expenseAdapter.setExpenses(displayedExpensesList);
        tvNoExpenses.setVisibility(displayedExpensesList.isEmpty() && !allExpensesList.isEmpty() ? View.VISIBLE : View.GONE);
        
        if (allExpensesList.isEmpty()) {
            layoutEmptyExpenses.setVisibility(View.VISIBLE);
        } else {
            layoutEmptyExpenses.setVisibility(View.GONE);
        }
    }

    private void shareInviteCode() {
        String shareLink = "masarifipro://join-trip?code=" + trip.getInviteCode();
        String shareText = getString(R.string.se_invite_share_text, trip.getName(), trip.getInviteCode(), shareLink);
        
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        sendIntent.setType("text/plain");

        try {
            getPackageManager().getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES);
            Intent whatsappIntent = new Intent(Intent.ACTION_SEND);
            whatsappIntent.setType("text/plain");
            whatsappIntent.setPackage("com.whatsapp");
            whatsappIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(whatsappIntent);
        } catch (PackageManager.NameNotFoundException e) {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.se_share_invite_via)));
        }
    }
}

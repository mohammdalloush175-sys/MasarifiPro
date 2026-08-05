package com.example.masarifipro.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.adapters.SharedAccountAdapter;
import com.example.masarifipro.models.SharedAccount;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SharedAccountActivity extends AppCompatActivity {

    private static final String TAG = "SharedAccountActivity";
    private static final String CLICK_TAG = "SHARED_CLICK";
    private static final String PREFS_NAME = "account_prefs";
    private static final String KEY_SHARED_ACCOUNT_ID = "shared_account_id";

    private TextInputEditText etAccountName, etInviteCode;
    private Button btnCreateAccount, btnJoinAccount;
    private ProgressBar progressBar;
    private RecyclerView rvSharedAccounts;
    private SharedAccountAdapter adapter;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_account);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        initViews();
        setupRecyclerView();
        setupListeners();
        loadMySharedAccounts();
    }

    private void initViews() {
        etAccountName = findViewById(R.id.etAccountName);
        etInviteCode = findViewById(R.id.etInviteCode);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        btnJoinAccount = findViewById(R.id.btnJoinAccount);
        progressBar = findViewById(R.id.progressBar);
        rvSharedAccounts = findViewById(R.id.rvSharedAccounts);
    }

    private void setupRecyclerView() {
        rvSharedAccounts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SharedAccountAdapter(account -> {
            Log.d(CLICK_TAG, "item clicked");
            Log.d(CLICK_TAG, "id=" + account.getId());
            Log.d(CLICK_TAG, "name=" + account.getName());
            Log.d(CLICK_TAG, "opening details activity");

            saveSelectedAccountId(account.getId());
            
            Intent intent = new Intent(this, SharedAccountDetailsActivity.class);
            intent.putExtra("sharedAccountId", account.getId());
            intent.putExtra("sharedAccountName", account.getName());
            startActivity(intent);
        });
        rvSharedAccounts.setAdapter(adapter);
    }

    private void setupListeners() {
        btnCreateAccount.setOnClickListener(v -> createSharedAccount());
        btnJoinAccount.setOnClickListener(v -> joinSharedAccount());
    }

    private void loadMySharedAccounts() {
        String uid = mAuth.getUid();
        if (uid == null) return;

        showLoading(true);
        db.collection("users").document(uid).collection("shared_accounts")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    showLoading(false);
                    List<SharedAccount> accountList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        SharedAccount account = doc.toObject(SharedAccount.class);
                        account.setId(doc.getId()); // Document ID is the account ID
                        account.setRole(doc.getString("role"));
                        accountList.add(account);
                    }
                    adapter.setAccounts(accountList);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error loading accounts", e);
                    Toast.makeText(this, "فشل تحميل الحسابات", Toast.LENGTH_SHORT).show();
                });
    }

    private void createSharedAccount() {
        String name = etAccountName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "يرجى إدخال اسم الحساب", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getUid();
        if (uid == null) {
            Toast.makeText(this, "يجب تسجيل الدخول أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        String accountId = db.collection("shared_accounts").document().getId();
        String inviteCode = generateInviteCode();

        WriteBatch batch = db.batch();

        // 1. Save shared_account document
        Map<String, Object> accountData = new HashMap<>();
        accountData.put("id", accountId);
        accountData.put("name", name);
        accountData.put("inviteCode", inviteCode);
        accountData.put("ownerId", uid);
        accountData.put("createdAt", FieldValue.serverTimestamp());
        batch.set(db.collection("shared_accounts").document(accountId), accountData);

        // 2. Save member document
        Map<String, Object> memberData = new HashMap<>();
        memberData.put("uid", uid);
        memberData.put("role", "owner");
        memberData.put("joinedAt", FieldValue.serverTimestamp());
        batch.set(db.collection("shared_accounts").document(accountId).collection("members").document(uid), memberData);

        // 3. Save user reference
        Map<String, Object> userRefData = new HashMap<>();
        userRefData.put("sharedAccountId", accountId);
        userRefData.put("name", name);
        userRefData.put("inviteCode", inviteCode);
        userRefData.put("role", "owner");
        userRefData.put("joinedAt", FieldValue.serverTimestamp());
        batch.set(db.collection("users").document(uid).collection("shared_accounts").document(accountId), userRefData);

        batch.commit().addOnSuccessListener(aVoid -> {
            showLoading(false);
            Toast.makeText(this, "تم إنشاء الحساب بنجاح", Toast.LENGTH_SHORT).show();
            etAccountName.setText("");
            loadMySharedAccounts();
        }).addOnFailureListener(e -> {
            showLoading(false);
            Log.e(TAG, "Error creating shared account batch", e);
            Toast.makeText(this, "فشل إنشاء الحساب المشترك", Toast.LENGTH_SHORT).show();
        });
    }

    private void joinSharedAccount() {
        String code = etInviteCode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            Toast.makeText(this, "يرجى إدخال رمز الدعوة", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = mAuth.getUid();
        if (uid == null) return;

        showLoading(true);
        db.collection("shared_accounts")
                .whereEqualTo("inviteCode", code)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) queryDocumentSnapshots.getDocuments().get(0);
                        String accountId = doc.getId();
                        String name = doc.getString("name");
                        addUserToSharedAccount(accountId, name, code, uid);
                    } else {
                        showLoading(false);
                        Toast.makeText(this, "رمز الدعوة غير صحيح", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e(TAG, "Error joining account", e);
                    Toast.makeText(this, "فشل الانضمام", Toast.LENGTH_SHORT).show();
                });
    }

    private void addUserToSharedAccount(String accountId, String name, String code, String uid) {
        WriteBatch batch = db.batch();

        Map<String, Object> memberData = new HashMap<>();
        memberData.put("uid", uid);
        memberData.put("role", "member");
        memberData.put("joinedAt", FieldValue.serverTimestamp());
        batch.set(db.collection("shared_accounts").document(accountId).collection("members").document(uid), memberData);

        Map<String, Object> userRefData = new HashMap<>();
        userRefData.put("sharedAccountId", accountId);
        userRefData.put("name", name);
        userRefData.put("inviteCode", code);
        userRefData.put("role", "member");
        userRefData.put("joinedAt", FieldValue.serverTimestamp());
        batch.set(db.collection("users").document(uid).collection("shared_accounts").document(accountId), userRefData);

        batch.commit().addOnSuccessListener(aVoid -> {
            showLoading(false);
            Toast.makeText(this, "تم الانضمام بنجاح", Toast.LENGTH_SHORT).show();
            etInviteCode.setText("");
            loadMySharedAccounts();
        }).addOnFailureListener(e -> {
            showLoading(false);
            Log.e(TAG, "Error joining shared account batch", e);
            Toast.makeText(this, "فشل إكمال عملية الانضمام", Toast.LENGTH_SHORT).show();
        });
    }

    private void saveSelectedAccountId(String accountId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_SHARED_ACCOUNT_ID, accountId).apply();
    }

    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        while (sb.length() < 6) {
            int index = (int) (rnd.nextFloat() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnCreateAccount.setEnabled(!isLoading);
        btnJoinAccount.setEnabled(!isLoading);
    }
}

package com.example.masarifipro.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.masarifipro.R;
import com.example.masarifipro.adapters.TransactionAdapter;
import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.utils.TransactionSyncManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class SharedAccountDetailsActivity extends AppCompatActivity {

    private static final String TAG = "SHARED_CLICK";
    private String sharedAccountId;
    private String sharedAccountName;

    private TextView tvAccountName, tvAccountId, tvNoTransactions;
    private RecyclerView rvTransactions;
    private TransactionAdapter adapter;
    private ProgressBar progressBar;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_account_details);

        db = FirebaseFirestore.getInstance();

        sharedAccountId = getIntent().getStringExtra("sharedAccountId");
        sharedAccountName = getIntent().getStringExtra("sharedAccountName");

        Log.d(TAG, "details received id=" + sharedAccountId);

        if (sharedAccountId == null || sharedAccountId.isEmpty()) {
            Log.e(TAG, "shared account id is null");
            Toast.makeText(this, "تعذر فتح الحساب المشترك", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadTransactions();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("تفاصيل الحساب المشترك");
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        tvAccountName = findViewById(R.id.tvAccountName);
        tvAccountId = findViewById(R.id.tvAccountId);
        tvNoTransactions = findViewById(R.id.tvNoTransactions);
        rvTransactions = findViewById(R.id.rvTransactions);
        progressBar = findViewById(R.id.progressBar);

        tvAccountName.setText(sharedAccountName);
        tvAccountId.setText("ID: " + sharedAccountId);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        rvTransactions.setAdapter(adapter);
    }

    private void loadTransactions() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("shared_accounts")
                .document(sharedAccountId)
                .collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);
                    List<Transaction> transactions = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Transaction t = TransactionSyncManager.transactionFromFirestore(doc);
                        if (t != null) {
                            transactions.add(t);
                        }
                    }

                    adapter.setTransactions(transactions);
                    tvNoTransactions.setVisibility(transactions.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error loading shared transactions", e);
                    Toast.makeText(this, "فشل تحميل المعاملات", Toast.LENGTH_SHORT).show();
                });
    }
}

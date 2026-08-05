package com.example.masarifipro.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.masarifipro.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AdminNotificationActivity extends AppCompatActivity {

    private TextInputEditText etTitle, etBody;
    private RadioGroup rgMessageType;
    private MaterialButton btnSend;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_notification);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etTitle = findViewById(R.id.etTitle);
        etBody = findViewById(R.id.etBody);
        rgMessageType = findViewById(R.id.rgMessageType);
        btnSend = findViewById(R.id.btnSendNotification);
        progressBar = findViewById(R.id.progressBar);

        btnSend.setOnClickListener(v -> sendNotificationRequest());
    }

    private void sendNotificationRequest() {
        String title = etTitle.getText().toString().trim();
        String body = etBody.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            etTitle.setError("عنوان الإشعار مطلوب");
            return;
        }

        if (TextUtils.isEmpty(body)) {
            etBody.setError("نص الرسالة مطلوب");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "يجب تسجيل الدخول أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = rgMessageType.getCheckedRadioButtonId();
        String messageType = "in_app";

        if (checkedId == R.id.rbPush) {
            messageType = "push";
        } else if (checkedId == R.id.rbBoth) {
            messageType = "both";
        }

        // Handle Push/Both warning for Spark Plan (No Cloud Functions)
        if (messageType.equals("push") || messageType.equals("both")) {
            new AlertDialog.Builder(this)
                    .setTitle("يتطلب إعداد إضافي")
                    .setMessage("إرسال إشعار للموبايل من داخل التطبيق يحتاج Cloud Function وخطة Firebase Blaze. حالياً يمكنك إرسال الإشعارات من Firebase Console إلى topic: all_users.")
                    .setPositiveButton("حسناً", null)
                    .show();
            return;
        }

        // Process only In-App message
        setLoading(true);

        Map<String, Object> announcement = new HashMap<>();
        announcement.put("title", title);
        announcement.put("body", body);
        announcement.put("isActive", true);
        announcement.put("createdAt", FieldValue.serverTimestamp());
        announcement.put("createdByUid", user.getUid());
        announcement.put("messageType", "in_app");

        db.collection("announcements").add(announcement)
                .addOnSuccessListener(documentReference -> {
                    setLoading(false);
                    Toast.makeText(this, "تم نشر الرسالة داخل التطبيق", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "فشل النشر: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!isLoading);
        etTitle.setEnabled(!isLoading);
        etBody.setEnabled(!isLoading);
        rgMessageType.setEnabled(!isLoading);
    }
}

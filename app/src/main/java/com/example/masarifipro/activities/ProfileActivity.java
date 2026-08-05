package com.example.masarifipro.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.masarifipro.R;
import com.example.masarifipro.models.SyncOperation;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.SyncManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private static final int RC_SWITCH_GOOGLE = 9002;
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_GUEST_MODE = "guest_mode";

    private TextView tvUserName, tvUserEmail, tvPasswordStatus;
    private MaterialButton btnLinkPassword, btnLogout;
    private View btnEditName, btnChangePassword, btnSwitchGoogle;
    private FirebaseAuth mAuth;
    private FirebaseFirestore dbFirestore;
    private FirebaseUser currentUser;
    private String uid;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        dbFirestore = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null && !isGuestMode()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (currentUser != null) {
            uid = currentUser.getUid();
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        if (isGuestMode()) {
            setupGuestUI();
        } else {
            loadProfileInfo();
            checkPasswordProvider();
        }
    }

    private boolean isGuestMode() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_GUEST_MODE, false);
    }

    private void initViews() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lang = prefs.getString("app_language", "ar");

        View root = findViewById(R.id.accountRoot);
        if (root != null) {
            root.setLayoutDirection("en".equals(lang) ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
            root.setTextDirection("en".equals(lang) ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        }

        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvPasswordStatus = findViewById(R.id.tvPasswordStatus);
        btnLinkPassword = findViewById(R.id.btnLinkPassword);
        btnLogout = findViewById(R.id.btnLogout);
        btnEditName = findViewById(R.id.btnEditName);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnSwitchGoogle = findViewById(R.id.btnSwitchGoogle);
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.my_account);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        if (currentUser != null) {
            tvUserEmail.setText(currentUser.getEmail());
        }

        btnEditName.setOnClickListener(v -> showEditNameDialog());
        btnChangePassword.setOnClickListener(v -> changePassword());
        btnSwitchGoogle.setOnClickListener(v -> switchGoogleAccount());
        btnLogout.setOnClickListener(v -> logout());
        btnLinkPassword.setOnClickListener(v -> showLinkPasswordDialog());
    }

    private void setupGuestUI() {
        tvUserName.setText(R.string.guest_user);
        tvUserEmail.setText(R.string.guest_desc);
        tvPasswordStatus.setVisibility(View.GONE);
        btnLinkPassword.setVisibility(View.GONE);
        btnEditName.setVisibility(View.GONE);
        btnChangePassword.setVisibility(View.GONE);
        btnSwitchGoogle.setVisibility(View.GONE);
        
        btnLogout.setText(R.string.login_or_register);
        btnLogout.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_GUEST_MODE, false).apply();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void checkPasswordProvider() {
        if (currentUser == null) return;

        boolean hasGoogle = false;
        boolean hasPassword = false;

        List<? extends UserInfo> providerData = currentUser.getProviderData();
        for (UserInfo userInfo : providerData) {
            if (userInfo.getProviderId().equals(GoogleAuthProvider.PROVIDER_ID)) {
                hasGoogle = true;
            }
            if (userInfo.getProviderId().equals(EmailAuthProvider.PROVIDER_ID)) {
                hasPassword = true;
            }
        }

        if (hasPassword) {
            btnLinkPassword.setVisibility(View.GONE);
            tvPasswordStatus.setVisibility(View.VISIBLE);
        } else if (hasGoogle && !TextUtils.isEmpty(currentUser.getEmail())) {
            btnLinkPassword.setVisibility(View.VISIBLE);
            tvPasswordStatus.setVisibility(View.GONE);
        } else {
            btnLinkPassword.setVisibility(View.GONE);
            tvPasswordStatus.setVisibility(View.GONE);
        }
    }

    private void showLinkPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_password);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etPassword = new EditText(this);
        etPassword.setHint(R.string.new_password);
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPassword);

        final EditText etConfirmPassword = new EditText(this);
        etConfirmPassword.setHint(R.string.confirm_password);
        etConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etConfirmPassword);

        builder.setView(layout);

        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            if (TextUtils.isEmpty(password)) {
                Toast.makeText(this, R.string.enter_password, Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, R.string.password_length_error, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, R.string.passwords_do_not_match, Toast.LENGTH_SHORT).show();
                return;
            }

            linkPassword(password);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void linkPassword(String password) {
        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
        currentUser.linkWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, R.string.password_added_success, Toast.LENGTH_SHORT).show();
                        currentUser = mAuth.getCurrentUser(); // Refresh user
                        checkPasswordProvider();
                    } else {
                        Log.e("LINK_PASSWORD", "Link failed", task.getException());
                        if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(this, R.string.email_linked_error, Toast.LENGTH_SHORT).show();
                        } else if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                            Toast.makeText(this, R.string.relogin_google_error, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, R.string.password_add_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void switchGoogleAccount() {
        mAuth.signOut();
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SWITCH_GOOGLE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SWITCH_GOOGLE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account.getIdToken());
                }
            } catch (ApiException e) {
                Toast.makeText(this, R.string.switch_account_failed, Toast.LENGTH_SHORT).show();
                logout();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_GUEST_MODE, false).apply();
                        Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(ProfileActivity.this, R.string.auth_failed, Toast.LENGTH_SHORT).show();
                        logout();
                    }
                });
    }

    private void loadProfileInfo() {
        if (uid == null) return;
        DocumentReference docRef = dbFirestore.collection("users").document(uid).collection("profile").document("info");
        docRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String name = documentSnapshot.getString("name");
                if (!TextUtils.isEmpty(name)) {
                    tvUserName.setText(name);
                } else {
                    setDefaultName();
                }
            } else {
                setDefaultName();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, R.string.profile_load_failed, Toast.LENGTH_SHORT).show();
            setDefaultName();
        });
    }

    private void setDefaultName() {
        String email = currentUser.getEmail();
        String defaultName = "";
        if (email != null && email.contains("@")) {
            defaultName = email.split("@")[0];
        } else {
            defaultName = getString(R.string.default_user_name);
        }
        
        tvUserName.setText(defaultName);
        saveProfileToFirestore(defaultName);
    }

    private void saveProfileToFirestore(String name) {
        if (uid == null) return;
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", uid);
        profile.put("name", name);
        profile.put("email", currentUser.getEmail());
        profile.put("updatedAt", System.currentTimeMillis());

        // Use SyncManager for profile info
        SyncManager.getInstance(this).enqueueOperation(
                SyncOperation.TYPE_UPDATE,
                "profile",
                "info",
                profile
        );
        
        showSyncSnackbar();
    }

    private void showEditNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_name);

        final EditText input = new EditText(this);
        input.setHint(R.string.name);
        input.setText(tvUserName.getText().toString());
        input.setPadding(40, 40, 40, 40);
        builder.setView(input);

        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newName)) {
                updateName(newName);
            } else {
                Toast.makeText(this, R.string.name_empty_error, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateName(String newName) {
        if (uid == null) return;
        tvUserName.setText(newName);
        saveProfileToFirestore(newName);
        Toast.makeText(this, R.string.name_updated, Toast.LENGTH_SHORT).show();
    }

    private void showSyncSnackbar() {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            String msg = LocaleHelper.getLanguage(this).equals("ar") ? 
                    "سيتم حفظ التغييرات محلياً ومزامنتها تلقائياً." : 
                    "Changes saved locally. Will sync automatically.";
            Snackbar.make(rootView, msg, Snackbar.LENGTH_LONG).show();
        }
    }

    private void changePassword() {
        String email = currentUser.getEmail();
        if (email != null) {
            mAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, R.string.password_link_sent, Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e -> Toast.makeText(this, R.string.password_link_failed, Toast.LENGTH_SHORT).show());
        }
    }

    private void logout() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(KEY_GUEST_MODE, false).apply();
        mAuth.signOut();
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}

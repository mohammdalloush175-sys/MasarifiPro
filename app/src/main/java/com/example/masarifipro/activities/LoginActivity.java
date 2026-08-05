package com.example.masarifipro.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.masarifipro.R;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.UserSessionManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_SIGN_IN = 9001;
    private static final String TAG = "LoginActivity";
    private static final String ACCOUNT_PREFS = "account_prefs";
    private static final String SAVED_ACCOUNTS = "saved_accounts";
    private static final String APP_PREFS = "app_prefs";
    private static final String KEY_GUEST_MODE = "guest_mode";
    private static final String KEY_GUEST_WARNING_SHOWN = "guest_warning_shown";

    private TextInputLayout tilName;
    private TextInputEditText etName, etEmail, etPassword;
    private TextView tvForgotPassword;
    private Button btnLogin, btnRegister, btnGuestMode;
    private MaterialButton btnGoogleSignIn;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore dbFirestore;
    private GoogleSignInClient mGoogleSignInClient;
    private boolean isRegisterMode = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getIntent().hasExtra("selected_email") || 
            getIntent().getBooleanExtra("add_account_mode", false) ||
            getIntent().getBooleanExtra("trigger_google_sign_in", false)) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
        boolean guestMode = prefs.getBoolean(KEY_GUEST_MODE, false);

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null || guestMode) {
            if (currentUser != null) {
                UserSessionManager.handleUserChanged(this, currentUser.getUid());
            }
            goToMainActivity();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        dbFirestore = FirebaseFirestore.getInstance();

        String webClientId = getString(R.string.default_web_client_id);
        Log.d(TAG, "Web Client ID: " + webClientId);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        tilName = findViewById(R.id.tilName);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnGuestMode = findViewById(R.id.btnGuestMode);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> {
            if (isRegisterMode) {
                isRegisterMode = false;
                updateUIState();
            } else {
                loginUser();
            }
        });

        btnRegister.setOnClickListener(v -> {
            if (!isRegisterMode) {
                isRegisterMode = true;
                updateUIState();
            } else {
                registerUser();
            }
        });

        tvForgotPassword.setOnClickListener(v -> forgotPassword());
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        btnGuestMode.setOnClickListener(v -> enterGuestMode());

        handleIntentExtras();
    }

    private void forgotPassword() {
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "أدخل البريد الإلكتروني أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "أدخل بريد إلكتروني صحيح", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        new AlertDialog.Builder(this)
                                .setTitle("تم إرسال الرابط")
                                .setMessage("تم إرسال رابط إعادة تعيين كلمة المرور إلى بريدك الإلكتروني.\n" +
                                        "إذا لم تجده، تحقق من مجلد Spam أو البريد غير الهام.\n" +
                                        "استخدم آخر رسالة وصلت فقط، ولا تضغط الرابط أكثر من مرة لأن الرابط قد تنتهي صلاحيته أو يُستخدم مرة واحدة.")
                                .setPositiveButton("حسناً", null)
                                .show();
                        startForgotPasswordCooldown();
                    } else {
                        handleResetError(task.getException());
                    }
                });
    }

    private void startForgotPasswordCooldown() {
        tvForgotPassword.setEnabled(false);
        new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvForgotPassword.setText("يمكنك إعادة الإرسال بعد " + (millisUntilFinished / 1000) + " ثانية");
            }

            @Override
            public void onFinish() {
                tvForgotPassword.setEnabled(true);
                tvForgotPassword.setText("نسيت كلمة المرور؟");
            }
        }.start();
    }

    private void handleResetError(Exception exception) {
        String message = "فشل إرسال رابط إعادة التعيين، حاول مرة أخرى.";
        if (exception instanceof FirebaseAuthException) {
            String errorCode = ((FirebaseAuthException) exception).getErrorCode();
            if (errorCode.equals("ERROR_USER_NOT_FOUND")) {
                message = "لا يوجد حساب مسجل بهذا البريد.";
            } else if (errorCode.equals("ERROR_TOO_MANY_REQUESTS")) {
                message = "تم إرسال طلبات كثيرة، حاول لاحقاً.";
            } else if (errorCode.equals("ERROR_NETWORK_REQUEST_FAILED")) {
                message = "تحقق من اتصال الإنترنت.";
            }
        } else if (exception instanceof FirebaseNetworkException) {
            message = "تحقق من اتصال الإنترنت.";
        } else if (exception != null) {
            String errorMsg = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
            if (errorMsg.contains("no user record") || errorMsg.contains("user not found") || errorMsg.contains("user-not-found")) {
                message = "لا يوجد حساب مسجل بهذا البريد.";
            } else if (errorMsg.contains("network") || errorMsg.contains("connection")) {
                message = "تحقق من اتصال الإنترنت.";
            } else if (errorMsg.contains("too many requests")) {
                message = "تم إرسال طلبات كثيرة، حاول لاحقاً.";
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateUIState() {
        if (isRegisterMode) {
            tilName.setVisibility(View.VISIBLE);
            tvForgotPassword.setVisibility(View.GONE);
            btnLogin.setText("لدي حساب بالفعل");
            btnRegister.setText("إنشاء الحساب");
        } else {
            tilName.setVisibility(View.GONE);
            tvForgotPassword.setVisibility(View.VISIBLE);
            btnLogin.setText("تسجيل الدخول");
            btnRegister.setText("إنشاء حساب جديد");
        }
    }

    private void enterGuestMode() {
        mAuth.signOut();
        setGuestMode(true);
        goToMainActivity();
    }

    private void setGuestMode(boolean isGuest) {
        SharedPreferences prefs = getSharedPreferences(APP_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_GUEST_MODE, isGuest);
        if (!isGuest) {
            editor.putBoolean(KEY_GUEST_WARNING_SHOWN, false);
            editor.putBoolean("anonymous_guest", false);
            editor.putBoolean("local_guest_only", false);
            Log.d("TX_SYNC", "login success guest_mode reset false");
        }
        editor.apply();
    }

    private void handleIntentExtras() {
        if (getIntent().getBooleanExtra("trigger_google_sign_in", false)) {
            signInWithGoogle();
        } else if (getIntent().hasExtra("selected_email")) {
            String selectedEmail = getIntent().getStringExtra("selected_email");
            etEmail.setText(selectedEmail);
            etPassword.setText("");
            etPassword.requestFocus();
            Toast.makeText(this, "أدخل كلمة المرور للمتابعة", Toast.LENGTH_SHORT).show();
        } else if (getIntent().getBooleanExtra("add_account_mode", false)) {
            etEmail.setText("");
            etPassword.setText("");
        }
    }

    private void signInWithGoogle() {
        showLoading(true);
        mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    firebaseAuthWithGoogle(account.getIdToken());
                } else {
                    showLoading(false);
                    Log.e(TAG, "GoogleSignInAccount is null");
                    Toast.makeText(this, "فشل الحصول على بيانات حساب Google", Toast.LENGTH_SHORT).show();
                }
            } catch (ApiException e) {
                showLoading(false);
                int statusCode = e.getStatusCode();
                String errorMsg = CommonStatusCodes.getStatusCodeString(statusCode);
                Log.e(TAG, "Google sign in failed. Code: " + statusCode + ", Message: " + errorMsg, e);
                
                String userMessage = "فشل تسجيل الدخول باستخدام Google";
                if (statusCode == 10) {
                    userMessage += " (DEVELOPER_ERROR: Check SHA-1/Client ID)";
                } else if (statusCode == 12501) {
                    userMessage = "تم إلغاء تسجيل الدخول";
                } else if (statusCode == 12500) {
                    userMessage += " (SIGN_IN_FAILED)";
                } else {
                    userMessage += " (Error: " + statusCode + ")";
                }
                
                Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        setGuestMode(false);
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            UserSessionManager.handleUserChanged(this, user.getUid());
                            checkAndCreateProfile(user);
                            saveAccount(user);
                        }
                        goToMainActivity();
                    } else {
                        Exception e = task.getException();
                        Log.e(TAG, "Firebase auth with Google failed", e);
                        String message = "فشل المصادقة مع Firebase";
                        if (e != null) message += ": " + e.getLocalizedMessage();
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveAccount(FirebaseUser user) {
        if (user == null || user.getEmail() == null) return;
        String email = user.getEmail();
        String provider = "unknown";

        for (UserInfo profile : user.getProviderData()) {
            String providerId = profile.getProviderId();
            if (providerId.equals("google.com")) {
                provider = "google";
                break;
            } else if (providerId.equals("password")) {
                provider = "password";
            }
        }

        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS, MODE_PRIVATE);
        String saved = prefs.getString(SAVED_ACCOUNTS, "");
        List<String> accounts = new ArrayList<>();
        if (!saved.isEmpty()) {
            accounts.addAll(Arrays.asList(saved.split(",")));
        }

        // Remove old entry for this email if it exists (regardless of provider)
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).startsWith(email + "|") || accounts.get(i).equals(email)) {
                accounts.remove(i);
                break;
            }
        }

        accounts.add(email + "|" + provider);
        prefs.edit().putString(SAVED_ACCOUNTS, String.join(",", accounts)).apply();
        
        // Also save to user_accounts for backward compatibility or other uses if needed
        getSharedPreferences("user_accounts", MODE_PRIVATE).edit()
                .putString("last_logged_in_email", email).apply();
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("الرجاء إدخال البريد الإلكتروني");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("الرجاء إدخال كلمة المرور");
            return;
        }

        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        setGuestMode(false);
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            UserSessionManager.handleUserChanged(this, user.getUid());
                            checkAndCreateProfile(user);
                            saveAccount(user);
                        }
                        goToMainActivity();
                    } else {
                        handleLoginError(task.getException());
                    }
                });
    }

    private void handleLoginError(Exception exception) {
        Log.e("LOGIN_ERROR", "Login failed", exception);
        String message = "فشل تسجيل الدخول، حاول مرة أخرى.";
        if (exception != null) {
            String errorMsg = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";

            if (errorMsg.contains("credential is incorrect") ||
                    errorMsg.contains("malformed") ||
                    errorMsg.contains("password is invalid") ||
                    errorMsg.contains("wrong-password") ||
                    errorMsg.contains("invalid-credential")) {
                message = "البريد أو كلمة المرور غير صحيحة. إذا كان الحساب مسجلاً عبر Google فقط، ادخل عبر Google ثم أضف كلمة مرور من صفحة حسابي.";
            } else if (errorMsg.contains("no user record") || errorMsg.contains("user not found") || errorMsg.contains("user-not-found")) {
                message = "لا يوجد حساب بهذا البريد. يمكنك إنشاء حساب جديد.";
            } else if (errorMsg.contains("network") || errorMsg.contains("connection")) {
                message = "تحقق من اتصال الإنترنت.";
            }
        }
        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("الرجاء إدخال الاسم");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("الرجاء إدخال البريد الإلكتروني");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("الرجاء إدخال كلمة المرور");
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("كلمة المرور يجب أن تكون 6 أحرف على الأقل");
            return;
        }

        showLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        setGuestMode(false);
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            UserSessionManager.handleUserChanged(this, user.getUid());
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                            user.updateProfile(profileUpdates);
                            saveProfileToFirestore(user.getUid(), name, email);
                            saveAccount(user);
                        }
                        Toast.makeText(LoginActivity.this, "تم إنشاء الحساب بنجاح", Toast.LENGTH_SHORT).show();
                        showLoading(false);
                        goToMainActivity();
                    } else {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this, "فشل إنشاء الحساب: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveProfileToFirestore(String uid, String name, String email) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", uid);
        profile.put("name", name);
        profile.put("email", email);
        profile.put("updatedAt", System.currentTimeMillis());

        dbFirestore.collection("users").document(uid).collection("profile").document("info")
                .set(profile);
    }

    private void checkAndCreateProfile(FirebaseUser user) {
        if (user == null) return;
        String uid = user.getUid();
        dbFirestore.collection("users").document(uid).collection("profile").document("info")
                .get().addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        String name = user.getDisplayName();
                        if (TextUtils.isEmpty(name)) {
                            String email = user.getEmail();
                            if (email != null && email.contains("@")) {
                                name = email.split("@")[0];
                            } else {
                                name = "مستخدم";
                            }
                        }
                        saveProfileToFirestore(uid, name, user.getEmail());
                    }
                });
    }

    private void goToMainActivity() {
        SharedPreferences prefs = getSharedPreferences("pending_invite_prefs", MODE_PRIVATE);
        String pendingCode = prefs.getString("pending_trip_code", null);

        if (pendingCode != null) {
            prefs.edit().remove("pending_trip_code").apply();
            Intent intent = new Intent(LoginActivity.this, SharedTripsActivity.class);
            intent.putExtra("invite_code", pendingCode);
            startActivity(intent);
        } else {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
        }
        finish();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        btnRegister.setEnabled(!isLoading);
        btnGoogleSignIn.setEnabled(!isLoading);
        btnGuestMode.setEnabled(!isLoading);
        if (tvForgotPassword != null) tvForgotPassword.setEnabled(!isLoading);
    }
}

package com.example.masarifipro.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.masarifipro.R;
import com.example.masarifipro.utils.AppLockManager;
import com.example.masarifipro.utils.LocaleHelper;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.Executor;

public class UnlockActivity extends AppCompatActivity {

    private TextInputEditText etUnlockPin;
    private View btnBiometric;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock);

        etUnlockPin = findViewById(R.id.etUnlockPin);
        btnBiometric = findViewById(R.id.btnBiometric);

        findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            String pin = etUnlockPin.getText().toString();
            if (AppLockManager.verifyPin(this, pin)) {
                unlockSuccess();
            } else {
                Toast.makeText(this, R.string.incorrect_pin, Toast.LENGTH_SHORT).show();
            }
        });

        if (AppLockManager.isBiometricEnabled(this)) {
            btnBiometric.setVisibility(View.VISIBLE);
            btnBiometric.setOnClickListener(v -> showBiometricPrompt());
            showBiometricPrompt(); // Auto show if enabled
        }
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                unlockSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_unlock))
                .setNegativeButtonText(getString(R.string.cancel))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void unlockSuccess() {
        AppLockManager.setSessionUnlocked(this, true);
        Intent intent = new Intent(UnlockActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Exit app instead of going back
        finishAffinity();
    }
}

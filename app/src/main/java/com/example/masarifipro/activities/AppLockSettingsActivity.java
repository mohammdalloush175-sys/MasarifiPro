package com.example.masarifipro.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;

import com.example.masarifipro.R;
import com.example.masarifipro.utils.AppLockManager;
import com.example.masarifipro.utils.LocaleHelper;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class AppLockSettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchEnableLock;
    private SwitchMaterial switchBiometric;
    private View btnSetPin;
    private View btnDisableLock;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_lock_settings);

        switchEnableLock = findViewById(R.id.switchEnableLock);
        switchBiometric = findViewById(R.id.switchBiometric);
        btnSetPin = findViewById(R.id.btnSetPin);
        btnDisableLock = findViewById(R.id.btnDisableLock);

        updateUI();

        switchEnableLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!AppLockManager.hasPin(this)) {
                    Toast.makeText(this, R.string.set_pin, Toast.LENGTH_SHORT).show();
                    switchEnableLock.setChecked(false);
                    startActivity(new Intent(this, SetPinActivity.class));
                } else {
                    AppLockManager.setAppLockEnabled(this, true);
                }
            } else {
                AppLockManager.setAppLockEnabled(this, false);
            }
            updateUI();
        });

        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (isBiometricAvailable()) {
                    AppLockManager.setBiometricEnabled(this, true);
                } else {
                    Toast.makeText(this, R.string.biometric_not_available, Toast.LENGTH_SHORT).show();
                    switchBiometric.setChecked(false);
                }
            } else {
                AppLockManager.setBiometricEnabled(this, false);
            }
        });

        btnSetPin.setOnClickListener(v -> startActivity(new Intent(this, SetPinActivity.class)));

        btnDisableLock.setOnClickListener(v -> {
            AppLockManager.disableLock(this);
            updateUI();
            Toast.makeText(this, R.string.disable_app_lock, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateUI() {
        boolean isEnabled = AppLockManager.isAppLockEnabled(this);
        boolean hasPin = AppLockManager.hasPin(this);

        switchEnableLock.setChecked(isEnabled);
        switchBiometric.setChecked(AppLockManager.isBiometricEnabled(this));
        switchBiometric.setEnabled(isEnabled && isBiometricAvailable());

        btnDisableLock.setVisibility(hasPin ? View.VISIBLE : View.GONE);
        
        com.google.android.material.button.MaterialButton pinBtn = (com.google.android.material.button.MaterialButton) btnSetPin;
        pinBtn.setText(hasPin ? R.string.change_pin : R.string.set_pin);
    }

    private boolean isBiometricAvailable() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG 
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
}

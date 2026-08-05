package com.example.masarifipro.activities;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.masarifipro.R;
import com.example.masarifipro.utils.AppLockManager;
import com.example.masarifipro.utils.LocaleHelper;
import com.google.android.material.textfield.TextInputEditText;

public class SetPinActivity extends AppCompatActivity {

    private TextInputEditText etPin;
    private TextInputEditText etConfirmPin;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_pin);

        etPin = findViewById(R.id.etPin);
        etConfirmPin = findViewById(R.id.etConfirmPin);

        findViewById(R.id.btnSavePin).setOnClickListener(v -> {
            String pin = etPin.getText().toString();
            String confirmPin = etConfirmPin.getText().toString();

            if (pin.length() < 4) {
                Toast.makeText(this, R.string.enter_pin, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!pin.equals(confirmPin)) {
                Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }

            AppLockManager.savePin(this, pin);
            AppLockManager.setAppLockEnabled(this, true);
            AppLockManager.markUnlocked(this);
            
            Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}

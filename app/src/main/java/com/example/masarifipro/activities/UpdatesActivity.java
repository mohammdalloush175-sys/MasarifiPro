package com.example.masarifipro.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.masarifipro.BuildConfig;
import com.example.masarifipro.R;
import com.example.masarifipro.updates.AppUpdateManager;
import com.example.masarifipro.updates.GitHubReleaseInfo;
import com.example.masarifipro.utils.AppLockManager;
import com.example.masarifipro.utils.LocaleHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdatesActivity extends AppCompatActivity {

    public static final String EXTRA_CHECK_NOW = "check_now";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvCurrentVersion;
    private TextView tvLatestVersion;
    private TextView tvPublishedAt;
    private TextView tvReleaseNotes;
    private TextView tvLastChecked;
    private MaterialButton btnCheckNow;
    private MaterialButton btnDownload;
    private MaterialButton btnReleasePage;
    private SwitchMaterial switchBackgroundUpdates;

    @Nullable
    private GitHubReleaseInfo latestRelease;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        bindViews();
        configureUi();
        checkForUpdates();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_CHECK_NOW, false)) {
            checkForUpdates();
        }
    }

    private void bindViews() {
        progressBar = findViewById(R.id.progressUpdateCheck);
        tvStatus = findViewById(R.id.tvUpdateStatus);
        tvCurrentVersion = findViewById(R.id.tvCurrentVersion);
        tvLatestVersion = findViewById(R.id.tvLatestVersion);
        tvPublishedAt = findViewById(R.id.tvPublishedAt);
        tvReleaseNotes = findViewById(R.id.tvReleaseNotes);
        tvLastChecked = findViewById(R.id.tvLastChecked);
        btnCheckNow = findViewById(R.id.btnCheckNow);
        btnDownload = findViewById(R.id.btnDownloadUpdate);
        btnReleasePage = findViewById(R.id.btnOpenReleasePage);
        switchBackgroundUpdates = findViewById(R.id.switchBackgroundUpdates);
    }

    private void configureUi() {
        tvCurrentVersion.setText(getString(R.string.update_current_version_value, BuildConfig.VERSION_NAME));
        switchBackgroundUpdates.setChecked(AppUpdateManager.isBackgroundCheckEnabled(this));
        updateLastCheckedText();

        findViewById(R.id.btnBackUpdates).setOnClickListener(v -> finish());
        findViewById(R.id.btnOpenRepository).setOnClickListener(v -> openUrl(AppUpdateManager.REPOSITORY_URL));
        btnCheckNow.setOnClickListener(v -> checkForUpdates());
        btnReleasePage.setOnClickListener(v -> openUrl(
                latestRelease != null ? latestRelease.getReleasePageUrl() : AppUpdateManager.LATEST_RELEASE_URL
        ));
        btnDownload.setOnClickListener(v -> {
            if (latestRelease == null) {
                openUrl(AppUpdateManager.LATEST_RELEASE_URL);
                return;
            }

            String downloadUrl = latestRelease.getApkDownloadUrl();
            if (TextUtils.isEmpty(downloadUrl)) {
                Toast.makeText(this, R.string.update_apk_not_attached, Toast.LENGTH_LONG).show();
                openUrl(latestRelease.getReleasePageUrl());
                return;
            }
            AppLockManager.ignoreLockTemporarily(this);
            openUrl(downloadUrl);
        });

        switchBackgroundUpdates.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppUpdateManager.setBackgroundCheckEnabled(this, isChecked);
            Toast.makeText(
                    this,
                    isChecked ? R.string.update_background_enabled : R.string.update_background_disabled,
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void checkForUpdates() {
        setLoading(true);
        tvStatus.setText(R.string.update_checking);

        executor.execute(() -> {
            try {
                GitHubReleaseInfo releaseInfo = AppUpdateManager.fetchLatestRelease();
                AppUpdateManager.recordSuccessfulCheck(this, releaseInfo);
                runOnUiThread(() -> showRelease(releaseInfo));
            } catch (AppUpdateManager.NoReleasePublishedException exception) {
                AppUpdateManager.recordCheckAttempt(this);
                runOnUiThread(this::showNoReleasePublished);
            } catch (IOException exception) {
                runOnUiThread(() -> showCheckError(exception));
            }
        });
    }

    private void showRelease(@Nullable GitHubReleaseInfo releaseInfo) {
        setLoading(false);
        latestRelease = releaseInfo;
        updateLastCheckedText();

        if (releaseInfo == null) {
            showNoReleasePublished();
            return;
        }

        tvLatestVersion.setText(getString(
                R.string.update_latest_version_value,
                releaseInfo.getVersionName()
        ));
        tvPublishedAt.setText(formatPublishedDate(releaseInfo.getPublishedAt()));
        tvReleaseNotes.setText(TextUtils.isEmpty(releaseInfo.getBody())
                ? getString(R.string.update_no_release_notes)
                : releaseInfo.getBody());

        boolean updateAvailable = AppUpdateManager.isNewerThanInstalled(releaseInfo);
        if (updateAvailable) {
            tvStatus.setText(getString(R.string.update_available_status, releaseInfo.getVersionName()));
            btnDownload.setVisibility(View.VISIBLE);
            btnDownload.setEnabled(!TextUtils.isEmpty(releaseInfo.getApkDownloadUrl()));
            btnDownload.setText(TextUtils.isEmpty(releaseInfo.getApkDownloadUrl())
                    ? R.string.update_apk_not_attached_short
                    : R.string.update_download_latest);
        } else {
            tvStatus.setText(R.string.update_up_to_date);
            btnDownload.setVisibility(View.GONE);
        }

        btnReleasePage.setVisibility(View.VISIBLE);
    }

    private void showNoReleasePublished() {
        setLoading(false);
        latestRelease = null;
        tvStatus.setText(R.string.update_no_release_published);
        tvLatestVersion.setText(R.string.update_not_available_value);
        tvPublishedAt.setText("");
        tvReleaseNotes.setText(R.string.update_no_release_published_details);
        btnDownload.setVisibility(View.GONE);
        btnReleasePage.setVisibility(View.VISIBLE);
        updateLastCheckedText();
    }

    private void showCheckError(IOException exception) {
        setLoading(false);
        tvStatus.setText(R.string.update_check_failed);
        tvReleaseNotes.setText(R.string.update_check_failed_details);
        btnDownload.setVisibility(View.GONE);
        btnReleasePage.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.update_check_failed, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnCheckNow.setEnabled(!loading);
    }

    private void updateLastCheckedText() {
        long lastCheck = AppUpdateManager.getLastCheckTime(this);
        if (lastCheck <= 0L) {
            tvLastChecked.setText(R.string.update_never_checked);
            return;
        }

        String formatted = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
        ).format(new Date(lastCheck));
        tvLastChecked.setText(getString(R.string.update_last_checked_value, formatted));
    }

    private String formatPublishedDate(String publishedAt) {
        if (TextUtils.isEmpty(publishedAt)) {
            return "";
        }

        try {
            SimpleDateFormat source = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            source.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = source.parse(publishedAt);
            if (date == null) {
                return "";
            }
            return getString(
                    R.string.update_published_at_value,
                    DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM,
                            DateFormat.SHORT,
                            Locale.getDefault()
                    ).format(date)
            );
        } catch (Exception ignored) {
            return getString(R.string.update_published_at_value, publishedAt);
        }
    }

    private void openUrl(String url) {
        try {
            AppLockManager.ignoreLockTemporarily(this);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception exception) {
            Toast.makeText(this, R.string.update_cannot_open_link, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}

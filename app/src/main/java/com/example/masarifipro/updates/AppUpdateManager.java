package com.example.masarifipro.updates;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.masarifipro.BuildConfig;
import com.example.masarifipro.R;
import com.example.masarifipro.activities.UpdatesActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class AppUpdateManager {

    public static final String GITHUB_OWNER = "mohammdalloush175-sys";
    public static final String GITHUB_REPOSITORY = "MasarifiPro";
    public static final String REPOSITORY_URL =
            "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPOSITORY;
    public static final String RELEASES_URL = REPOSITORY_URL + "/releases";
    public static final String LATEST_RELEASE_URL = RELEASES_URL + "/latest";
    public static final String API_LATEST_RELEASE_URL =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPOSITORY + "/releases/latest";

    private static final String PREFS_NAME = "github_update_preferences";
    private static final String KEY_BACKGROUND_CHECK_ENABLED = "background_check_enabled";
    private static final String KEY_LAST_NOTIFIED_TAG = "last_notified_tag";
    private static final String KEY_LAST_CHECK_TIME = "last_check_time";
    private static final String KEY_LAST_AVAILABLE_VERSION = "last_available_version";

    private static final String UNIQUE_PERIODIC_WORK = "masarifipro_github_update_periodic";
    private static final String UNIQUE_IMMEDIATE_WORK = "masarifipro_github_update_immediate";
    private static final String NOTIFICATION_CHANNEL_ID = "app_updates";
    private static final int UPDATE_NOTIFICATION_ID = 12001;

    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private AppUpdateManager() {
    }

    public static boolean isBackgroundCheckEnabled(@NonNull Context context) {
        return preferences(context).getBoolean(KEY_BACKGROUND_CHECK_ENABLED, true);
    }

    public static void setBackgroundCheckEnabled(@NonNull Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_BACKGROUND_CHECK_ENABLED, enabled).apply();
        if (enabled) {
            schedulePeriodicChecks(context);
            enqueueImmediateCheck(context);
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK);
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE_WORK);
        }
    }

    public static void schedulePeriodicChecks(@NonNull Context context) {
        if (!isBackgroundCheckEnabled(context)) {
            return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                UpdateCheckWorker.class,
                6,
                TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    public static void enqueueImmediateCheck(@NonNull Context context) {
        if (!isBackgroundCheckEnabled(context)) {
            return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UpdateCheckWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                request
        );
    }

    @NonNull
    public static GitHubReleaseInfo fetchLatestRelease() throws IOException, NoReleasePublishedException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_LATEST_RELEASE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "MasarifiPro-Android/" + BuildConfig.VERSION_NAME);
            connection.setUseCaches(false);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new NoReleasePublishedException();
            }

            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readFully(stream);

            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("GitHub API returned HTTP " + responseCode + ": " + responseBody);
            }

            return parseRelease(responseBody);
        } catch (JSONException exception) {
            throw new IOException("Invalid GitHub release response", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static GitHubReleaseInfo parseRelease(@NonNull String json) throws JSONException {
        JSONObject release = new JSONObject(json);
        String tagName = release.optString("tag_name", "").trim();
        String versionName = normalizeVersion(tagName);
        String title = release.optString("name", tagName).trim();
        String body = release.optString("body", "").trim();
        String htmlUrl = release.optString("html_url", LATEST_RELEASE_URL).trim();
        String publishedAt = release.optString("published_at", "").trim();

        String apkDownloadUrl = "";
        String apkFileName = "";
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "").trim();
                if (!name.toLowerCase(Locale.US).endsWith(".apk")) {
                    continue;
                }

                String downloadUrl = asset.optString("browser_download_url", "").trim();
                if (TextUtils.isEmpty(downloadUrl)) {
                    continue;
                }

                if (TextUtils.isEmpty(apkDownloadUrl) || "masarifipro.apk".equalsIgnoreCase(name)) {
                    apkDownloadUrl = downloadUrl;
                    apkFileName = name;
                }

                if ("masarifipro.apk".equalsIgnoreCase(name)) {
                    break;
                }
            }
        }

        return new GitHubReleaseInfo(
                tagName,
                versionName,
                TextUtils.isEmpty(title) ? tagName : title,
                body,
                TextUtils.isEmpty(htmlUrl) ? LATEST_RELEASE_URL : htmlUrl,
                apkDownloadUrl,
                apkFileName,
                publishedAt
        );
    }

    public static boolean isNewerThanInstalled(@NonNull GitHubReleaseInfo releaseInfo) {
        return compareVersions(releaseInfo.getVersionName(), BuildConfig.VERSION_NAME) > 0;
    }

    public static int compareVersions(String first, String second) {
        int[] firstParts = parseVersionParts(first);
        int[] secondParts = parseVersionParts(second);
        int length = Math.max(firstParts.length, secondParts.length);

        for (int i = 0; i < length; i++) {
            int firstValue = i < firstParts.length ? firstParts[i] : 0;
            int secondValue = i < secondParts.length ? secondParts[i] : 0;
            if (firstValue != secondValue) {
                return Integer.compare(firstValue, secondValue);
            }
        }
        return 0;
    }

    private static int[] parseVersionParts(String rawVersion) {
        String normalized = normalizeVersion(rawVersion);
        if (TextUtils.isEmpty(normalized)) {
            return new int[]{0};
        }

        String[] tokens = normalized.split("\\.");
        int[] result = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String numeric = tokens[i].replaceAll("[^0-9].*$", "");
            try {
                result[i] = TextUtils.isEmpty(numeric) ? 0 : Integer.parseInt(numeric);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    @NonNull
    public static String normalizeVersion(String rawVersion) {
        if (rawVersion == null) {
            return "";
        }
        String normalized = rawVersion.trim();
        while (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    public static void recordSuccessfulCheck(@NonNull Context context, @NonNull GitHubReleaseInfo info) {
        preferences(context).edit()
                .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                .putString(KEY_LAST_AVAILABLE_VERSION, info.getVersionName())
                .apply();
    }

    public static void recordCheckAttempt(@NonNull Context context) {
        preferences(context).edit()
                .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                .apply();
    }

    public static boolean shouldRunAutomaticCheck(@NonNull Context context) {
        long lastCheck = getLastCheckTime(context);
        return lastCheck <= 0L || System.currentTimeMillis() - lastCheck >= TimeUnit.HOURS.toMillis(3);
    }

    public static long getLastCheckTime(@NonNull Context context) {
        return preferences(context).getLong(KEY_LAST_CHECK_TIME, 0L);
    }

    public static String getLastAvailableVersion(@NonNull Context context) {
        return preferences(context).getString(KEY_LAST_AVAILABLE_VERSION, "");
    }

    public static boolean shouldNotifyFor(@NonNull Context context, @NonNull GitHubReleaseInfo info) {
        String lastTag = preferences(context).getString(KEY_LAST_NOTIFIED_TAG, "");
        return !info.getTagName().equals(lastTag);
    }

    public static void markNotified(@NonNull Context context, @NonNull GitHubReleaseInfo info) {
        preferences(context).edit().putString(KEY_LAST_NOTIFIED_TAG, info.getTagName()).apply();
    }

    public static boolean showUpdateNotification(@NonNull Context context, @NonNull GitHubReleaseInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        Intent intent = new Intent(context, UpdatesActivity.class);
        intent.putExtra(UpdatesActivity.EXTRA_CHECK_NOW, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                UPDATE_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.update_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(context.getString(R.string.update_notification_channel_description));
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_available_notification_title))
                .setContentText(context.getString(
                        R.string.update_available_notification_body,
                        info.getVersionName()
                ))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(
                        R.string.update_available_notification_body,
                        info.getVersionName()
                )))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(UPDATE_NOTIFICATION_ID, builder.build());
        return true;
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String readFully(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    public static final class NoReleasePublishedException extends Exception {
        public NoReleasePublishedException() {
            super("No published GitHub release was found");
        }
    }
}

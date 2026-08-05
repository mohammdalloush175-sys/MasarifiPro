package com.example.masarifipro.updates;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

public class UpdateCheckWorker extends Worker {

    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!AppUpdateManager.isBackgroundCheckEnabled(context)) {
            return Result.success();
        }

        if (!AppUpdateManager.shouldRunAutomaticCheck(context)) {
            return Result.success();
        }

        try {
            GitHubReleaseInfo releaseInfo = AppUpdateManager.fetchLatestRelease();
            AppUpdateManager.recordSuccessfulCheck(context, releaseInfo);

            if (AppUpdateManager.isNewerThanInstalled(releaseInfo)
                    && AppUpdateManager.shouldNotifyFor(context, releaseInfo)
                    && AppUpdateManager.showUpdateNotification(context, releaseInfo)) {
                AppUpdateManager.markNotified(context, releaseInfo);
            }
            return Result.success();
        } catch (AppUpdateManager.NoReleasePublishedException ignored) {
            AppUpdateManager.recordCheckAttempt(context);
            return Result.success();
        } catch (IOException exception) {
            return Result.retry();
        } catch (RuntimeException exception) {
            return Result.failure();
        }
    }
}

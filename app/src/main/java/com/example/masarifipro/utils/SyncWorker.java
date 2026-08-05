package com.example.masarifipro.utils;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean success = SyncManager.getInstance(getApplicationContext()).syncBlocking();
        return success ? Result.success() : Result.retry();
    }
}

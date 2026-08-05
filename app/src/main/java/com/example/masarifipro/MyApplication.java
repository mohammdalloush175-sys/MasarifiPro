package com.example.masarifipro;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.masarifipro.activities.UnlockActivity;
import com.example.masarifipro.updates.AppUpdateManager;
import com.example.masarifipro.utils.AppLockManager;

public class MyApplication extends Application {

    private int startedActivityCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        AppUpdateManager.schedulePeriodicChecks(this);
        AppUpdateManager.enqueueImmediateCheck(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                startedActivityCount++;
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                startedActivityCount--;
                if (startedActivityCount == 0) {
                    if (activity.isChangingConfigurations()) {
                        Log.d("APP_LOCK", "skip lock: changing configurations");
                        return;
                    }

                    if (AppLockManager.shouldIgnoreLockTemporarily(activity)) {
                        Log.d("APP_LOCK", "skip lock: internal recreate/theme/language change");
                        return;
                    }

                    if (!(activity instanceof UnlockActivity)) {
                        AppLockManager.markLocked(activity);
                        Log.d("APP_LOCK", "app moved to background -> lock session");
                    }
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }
}

package com.example.masarifipro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_LANGUAGE = "app_language";

    public static Context setLocale(Context context) {
        return updateResources(context, getLanguage(context));
    }

    public static Context setNewLocale(Context context, String language) {
        persist(context, language);
        return updateResources(context, language);
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, "ar");
    }

    private static void persist(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            config.setLayoutDirection(locale);
        } else {
            config.locale = locale;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.createConfigurationContext(config);
        } else {
            res.updateConfiguration(config, res.getDisplayMetrics());
            return context;
        }
    }
}

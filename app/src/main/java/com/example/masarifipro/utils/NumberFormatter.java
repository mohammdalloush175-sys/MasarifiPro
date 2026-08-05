package com.example.masarifipro.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.DecimalFormat;

public class NumberFormatter {

    /**
     * Standard format for amounts.
     * For SYP, returns full format: e.g., 865,000 ل.س
     */
    public static String format(double amount, String currencyCode) {
        String formatted = formatDecimal(amount);
        if ("SYP".equalsIgnoreCase(currencyCode)) {
            return formatted + " ل.س";
        }
        return formatted + " " + (currencyCode != null ? currencyCode : "");
    }

    /**
     * Format amount based on user preferences.
     * SYP can be short (865 ألف ل.س) or normal (865,000 ل.س) based on 'syp_short_format' setting.
     */
    public static String formatAmount(Context context, double amount, String currencyCode) {
        if (context != null && "SYP".equalsIgnoreCase(currencyCode)) {
            SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            boolean isShort = prefs.getBoolean("syp_short_format", false);
            
            if (isShort) {
                double absAmount = Math.abs(amount);
                String sign = amount < 0 ? "-" : "";

                if (absAmount >= 1000000) {
                    double millions = absAmount / 1000000.0;
                    return sign + formatDecimal(millions) + " مليون ل.س";
                } else if (absAmount >= 1000) {
                    double thousands = absAmount / 1000.0;
                    return sign + formatDecimal(thousands) + " ألف ل.س";
                } else {
                    return sign + formatDecimal(absAmount) + " ل.س";
                }
            }
        }
        // Fallback to standard format
        return format(amount, currencyCode);
    }

    /**
     * Helper for basic comma formatting (used by amount inputs and standard displays)
     */
    public static String formatDecimal(double value) {
        DecimalFormat df = new DecimalFormat("#,###.##");
        return df.format(value);
    }
}

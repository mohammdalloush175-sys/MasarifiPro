package com.example.masarifipro.utils;

import android.content.Context;
import com.example.masarifipro.R;
import com.example.masarifipro.models.Transaction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Unified source of truth for transaction export columns (CSV and PDF).
 */
public class TransactionExportMapper {

    public static List<String> getHeaders(Context context) {
        List<String> headers = new ArrayList<>();
        headers.add(context.getString(R.string.date));
        headers.add(context.getString(R.string.type));
        headers.add(context.getString(R.string.category));
        headers.add(context.getString(R.string.description));
        headers.add(context.getString(R.string.amount));
        headers.add(context.getString(R.string.currency));
        headers.add(context.getString(R.string.balance_before));
        headers.add(context.getString(R.string.balance_after));
        headers.add(context.getString(R.string.name)); 
        headers.add(context.getString(R.string.sync_status));
        headers.add(context.getString(R.string.created_at));
        headers.add(context.getString(R.string.exchange));
        headers.add(context.getString(R.string.source_currency));
        headers.add(context.getString(R.string.amount_to_exchange)); // Original Amount
        headers.add(context.getString(R.string.exchange_rate));
        return headers;
    }

    public static List<String> getRowData(Context context, Transaction t) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        List<String> data = new ArrayList<>();
        
        // Date
        data.add(sdf.format(new Date(t.getDate())));
        
        // Type
        String typeStr = t.getType().equals("income") ? context.getString(R.string.income) : context.getString(R.string.expense);
        data.add(typeStr);
        
        // Category
        data.add(t.getCategoryName() != null ? t.getCategoryName() : "");
        
        // Description
        data.add(t.getDescription() != null ? t.getDescription() : "");
        
        // Amount (Formatted with commas via NumberFormatter)
        data.add(NumberFormatter.formatDecimal(t.getAmount()));
        
        // Currency
        data.add(t.getCurrencyCode() != null ? t.getCurrencyCode() : "");

        // Running balance snapshot. Old transactions intentionally export blanks.
        if (t.isBalanceSnapshotEnabled()
                && t.getBalanceBefore() != null
                && t.getBalanceAfter() != null) {
            data.add(NumberFormatter.formatDecimal(t.getBalanceBefore()));
            data.add(NumberFormatter.formatDecimal(t.getBalanceAfter()));
        } else {
            data.add("");
            data.add("");
        }
        
        // Name/Person
        data.add(t.getUserName() != null ? t.getUserName() : "");
        
        // Sync Status
        data.add(t.getSyncStatus() != null ? t.getSyncStatus() : "");
        
        // Created At
        data.add(sdf.format(new Date(t.getCreatedAt())));
        
        // Exchange Info
        data.add(t.isExchangeTransfer() ? context.getString(R.string.yes) : context.getString(R.string.no));
        data.add(t.getSourceCurrency() != null ? t.getSourceCurrency() : "");
        
        if (t.isExchangeTransfer() && t.getExchangeRate() > 0) {
            // Original Amount = Target Amount / Rate (assuming amount is the result of exchange)
            data.add(NumberFormatter.formatDecimal(t.getAmount() / t.getExchangeRate()));
            data.add(String.valueOf(t.getExchangeRate()));
        } else {
            data.add("");
            data.add("");
        }
        
        return data;
    }
}

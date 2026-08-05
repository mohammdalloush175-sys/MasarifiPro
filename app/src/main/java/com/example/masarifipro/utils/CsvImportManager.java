package com.example.masarifipro.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.masarifipro.models.Transaction;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvImportManager {
    private static final String TAG = "CSV_IMPORT";

    public static class CsvData {
        public List<String> headers;
        public List<List<String>> rows;
    }

    public static CsvData readCsv(Context context, Uri uri) {
        CsvData csvData = new CsvData();
        csvData.headers = new ArrayList<>();
        csvData.rows = new ArrayList<>();

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                
                // Handle UTF-8 BOM if present
                if (firstLine && line.startsWith("\ufeff")) {
                    line = line.substring(1);
                }

                List<String> columns = parseCsvLine(line);
                if (firstLine) {
                    csvData.headers = columns;
                    firstLine = false;
                    Log.d(TAG, "IMPORT_CSV headers count: " + csvData.headers.size());
                } else {
                    csvData.rows.add(columns);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading CSV", e);
        }
        return csvData;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder currentColumn = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    currentColumn.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(currentColumn.toString().trim());
                currentColumn.setLength(0);
            } else {
                currentColumn.append(c);
            }
        }
        result.add(currentColumn.toString().trim());
        return result;
    }

    public static int detectColumn(List<String> headers, String[] keywords) {
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).toLowerCase().trim();
            for (String keyword : keywords) {
                if (header.equalsIgnoreCase(keyword)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static final String[] DATE_KEYWORDS = {"date", "transaction date", "تاريخ", "التاريخ"};
    public static final String[] TYPE_KEYWORDS = {"type", "transaction type", "النوع"};
    public static final String[] AMOUNT_KEYWORDS = {"amount", "total", "original amount", "total amount", "المبلغ", "القيمة"};
    public static final String[] CURRENCY_KEYWORDS = {"currency", "currencycode", "العملة"};
    public static final String[] CATEGORY_KEYWORDS = {"category", "الفئة", "التصنيف"};
    public static final String[] DESCRIPTION_KEYWORDS = {"description", "note", "notes", "الوصف", "ملاحظات", "البيان"};

    public static Transaction parseRow(List<String> row, ColumnMapping mapping) {
        try {
            Transaction transaction = new Transaction();

            // Amount (Required)
            if (mapping.amountIndex == -1 || mapping.amountIndex >= row.size()) return null;
            String amountStr = row.get(mapping.amountIndex).replace(",", "");
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) return null;
            transaction.setAmount(amount);

            // Date (Required)
            if (mapping.dateIndex == -1 || mapping.dateIndex >= row.size()) return null;
            long timestamp = parseDate(row.get(mapping.dateIndex));
            if (timestamp == -1) return null;
            transaction.setDate(timestamp);

            // Type
            String type = mapping.defaultType;
            if (mapping.typeIndex != -1 && mapping.typeIndex < row.size()) {
                String rowType = row.get(mapping.typeIndex).toLowerCase();
                if (rowType.contains("income") || rowType.contains("دخل") || rowType.contains("ايراد") || rowType.contains("إيراد")) {
                    type = "income";
                } else if (rowType.contains("expense") || rowType.contains("مصروف") || rowType.contains("صرف")) {
                    type = "expense";
                }
            }
            transaction.setType(type);

            // Currency
            String currency = mapping.defaultCurrency;
            if (mapping.currencyIndex != -1 && mapping.currencyIndex < row.size()) {
                String rowCurrency = row.get(mapping.currencyIndex).trim();
                if (!rowCurrency.isEmpty()) {
                    currency = rowCurrency;
                }
            }
            transaction.setCurrencyCode(currency);

            // Category
            if (mapping.categoryIndex != -1 && mapping.categoryIndex < row.size()) {
                transaction.setCategoryName(row.get(mapping.categoryIndex));
            } else {
                transaction.setCategoryName("General");
            }

            // Description
            if (mapping.descriptionIndex != -1 && mapping.descriptionIndex < row.size()) {
                transaction.setDescription(row.get(mapping.descriptionIndex));
            }

            transaction.setCreatedAt(System.currentTimeMillis());
            transaction.setUpdatedAt(System.currentTimeMillis());

            return transaction;
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseDate(String dateStr) {
        String[] formats = {"yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy", "dd-MM-yyyy"};
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                sdf.setLenient(false);
                Date date = sdf.parse(dateStr);
                if (date != null) return date.getTime();
            } catch (ParseException ignored) {}
        }
        return -1;
    }

    public static class ColumnMapping {
        public int dateIndex = -1;
        public int amountIndex = -1;
        public int typeIndex = -1;
        public int currencyIndex = -1;
        public int categoryIndex = -1;
        public int descriptionIndex = -1;
        public String defaultType = "expense";
        public String defaultCurrency = "SYP";
    }
}

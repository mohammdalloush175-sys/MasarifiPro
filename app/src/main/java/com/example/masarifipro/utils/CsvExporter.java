package com.example.masarifipro.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.masarifipro.R;
import com.example.masarifipro.models.Transaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CsvExporter {

    private static final String TAG = "CsvExporter";

    public interface ExportCallback {
        void onSuccess(Uri uri);
        void onFailure(Exception e);
    }

    public static void exportTransactions(Context context, List<Transaction> transactions, String fileName, ExportCallback callback) {
        if (transactions == null || transactions.isEmpty()) {
            Toast.makeText(context, R.string.no_transactions, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvData = new StringBuilder();
        // UTF-8 BOM for Excel to recognize Arabic characters
        csvData.append('\ufeff');

        // Headers from Mapper
        List<String> headers = TransactionExportMapper.getHeaders(context);
        for (int i = 0; i < headers.size(); i++) {
            csvData.append(escapeCsv(headers.get(i)));
            if (i < headers.size() - 1) csvData.append(",");
        }
        csvData.append("\n");

        // Data rows from Mapper
        for (Transaction t : transactions) {
            List<String> row = TransactionExportMapper.getRowData(context, t);
            for (int i = 0; i < row.size(); i++) {
                csvData.append(escapeCsv(row.get(i)));
                if (i < row.size() - 1) csvData.append(",");
            }
            csvData.append("\n");
        }

        saveCsv(context, csvData.toString(), fileName, callback);
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        // If the value contains comma, double quote or newline, wrap it in double quotes
        // and escape existing double quotes by doubling them.
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void saveCsv(Context context, String data, String fileName, ExportCallback callback) {
        OutputStream outputStream = null;
        Uri fileUri = null;
        try {
            String fullFileName = fileName + "_" + System.currentTimeMillis() + ".csv";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fullFileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MasarifiPro");

                fileUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                if (fileUri != null) {
                    outputStream = context.getContentResolver().openOutputStream(fileUri);
                }
            } else {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File masarifiDir = new File(downloadDir, "MasarifiPro");
                if (!masarifiDir.exists()) masarifiDir.mkdirs();
                
                File file = new File(masarifiDir, fullFileName);
                outputStream = new FileOutputStream(file);
                fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            }

            if (outputStream != null) {
                outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                if (callback != null) callback.onSuccess(fileUri);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving CSV: ", e);
            if (callback != null) callback.onFailure(e);
        } finally {
            try {
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing stream: ", e);
            }
        }
    }
}

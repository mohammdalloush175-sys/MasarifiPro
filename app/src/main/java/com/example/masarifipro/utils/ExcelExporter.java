package com.example.masarifipro.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.example.masarifipro.R;
import com.example.masarifipro.models.Transaction;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExcelExporter {

    private static final String TAG = "ExcelExporter";

    public interface ExportCallback {
        void onSuccess(Uri uri);
        void onFailure(Exception e);
    }

    public static void exportTransactions(Context context, List<Transaction> transactions, String fileName, ExportCallback callback) {
        if (transactions == null || transactions.isEmpty()) {
            if (callback != null) callback.onFailure(new Exception("No data to export"));
            return;
        }

        OutputStream outputStream = null;
        ZipOutputStream zos = null;
        try {
            SimpleDateFormat fileSdf = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault());
            String fullFileName = "Masarifi_Export_" + fileSdf.format(new Date()) + ".xlsx";
            Uri fileUri = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fullFileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MasarifiPro");

                fileUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                if (fileUri != null) {
                    outputStream = context.getContentResolver().openOutputStream(fileUri);
                }
            } else {
                java.io.File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File masarifiDir = new java.io.File(downloadDir, "MasarifiPro");
                if (!masarifiDir.exists()) masarifiDir.mkdirs();

                java.io.File file = new java.io.File(masarifiDir, fullFileName);
                outputStream = new java.io.FileOutputStream(file);
                fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            }

            if (outputStream == null) {
                if (callback != null) callback.onFailure(new IOException("Could not open output stream"));
                return;
            }

            zos = new ZipOutputStream(outputStream);

            // 1. [Content_Types].xml
            writeZipEntry(zos, "[Content_Types].xml", 
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "</Types>");

            // 2. _rels/.rels
            writeZipEntry(zos, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>");

            // 3. xl/workbook.xml
            writeZipEntry(zos, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"Transactions\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");

            // 4. xl/_rels/workbook.xml.rels
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "</Relationships>");

            // 5. xl/worksheets/sheet1.xml
            StringBuilder sheetXml = new StringBuilder();
            sheetXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                    .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                    .append("<sheetData>");

            // Headers
            List<String> headers = TransactionExportMapper.getHeaders(context);
            sheetXml.append("<row r=\"1\">");
            for (int i = 0; i < headers.size(); i++) {
                sheetXml.append(createCell(i, 1, headers.get(i)));
            }
            sheetXml.append("</row>");

            // Data rows
            for (int r = 0; r < transactions.size(); r++) {
                Transaction t = transactions.get(r);
                List<String> rowData = TransactionExportMapper.getRowData(context, t);
                sheetXml.append("<row r=\"").append(r + 2).append("\">");
                for (int c = 0; c < rowData.size(); c++) {
                    sheetXml.append(createCell(c, r + 2, rowData.get(c)));
                }
                sheetXml.append("</row>");
            }

            sheetXml.append("</sheetData></worksheet>");
            writeZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml.toString());

            zos.finish();
            if (callback != null) callback.onSuccess(fileUri);

        } catch (Exception e) {
            Log.e(TAG, "Error exporting Excel: ", e);
            if (callback != null) callback.onFailure(e);
        } finally {
            try {
                if (zos != null) zos.close();
                else if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing stream", e);
            }
        }
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String createCell(int colIndex, int rowNum, String value) {
        String colLetter = getColumnLetter(colIndex);
        String ref = colLetter + rowNum;
        if (value == null) value = "";
        value = value.replace("&", "&amp;")
                     .replace("<", "&lt;")
                     .replace(">", "&gt;")
                     .replace("\"", "&quot;")
                     .replace("'", "&apos;");
        
        return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t>" + value + "</t></is></c>";
    }

    private static String getColumnLetter(int index) {
        StringBuilder letter = new StringBuilder();
        int i = index;
        while (i >= 0) {
            letter.insert(0, (char) ('A' + (i % 26)));
            i = (i / 26) - 1;
        }
        return letter.toString();
    }
}

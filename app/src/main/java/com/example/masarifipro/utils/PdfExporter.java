package com.example.masarifipro.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.example.masarifipro.R;
import com.example.masarifipro.models.Transaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfExporter {

    private static final String TAG = "PdfExporter";

    public interface ExportCallback {
        void onSuccess(Uri uri);
        void onFailure(Exception e);
    }

    public static void exportTransactions(Context context, List<Transaction> transactions, String fileName, 
                                          String currencyCode, double totalIncome, double totalExpense, 
                                          String period, ExportCallback callback) {
        
        boolean isArabic = LocaleHelper.getLanguage(context).equals("ar");
        
        PdfDocument pdfDocument = new PdfDocument();
        
        // A4 Landscape size in points (72 points per inch)
        int pageWidth = 842;
        int pageHeight = 595;
        int margin = 30;

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(18f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        TextPaint summaryPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        summaryPaint.setTextSize(10f);
        summaryPaint.setColor(Color.BLACK);

        TextPaint headerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setTextSize(8f);
        headerPaint.setFakeBoldText(true);

        TextPaint dataPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        dataPaint.setTextSize(7f);

        int pageNumber = 1;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        int y = margin;

        // 1. Report Title
        canvas.drawText(context.getString(R.string.report_title), pageWidth / 2f, y + 15, titlePaint);
        y += 40;

        // 2. Summary Table-like info
        summaryPaint.setTextAlign(isArabic ? Paint.Align.RIGHT : Paint.Align.LEFT);
        float startX = isArabic ? pageWidth - margin : margin;
        
        String dateText = context.getString(R.string.report_date) + ": " + new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());
        String currencyText = context.getString(R.string.currency) + ": " + (currencyCode != null ? currencyCode : context.getString(R.string.all));
        String periodText = context.getString(R.string.period) + ": " + period;
        
        canvas.drawText(dateText + "  |  " + currencyText + "  |  " + periodText, startX, y, summaryPaint);
        y += 15;
        
        String incomeText = context.getString(R.string.total_income) + ": " + NumberFormatter.format(totalIncome, currencyCode);
        String expenseText = context.getString(R.string.total_expenses) + ": " + NumberFormatter.format(totalExpense, currencyCode);
        String balanceText = context.getString(R.string.net_balance) + ": " + NumberFormatter.format(totalIncome - totalExpense, currencyCode);
        String countText = context.getString(R.string.transaction_count) + ": " + transactions.size();
        
        canvas.drawText(incomeText + "  |  " + expenseText + "  |  " + balanceText + "  |  " + countText, startX, y, summaryPaint);
        y += 25;

        // 3. Table Headers
        List<String> headers = TransactionExportMapper.getHeaders(context);
        float[] colWidths = calculateColumnWidths(pageWidth - 2 * margin);
        
        y = drawTableHeader(canvas, y, margin, pageWidth, headers, colWidths, headerPaint, isArabic);

        // 4. Rows
        for (Transaction t : transactions) {
            List<String> rowData = TransactionExportMapper.getRowData(context, t);
            int rowHeight = estimateRowHeight(rowData, colWidths, dataPaint);

            if (y + rowHeight > pageHeight - margin - 20) {
                drawFooter(canvas, pageWidth, pageHeight, pageNumber, context);
                pdfDocument.finishPage(page);
                pageNumber++;
                page = pdfDocument.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                canvas = page.getCanvas();
                y = margin;
                y = drawTableHeader(canvas, y, margin, pageWidth, headers, colWidths, headerPaint, isArabic);
            }

            drawTableRow(canvas, y, margin, pageWidth, rowData, colWidths, dataPaint, isArabic, rowHeight);
            y += rowHeight;
            
            // Draw horizontal line between rows
            Paint linePaint = new Paint();
            linePaint.setColor(Color.LTGRAY);
            linePaint.setStrokeWidth(0.5f);
            canvas.drawLine(margin, y - 2, pageWidth - margin, y - 2, linePaint);
        }

        drawFooter(canvas, pageWidth, pageHeight, pageNumber, context);
        pdfDocument.finishPage(page);
        savePdf(context, pdfDocument, fileName, callback);
    }

    private static int drawTableHeader(Canvas canvas, int y, int margin, int pageWidth, List<String> headers, float[] colWidths, TextPaint paint, boolean isArabic) {
        paint.setColor(Color.LTGRAY);
        canvas.drawRect(margin, y - 10, pageWidth - margin, y + 15, paint);
        paint.setColor(Color.BLACK);
        
        float currentX = isArabic ? pageWidth - margin : margin;
        for (int i = 0; i < headers.size(); i++) {
            float w = colWidths[i];
            drawCell(canvas, headers.get(i), currentX, y, w, paint, isArabic, true);
            if (isArabic) currentX -= w; else currentX += w;
        }
        return y + 25;
    }

    private static void drawTableRow(Canvas canvas, int y, int margin, int pageWidth, List<String> rowData, float[] colWidths, TextPaint paint, boolean isArabic, int rowHeight) {
        float currentX = isArabic ? pageWidth - margin : margin;
        for (int i = 0; i < rowData.size(); i++) {
            float w = colWidths[i];
            drawCell(canvas, rowData.get(i), currentX, y, w, paint, isArabic, false);
            if (isArabic) currentX -= w; else currentX += w;
        }
    }

    private static void drawCell(Canvas canvas, String text, float x, float y, float width, TextPaint paint, boolean isArabic, boolean isHeader) {
        if (text == null) text = "";
        
        canvas.save();
        float padding = 3f;
        float availableWidth = width - (2 * padding);
        
        // Translate to the cell start position
        if (isArabic) {
            canvas.translate(x - width + padding, y);
        } else {
            canvas.translate(x + padding, y);
        }

        Layout.Alignment alignment = isArabic ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL;
        
        StaticLayout staticLayout = new StaticLayout(text, paint, (int) availableWidth, alignment, 1.0f, 0.0f, false);
        
        // If not header, we might want to truncate if it exceeds a certain height, but here we calculated rowHeight
        staticLayout.draw(canvas);
        canvas.restore();
    }

    private static int estimateRowHeight(List<String> rowData, float[] colWidths, TextPaint paint) {
        int maxHeight = 15;
        for (int i = 0; i < rowData.size(); i++) {
            String text = rowData.get(i);
            if (text == null) continue;
            float availableWidth = colWidths[i] - 6f;
            StaticLayout sl = new StaticLayout(text, paint, (int) availableWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (sl.getHeight() + 5 > maxHeight) {
                maxHeight = sl.getHeight() + 5;
            }
        }
        return Math.min(maxHeight, 40); // Cap row height
    }

    private static float[] calculateColumnWidths(int totalWidth) {
        // Headers: Date, Type, Category, Description, Amount, Currency,
        // BalanceBefore, BalanceAfter, Name, Sync, Created, Exch, SrcCurr, OrigAmt, Rate
        // Total 15 columns.
        float[] weights = {
                1.2f, 0.8f, 1.2f, 1.8f, 1.0f, 0.7f,
                1.0f, 1.0f, 0.9f, 0.8f, 1.2f, 0.6f, 0.7f, 1.0f, 0.8f
        };
        float totalWeight = 0;
        for (float w : weights) totalWeight += w;
        
        float[] widths = new float[weights.length];
        for (int i = 0; i < weights.length; i++) {
            widths[i] = (weights[i] / totalWeight) * totalWidth;
        }
        return widths;
    }

    private static void drawFooter(Canvas canvas, int pageWidth, int pageHeight, int pageNumber, Context context) {
        Paint p = new Paint();
        p.setTextSize(8f);
        p.setColor(Color.GRAY);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(context.getString(R.string.page) + " " + pageNumber, pageWidth / 2f, pageHeight - 15, p);
    }

    private static void savePdf(Context context, PdfDocument pdfDocument, String fileName, ExportCallback callback) {
        OutputStream outputStream = null;
        Uri fileUri = null;
        try {
            String fullFileName = fileName + "_" + System.currentTimeMillis() + ".pdf";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fullFileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
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
                pdfDocument.writeTo(outputStream);
                if (callback != null) callback.onSuccess(fileUri);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving PDF: ", e);
            if (callback != null) callback.onFailure(e);
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                pdfDocument.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams: ", e);
            }
        }
    }
}

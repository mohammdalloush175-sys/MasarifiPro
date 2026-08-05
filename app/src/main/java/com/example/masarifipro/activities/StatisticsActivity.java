package com.example.masarifipro.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.core.widget.NestedScrollView;

import com.example.masarifipro.R;
import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.CurrencyAccount;
import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.utils.LocaleHelper;
import com.example.masarifipro.utils.NumberFormatter;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_prefs";

    private TextView tvRemainingCardValue, tvSavingRateCardValue, tvSavingProgressLabel, tvDailyAverageValue;
    private TextView tvSmartIncome, tvSmartExpense, tvSmartDiff, tvFinancialStatusMsg;
    private TextView tvSelectedCategoryInfo;
    private ChipGroup chipGroupCurrencies, chipGroupPeriod;
    private PieChart pieChartCategories;
    private BarChart barChartIncomeExpense;
    private LinearProgressIndicator savingProgressBar;
    private NestedScrollView statsScrollView;
    private LinearLayout emptyStateView;
    private Button btnAddTransaction;
    private ProgressBar statsProgressBar;

    private AppDatabase db;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private String selectedCurrency = "USD";
    private long startDate = 0, endDate = Long.MAX_VALUE;
    private int selectedPeriodChipId = R.id.chipYear;

    private final Map<String, CategoryData> currentCategoryDataMap = new HashMap<>();
    private final Map<String, Integer> categoryColors = new HashMap<>();

    private boolean isDarkMode = false;
    private int colorPrimaryText, colorSecondaryText, colorPurple, colorIncome, colorExpense, colorChartGrid;

    private static class StatsCache {
        String currency;
        long start, end;
        StatsResult result;
    }
    private StatsCache lastCache = null;

    private static class StatsResult {
        double income, expense;
        Map<String, Double> catAmounts;
        Map<String, Integer> catCounts;
        List<Transaction> transactions;
        boolean hasData;
    }

    private final int[] CHART_PALETTE = new int[]{
            Color.parseColor("#9C27B0"),
            Color.parseColor("#673AB7"),
            Color.parseColor("#2196F3"),
            Color.parseColor("#00BCD4"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#E91E63"),
            Color.parseColor("#F44336")
    };

    private static class CategoryData {
        String name;
        double amount;
        int transactionCount;
        float percentage;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        isDarkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        initColors();

        db = AppDatabase.getDatabase(this);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedCurrency = prefs.getString("current_currency", "USD");

        initViews();
        setupCharts();
        setupDefaultPeriod();
        setupPeriodFilters();
        setupBottomNavigation();
        loadActiveCurrencies();
    }

    private void initColors() {
        colorPrimaryText = ContextCompat.getColor(this, R.color.stats_text_primary);
        colorSecondaryText = ContextCompat.getColor(this, R.color.stats_text_secondary);
        colorPurple = ContextCompat.getColor(this, R.color.stats_purple);
        colorIncome = ContextCompat.getColor(this, R.color.stats_income);
        colorExpense = ContextCompat.getColor(this, R.color.stats_expense);
        colorChartGrid = ContextCompat.getColor(this, R.color.stats_chart_grid);
    }

    private void applyTheme() {
        SharedPreferences themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void initViews() {
        tvRemainingCardValue = findViewById(R.id.tvRemainingCardValue);
        tvSavingRateCardValue = findViewById(R.id.tvSavingRateCardValue);
        tvSavingProgressLabel = findViewById(R.id.tvSavingProgressLabel);
        tvDailyAverageValue = findViewById(R.id.tvDailyAverageValue);
        tvSmartIncome = findViewById(R.id.tvSmartIncome);
        tvSmartExpense = findViewById(R.id.tvSmartExpense);
        tvSmartDiff = findViewById(R.id.tvSmartDiff);
        tvFinancialStatusMsg = findViewById(R.id.tvFinancialStatusMsg);
        tvSelectedCategoryInfo = findViewById(R.id.selectedCategoryInfo);
        
        chipGroupCurrencies = findViewById(R.id.chipGroupCurrencies);
        chipGroupPeriod = findViewById(R.id.chipGroupPeriod);
        
        pieChartCategories = findViewById(R.id.pieChartCategories);
        barChartIncomeExpense = findViewById(R.id.barChartIncomeExpense);
        savingProgressBar = findViewById(R.id.savingProgressBar);
        
        statsScrollView = findViewById(R.id.statsScrollView);
        emptyStateView = findViewById(R.id.emptyStateView);
        btnAddTransaction = findViewById(R.id.btnAddTransaction);
        statsProgressBar = findViewById(R.id.statsProgressBar);

        btnAddTransaction.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("ACTION", "ADD_TRANSACTION");
            startActivity(intent);
        });
    }

    private void setupCharts() {
        enableChartTouch(pieChartCategories);
        enableChartTouch(barChartIncomeExpense);

        // Pie Chart Specifics
        pieChartCategories.setUsePercentValues(true);
        pieChartCategories.getDescription().setEnabled(false);
        pieChartCategories.setDrawHoleEnabled(true);
        pieChartCategories.setHoleColor(Color.TRANSPARENT);
        pieChartCategories.setHoleRadius(70f);
        pieChartCategories.setTransparentCircleRadius(74f);
        pieChartCategories.setDrawCenterText(true);
        pieChartCategories.setNoDataText(getString(R.string.no_data_stats_msg));
        pieChartCategories.setNoDataTextColor(colorPrimaryText);

        Legend l = pieChartCategories.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
        l.setYOffset(10f);
        l.setWordWrapEnabled(true);
        l.setTextColor(colorPrimaryText);

        pieChartCategories.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e instanceof PieEntry) {
                    PieEntry pe = (PieEntry) e;
                    CategoryData data = currentCategoryDataMap.get(pe.getLabel());
                    if (data != null) {
                        String amountStr = NumberFormatter.formatAmount(StatisticsActivity.this, data.amount, selectedCurrency);
                        String info = getString(R.string.category_details_format,
                                data.name, amountStr, String.format(Locale.getDefault(), "%.1f%%", data.percentage), data.transactionCount);
                        tvSelectedCategoryInfo.setText(info);
                    }
                }
            }
            @Override
            public void onNothingSelected() {
                tvSelectedCategoryInfo.setText(R.string.tap_category_details);
            }
        });

        // Bar Chart Specifics
        barChartIncomeExpense.getDescription().setEnabled(false);
        barChartIncomeExpense.setDrawBarShadow(false);
        barChartIncomeExpense.setDrawValueAboveBar(true);
        barChartIncomeExpense.setNoDataTextColor(colorPrimaryText);

        XAxis barXAxis = barChartIncomeExpense.getXAxis();
        barXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        barXAxis.setDrawGridLines(false);
        barXAxis.setGranularity(1f);
        barXAxis.setTextColor(colorSecondaryText);

        YAxis barLeftAxis = barChartIncomeExpense.getAxisLeft();
        barLeftAxis.setDrawGridLines(true);
        barLeftAxis.setGridColor(colorChartGrid);
        barLeftAxis.setAxisMinimum(0f);
        barLeftAxis.setTextColor(colorSecondaryText);

        barChartIncomeExpense.getAxisRight().setEnabled(false);
        barChartIncomeExpense.getLegend().setEnabled(false);

        // Add MarkerView
        CustomMarkerView marker = new CustomMarkerView(this, R.layout.marker_chart_value);
        marker.setChartView(barChartIncomeExpense);
        barChartIncomeExpense.setMarker(marker);
    }

    private void enableChartTouch(Chart<?> chart) {
        if (chart == null) return;

        chart.setTouchEnabled(true);
        chart.setHighlightPerTapEnabled(true);

        if (chart instanceof BarLineChartBase) {
            BarLineChartBase<?> barLineChart = (BarLineChartBase<?>) chart;

            barLineChart.setDragEnabled(true);
            barLineChart.setScaleEnabled(true);
            barLineChart.setPinchZoom(true);
            barLineChart.setDoubleTapToZoomEnabled(true);
            barLineChart.setHighlightPerDragEnabled(true);
        }

        chart.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });
    }

    private void setupDefaultPeriod() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        startDate = cal.getTimeInMillis();
        endDate = Long.MAX_VALUE;
        selectedPeriodChipId = R.id.chipYear;
    }

    private void setupPeriodFilters() {
        chipGroupPeriod.check(R.id.chipYear);
        updatePeriodChipsAppearance(R.id.chipYear);
        chipGroupPeriod.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            updatePeriodChipsAppearance(id);
            if (id == selectedPeriodChipId && id != R.id.chipCustom) return;
            
            selectedPeriodChipId = id;
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if (id == R.id.chipToday) {
                startDate = cal.getTimeInMillis();
                endDate = startDate + (24 * 60 * 60 * 1000) - 1;
            } else if (id == R.id.chipWeek) {
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                startDate = cal.getTimeInMillis();
                endDate = Long.MAX_VALUE;
            } else if (id == R.id.chipMonth) {
                cal.set(Calendar.DAY_OF_MONTH, 1);
                startDate = cal.getTimeInMillis();
                endDate = Long.MAX_VALUE;
            } else if (id == R.id.chipYear) {
                cal.set(Calendar.DAY_OF_YEAR, 1);
                startDate = cal.getTimeInMillis();
                endDate = Long.MAX_VALUE;
            } else if (id == R.id.chipCustom) {
                showDateRangePicker();
                return;
            }
            calculateStatistics();
        });
    }

    private void updatePeriodChipsAppearance(int checkedId) {
        int selectedBg = colorPurple;
        int unselectedBg = ContextCompat.getColor(this, R.color.stats_info_bg);
        int selectedText = Color.WHITE;
        int unselectedText = colorPurple;

        for (int i = 0; i < chipGroupPeriod.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupPeriod.getChildAt(i);
            boolean isSelected = chip.getId() == checkedId;
            chip.setChipBackgroundColor(ColorStateList.valueOf(isSelected ? selectedBg : unselectedBg));
            chip.setTextColor(isSelected ? selectedText : unselectedText);
            chip.setElevation(isSelected ? 4f : 0f);
        }
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.filter_custom)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            startDate = selection.first;
            endDate = selection.second + (24 * 60 * 60 * 1000) - 1;
            calculateStatistics();
        });
        picker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void loadActiveCurrencies() {
        executorService.execute(() -> {
            List<CurrencyAccount> activeCurrencies = db.currencyDao().getActiveCurrencies();
            runOnUiThread(() -> {
                chipGroupCurrencies.removeAllViews();
                for (CurrencyAccount currency : activeCurrencies) {
                    Chip chip = new Chip(this);
                    chip.setText(currency.getCurrencyCode());
                    chip.setCheckable(true);
                    chip.setClickable(true);
                    chip.setChipCornerRadius(12f);
                    updateCurrencyChipAppearance(chip, currency.getCurrencyCode().equals(selectedCurrency));
                    if (currency.getCurrencyCode().equals(selectedCurrency)) chip.setChecked(true);
                    chip.setOnClickListener(v -> {
                        if (!selectedCurrency.equals(currency.getCurrencyCode())) {
                            selectedCurrency = currency.getCurrencyCode();
                            for (int i = 0; i < chipGroupCurrencies.getChildCount(); i++) {
                                Chip c = (Chip) chipGroupCurrencies.getChildAt(i);
                                updateCurrencyChipAppearance(c, c.getText().equals(selectedCurrency));
                            }
                            calculateStatistics();
                        }
                    });
                    chipGroupCurrencies.addView(chip);
                }
                calculateStatistics();
            });
        });
    }

    private void updateCurrencyChipAppearance(Chip chip, boolean isSelected) {
        int selectedBg = colorPurple;
        int unselectedBg = ContextCompat.getColor(this, R.color.stats_info_bg);
        int selectedText = Color.WHITE;
        int unselectedText = colorPurple;

        chip.setChipBackgroundColor(ColorStateList.valueOf(isSelected ? selectedBg : unselectedBg));
        chip.setTextColor(isSelected ? selectedText : unselectedText);
        chip.setChipStrokeWidth(0f);
        chip.setElevation(isSelected ? 4f : 0f);
    }

    private void calculateStatistics() {
        if (lastCache != null && lastCache.currency.equals(selectedCurrency) && 
            lastCache.start == startDate && lastCache.end == endDate) {
            updateUI(lastCache.result);
            return;
        }

        statsProgressBar.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            List<Transaction> transactions = db.transactionDao().getTransactionsByDateRange(selectedCurrency, startDate, endDate);
            
            StatsResult result = new StatsResult();
            result.catAmounts = new HashMap<>();
            result.catCounts = new HashMap<>();
            result.transactions = transactions;
            result.hasData = !transactions.isEmpty();

            for (Transaction t : transactions) {
                if ("income".equals(t.getType())) {
                    result.income += t.getAmount();
                } else if ("expense".equals(t.getType())) {
                    result.expense += t.getAmount();
                    String cat = (t.getCategoryName() != null && !t.getCategoryName().isEmpty()) ? t.getCategoryName() : getString(R.string.category_general);
                    
                    Double amount = result.catAmounts.get(cat);
                    result.catAmounts.put(cat, (amount == null ? 0.0 : amount) + t.getAmount());
                    
                    Integer count = result.catCounts.get(cat);
                    result.catCounts.put(cat, (count == null ? 0 : count) + 1);
                }
            }

            lastCache = new StatsCache();
            lastCache.currency = selectedCurrency;
            lastCache.start = startDate;
            lastCache.end = endDate;
            lastCache.result = result;

            runOnUiThread(() -> {
                statsProgressBar.setVisibility(View.GONE);
                updateUI(result);
            });
        });
    }

    private void updateUI(StatsResult result) {
        if (!result.hasData) {
            statsScrollView.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
            return;
        }
        statsScrollView.setVisibility(View.VISIBLE);
        emptyStateView.setVisibility(View.GONE);

        double remaining = result.income - result.expense;
        double savingRate = result.income > 0 ? (remaining / result.income) * 100 : 0;
        if (savingRate < 0) savingRate = 0;

        tvRemainingCardValue.setText(NumberFormatter.formatAmount(this, remaining, selectedCurrency));
        tvSavingRateCardValue.setText(String.format(Locale.getDefault(), "%.1f%%", savingRate));
        
        updateSavingProgress(savingRate);
        updateDailyAverage(result.expense);
        updateBarChart(result.income, result.expense);
        updateSmartComparison(result.income, result.expense, remaining, savingRate);
        updatePieChart(result.expense, result.catAmounts, result.catCounts);
    }

    private void updateDailyAverage(double totalExpense) {
        long daysCount = 1;
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 23);
        today.set(Calendar.MINUTE, 59);
        
        long effectiveEndDate = Math.min(endDate, today.getTimeInMillis());
        long diff = effectiveEndDate - startDate;
        
        if (selectedPeriodChipId == R.id.chipToday) {
            daysCount = 1;
        } else {
            daysCount = (diff / (24 * 60 * 60 * 1000)) + 1;
        }
        
        if (daysCount <= 0) daysCount = 1;
        
        double avg = totalExpense / daysCount;
        tvDailyAverageValue.setText(NumberFormatter.formatAmount(this, avg, selectedCurrency));
    }

    private void updateSavingProgress(double rate) {
        savingProgressBar.setProgress((int) rate);
        tvSavingProgressLabel.setText(getString(R.string.saved_from_income, String.format(Locale.getDefault(), "%.1f%%", rate)));
    }

    private void updateSmartComparison(double income, double expense, double diff, double rate) {
        tvSmartIncome.setText(NumberFormatter.formatAmount(this, income, selectedCurrency));
        tvSmartExpense.setText(NumberFormatter.formatAmount(this, expense, selectedCurrency));
        tvSmartDiff.setText(NumberFormatter.formatAmount(this, diff, selectedCurrency));
        tvSmartDiff.setTextColor(diff >= 0 ? colorIncome : colorExpense);

        String msg;
        int color;
        if (diff < 0) {
            msg = getString(R.string.expenses_exceeded_income);
            color = colorExpense;
        } else if (rate < 5) {
            msg = getString(R.string.status_warning);
            color = Color.parseColor("#FF9800");
        } else if (rate < 20) {
            msg = getString(R.string.status_good);
            color = Color.parseColor("#2196F3");
        } else {
            msg = getString(R.string.status_excellent);
            color = colorIncome;
        }
        tvFinancialStatusMsg.setText(msg);
        tvFinancialStatusMsg.setTextColor(color);
    }

    private void updatePieChart(double totalExpense, Map<String, Double> catAmounts, Map<String, Integer> catCounts) {
        currentCategoryDataMap.clear();
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(catAmounts.entrySet());
        Collections.sort(sorted, (e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        for (Map.Entry<String, Double> entry : sorted) {
            float percentage = totalExpense > 0 ? (float) (entry.getValue() / totalExpense * 100) : 0;
            if (percentage >= 0.1f) {
                entries.add(new PieEntry((float) entry.getValue().doubleValue(), entry.getKey()));
                colors.add(getCategoryColor(entry.getKey()));
                CategoryData data = new CategoryData();
                data.name = entry.getKey();
                data.amount = entry.getValue();
                Integer count = catCounts.get(entry.getKey());
                data.transactionCount = (count == null ? 0 : count);
                data.percentage = percentage;
                currentCategoryDataMap.put(entry.getKey(), data);
            }
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLineColor(colorSecondaryText);
        dataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        dataSet.setValueLinePart1OffsetPercentage(80f);
        dataSet.setValueLinePart1Length(0.2f);
        dataSet.setValueLinePart2Length(0.4f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChartCategories));
        data.setValueTextSize(11f);
        data.setValueTextColor(colorPrimaryText);

        pieChartCategories.setData(data);
        pieChartCategories.setCenterText(generateCenterSpannableText(totalExpense));
        pieChartCategories.animateY(800);
        pieChartCategories.invalidate();
    }

    private SpannableString generateCenterSpannableText(double amount) {
        String label = getString(R.string.total_expenses);
        String amountStr = NumberFormatter.formatAmount(this, amount, selectedCurrency);
        SpannableString s = new SpannableString(label + "\n" + amountStr);
        s.setSpan(new RelativeSizeSpan(0.85f), 0, label.length(), 0);
        s.setSpan(new ForegroundColorSpan(colorSecondaryText), 0, label.length(), 0);
        s.setSpan(new RelativeSizeSpan(1.4f), label.length() + 1, s.length(), 0);
        s.setSpan(new StyleSpan(Typeface.BOLD), label.length() + 1, s.length(), 0);
        s.setSpan(new ForegroundColorSpan(colorExpense), label.length() + 1, s.length(), 0);
        return s;
    }

    private int getCategoryColor(String category) {
        if (!categoryColors.containsKey(category)) {
            int index = categoryColors.size() % CHART_PALETTE.length;
            categoryColors.put(category, CHART_PALETTE[index]);
        }
        Integer color = categoryColors.get(category);
        return color != null ? color : Color.GRAY;
    }

    private void updateBarChart(double income, double expense) {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, (float) income));
        entries.add(new BarEntry(1, (float) expense));

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(colorIncome, colorExpense);
        dataSet.setValueTextColor(colorPrimaryText);
        dataSet.setValueTextSize(10f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return NumberFormatter.formatAmount(StatisticsActivity.this, value, selectedCurrency);
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f);
        barChartIncomeExpense.setData(data);
        barChartIncomeExpense.getXAxis().setValueFormatter(new IndexAxisValueFormatter(new String[]{
                getString(R.string.income), getString(R.string.expense)
        }));
        barChartIncomeExpense.animateY(800);
        barChartIncomeExpense.invalidate();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        applyBottomNavDirection(bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_statistics);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) openActivity(MainActivity.class);
            else if (id == R.id.nav_debts) openActivity(DebtActivity.class);
            else if (id == R.id.nav_calendar) openActivity(CalendarActivity.class);
            else if (id == R.id.nav_settings) openActivity(SettingsActivity.class);
            return id == R.id.nav_statistics;
        });
    }

    private void applyBottomNavDirection(BottomNavigationView bottomNav) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String lang = prefs.getString("app_language", "ar");
        bottomNav.setLayoutDirection("en".equals(lang) ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        bottomNav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
    }

    private void openActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_statistics);
    }

    public class CustomMarkerView extends MarkerView {
        private final TextView tvLabel;
        private final TextView tvValue;

        public CustomMarkerView(Context context, int layoutResource) {
            super(context, layoutResource);
            tvLabel = findViewById(R.id.tvMarkerLabel);
            tvValue = findViewById(R.id.tvMarkerValue);
        }

        @Override
        public void refreshContent(Entry e, Highlight h) {
            if (e instanceof BarEntry) {
                int index = (int) e.getX();
                tvLabel.setText(index == 0 ? getString(R.string.income) : getString(R.string.expense));
                tvValue.setText(NumberFormatter.formatAmount(getContext(), e.getY(), selectedCurrency));
            }
            super.refreshContent(e, h);
        }

        @Override
        public com.github.mikephil.charting.utils.MPPointF getOffset() {
            return new com.github.mikephil.charting.utils.MPPointF(-(getWidth() / 2f), -getHeight());
        }
    }
}

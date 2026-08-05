package com.example.masarifipro.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "monthly_budgets",
        indices = {@Index(value = {"month", "year", "currencyCode", "categoryName"}, unique = true)}
)
public class MonthlyBudget {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private int month;
    private int year;
    private String currencyCode;
    private double budgetAmount;

    @NonNull
    @ColumnInfo(defaultValue = "'ALL'")
    private String categoryName = "ALL";

    private long createdAt;
    private long updatedAt;

    public MonthlyBudget() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public double getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(double budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    @NonNull
    public String getCategoryName() {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return "ALL";
        }
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            this.categoryName = "ALL";
        } else {
            this.categoryName = categoryName;
        }
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

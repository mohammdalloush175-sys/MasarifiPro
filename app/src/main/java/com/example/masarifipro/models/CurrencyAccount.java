package com.example.masarifipro.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "currency_accounts")
public class CurrencyAccount {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String currencyCode; // USD, EUR, SAR, AED, SYP, LBP
    private String currencyName;
    private String currencySymbol;
    private double balance;
    private String userId;
    private boolean isActive;

    public CurrencyAccount() {
    }

    public CurrencyAccount(String currencyCode, String currencyName, String currencySymbol, double balance, String userId, boolean isActive) {
        this.currencyCode = currencyCode;
        this.currencyName = currencyName;
        this.currencySymbol = currencySymbol;
        this.balance = balance;
        this.userId = userId;
        this.isActive = isActive;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getCurrencyName() { return currencyName; }
    public void setCurrencyName(String currencyName) { this.currencyName = currencyName; }

    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}

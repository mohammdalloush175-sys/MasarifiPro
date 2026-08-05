package com.example.masarifipro.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "shared_trip_expenses")
public class SharedTripExpense implements Serializable {
    @PrimaryKey
    @NonNull
    private String expenseId;
    private String tripId;
    private String title;
    private double amount;
    private String currencyCode;
    private String paidByUid;
    private String paidByName;
    private long createdAt;
    private String note;
    
    // Sync fields
    private String syncStatus = "SYNCED"; // PENDING, SYNCED, FAILED

    public SharedTripExpense() {
        // Required for Firestore/Room
    }

    public SharedTripExpense(@NonNull String expenseId, String tripId, String title, double amount, String currencyCode, String paidByUid, String paidByName, long createdAt, String note) {
        this.expenseId = expenseId;
        this.tripId = tripId;
        this.title = title;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.paidByUid = paidByUid;
        this.paidByName = paidByName;
        this.createdAt = createdAt;
        this.note = note;
    }

    @NonNull
    public String getExpenseId() { return expenseId; }
    public void setExpenseId(@NonNull String expenseId) { this.expenseId = expenseId; }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getPaidByUid() { return paidByUid; }
    public void setPaidByUid(String paidByUid) { this.paidByUid = paidByUid; }

    public String getPaidByName() { return paidByName; }
    public void setPaidByName(String paidByName) { this.paidByName = paidByName; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
}

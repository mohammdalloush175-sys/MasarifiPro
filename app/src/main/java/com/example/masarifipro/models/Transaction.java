package com.example.masarifipro.models;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

@Entity(
        tableName = "transactions",
        indices = {@Index(value = {"firestoreId"}, unique = true)}
)
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String firestoreId; // Added for Firebase synchronization
    private String sharedAccountId;
    private String userId;
    private String userName;
    private String type; // "income" or "expense"
    private double amount;
    private String currencyCode;
    private long categoryId;
    private String categoryName;
    private String description;
    private String invoiceImageUrl;
    private String localImagePath;
    private long date;
    private long createdAt;
    private long updatedAt;
    private boolean synced;

    // Running balance snapshot. Old transactions keep this disabled and are
    // still included in the running balance calculation for newer records.
    private Double balanceBefore;
    private Double balanceAfter;
    private boolean balanceSnapshotEnabled;

    // Sync Status Constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SYNCING = "SYNCING";
    public static final String STATUS_SYNCED = "SYNCED";
    public static final String STATUS_FAILED = "FAILED";

    private String syncStatus = STATUS_PENDING;

    // Exchange features
    private boolean isExchangeTransfer;
    private String exchangeGroupId;
    private String sourceCurrency;
    private String targetCurrency;
    private double exchangeRate;

    public Transaction() {
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }

    public String getSharedAccountId() { return sharedAccountId; }
    public void setSharedAccountId(String sharedAccountId) { this.sharedAccountId = sharedAccountId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(long categoryId) { this.categoryId = categoryId; }

    @PropertyName("category")
    public String getCategoryName() { return categoryName; }
    
    @PropertyName("category")
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    // Aliases for Firestore "category" field if PropertyName is not enough
    @Ignore
    @Exclude
    public String getCategory() { return categoryName; }
    @Ignore
    @Exclude
    public void setCategory(String category) { this.categoryName = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getInvoiceImageUrl() { return invoiceImageUrl; }
    public void setInvoiceImageUrl(String invoiceImageUrl) { this.invoiceImageUrl = invoiceImageUrl; }

    public String getLocalImagePath() { return localImagePath; }
    public void setLocalImagePath(String localImagePath) { this.localImagePath = localImagePath; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSynced() { return synced; }
    public void setSynced(boolean synced) { this.synced = synced; }

    public Double getBalanceBefore() { return balanceBefore; }
    public void setBalanceBefore(Double balanceBefore) { this.balanceBefore = balanceBefore; }

    public Double getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Double balanceAfter) { this.balanceAfter = balanceAfter; }

    public boolean isBalanceSnapshotEnabled() { return balanceSnapshotEnabled; }
    public void setBalanceSnapshotEnabled(boolean balanceSnapshotEnabled) {
        this.balanceSnapshotEnabled = balanceSnapshotEnabled;
    }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public boolean isExchangeTransfer() { return isExchangeTransfer; }
    public void setExchangeTransfer(boolean exchangeTransfer) { isExchangeTransfer = exchangeTransfer; }

    public String getExchangeGroupId() { return exchangeGroupId; }
    public void setExchangeGroupId(String exchangeGroupId) { this.exchangeGroupId = exchangeGroupId; }

    public String getSourceCurrency() { return sourceCurrency; }
    public void setSourceCurrency(String sourceCurrency) { this.sourceCurrency = sourceCurrency; }

    public String getTargetCurrency() { return targetCurrency; }
    public void setTargetCurrency(String targetCurrency) { this.targetCurrency = targetCurrency; }

    public double getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(double exchangeRate) { this.exchangeRate = exchangeRate; }
}
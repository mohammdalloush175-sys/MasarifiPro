package com.example.masarifipro.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_operations")
public class SyncOperation {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String operationType; // ADD, UPDATE, DELETE
    private String collectionName; // transactions, users, etc.
    private String documentId;
    private String jsonData;
    private long timestamp;
    private String status; // PENDING, SYNCING, FAILED
    private int retryCount;

    public static final String TYPE_ADD = "ADD";
    public static final String TYPE_UPDATE = "UPDATE";
    public static final String TYPE_DELETE = "DELETE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SYNCING = "SYNCING";
    public static final String STATUS_FAILED = "FAILED";

    public SyncOperation() {}

    public SyncOperation(String operationType, String collectionName, String documentId, String jsonData) {
        this.operationType = operationType;
        this.collectionName = collectionName;
        this.documentId = documentId;
        this.jsonData = jsonData;
        this.timestamp = System.currentTimeMillis();
        this.status = STATUS_PENDING;
        this.retryCount = 0;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getJsonData() { return jsonData; }
    public void setJsonData(String jsonData) { this.jsonData = jsonData; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
}

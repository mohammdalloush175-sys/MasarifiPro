package com.example.masarifipro.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "shared_trips")
public class SharedTrip implements Serializable {
    @PrimaryKey
    @NonNull
    private String tripId;
    private String name;
    private String inviteCode;
    private String ownerUid;
    private String ownerName;
    private String currencyCode;
    private long createdAt;
    private long updatedAt;
    
    // Sync status constants
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SYNCED = "SYNCED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_LOCAL_ONLY = "LOCAL_ONLY";

    // Sync fields
    private String syncStatus = STATUS_PENDING;
    private String remoteId; 

    @Ignore
    private List<String> memberUids;

    public SharedTrip() {
        this.memberUids = new ArrayList<>();
    }

    public SharedTrip(@NonNull String tripId, String name, String inviteCode, String ownerUid, String ownerName, String currencyCode, long createdAt) {
        this.tripId = tripId;
        this.name = name;
        this.inviteCode = inviteCode;
        this.ownerUid = ownerUid;
        this.ownerName = ownerName;
        this.currencyCode = currencyCode;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.memberUids = new ArrayList<>();
        this.memberUids.add(ownerUid);
    }

    @NonNull
    public String getTripId() { return tripId; }
    public void setTripId(@NonNull String tripId) { this.tripId = tripId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public String getRemoteId() { return remoteId; }
    public void setRemoteId(String remoteId) { this.remoteId = remoteId; }

    public List<String> getMemberUids() { 
        if (memberUids == null) memberUids = new ArrayList<>();
        return memberUids; 
    }
    public void setMemberUids(List<String> memberUids) { this.memberUids = memberUids; }
}

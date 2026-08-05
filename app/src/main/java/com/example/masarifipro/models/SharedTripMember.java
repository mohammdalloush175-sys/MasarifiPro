package com.example.masarifipro.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "shared_trip_members")
public class SharedTripMember implements Serializable {
    @PrimaryKey
    @NonNull
    private String uid;
    private String tripId;
    private String name;
    private String email;
    private long joinedAt;
    private boolean isOffline;
    private String addedByUid;

    public SharedTripMember() {
        // Required for Firestore/Room
    }

    // Existing constructor (5 parameters) used for online members
    public SharedTripMember(@NonNull String uid, String tripId, String name, String email, long joinedAt) {
        this.uid = uid;
        this.tripId = tripId;
        this.name = name;
        this.email = email;
        this.joinedAt = joinedAt;
    }

    // New constructor (6 parameters) matching the call in SharedTripDetailsActivity for offline members
    public SharedTripMember(String uid, String name, String email, long joinedAt, boolean isOffline, String addedByUid) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.joinedAt = joinedAt;
        this.isOffline = isOffline;
        this.addedByUid = addedByUid;
    }

    @NonNull
    public String getUid() { return uid; }
    public void setUid(@NonNull String uid) { this.uid = uid; }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public long getJoinedAt() { return joinedAt; }
    public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }

    public boolean isOffline() { return isOffline; }
    public void setOffline(boolean offline) { isOffline = offline; }

    public String getAddedByUid() { return addedByUid; }
    public void setAddedByUid(String addedByUid) { this.addedByUid = addedByUid; }
}

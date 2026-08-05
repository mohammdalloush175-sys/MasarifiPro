package com.example.masarifipro.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "users")
public class AppUser {
    @PrimaryKey
    @NonNull
    private String uid; // Firebase UID
    private String name;
    private String email;
    private String profileImageUrl;
    private String defaultCurrency;

    public AppUser() {
    }

    public AppUser(@NonNull String uid, String name, String email) {
        this.uid = uid;
        this.name = name;
        this.email = email;
    }

    @NonNull
    public String getUid() { return uid; }
    public void setUid(@NonNull String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
}

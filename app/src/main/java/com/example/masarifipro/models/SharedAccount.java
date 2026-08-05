package com.example.masarifipro.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.annotation.NonNull;
import java.io.Serializable;

@Entity(tableName = "shared_accounts")
public class SharedAccount implements Serializable {
    @PrimaryKey
    @NonNull
    private String id; // Firebase Document ID
    private String name;
    private String ownerId;
    private String inviteCode;

    @Ignore
    private String role;

    public SharedAccount() {
    }

    public SharedAccount(@NonNull String id, String name, String ownerId, String inviteCode) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.inviteCode = inviteCode;
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}

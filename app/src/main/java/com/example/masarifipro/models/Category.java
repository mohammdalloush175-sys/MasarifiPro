package com.example.masarifipro.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class Category {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private String type; // "income" or "expense"
    private String currencyCode; // To support per-currency categories if needed
    private String iconName;
    private String userId;

    public Category() {
    }

    public Category(String name, String type, String currencyCode, String iconName, String userId) {
        this.name = name;
        this.type = type;
        this.currencyCode = currencyCode;
        this.iconName = iconName;
        this.userId = userId;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getIconName() { return iconName; }
    public void setIconName(String iconName) { this.iconName = iconName; }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}

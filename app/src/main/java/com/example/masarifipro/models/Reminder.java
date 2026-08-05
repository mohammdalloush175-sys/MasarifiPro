package com.example.masarifipro.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reminders")
public class Reminder {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String title;
    private String description;
    private long reminderTime;
    private String dateText;
    private double amount;
    private String currencyCode;
    private boolean isDone;
    private long createdAt;

    public Reminder(String title, String description, long reminderTime, String dateText, double amount, String currencyCode) {
        this.title = title;
        this.description = description;
        this.reminderTime = reminderTime;
        this.dateText = dateText;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.isDone = false;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getReminderTime() { return reminderTime; }
    public void setReminderTime(long reminderTime) { this.reminderTime = reminderTime; }
    public String getDateText() { return dateText; }
    public void setDateText(String dateText) { this.dateText = dateText; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public boolean isDone() { return isDone; }
    public void setDone(boolean done) { isDone = done; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

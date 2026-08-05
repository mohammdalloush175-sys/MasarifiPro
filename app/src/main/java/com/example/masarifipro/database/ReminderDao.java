package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.Reminder;

import java.util.List;

@Dao
public interface ReminderDao {
    @Insert
    long insert(Reminder reminder);

    @Update
    void update(Reminder reminder);

    @Delete
    void delete(Reminder reminder);

    @Query("SELECT * FROM reminders WHERE dateText = :dateText ORDER BY reminderTime ASC")
    List<Reminder> getRemindersForDate(String dateText);

    @Query("SELECT * FROM reminders WHERE isDone = 0 ORDER BY reminderTime ASC")
    List<Reminder> getAllActiveReminders();

    @Query("UPDATE reminders SET isDone = :isDone WHERE id = :id")
    void markDone(int id, boolean isDone);
    
    @Query("SELECT * FROM reminders WHERE id = :id")
    Reminder getReminderById(int id);

    @Query("DELETE FROM reminders")
    void deleteAll();
}

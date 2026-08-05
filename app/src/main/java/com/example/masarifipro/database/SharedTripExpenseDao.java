package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.SharedTripExpense;

import java.util.List;

@Dao
public interface SharedTripExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SharedTripExpense expense);

    @Update
    void update(SharedTripExpense expense);

    @Delete
    void delete(SharedTripExpense expense);

    @Query("SELECT * FROM shared_trip_expenses WHERE tripId = :tripId ORDER BY createdAt DESC")
    List<SharedTripExpense> getExpensesByTripId(String tripId);

    @Query("SELECT * FROM shared_trip_expenses WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    List<SharedTripExpense> getPendingExpenses();

    @Query("DELETE FROM shared_trip_expenses WHERE tripId = :tripId")
    void deleteByTripId(String tripId);

    @Query("DELETE FROM shared_trip_expenses")
    void deleteAll();
}

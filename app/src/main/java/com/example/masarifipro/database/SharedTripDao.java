package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.SharedTrip;

import java.util.List;

@Dao
public interface SharedTripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SharedTrip trip);

    @Update
    void update(SharedTrip trip);

    @Delete
    void delete(SharedTrip trip);

    @Query("SELECT * FROM shared_trips ORDER BY updatedAt DESC")
    List<SharedTrip> getAllTrips();

    @Query("SELECT * FROM shared_trips WHERE tripId = :tripId")
    SharedTrip getTripById(String tripId);

    @Query("SELECT * FROM shared_trips WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    List<SharedTrip> getPendingTrips();

    @Query("DELETE FROM shared_trips")
    void deleteAll();

    @Query("DELETE FROM shared_trips WHERE tripId = :tripId")
    void deleteById(String tripId);

    // Sync Center Queries
    @Query("SELECT COUNT(*) FROM shared_trips WHERE syncStatus = :status")
    int getCountBySyncStatus(String status);

    @Query("SELECT * FROM shared_trips WHERE syncStatus = :status1 OR syncStatus = :status2 ORDER BY updatedAt DESC LIMIT :limit")
    List<SharedTrip> getTripsBySyncStatuses(String status1, String status2, int limit);
}

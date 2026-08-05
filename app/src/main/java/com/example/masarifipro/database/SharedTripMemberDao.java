package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.SharedTripMember;

import java.util.List;

@Dao
public interface SharedTripMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SharedTripMember member);

    @Update
    void update(SharedTripMember member);

    @Delete
    void delete(SharedTripMember member);

    @Query("SELECT * FROM shared_trip_members WHERE tripId = :tripId")
    List<SharedTripMember> getMembersByTripId(String tripId);

    @Query("DELETE FROM shared_trip_members WHERE tripId = :tripId")
    void deleteByTripId(String tripId);

    @Query("DELETE FROM shared_trip_members")
    void deleteAll();
}

package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.AppUser;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AppUser user);

    @Update
    void update(AppUser user);

    @Delete
    void delete(AppUser user);

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    AppUser getUserByUid(String uid);

    @Query("DELETE FROM users")
    void deleteAll();
}

package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.SharedAccount;

import java.util.List;

@Dao
public interface SharedAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SharedAccount account);

    @Update
    void update(SharedAccount account);

    @Delete
    void delete(SharedAccount account);

    @Query("SELECT * FROM shared_accounts")
    List<SharedAccount> getAllAccounts();

    @Query("DELETE FROM shared_accounts")
    void deleteAll();
}

package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.CurrencyAccount;

import java.util.List;

@Dao
public interface CurrencyAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CurrencyAccount account);

    @Update
    void update(CurrencyAccount account);

    @Delete
    void delete(CurrencyAccount account);

    @Query("SELECT * FROM currency_accounts")
    List<CurrencyAccount> getAllAccounts();

    @Query("SELECT * FROM currency_accounts WHERE currencyCode = :code")
    CurrencyAccount getAccountByCurrency(String code);
}

package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.CurrencyAccount;

import java.util.List;

@Dao
public interface CurrencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CurrencyAccount currencyAccount);

    @Update
    void update(CurrencyAccount currencyAccount);

    @Query("SELECT * FROM currency_accounts WHERE isActive = 1")
    List<CurrencyAccount> getActiveCurrencies();

    @Query("SELECT * FROM currency_accounts WHERE currencyCode = :code LIMIT 1")
    CurrencyAccount getCurrencyByCode(String code);

    @Query("SELECT * FROM currency_accounts")
    List<CurrencyAccount> getAllCurrencies();

    @Query("UPDATE currency_accounts SET isActive = :isActive WHERE currencyCode = :currencyCode")
    void updateCurrencyActive(String currencyCode, boolean isActive);

    @Query("SELECT COUNT(*) FROM currency_accounts")
    int getCurrencyCount();

    @Query("DELETE FROM currency_accounts")
    void deleteAllCurrencies();
}

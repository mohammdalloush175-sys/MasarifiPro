package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.Debt;

import java.util.List;

@Dao
public interface DebtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Debt debt);

    @Update
    void update(Debt debt);

    @Delete
    void delete(Debt debt);

    @Query("SELECT * FROM debts WHERE currencyCode = :currencyCode ORDER BY dueDate ASC")
    List<Debt> getDebtsByCurrency(String currencyCode);

    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    List<Debt> getAllDebts();

    @Query("SELECT * FROM debts WHERE id = :id")
    Debt getDebtById(int id);

    @Query("SELECT * FROM debts WHERE firestoreId = :firestoreId")
    Debt getDebtByFirestoreId(String firestoreId);

    @Query("DELETE FROM debts")
    void deleteAll();
}

package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.MonthlyBudget;

import java.util.List;

@Dao
public interface MonthlyBudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MonthlyBudget budget);

    @Update
    void update(MonthlyBudget budget);

    @Delete
    void delete(MonthlyBudget budget);

    @Query("SELECT * FROM monthly_budgets WHERE month = :month AND year = :year AND currencyCode = :currencyCode AND categoryName = :categoryName LIMIT 1")
    MonthlyBudget getBudget(int month, int year, String currencyCode, String categoryName);

    @Query("SELECT * FROM monthly_budgets WHERE month = :month AND year = :year AND currencyCode = :currencyCode")
    List<MonthlyBudget> getBudgetsForMonthCurrency(int month, int year, String currencyCode);

    @Query("SELECT * FROM monthly_budgets ORDER BY year DESC, month DESC, currencyCode ASC, categoryName ASC")
    List<MonthlyBudget> getAllBudgets();

    @Query("DELETE FROM monthly_budgets WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM monthly_budgets")
    void deleteAllMonthlyBudgets();
}

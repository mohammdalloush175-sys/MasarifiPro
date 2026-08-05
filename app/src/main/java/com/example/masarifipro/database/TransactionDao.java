package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.models.TransactionCategorySuggestion;

import java.util.List;

@Dao
public interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Transaction transaction);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(Transaction transaction);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Transaction> transactions);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Query("DELETE FROM transactions WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM transactions WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);

    @Query("DELETE FROM transactions")
    void deleteAllTransactions();

    @Query("SELECT * FROM transactions WHERE id = :id")
    Transaction getTransactionById(long id);

    @Query("SELECT * FROM transactions WHERE firestoreId = :firestoreId LIMIT 1")
    Transaction getTransactionByFirestoreId(String firestoreId);

    @Query("SELECT * FROM transactions WHERE firestoreId IS NULL " +
            "AND date = :date AND createdAt = :createdAt AND ABS(amount - :amount) < 0.000001 " +
            "AND type = :type AND currencyCode = :currency " +
            "AND (description = :description OR (description IS NULL AND :description IS NULL)) " +
            "LIMIT 1")
    Transaction findLegacyWithoutFirestoreId(long date, long createdAt, double amount, String type,
                                               String currency, String description);

    @Query("UPDATE transactions SET synced = :synced, syncStatus = :status WHERE firestoreId = :firestoreId")
    void updateSyncStatus(String firestoreId, boolean synced, String status);

    @Query("UPDATE transactions SET balanceBefore = :balanceBefore, balanceAfter = :balanceAfter WHERE id = :id")
    void updateBalanceSnapshot(long id, Double balanceBefore, Double balanceAfter);

    @Query("SELECT * FROM transactions WHERE currencyCode = :currencyCode ORDER BY date DESC")
    List<Transaction> getTransactionsByCurrency(String currencyCode);

    @Query("SELECT * FROM transactions WHERE type = :type AND currencyCode = :currencyCode ORDER BY date DESC")
    List<Transaction> getTransactionsByTypeAndCurrency(String type, String currencyCode);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM transactions WHERE type = 'income' AND currencyCode = :currency")
    double getTotalIncomeByCurrency(String currency);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM transactions WHERE type = 'expense' AND currencyCode = :currency")
    double getTotalExpenseByCurrency(String currency);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM transactions WHERE type = 'expense' AND currencyCode = :currencyCode AND date >= :startDate AND date <= :endDate")
    double getMonthlyExpenseTotal(String currencyCode, long startDate, long endDate);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM transactions WHERE type = 'expense' AND currencyCode = :currencyCode AND categoryName = :categoryName AND date >= :startDate AND date <= :endDate")
    double getMonthlyExpenseTotalByCategory(String currencyCode, String categoryName, long startDate, long endDate);

    @Query("SELECT * FROM transactions WHERE description LIKE '%' || :query || '%' ORDER BY date DESC")
    List<Transaction> searchTransactions(String query);

    @Query("SELECT * FROM transactions WHERE currencyCode = :currencyCode AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    List<Transaction> getTransactionsByDateRange(String currencyCode, long startDate, long endDate);

    @Query("SELECT * FROM transactions WHERE currencyCode = :currencyCode AND categoryName = :category ORDER BY date DESC")
    List<Transaction> getTransactionsByCategory(String currencyCode, String category);

    @Query("SELECT * FROM transactions WHERE currencyCode = :currencyCode AND categoryName = :category AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    List<Transaction> getTransactionsByDateRangeAndCategory(String currencyCode, long startDate, long endDate, String category);

    @Query("SELECT * FROM transactions WHERE userId = :userId " +
            "AND (synced = 0 OR syncStatus = 'PENDING' OR syncStatus = 'FAILED' OR syncStatus = 'SYNCING')")
    List<Transaction> getUnsyncedTransactionsForUser(String userId);

    @Query("SELECT * FROM transactions WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    List<Transaction> getTransactionsToSync();

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<Transaction> getAllTransactions();

    @Query("SELECT * FROM transactions ORDER BY date ASC, createdAt ASC, firestoreId ASC, id ASC")
    List<Transaction> getAllTransactionsChronological();

    @Query("SELECT * FROM transactions WHERE date = :date AND amount = :amount AND type = :type AND currencyCode = :currency AND (description = :description OR (description IS NULL AND :description IS NULL)) LIMIT 1")
    Transaction findDuplicate(long date, double amount, String type, String currency, String description);

    @Query("SELECT DISTINCT categoryName FROM transactions WHERE categoryName IS NOT NULL AND TRIM(categoryName) != '' AND type = :type AND currencyCode = :currencyCode ORDER BY categoryName COLLATE NOCASE ASC")
    List<String> getCategorySuggestions(String type, String currencyCode);

    @Query("SELECT DISTINCT categoryName FROM transactions WHERE categoryName IS NOT NULL AND TRIM(categoryName) != '' ORDER BY categoryName COLLATE NOCASE ASC")
    List<String> getAllCategorySuggestions();

    @Query("SELECT DISTINCT TRIM(categoryName) AS name, type, currencyCode " +
            "FROM transactions " +
            "WHERE categoryName IS NOT NULL AND TRIM(categoryName) != '' " +
            "AND categoryName != 'exchange_category' " +
            "ORDER BY type ASC, currencyCode ASC, name COLLATE NOCASE ASC")
    List<TransactionCategorySuggestion> getUniqueCategoriesFromTransactions();

    @Query("SELECT COUNT(*) FROM transactions WHERE LOWER(TRIM(categoryName)) = LOWER(TRIM(:categoryName)) AND type = :type AND currencyCode = :currencyCode")
    int countTransactionsByCategory(String categoryName, String type, String currencyCode);

    // Sync Center Queries
    @Query("SELECT COUNT(*) FROM transactions WHERE syncStatus = :status")
    int getCountBySyncStatus(String status);

    @Query("SELECT * FROM transactions WHERE syncStatus = :status1 OR syncStatus = :status2 ORDER BY date DESC LIMIT :limit")
    List<Transaction> getTransactionsBySyncStatuses(String status1, String status2, int limit);
}

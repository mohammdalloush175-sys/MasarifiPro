package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.Category;

import java.util.List;

@Dao
public interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Category category);

    @Update
    void update(Category category);

    @Delete
    void delete(Category category);

    @Query("DELETE FROM categories WHERE id = :id AND userId = :userId")
    void deleteByIdAndUser(long id, String userId);

    @Query("DELETE FROM categories WHERE userId = :userId")
    void deleteAllCategories(String userId);

    @Query("DELETE FROM categories WHERE userId = :userId AND type = :type AND currencyCode = :currencyCode")
    void deleteByTypeAndCurrency(String userId, String type, String currencyCode);

    @Query("DELETE FROM categories WHERE userId = :userId AND type = :type")
    void deleteByType(String userId, String type);

    @Query("DELETE FROM categories WHERE userId = :userId AND currencyCode = :currencyCode")
    void deleteByCurrency(String userId, String currencyCode);

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY type ASC, currencyCode ASC, name COLLATE NOCASE ASC")
    List<Category> getAllCategories(String userId);

    @Query("SELECT * FROM categories WHERE type = :type AND userId = :userId ORDER BY name COLLATE NOCASE ASC")
    List<Category> getCategoriesByType(String userId, String type);

    @Query("SELECT * FROM categories WHERE currencyCode = :currencyCode AND userId = :userId ORDER BY type ASC, name COLLATE NOCASE ASC")
    List<Category> getCategoriesByCurrency(String userId, String currencyCode);

    @Query("SELECT * FROM categories WHERE type = :type AND currencyCode = :currencyCode AND userId = :userId ORDER BY name COLLATE NOCASE ASC")
    List<Category> getCategoriesByTypeAndCurrency(String userId, String type, String currencyCode);

    @Query("SELECT COUNT(*) FROM categories WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND type = :type AND currencyCode = :currencyCode AND userId = :userId AND id != :excludeId")
    int countDuplicateCategory(String userId, String name, String type, String currencyCode, long excludeId);

    @Query("SELECT COUNT(*) FROM categories WHERE userId = :userId")
    int getCategoryCount(String userId);

    // New methods added in Phase 2
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY type ASC, currencyCode ASC, name COLLATE NOCASE ASC")
    List<Category> getAllCategoriesByUser(String userId);

    @Query("SELECT * FROM categories WHERE userId = :userId AND type = :type ORDER BY name COLLATE NOCASE ASC")
    List<Category> getCategoriesByTypeByUser(String userId, String type);

    @Query("SELECT * FROM categories WHERE userId = :userId AND currencyCode = :currencyCode ORDER BY type ASC, name COLLATE NOCASE ASC")
    List<Category> getCategoriesByCurrencyByUser(String userId, String currencyCode);

    @Query("SELECT * FROM categories WHERE userId = :userId AND type = :type AND currencyCode = :currencyCode ORDER BY name COLLATE NOCASE ASC")
    List<Category> getCategoriesByTypeAndCurrencyByUser(String userId, String type, String currencyCode);

    @Query("SELECT COUNT(*) FROM categories WHERE userId = :userId AND LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND type = :type AND currencyCode = :currencyCode AND id != :excludeId")
    int countDuplicateCategoryByUser(String userId, String name, String type, String currencyCode, long excludeId);

    @Query("DELETE FROM categories WHERE userId = :userId AND id = :id")
    void deleteByIdAndUser(String userId, long id);

    @Query("DELETE FROM categories WHERE userId = :userId AND type = :type AND currencyCode = :currencyCode")
    void deleteByTypeAndCurrencyByUser(String userId, String type, String currencyCode);

    @Query("DELETE FROM categories WHERE userId = :userId AND type = :type")
    void deleteByTypeByUser(String userId, String type);

    @Query("DELETE FROM categories WHERE userId = :userId AND currencyCode = :currencyCode")
    void deleteByCurrencyByUser(String userId, String currencyCode);

    @Query("DELETE FROM categories WHERE userId = :userId")
    void deleteAllCategoriesByUser(String userId);

    @Query("UPDATE categories SET userId = :userId WHERE userId IS NULL OR TRIM(userId) = ''")
    void assignMissingUserIdToCategories(String userId);
}

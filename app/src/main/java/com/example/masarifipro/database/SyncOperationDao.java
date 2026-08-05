package com.example.masarifipro.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.masarifipro.models.SyncOperation;

import java.util.List;

@Dao
public interface SyncOperationDao {
    @Insert
    long insert(SyncOperation operation);

    @Update
    void update(SyncOperation operation);

    @Delete
    void delete(SyncOperation operation);

    @Query("SELECT * FROM sync_operations WHERE status = 'PENDING' OR status = 'FAILED' OR status = 'SYNCING' ORDER BY timestamp ASC")
    List<SyncOperation> getAllPendingOperations();

    @Query("DELETE FROM sync_operations WHERE collectionName = :collectionName AND documentId = :documentId " +
            "AND (status = 'PENDING' OR status = 'FAILED')")
    void deleteSupersededOperations(String collectionName, String documentId);

    @Query("SELECT COUNT(*) FROM sync_operations WHERE collectionName = :collectionName AND documentId = :documentId")
    int countOperationsForDocument(String collectionName, String documentId);

    @Query("SELECT COUNT(*) FROM sync_operations WHERE collectionName = :collectionName AND documentId = :documentId " +
            "AND operationType = 'DELETE'")
    int countPendingDeletes(String collectionName, String documentId);

    @Query("DELETE FROM sync_operations WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status != 'SYNCED'")
    int getUnsyncedCount();

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'FAILED'")
    int getErrorCount();

    @Query("DELETE FROM sync_operations")
    void deleteAll();
}

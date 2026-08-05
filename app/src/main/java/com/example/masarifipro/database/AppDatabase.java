package com.example.masarifipro.database;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.masarifipro.models.AppUser;
import com.example.masarifipro.models.Category;
import com.example.masarifipro.models.CurrencyAccount;
import com.example.masarifipro.models.Debt;
import com.example.masarifipro.models.MonthlyBudget;
import com.example.masarifipro.models.Reminder;
import com.example.masarifipro.models.SharedAccount;
import com.example.masarifipro.models.SharedTrip;
import com.example.masarifipro.models.SharedTripExpense;
import com.example.masarifipro.models.SharedTripMember;
import com.example.masarifipro.models.SyncOperation;
import com.example.masarifipro.models.Transaction;

@Database(entities = {
        Transaction.class, 
        Category.class, 
        CurrencyAccount.class, 
        SharedAccount.class, 
        AppUser.class, 
        Reminder.class, 
        Debt.class, 
        SyncOperation.class,
        SharedTrip.class,
        SharedTripMember.class,
        SharedTripExpense.class,
        MonthlyBudget.class
}, version = 19, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract CurrencyDao currencyDao();
    public abstract ReminderDao reminderDao();
    public abstract DebtDao debtDao();
    public abstract SyncOperationDao syncOperationDao();
    public abstract SharedTripDao sharedTripDao();
    public abstract SharedTripMemberDao sharedTripMemberDao();
    public abstract SharedTripExpenseDao sharedTripExpenseDao();
    public abstract MonthlyBudgetDao monthlyBudgetDao();
    public abstract UserDao userDao();
    public abstract SharedAccountDao sharedAccountDao();

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN syncStatus TEXT DEFAULT 'PENDING'");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `sync_operations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operationType` TEXT, `collectionName` TEXT, `documentId` TEXT, `jsonData` TEXT, `timestamp` INTEGER NOT NULL, `status` TEXT, `retryCount` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `shared_trips` (`tripId` TEXT NOT NULL, `name` TEXT, `inviteCode` TEXT, `ownerUid` TEXT, `ownerName` TEXT, `currencyCode` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncStatus` TEXT, `remoteId` TEXT, PRIMARY KEY(`tripId`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `shared_trip_members` (`uid` TEXT NOT NULL, `tripId` TEXT, `name` TEXT, `email` TEXT, `joinedAt` INTEGER NOT NULL, `isOffline` INTEGER NOT NULL, `addedByUid` TEXT, PRIMARY KEY(`uid`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `shared_trip_expenses` (`expenseId` TEXT NOT NULL, `tripId` TEXT, `title` TEXT, `amount` REAL NOT NULL, `currencyCode` TEXT, `paidByUid` TEXT, `paidByName` TEXT, `createdAt` INTEGER NOT NULL, `note` TEXT, `syncStatus` TEXT, PRIMARY KEY(`expenseId`))");
        }
    };

    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS monthly_budgets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "month INTEGER NOT NULL, " +
                    "year INTEGER NOT NULL, " +
                    "currencyCode TEXT, " +
                    "budgetAmount REAL NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_monthly_budgets_month_year_currencyCode ON monthly_budgets (month, year, currencyCode)");
        }
    };

    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE monthly_budgets ADD COLUMN categoryName TEXT NOT NULL DEFAULT 'ALL'");
            database.execSQL("DROP INDEX IF EXISTS index_monthly_budgets_month_year_currencyCode");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_monthly_budgets_month_year_currencyCode_categoryName ON monthly_budgets (month, year, currencyCode, categoryName)");
        }
    };

    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "categories", "userId", "ALTER TABLE categories ADD COLUMN userId TEXT");
            database.execSQL("UPDATE categories SET userId = 'guest' WHERE userId IS NULL");
        }
    };

    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "categories", "userId", "ALTER TABLE categories ADD COLUMN userId TEXT");
        }
    };

    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN balanceBefore REAL");
            database.execSQL("ALTER TABLE transactions ADD COLUMN balanceAfter REAL");
            database.execSQL("ALTER TABLE transactions ADD COLUMN balanceSnapshotEnabled INTEGER NOT NULL DEFAULT 0");

            // Keep a single local row for each Firestore document before adding
            // the unique index. Existing duplicate rows are the main source of
            // doubled operations and incorrect balances.
            database.execSQL("DELETE FROM transactions " +
                    "WHERE firestoreId IS NOT NULL AND TRIM(firestoreId) != '' " +
                    "AND id NOT IN (" +
                    "SELECT MAX(id) FROM transactions " +
                    "WHERE firestoreId IS NOT NULL AND TRIM(firestoreId) != '' " +
                    "GROUP BY firestoreId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_firestoreId " +
                    "ON transactions(firestoreId)");
        }
    };

    private static void addColumnIfMissing(SupportSQLiteDatabase database, String tableName,
                                           String columnName, String alterSql) {
        boolean exists = false;
        try (Cursor cursor = database.query("PRAGMA table_info(" + tableName + ")")) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && columnName.equals(cursor.getString(nameIndex))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            database.execSQL(alterSql);
        }
    }

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "masarifi_database")
                            .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                                    MIGRATION_17_18, MIGRATION_18_19)
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

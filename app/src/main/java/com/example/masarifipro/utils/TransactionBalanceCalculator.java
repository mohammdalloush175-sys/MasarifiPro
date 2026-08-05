package com.example.masarifipro.utils;

import com.example.masarifipro.database.AppDatabase;
import com.example.masarifipro.models.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds deterministic running-balance snapshots from the complete ledger.
 *
 * Old transactions may have balanceSnapshotEnabled=false. They still contribute
 * to the running balance, but their before/after values remain empty.
 */
public final class TransactionBalanceCalculator {

    private TransactionBalanceCalculator() {
    }

    public static void recalculateAll(AppDatabase db) {
        List<Transaction> transactions = db.transactionDao().getAllTransactionsChronological();
        calculateSnapshots(transactions);

        for (Transaction transaction : transactions) {
            if (transaction.isBalanceSnapshotEnabled()) {
                db.transactionDao().updateBalanceSnapshot(
                        transaction.getId(),
                        transaction.getBalanceBefore(),
                        transaction.getBalanceAfter());
            }
        }
    }

    /** Pure in-memory calculation used by tests and database reconciliation. */
    public static void calculateSnapshots(List<Transaction> source) {
        List<Transaction> transactions = new ArrayList<>(source);
        transactions.sort(Comparator
                .comparingLong(Transaction::getDate)
                .thenComparingLong(Transaction::getCreatedAt)
                .thenComparing(t -> safe(t.getFirestoreId()))
                .thenComparingLong(Transaction::getId));

        Map<String, Double> balances = new HashMap<>();
        for (Transaction transaction : transactions) {
            String currency = normalizeCurrency(transaction.getCurrencyCode());
            double before = balances.getOrDefault(currency, 0.0d);
            double delta = isIncome(transaction) ? transaction.getAmount() : -transaction.getAmount();
            double after = before + delta;

            if (transaction.isBalanceSnapshotEnabled()) {
                transaction.setBalanceBefore(before);
                transaction.setBalanceAfter(after);
            } else {
                transaction.setBalanceBefore(null);
                transaction.setBalanceAfter(null);
            }

            balances.put(currency, after);
        }
    }

    private static boolean isIncome(Transaction transaction) {
        return "income".equalsIgnoreCase(transaction.getType());
    }

    private static String normalizeCurrency(String currencyCode) {
        return safe(currencyCode).trim().toUpperCase();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

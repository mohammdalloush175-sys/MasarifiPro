package com.example.masarifipro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.masarifipro.models.Transaction;
import com.example.masarifipro.utils.TransactionBalanceCalculator;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TransactionBalanceCalculatorTest {

    @Test
    public void oldTransactionsContributeButOnlyNewTransactionsExposeSnapshots() {
        Transaction oldIncome = transaction(1, "a", "income", 100, "USD", 1000, false);
        Transaction newExpense = transaction(2, "b", "expense", 30, "USD", 2000, true);
        Transaction newIncome = transaction(3, "c", "income", 20, "USD", 3000, true);
        Transaction otherCurrency = transaction(4, "d", "expense", 10, "EUR", 1500, true);

        List<Transaction> transactions = Arrays.asList(
                newIncome, otherCurrency, oldIncome, newExpense);

        TransactionBalanceCalculator.calculateSnapshots(transactions);

        assertNull(oldIncome.getBalanceBefore());
        assertNull(oldIncome.getBalanceAfter());
        assertEquals(100.0, newExpense.getBalanceBefore(), 0.000001);
        assertEquals(70.0, newExpense.getBalanceAfter(), 0.000001);
        assertEquals(70.0, newIncome.getBalanceBefore(), 0.000001);
        assertEquals(90.0, newIncome.getBalanceAfter(), 0.000001);
        assertEquals(0.0, otherCurrency.getBalanceBefore(), 0.000001);
        assertEquals(-10.0, otherCurrency.getBalanceAfter(), 0.000001);
    }

    private static Transaction transaction(long id, String firestoreId, String type,
                                           double amount, String currency, long date,
                                           boolean snapshotEnabled) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setFirestoreId(firestoreId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setCurrencyCode(currency);
        transaction.setDate(date);
        transaction.setCreatedAt(date);
        transaction.setBalanceSnapshotEnabled(snapshotEnabled);
        return transaction;
    }
}

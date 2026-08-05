package com.example.masarifipro.models;

public class TransactionCategorySuggestion {
    public String name;
    public String type;
    public String currencyCode;

    public TransactionCategorySuggestion(String name, String type, String currencyCode) {
        this.name = name;
        this.type = type;
        this.currencyCode = currencyCode;
    }
}

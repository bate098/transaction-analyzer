package com.bucketobate.transactionanalyzer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum TransactionType {
    CREDIT,
    DEBIT;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static TransactionType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TransactionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

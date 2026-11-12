package com.bucketobate.transactionanalyzer.web;

import com.bucketobate.transactionanalyzer.model.Transaction;
import com.bucketobate.transactionanalyzer.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        Instant timestamp
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getTimestamp()
        );
    }
}

package com.bucketobate.transactionanalyzer.web;

import com.bucketobate.transactionanalyzer.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
        String accountId,
        TransactionType type,
        BigDecimal amount,
        Instant timestamp
) {
}

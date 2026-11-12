package com.bucketobate.transactionanalyzer.web;

import java.math.BigDecimal;

public record AccountSummary(
        String accountId,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal endingBalance,
        BigDecimal largestTransaction,
        int transactionCount,
        boolean overdrawn
) {
}

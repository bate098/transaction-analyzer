package com.bucketobate.transactionanalyzer.web;

import java.util.List;

public record TransactionSummaryResponse(
        List<AccountSummary> summaries,
        String topSpender
) {
}

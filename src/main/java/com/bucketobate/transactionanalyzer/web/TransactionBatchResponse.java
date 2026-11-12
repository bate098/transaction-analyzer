package com.bucketobate.transactionanalyzer.web;

import java.util.List;

public record TransactionBatchResponse(
        List<TransactionResponse> accepted,
        List<IgnoredTransaction> ignored
) {
}

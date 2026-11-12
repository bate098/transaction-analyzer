# Transaction Analyzer

Spring Boot API for posting financial transactions and reading per-account summaries. Requires Java 21.

## Run

```bash
./gradlew bootRun
```

The app listens on [http://localhost:8080](http://localhost:8080). Data is stored in an in-memory H2 database and is gone when the process stops.

```bash
./gradlew test
```

## API

`POST /api/transactions` accepts a JSON list. Valid rows are saved. Invalid rows are skipped.

A request that saves at least one transaction returns `201`, a body with lists of `accepted` and `ignored` transactions, and a `Location` header for the first saved transaction (`/api/transactions/{id}`). If every row is invalid, the response is `400` with the same body shape. A malformed body, including a single object instead of a list, is **400**.
The response body is formatted like the following example:
```json
{
"accepted": [ { "id": 1, "accountId": "A100", "type": "credit", "amount": 500, "timestamp": "2026-05-10T10:00:00Z" }, {"id":2,"accountId":"A100","type":"debit","amount":200,"timestamp":"2026-05-10T12:00:00Z"} ],
"ignored": [ { "index": 4, "reason": "accountId is required" } ]
}
```

`GET /api/transactions` lists transactions, optionally filtered with `?accountId=`. `GET /api/transactions/{id}` fetches one. `DELETE /api/transactions/{id}` removes one.

`GET /api/summary` returns one summary per account, ordered by `accountId`, plus `topSpender`. Ending balance is total credits minus total debits. An account is overdrawn when that balance is negative. `topSpender` is the account with the highest total debits; equal debit totals use the lexicographically smaller `accountId`.

## Assumptions

- Every create request is a list. A single transaction object is treated as a malformed body.
- A missing timestamp is invalid, in addition to a missing or blank `accountId`, a type other than credit or debit, and an amount that is missing, zero, or negative.
- `credit` and `debit` are matched case-insensitively and stored in lowercase.
- The `Location` header points at the first transaction saved from the list, not at every saved row.
- Duplicate transactions are legitimate. There is no idempotency key or field comparison to drop repeats.
- Accounts are implicit (derived from transactions), so unknown accounts return empty results rather than 404.

## Design choices

- Invalid rows are reported with their list index and a reason instead of failing the whole batch. The batch is rejected only when nothing can be saved, or when the JSON itself cannot be read.
- Summaries are computed from stored rows in memory. That keeps the rules in one place for this data set, and it would need a query or cache if the table grew large. In the future, the performance cost of summing up all the transactions could become onerous and require keeping a stored balance per account.
- Account summaries are sorted by `accountId` so the response is stable. The top-spender tie uses that same ordering: the first account with the highest debit total wins.
- Using in-memory H2 to keep setup to zero for this prototype despite the trade-off of data loss on restart. The persistence layer uses standard JPA/JDBC with no H2-specific SQL, so it can move to PostgreSQL (via a Spring profile and docker-compose) if we want durability or production-like concurrency behavior.

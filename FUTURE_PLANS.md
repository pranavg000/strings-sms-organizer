# Strings - Future Plans

### Accessibility
- OTP autofill: rely on the platform's notification-based OTP autofill only. The OS/keyboard (Gboard, AOSP autofill) already scans incoming notification text for OTP codes and surfaces them as a keyboard suggestion, and `SmsNotifier.notifyOtp()` posts the code in the notification text. No `AutofillService` (system-wide autofill provider) -- that is a single, exclusive slot meant for credential managers and is a deliberate non-goal.

---

## Phase 4: Bank/CC Transaction Tracking (India)

### Parser (DONE - user-configurable accounts)

Implemented as per-bank `BankParser`s in `domain/transaction/` routed by sender principal through the public `BankCatalog`. All account identity (last digits, names, colors, family links) is user data in the Room `accounts` table, managed from the Manage accounts screen -- nothing personal lives in code.

- `BankCatalog`: one public entry per supported institution (bank code, display name, sender principals, supported account types, whether accounts are identified by tail digits). Tail-less wallets (Swiggy/Zomato/Amazon Pay) are brand-matched and enabled with a toggle.
- `BankParser` per bank (`AxisParser`, `IciciParser`, `HdfcParser`, ...): shared extraction helpers in `BankParsing` (amount/balance/tail/time regexes, direction classification with the dual-keyword "debited...credited" rule, reversal-as-credit). Each parser receives only the user's enabled accounts for its bank.
- `TransactionParser.parse(body, sender, accounts)` returns a `ParseOutcome`: `Match` (transaction against a configured account), `UnconfiguredAccount` (transactional SMS from a supported bank with an unknown tail -> recorded as an account suggestion, surfaced in Manage accounts), or `NoMatch`.
- Wired into the single ingest hook `SyncSmsUseCase.persist()` via `TransactionCategorizer`: insert the `Transaction` against the matched account id and auto-tag the message `Finance > {Source}`. Guarded so parse/store failure never blocks ingest or notifications. Saving an account re-runs the idempotent `RecategorizeTransactionsUseCase`.
- Covered by `TransactionParserTest` with anonymized fixtures (wallet cases, bank debit/credit, balance extraction, refund/reversal direction, unconfigured-account suggestions).

### Remaining follow-ups

#### Parser coverage to extend (add a `BankParser` + `BankCatalog` entry)
- More banks: SBI, Kotak, PNB, BoB, Google Pay/PhonePe/Paytm UPI wallets.
- Credit card specifics: bill reminders ("CC bill of Rs X due on DATE"), EMI debits.
- Merchant extraction (the `merchant` field is mostly null) and UPI reference-number extraction.
- A `loan` account type and EMI handling are not modeled yet.

#### Tag hierarchy depth
- First cut tags 2 levels (`Finance > {Source}`). Deferred: a third level for account type (e.g. `Finance > HDFC > Credit Card`).

### Transaction Dashboard UI (TODO)
- Monthly overview: total income vs spending (bar chart)
- Per-account breakdown with balance timeline
- Category-wise spending (if merchant data available)
- Filters: date range, account, type (credit/debit), amount range
- Export to CSV for personal records

### Tag Integration
- Auto-tag with hierarchy: Finance > {BankName} > {AccountType}
- E.g., "Finance > HDFC > Credit Card", "Finance > SBI > Savings"

---

## Phase 5 (Stretch Goals)

### Smart Categorization (ML-based)
- On-device ML model for message classification
- Train on user's tag assignments as labeled data
- Suggest tags for uncategorized messages

### Scheduled Reports
- Weekly/monthly transaction summary notification
- Configurable report day and time

### Backup & Restore
- Export Room database to encrypted file
- Google Drive backup integration
- Import on new device

### Widgets
- Home screen widget showing recent OTPs
- Transaction summary widget (today's spending)

### Multi-device Sync
- Optional cloud sync for tags/filters (not message content)
- Share filter rules between devices

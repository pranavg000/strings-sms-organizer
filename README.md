# Strings

A read-only SMS organizer for Android. Gmail-style tags and filters for your SMS, one-tap OTP copy from the notification, and bank transaction tracking (India) -- without replacing your default SMS app.

Strings reads and organizes the messages already on your phone. It never sends messages, never uploads anything, and works entirely offline. Archiving, tagging, or trashing a message in Strings changes nothing in your default messaging app.

## Features

- **Tags, not folders.** Messages are organized with a hierarchical tag system (e.g. `Finance > HDFC`), like labels in Gmail. A message can carry many tags and appears in every matching inbox tab. Tabs are configurable.
- **Filters.** Rules with nested AND/OR conditions (sender, contact name, body; contains/equals/regex) and combinable actions: assign tags, archive, trash, mark read, suppress or silence the notification, stop processing. Filters run in a user-defined order and can be applied retroactively.
- **Filter suggestions.** Select a few similar messages and Strings drafts a filter from what they have in common -- shared sender principal and common body phrases, order-aware.
- **OTP detection.** One-time passwords are detected on-device and collected under an OTP tag. Notifications show the code in large digit boxes with a Copy action and auto-expire.
- **Finance dashboard (India).** Bank and card transactions are parsed from SMS alerts of supported banks: balances, monthly income/expense, per-account history, add-on card grouping, and balance reconciliation that flags unaccounted gaps. You configure your accounts (bank + last digits) in the app; nothing is hardcoded.
- **Backup / restore.** Versioned JSON export of everything you curate -- tags, filters, tabs, accounts, per-message state -- with cross-device import.

## Privacy

- Read-only: requires `READ_SMS` / `RECEIVE_SMS`, never `SEND_SMS`.
- No network access; all parsing (OTP, transactions) runs on-device.
- `READ_CONTACTS` is optional and only used to display contact names.

## Supported banks (transaction parsing)

Axis, ICICI, HDFC, IDFC First, Bank of India, EPFO, Pluxee, and the Swiggy Money / Zomato Money / Amazon Pay wallets. Adding a bank means implementing one small `BankParser` and registering it in `BankCatalog` -- see `domain/transaction/`.

## Tech

Kotlin, Jetpack Compose + Material 3 (dynamic color), MVVM with a pure-Kotlin domain layer, Room, Koin, kotlinx-serialization. minSdk 35.

## Building

Open in Android Studio, or from the command line:

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # run the JVM unit tests
```

## Status

Built for personal use and shared as-is. Issues and PRs welcome, especially new `BankParser` implementations.

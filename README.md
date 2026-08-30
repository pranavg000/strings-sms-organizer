# Strings

A read-only SMS organizer for Android. Gmail-style tags and filters for your SMS, one-tap OTP copy from the notification, and bank transaction tracking (India) -- without replacing your default SMS app.

Strings reads and organizes the messages already on your phone. It never sends messages, never uploads anything, and works entirely offline. Archiving, tagging, or trashing a message in Strings changes nothing in your default messaging app.

## Install

Download the latest APK from the [Releases page](https://github.com/pranavg000/strings-sms-organizer/releases/latest) and open it on your phone. Requires Android 15+.

Because Strings is sideloaded and reads SMS, Android puts two deliberate speed bumps in the way. Both are expected -- here is the full walkthrough:

1. **"Install unknown apps"** -- the first time, Android asks you to allow installs from your browser or file manager. Allow it and continue.
2. **Play Protect warning** -- Google warns about APKs it hasn't seen widely before, and it is extra suspicious of anything requesting SMS access (that's what SMS-stealer malware requests). Tap "More details" > "Install anyway". You can verify what you're installing: the APK is built from exactly the source in this repo.
3. **Grant SMS access via restricted settings** -- on Android 15+, a sideloaded app cannot be granted SMS permissions until you deliberately unlock them ([this is system policy](https://source.android.com/docs/compatibility/15/android-15-cdd#98_restricted_settings) for all sideloaded apps, not something Strings controls). When Strings asks for SMS access and the grant is blocked: open **Settings > Apps > Strings**, tap the **three-dot menu** (top right), choose **Allow restricted settings**, authenticate, then grant SMS access normally.

All releases are signed with the same certificate, so updates install cleanly over the previous version. For automatic updates, add this repo to [Obtainium](https://github.com/ImranR98/Obtainium) -- it installs and updates apps directly from GitHub Releases.

Strings is not on the Play Store: Google only permits SMS permissions for an app acting as the default SMS handler, and Strings is deliberately read-only.

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

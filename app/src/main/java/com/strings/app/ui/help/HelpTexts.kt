package com.strings.app.ui.help

/**
 * Central home for all in-app help copy, shared by the info tooltips and the
 * Getting started screen so wording stays consistent in one place.
 */
object HelpTexts {
    const val FILTER_ENABLED: String =
        "When enabled, this filter runs automatically on every incoming message. " +
            "Filters run in the order shown on the Manage filters screen."
    const val FILTER_CONDITIONS: String =
        "Conditions decide which messages this filter matches. " +
            "ALL means every condition must match; ANY means at least one is enough. " +
            "Add a nested group to mix ALL and ANY. " +
            "Sender is the raw SMS header (e.g. VM-HDFCBK), Sender name is the resolved contact name, " +
            "and Body is the message text. The regex operator accepts full regular expressions."
    const val FILTER_ACTIONS: String =
        "What happens to a matching message. Combine as many as you need.\n" +
            "\u2022 Remove from inbox: takes it off the Inbox tab but keeps its other tags.\n" +
            "\u2022 Archive / Trash: archives it, or moves it to the recoverable Trash.\n" +
            "\u2022 Mark read: no unread styling or count.\n" +
            "\u2022 Suppress notification: it arrives with no notification at all.\n" +
            "\u2022 Notify silently: the notification appears in the shade but makes " +
            "no sound and doesn't pop on screen.\n" +
            "\u2022 Stop processing: filters below this one are skipped for the message."
    const val FILTER_ASSIGN_TAGS: String =
        "Matching messages get these tags. Tags organize messages into inbox tabs " +
            "and drawer views \u2014 a message appears in every tab whose tag it has."
    const val FILTER_APPLY_EXISTING: String =
        "Also run this filter once over the messages already in the app when you save, " +
            "not just future ones."
    const val FILTER_LIST: String =
        "Filters are rules that run automatically on every incoming message, from top to bottom. " +
            "Drag the handle to change the order. The switch turns a filter on or off " +
            "without deleting it. A filter with the \u201Cstop processing\u201D action " +
            "prevents the filters below it from running on a matched message."
    const val TAG_LIST: String =
        "Tags are how Strings organizes messages \u2014 like labels in Gmail. " +
            "A tag can be shown as a tab on the inbox home screen, and tags can nest " +
            "under a parent tag (e.g. Finance > HDFC). A message can have many tags " +
            "and appears in every matching tab."
    const val TAG_PARENT: String =
        "Nest this tag under another to build a hierarchy (e.g. Finance > HDFC). " +
            "A tab for the parent tag also shows messages tagged with any of its children."
    const val TAG_SHOW_AS_TAB: String =
        "Adds a tab for this tag on the inbox home screen. The tab shows every message " +
            "with this tag or one of its child tags."
    const val PAGE_OVERVIEW: String =
        "Strings reads and organizes the SMS already on your phone. It is read-only: " +
            "it never sends messages and it does not replace your default SMS app. " +
            "Archiving, trashing, or tagging here changes nothing in your default app."
    const val PAGE_TAGS: String =
        "Tags are labels that categorize messages, like in Gmail. Each inbox tab maps to a tag, " +
            "and you choose which tags appear as tabs. Tags can nest under a parent " +
            "(e.g. Finance > HDFC), and a parent's tab includes messages tagged with its children. " +
            "A message can have many tags, so it appears in every matching tab \u2014 not just one."
    const val PAGE_FILTERS: String =
        "Filters are rules that run automatically on incoming messages. Each filter has conditions " +
            "(who sent it, what the text contains) and actions (assign tags, archive, mark read, " +
            "suppress the notification, and more). Filters run in the order shown on the Manage " +
            "filters screen. A disabled \u201CExample: Order updates\u201D filter is included \u2014 " +
            "open it to see how conditions and actions fit together, then enable it or delete it."
    const val PAGE_SUGGEST: String =
        "Long-press a message to start selecting, pick a few similar ones, then tap the wand icon. " +
            "Strings drafts a filter from what the messages have in common \u2014 you just review it, " +
            "add actions, and save."
    const val PAGE_OTP: String =
        "One-time passwords are detected automatically and collected under the OTP tag. " +
            "Their notifications show the code in large digits with a Copy button, " +
            "and expire on their own after a few minutes."
    const val PAGE_FINANCE: String =
        "Bank and card transactions are detected from your messages and summarized in the " +
            "Finance dashboard (in the drawer): balances, monthly income and expenses, " +
            "and per-account transaction history. Detection only runs for accounts you add " +
            "under Manage accounts \u2014 pick the bank and enter the last digits of the " +
            "account or card number, and matching SMS alerts turn into transactions. " +
            "When a reported balance doesn't tally with the transactions seen so far, the gap " +
            "is kept as an \u201Cunaccounted\u201D entry in the list until you dismiss it."
    const val ACCOUNTS_LIST: String =
        "Add each bank account, card, or wallet you want tracked. Transactions are detected " +
            "from that bank's SMS alerts using the last digits you enter here. When a " +
            "transactional SMS arrives for an unknown account, it appears at the top as a " +
            "suggestion you can add or dismiss. Saving an account rebuilds transaction history " +
            "automatically."
    const val ACCOUNT_TAIL: String =
        "The last digits of the account or card number exactly as they appear in the bank's " +
            "SMS (e.g. \u201CAcct XX1234\u201D or \u201CCard ending 1234\u201D). They match " +
            "messages to this account \u2014 3 to 6 digits."
    const val ACCOUNT_PARENT: String =
        "Link an add-on or supplementary card to its primary card. Linked cards share one " +
            "statement, so their balance and monthly totals are combined on the dashboard."
    const val ACCOUNT_TRACKING: String =
        "When off, this account's SMS are no longer parsed and its transactions from the " +
            "past year are removed. Messages are untouched, so switching it back on " +
            "re-scans the last year and rebuilds the history."
    const val ACCOUNT_WALLETS: String =
        "Wallets are matched by brand name instead of account digits, so they only need to " +
            "be switched on. Turning one off stops new transactions without deleting history."
    const val SENTINEL_INFO: String =
        "This entry stands in for money that moved without a matching SMS \u2014 the reported " +
            "balance didn't tally with the transactions seen before it. Dismiss it from its menu " +
            "once you've accounted for it."
    const val SENTINEL_DISMISS_BODY: String =
        "This removes the placeholder for the unaccounted amount from the ledger and its " +
            "monthly totals. This can't be undone."
}

package com.strings.app.domain.filter

/**
 * One-shot hand-off for a suggested filter draft between the inbox (where it is
 * generated) and the filter editor (which opens as a new, unsaved filter).
 * Consuming clears the pending draft so a normal "create filter" is unaffected.
 */
class FilterDraftHolder {
    private var pending: SuggestedFilter? = null

    fun set(draft: SuggestedFilter) {
        pending = draft
    }

    fun consume(): SuggestedFilter? {
        val draft: SuggestedFilter? = pending
        pending = null
        return draft
    }
}

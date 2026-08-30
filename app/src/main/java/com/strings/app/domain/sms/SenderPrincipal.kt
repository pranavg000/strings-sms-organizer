package com.strings.app.domain.sms

/**
 * Resolves the principal token of an SMS sender header. DLT-aware: it splits on '-',
 * drops a trailing single-char category code (P/T/S/G) and a leading <=2-char operator
 * access code, so "AX-BSELTD-S" -> "BSELTD" and "VM-HDFCBK" -> "HDFCBK". Single-token or
 * numeric senders pass through unchanged (uppercased).
 */
object SenderPrincipal {
    fun principal(sender: String): String {
        val parts: List<String> = sender.split('-').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return sender.trim().uppercase()
        val working: MutableList<String> = parts.toMutableList()
        if (working.size > 1 && working.last().length == 1) working.removeAt(working.size - 1)
        if (working.size > 1 && working.first().length <= 2) working.removeAt(0)
        return working.joinToString("-").uppercase()
    }
}

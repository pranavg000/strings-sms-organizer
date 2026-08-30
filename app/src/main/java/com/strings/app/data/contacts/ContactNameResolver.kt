package com.strings.app.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a sender address (phone number) to a contact display name at runtime.
 * Names are never stored: lookups go through ContactsContract.PhoneLookup and are
 * cached in memory per unique address (including negative results to avoid repeats).
 */
class ContactNameResolver(private val context: Context) {
    private val cache: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    fun resolve(address: String): String? {
        if (address.isBlank()) return null
        if (!isPhoneNumber(address)) return null
        if (!hasPermission()) return null
        val cached: String? = cache[address]
        if (cached != null) return cached.ifEmpty { null }
        val resolved: String = lookup(address) ?: ""
        cache[address] = resolved
        return resolved.ifEmpty { null }
    }

    private fun lookup(address: String): String? {
        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address)
        )
        val projection: Array<String> = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index: Int = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (index >= 0) {
                    val name: String? = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return null
    }

    fun sendersMatchingName(query: String): List<String> {
        return cache.entries
            .filter { (_, name) -> name.isNotEmpty() && name.contains(query, ignoreCase = true) }
            .map { it.key }
    }

    private fun isPhoneNumber(address: String): Boolean {
        return address.any { it.isDigit() } && address.none { it.isLetter() }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

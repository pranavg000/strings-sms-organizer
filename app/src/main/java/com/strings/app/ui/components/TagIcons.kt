package com.strings.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated set of icons selectable for tags. Stored as portable string keys so the
 * choice survives in the database independent of the Compose icon API.
 */
val TagIcons: Map<String, ImageVector> = linkedMapOf(
    "label" to Icons.AutoMirrored.Filled.Label,
    "inbox" to Icons.Filled.Inbox,
    "account_balance" to Icons.Filled.AccountBalance,
    "credit_card" to Icons.Filled.CreditCard,
    "receipt" to Icons.Filled.Receipt,
    "shopping_cart" to Icons.Filled.ShoppingCart,
    "local_offer" to Icons.Filled.LocalOffer,
    "notifications" to Icons.Filled.Notifications,
    "star" to Icons.Filled.Star,
    "favorite" to Icons.Filled.Favorite,
    "person" to Icons.Filled.Person,
    "work" to Icons.Filled.Work,
    "home" to Icons.Filled.Home,
    "school" to Icons.Filled.School,
    "restaurant" to Icons.Filled.Restaurant,
    "flight" to Icons.Filled.Flight,
    "medical" to Icons.Filled.MedicalServices,
    "security" to Icons.Filled.Security,
    "email" to Icons.Filled.Email,
    "phone" to Icons.Filled.Phone
)

const val DEFAULT_TAG_ICON: String = "label"

fun tagIconFor(key: String): ImageVector = TagIcons[key] ?: TagIcons.getValue(DEFAULT_TAG_ICON)

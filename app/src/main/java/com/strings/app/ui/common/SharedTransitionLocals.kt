@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.strings.app.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Provided by the SharedTransitionLayout wrapping the NavHost. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** Provided per navigation destination so shared elements can animate with it. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Container-transform bounds shared between a MessageCard in any list and the
 * message detail screen.
 */
@Composable
fun Modifier.messageSharedBounds(messageId: Long): Modifier =
    appSharedBounds(key = "message-$messageId")

/**
 * Container-transform bounds shared between an AccountCard on the finance
 * dashboard and the account detail screen.
 */
@Composable
fun Modifier.accountSharedBounds(accountId: Long): Modifier =
    appSharedBounds(key = "account-$accountId")

/**
 * No-ops when the scopes aren't available (previews, screens outside the
 * NavHost). Default resizeMode is ScaleToBounds — exactly the
 * container-transform behavior we want, so it isn't passed explicitly.
 */
@Composable
private fun Modifier.appSharedBounds(key: String): Modifier {
    val sharedScope: SharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animScope: AnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@appSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope
        )
    }
}

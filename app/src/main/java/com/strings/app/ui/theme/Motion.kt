package com.strings.app.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

private const val DURATION: Int = 300
private const val FADE_OUT_DURATION: Int = 90
private const val FADE_IN_DURATION: Int = 210

// M3 motion easing tokens.
private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val AccelerateEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
private val DecelerateEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

// Shared-axis drift is a small offset (spec: ~30dp), not a full-screen slide.
// Expressed as a fraction of the container so these builders stay density-free.
private fun drift(fullSize: Int): Int = fullSize / 10

/**
 * Navigation motion vocabulary (M3 motion patterns):
 * - Shared axis X: hierarchical drill-down (list -> detail). The default.
 * - Shared axis Y: "begin a task" (editors slide up slightly).
 * - Fade-through: switching between peer areas (Settings, Finance, Search,
 *   management lists) where neither screen is "deeper" than the other.
 *
 * All patterns pair a short outgoing fade (90ms) with a delayed incoming
 * fade (210ms) so a still-loading destination fades in instead of visibly
 * sliding in blank.
 */
object NavTransitions {

    fun sharedAxisXEnter(): EnterTransition =
        slideInHorizontally(tween(DURATION, easing = StandardEasing)) { drift(it) } +
            fadeIn(tween(FADE_IN_DURATION, delayMillis = FADE_OUT_DURATION, easing = DecelerateEasing))

    fun sharedAxisXExit(): ExitTransition =
        slideOutHorizontally(tween(DURATION, easing = StandardEasing)) { -drift(it) } +
            fadeOut(tween(FADE_OUT_DURATION, easing = AccelerateEasing))

    fun sharedAxisXPopEnter(): EnterTransition =
        slideInHorizontally(tween(DURATION, easing = StandardEasing)) { -drift(it) } +
            fadeIn(tween(FADE_IN_DURATION, delayMillis = FADE_OUT_DURATION, easing = DecelerateEasing))

    fun sharedAxisXPopExit(): ExitTransition =
        slideOutHorizontally(tween(DURATION, easing = StandardEasing)) { drift(it) } +
            fadeOut(tween(FADE_OUT_DURATION, easing = AccelerateEasing))

    fun sharedAxisYEnter(): EnterTransition =
        slideInVertically(tween(DURATION, easing = StandardEasing)) { drift(it) } +
            fadeIn(tween(FADE_IN_DURATION, delayMillis = FADE_OUT_DURATION, easing = DecelerateEasing))

    fun sharedAxisYExit(): ExitTransition =
        slideOutVertically(tween(DURATION, easing = StandardEasing)) { -drift(it) } +
            fadeOut(tween(FADE_OUT_DURATION, easing = AccelerateEasing))

    fun sharedAxisYPopEnter(): EnterTransition =
        slideInVertically(tween(DURATION, easing = StandardEasing)) { -drift(it) } +
            fadeIn(tween(FADE_IN_DURATION, delayMillis = FADE_OUT_DURATION, easing = DecelerateEasing))

    fun sharedAxisYPopExit(): ExitTransition =
        slideOutVertically(tween(DURATION, easing = StandardEasing)) { drift(it) } +
            fadeOut(tween(FADE_OUT_DURATION, easing = AccelerateEasing))

    fun fadeThroughEnter(): EnterTransition =
        fadeIn(tween(FADE_IN_DURATION, delayMillis = FADE_OUT_DURATION, easing = DecelerateEasing)) +
            scaleIn(
                animationSpec = tween(FADE_IN_DURATION, delayMillis = FADE_OUT_DURATION, easing = StandardEasing),
                initialScale = 0.92f
            )

    fun fadeThroughExit(): ExitTransition =
        fadeOut(tween(FADE_OUT_DURATION, easing = AccelerateEasing))

    /** In-place content swaps (e.g. the selection top bar) — same fade-through timing. */
    fun contentSwap(): ContentTransform =
        fadeIn(tween(FADE_IN_DURATION, delayMillis = FADE_OUT_DURATION, easing = DecelerateEasing))
            .togetherWith(fadeOut(tween(FADE_OUT_DURATION, easing = AccelerateEasing)))

    // Plain full-duration crossfade for container-transform destinations: the
    // shared element carries the motion, so the screens themselves only fade
    // (a slide or scale would fight the bounds morph).
    fun containerTransformEnter(): EnterTransition =
        fadeIn(tween(DURATION, easing = DecelerateEasing))

    fun containerTransformExit(): ExitTransition =
        fadeOut(tween(DURATION, easing = AccelerateEasing))
}

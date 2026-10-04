package com.strings.app.util

import android.os.SystemClock

/**
 * Coordinates the app lock between screens that deliberately leave the activity and the
 * activity's relock-on-stop rule. Launching a system picker (SAF export/import) stops
 * [com.strings.app.MainActivity] exactly like switching apps does; without this the user
 * came back from choosing a file to a lock screen and the picker result was lost.
 *
 * A screen calls [expectTrustedActivityResult] right before launching such an intent; the
 * activity then skips the relock for that one stop, but only if the app returns within
 * [TRUSTED_LAUNCH_MAX_MS] -- a picker left open for a long time still relocks.
 */
class AppLockController {
    @Volatile
    private var trustedLaunchArmed: Boolean = false

    @Volatile
    private var trustedStopAt: Long = NO_TRUSTED_STOP

    fun expectTrustedActivityResult() {
        trustedLaunchArmed = true
    }

    /**
     * Called from `onStop`. Returns true when this stop was caused by a trusted launch and
     * the lock should be kept open for now.
     */
    fun consumeTrustedStop(): Boolean {
        val trusted: Boolean = trustedLaunchArmed
        trustedLaunchArmed = false
        trustedStopAt = if (trusted) SystemClock.elapsedRealtime() else NO_TRUSTED_STOP
        return trusted
    }

    /**
     * Called from `onStart`. Returns true when a trusted stop kept the app unlocked but the
     * user stayed away too long, so the activity should relock after all.
     */
    fun shouldRelockAfterTrustedStop(): Boolean {
        val stoppedAt: Long = trustedStopAt
        trustedStopAt = NO_TRUSTED_STOP
        if (stoppedAt == NO_TRUSTED_STOP) return false
        return SystemClock.elapsedRealtime() - stoppedAt > TRUSTED_LAUNCH_MAX_MS
    }

    private companion object {
        const val NO_TRUSTED_STOP: Long = -1L
        const val TRUSTED_LAUNCH_MAX_MS: Long = 5L * 60 * 1000
    }
}

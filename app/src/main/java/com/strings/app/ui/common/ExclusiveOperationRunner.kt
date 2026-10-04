package com.strings.app.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs long, user-triggered data operations (backup import/export, recategorization,
 * clearing finance data) from a ViewModel so they survive the screen's composition and are
 * never silently cancelled half-way: the work runs on IO under [NonCancellable], one at a
 * time ([isBusy] gates the UI), and the outcome is delivered as a one-shot [messages] event
 * for a snackbar.
 */
class ExclusiveOperationRunner(private val scope: CoroutineScope) {
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Returns false (and does nothing) when another operation is still running. */
    fun run(onFailure: (Exception) -> String, block: suspend () -> String): Boolean {
        if (!_isBusy.compareAndSet(expect = false, update = true)) return false
        scope.launch {
            val message: String = try {
                withContext(Dispatchers.IO + NonCancellable) { block() }
            } catch (e: Exception) {
                onFailure(e)
            } finally {
                _isBusy.value = false
            }
            _messages.tryEmit(message)
        }
        return true
    }
}

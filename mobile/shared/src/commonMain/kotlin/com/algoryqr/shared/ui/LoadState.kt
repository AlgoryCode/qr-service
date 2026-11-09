package com.algoryqr.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.algoryqr.shared.network.safeCall
import com.algoryqr.shared.network.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Remote data for one screen; [data] is kept while a refresh runs so lists don't flash empty. */
data class LoadState<T>(
    val data: T? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** Last failed action (confirm, close bill…), shown as a banner without hiding the data. */
    val actionError: String? = null,
    val busy: Boolean = false,
)

/**
 * Base for list screens: loads with [fetch], optionally polls every [pollMillis] (the backend has no push
 * channel for staff yet), and runs actions that reload the list when they succeed.
 */
abstract class LoadViewModel<T>(private val pollMillis: Long? = null) : ViewModel() {

    private val _state = MutableStateFlow(LoadState<T>())
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()
    private var pollJob: Job? = null

    protected abstract suspend fun fetch(): T

    /** Call from `init` of subclasses once their constructor properties are set. */
    protected fun start() {
        refresh()
        if (pollMillis != null) {
            pollJob?.cancel()
            pollJob = viewModelScope.launch {
                while (isActive) {
                    delay(pollMillis)
                    load(silent = true)
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(silent = false) }
    }

    private suspend fun load(silent: Boolean) {
        if (!silent) _state.update { it.copy(loading = true, error = null) }
        safeCall { fetch() }
            .onSuccess { data -> _state.update { it.copy(data = data, loading = false, error = null) } }
            .onFailure { error ->
                // A failed background poll keeps the last good data on screen.
                if (!silent || _state.value.data == null) {
                    _state.update { it.copy(loading = false, error = error.userMessage()) }
                }
            }
    }

    protected fun runAction(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, actionError = null) }
            safeCall { block() }
                .onSuccess {
                    _state.update { it.copy(busy = false) }
                    onSuccess()
                    load(silent = true)
                }
                .onFailure { error -> _state.update { it.copy(busy = false, actionError = error.userMessage()) } }
        }
    }

    /** For actions that run outside [runAction], e.g. on a longer-lived scope. */
    protected fun setActionError(message: String) = _state.update { it.copy(actionError = message) }

    fun dismissActionError() = _state.update { it.copy(actionError = null) }
}

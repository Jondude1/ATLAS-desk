package com.jondude1.atlasdesk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jondude1.atlasdesk.data.DeckRepository
import com.jondude1.atlasdesk.data.DeckState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DeckUiState(
    val deck: DeckState? = null,
    val refreshing: Boolean = false,
    /** Wall-clock of the last successful fetch; null until the first one. */
    val lastSuccessAtMs: Long? = null,
    /**
     * Wall-clock of the first failed fetch in the current outage. While
     * non-null the UI shows last-known values as stale — never blanks,
     * never zeros.
     */
    val staleSinceMs: Long? = null,
    val endpointLabel: String? = null,
)

class DeckViewModel(
    private val repo: DeckRepository = DeckRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(DeckUiState())
    val state: StateFlow<DeckUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                fetchOnce()
                delay(POLL_MS)
            }
        }
    }

    /** Pull-to-refresh: fetch now; the poll loop's cadence is unaffected. */
    fun refresh() {
        viewModelScope.launch { fetchOnce() }
    }

    private suspend fun fetchOnce() {
        _state.update { it.copy(refreshing = true) }
        try {
            val result = repo.fetchDeck()
            _state.update {
                it.copy(
                    deck = result.deck,
                    refreshing = false,
                    lastSuccessAtMs = System.currentTimeMillis(),
                    staleSinceMs = null,
                    endpointLabel = result.endpointLabel,
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    refreshing = false,
                    // First failure starts the stale clock; later failures
                    // in the same outage keep the original timestamp.
                    staleSinceMs = it.staleSinceMs ?: System.currentTimeMillis(),
                )
            }
        }
    }

    companion object {
        const val POLL_MS = 30_000L
    }
}

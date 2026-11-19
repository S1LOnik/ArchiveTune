/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.LyricsProviderTestResult
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.paxsenix.models.PaxsenixStats
import javax.inject.Inject

sealed interface PaxsenixStatsState {
    data object Loading : PaxsenixStatsState

    data class Success(
        val stats: PaxsenixStats,
    ) : PaxsenixStatsState

    data object Error : PaxsenixStatsState
}

// [FIX-ZCODE] donor parity: state for the "test all lyrics providers" action in LyricsProvidersSettings
sealed interface LyricsTestState {
    data object Idle : LyricsTestState

    data object Loading : LyricsTestState

    data class Done(
        val results: List<LyricsProviderTestResult>,
    ) : LyricsTestState
}

@HiltViewModel
class ContentSettingsViewModel
    @Inject
    constructor(
        private val lyricsHelper: LyricsHelper,
        private val database: MusicDatabase,
    ) : ViewModel() {
        private val _paxsenixStatsState = MutableStateFlow<PaxsenixStatsState>(PaxsenixStatsState.Loading)
        val paxsenixStatsState = _paxsenixStatsState.asStateFlow()
        // [FIX-ZCODE] donor parity: lyrics provider test state for LyricsProvidersSettings
        private val _lyricsTestState = MutableStateFlow<LyricsTestState>(LyricsTestState.Idle)
        val lyricsTestState = _lyricsTestState.asStateFlow()
        private var lyricsTestJob: Job? = null
        fun fetchPaxsenixStats() {
            _paxsenixStatsState.value = PaxsenixStatsState.Loading
            viewModelScope.launch(Dispatchers.IO) {
                PaxsenixLyrics
                    .getStats()
                    .onSuccess { _paxsenixStatsState.value = PaxsenixStatsState.Success(it) }
                    .onFailure { _paxsenixStatsState.value = PaxsenixStatsState.Error }
            }
        }

        // [FIX-ZCODE] donor parity: test every enabled lyrics provider and surface results
        fun runLyricsTest() {
            lyricsTestJob?.cancel()
            _lyricsTestState.value = LyricsTestState.Loading
            lyricsTestJob =
                viewModelScope.launch(Dispatchers.IO) {
                    val results = lyricsHelper.testAllProviders()
                    _lyricsTestState.value = LyricsTestState.Done(results)
                }
        }

        fun clearLyricsCache() {
            viewModelScope.launch(Dispatchers.IO) {
                lyricsHelper.clearCache()
                database.query {
                    clearAllLyrics()
                }
            }
        }

    }

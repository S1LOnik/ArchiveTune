/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.spotify.session.SpotifySessionManager
import moe.rukamori.archivetune.spotify.session.SpotifySessionState
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class SpotifyAccountViewModel
    @Inject
    constructor(
        private val repository: SpotifyLibraryRepository,
        val sessionManager: SpotifySessionManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SpotifyAccountUiState(isLoading = true))
        val uiState: StateFlow<SpotifyAccountUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                sessionManager.sessionState.collect { state ->
                    when (state) {
                        is SpotifySessionState.Connected -> {
                            _uiState.update {
                                it.copy(
                                    isAuthenticated = true,
                                    accountName = state.username,
                                    sessionState = state,
                                    isLoading = false,
                                    errorMessage = null,
                                )
                            }
                            reloadPlaylists()
                        }
                        is SpotifySessionState.Error -> {
                            _uiState.update {
                                it.copy(
                                    sessionState = state,
                                    isLoading = false,
                                    errorMessage = state.message,
                                )
                            }
                        }
                        is SpotifySessionState.Connecting -> {
                            _uiState.update {
                                it.copy(
                                    sessionState = state,
                                    isLoading = true,
                                )
                            }
                        }
                        is SpotifySessionState.Discovering -> {
                            _uiState.update {
                                it.copy(
                                    sessionState = state,
                                    isLoading = false,
                                )
                            }
                        }
                        is SpotifySessionState.Idle -> {
                            _uiState.update {
                                it.copy(
                                    sessionState = state,
                                )
                            }
                        }
                    }
                }
            }

            viewModelScope.launch {
                sessionManager.isNativePlaybackEnabled.collect { enabled ->
                    _uiState.update { it.copy(isNativePlaybackEnabled = enabled) }
                }
            }

            viewModelScope.launch {
                sessionManager.logFlow.collect { logs ->
                    _uiState.update { it.copy(logs = logs) }
                }
            }

            restoreSession()
        }

        fun restoreSession() {
            viewModelScope.launch(Dispatchers.IO) {
                val restored = sessionManager.tryRestoreSavedSession()
                runCatching { repository.restoreSession() }
                    .onSuccess { session ->
                        _uiState.update {
                            it.copy(
                                isAuthenticated = it.isAuthenticated || session.isAuthenticated || restored,
                                accountName = if (it.accountName.isNotBlank()) it.accountName else session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                                isLoading = false,
                            )
                        }
                        if (session.isAuthenticated || restored) reloadPlaylists()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isAuthenticated = restored,
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun startOAuthBrowserLogin(launcherContext: Context) {
            sessionManager.startOAuthBrowserLogin(launcherContext)
        }

        fun loginWithUserPass(username: String, pass: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val result = sessionManager.loginWithUserPass(username, pass)
                result.onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
                }
            }
        }

        fun startZeroConfDiscovery(deviceName: String) {
            sessionManager.startZeroConfDiscovery(deviceName)
        }

        fun stopZeroConfDiscovery() {
            sessionManager.stopZeroConfDiscovery()
        }

        fun setNativePlaybackEnabled(enabled: Boolean) {
            sessionManager.setNativePlaybackEnabled(enabled)
        }

        fun connectWithCookies(
            spDc: String,
            spKey: String,
        ) {
            if (spDc.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                runCatching { repository.connectWithCookies(spDc = spDc, spKey = spKey) }
                    .onSuccess { session ->
                        _uiState.update {
                            it.copy(
                                isAuthenticated = true,
                                accountName = session.accountName,
                                accountAvatarUrl = session.accountAvatarUrl,
                                isLoading = false,
                            )
                        }
                        reloadPlaylists()
                        sessionManager.tryRestoreSavedSession()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun reloadPlaylists() {
            if (_uiState.value.isLoading) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val playlists = repository.refreshPlaylists()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlistCount = playlists.size,
                        errorMessage = repository.errorMessage.value,
                    )
                }
            }
        }

        fun logout() {
            if (_uiState.value.isLoading) return
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                sessionManager.disconnect()
                runCatching { repository.logout() }
                    .onSuccess {
                        _uiState.value = SpotifyAccountUiState()
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        reportException(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message,
                            )
                        }
                    }
            }
        }

        fun dismissError() {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

@Immutable
data class SpotifyAccountUiState(
    val isAuthenticated: Boolean = false,
    val accountName: String = "",
    val accountAvatarUrl: String? = null,
    val playlistCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val sessionState: SpotifySessionState = SpotifySessionState.Idle,
    val isNativePlaybackEnabled: Boolean = true,
    val logs: List<String> = emptyList(),
)

package com.kdu.mc.lifelogger

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kdu.mc.lifelogger.auth.AuthRepository
import com.kdu.mc.lifelogger.data.repository.EntryRepository
import com.kdu.mc.lifelogger.sync.SyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── DataStore extension ───────────────────────────────────────────────────────
val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

// ── Preference Keys ───────────────────────────────────────────────────────────
object SyncPrefsKeys {
    val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
    val SYNC_MODE         = stringPreferencesKey("sync_mode") // "wifi", "data", "both"
}

// ── Data Models ───────────────────────────────────────────────────────────────
enum class SyncMode(val label: String) {
    WIFI("Wi-Fi Only"),
    DATA("Mobile Data Only"),
    BOTH("Wi-Fi & Mobile Data")
}

enum class NetworkType { WIFI, MOBILE_DATA, NONE }

enum class SyncStatus { IDLE, SYNCING, SUCCESS, FAILED }

data class PendingEntry(
    val id: String,
    val title: String,
    val date: String,
    val syncStatus: EntrySync = EntrySync.PENDING
)

enum class EntrySync { PENDING, SYNCING, SUCCESS, FAILED }

data class SyncUiState(
    val autoSyncEnabled: Boolean       = true,
    val syncMode: SyncMode             = SyncMode.WIFI,
    val networkType: NetworkType       = NetworkType.NONE,
    val syncStatus: SyncStatus         = SyncStatus.IDLE,
    val progress: Float                = 0f,          // 0.0 – 1.0
    val syncedCount: Int               = 0,
    val totalCount: Int                = 0,
    val pendingEntries: List<PendingEntry> = emptyList(),
    val showSuccess: Boolean           = false,
    val lastSyncedTime: String         = "Never"
)

// ── ViewModel ─────────────────────────────────────────────────────────────────
class SyncViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val entryRepository: EntryRepository,
    private val syncManager: SyncManager
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        observeNetwork()
        observeRealPendingEntries()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            context.syncDataStore.data.collect { prefs ->
                val autoSync  = prefs[SyncPrefsKeys.AUTO_SYNC_ENABLED] ?: true
                val modeStr   = prefs[SyncPrefsKeys.SYNC_MODE] ?: SyncMode.WIFI.name
                val mode      = SyncMode.valueOf(modeStr)
                _uiState.update { it.copy(autoSyncEnabled = autoSync, syncMode = mode) }
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            context.syncDataStore.edit { prefs ->
                prefs[SyncPrefsKeys.AUTO_SYNC_ENABLED] = enabled
            }
        }
    }

    fun setSyncMode(mode: SyncMode) {
        viewModelScope.launch {
            context.syncDataStore.edit { prefs ->
                prefs[SyncPrefsKeys.SYNC_MODE] = mode.name
            }
            _uiState.update { it.copy(syncMode = mode) }
        }
    }

    private fun observeNetwork() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkType()
            }
            override fun onLost(network: Network) {
                _uiState.update { it.copy(networkType = NetworkType.NONE) }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateNetworkType(caps)
            }
        })
        updateNetworkType()
    }

    private fun updateNetworkType(caps: NetworkCapabilities? = null) {
        val activeCaps = caps ?: connectivityManager
            .getNetworkCapabilities(connectivityManager.activeNetwork)

        val type = when {
            activeCaps == null -> NetworkType.NONE
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE_DATA
            else -> NetworkType.NONE
        }
        _uiState.update { it.copy(networkType = type) }
    }

    private fun observeRealPendingEntries() {
        val uid = authRepository.currentUid ?: return
        viewModelScope.launch {
            entryRepository.observeUnsyncedEntries(uid).collect { entries ->
                _uiState.update { state ->
                    // Only update the list, but preserve existing "SYNCING" status for active items
                    val newList = entries.map { entry ->
                        val existing = state.pendingEntries.find { it.id == entry.localId.toString() }
                        PendingEntry(
                            id = entry.localId.toString(),
                            title = entry.title.ifBlank { "Untitled Entry" },
                            date = formatDateTime(entry.createdAt),
                            syncStatus = existing?.syncStatus ?: EntrySync.PENDING
                        )
                    }
                    state.copy(pendingEntries = newList)
                }
            }
        }
    }

    fun canSync(): Boolean {
        val state = _uiState.value
        return when (state.syncMode) {
            SyncMode.WIFI -> state.networkType == NetworkType.WIFI
            SyncMode.DATA -> state.networkType == NetworkType.MOBILE_DATA
            SyncMode.BOTH -> state.networkType != NetworkType.NONE
        }
    }

    fun startSync() {
        if (_uiState.value.syncStatus == SyncStatus.SYNCING) return
        if (!canSync()) return

        val uid = authRepository.currentUid ?: return
        val pendingItems = _uiState.value.pendingEntries
        if (pendingItems.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    syncStatus  = SyncStatus.SYNCING,
                    progress    = 0.1f,
                    totalCount  = pendingItems.size,
                    syncedCount = 0
                )
            }

            val result = syncManager.syncAll(uid) { syncedLocalId ->
                // Update specific entry status to SUCCESS in UI
                _uiState.update { state ->
                    val updatedSyncedCount = state.syncedCount + 1
                    state.copy(
                        syncedCount = updatedSyncedCount,
                        progress = updatedSyncedCount.toFloat() / state.totalCount,
                        pendingEntries = state.pendingEntries.map {
                            if (it.id == syncedLocalId.toString()) it.copy(syncStatus = EntrySync.SUCCESS) else it
                        }
                    )
                }
            }

            if (result is SyncManager.SyncResult.Success) {
                val now = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())

                _uiState.update {
                    it.copy(
                        syncStatus    = SyncStatus.SUCCESS,
                        showSuccess   = true,
                        lastSyncedTime = now,
                        progress      = 1.0f
                    )
                }
            } else {
                _uiState.update { it.copy(syncStatus = SyncStatus.FAILED) }
            }
        }
    }

    fun retryEntry(entryId: String) {
        startSync()
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(showSuccess = false, syncStatus = SyncStatus.IDLE) }
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

class SyncViewModelFactory(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val entryRepository: EntryRepository,
    private val syncManager: SyncManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SyncViewModel(application, authRepository, entryRepository, syncManager) as T
    }
}

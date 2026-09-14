package com.versarepair.meloremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.versarepair.meloremote.data.AppPreferences
import com.versarepair.meloremote.data.MusicAssistantClient
import com.versarepair.meloremote.data.SecureTokenStore
import com.versarepair.meloremote.data.model.AuthenticationMode
import com.versarepair.meloremote.data.model.ConnectionState
import com.versarepair.meloremote.data.model.MAClientException
import com.versarepair.meloremote.data.model.MAEventEnvelope
import com.versarepair.meloremote.data.model.MAMediaImage
import com.versarepair.meloremote.data.model.MAMediaItem
import com.versarepair.meloremote.data.model.MAMediaReference
import com.versarepair.meloremote.data.model.MAPlayer
import com.versarepair.meloremote.data.model.MAPlayerMedia
import com.versarepair.meloremote.data.model.MAPlayerQueue
import com.versarepair.meloremote.data.model.MAQueueItem
import com.versarepair.meloremote.data.model.MASearchResult
import com.versarepair.meloremote.data.model.MAServerInfo
import com.versarepair.meloremote.data.model.MAUser
import com.versarepair.meloremote.data.model.QueuePlaybackOption
import com.versarepair.meloremote.data.model.RepeatMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class MeloUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isAuthenticated: Boolean = false,
    val isWorking: Boolean = false,
    val serverAddress: String = "",
    val username: String = "",
    val password: String = "",
    val longLivedToken: String = "",
    val authMode: AuthenticationMode = AuthenticationMode.TOKEN,
    val serverInfo: MAServerInfo? = null,
    val currentUser: MAUser? = null,
    val players: List<MAPlayer> = emptyList(),
    val queues: List<MAPlayerQueue> = emptyList(),
    val queueItems: List<MAQueueItem> = emptyList(),
    val playlists: List<MAMediaItem> = emptyList(),
    val searchResult: MASearchResult = MASearchResult.Empty,
    val selectedPlayerId: String? = null,
    val errorMessage: String? = null,
) {
    val visiblePlayers: List<MAPlayer>
        get() = players.filterNot { it.isHidden }.sortedWith(
            compareByDescending<MAPlayer> { it.isAvailable }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )

    val activePlayer: MAPlayer?
        get() = selectedPlayerId?.let { selected -> players.firstOrNull { it.playerId == selected } }
            ?: visiblePlayers.firstOrNull { it.isAvailable }
            ?: visiblePlayers.firstOrNull()

    val activeQueue: MAPlayerQueue?
        get() {
            val player = activePlayer ?: return queues.firstOrNull()
            player.activeSource?.let { source ->
                queues.firstOrNull { it.queueId == source }?.let { return it }
            }
            queues.firstOrNull { it.queueId == player.playerId }?.let { return it }
            return queues.firstOrNull { it.active == true } ?: queues.firstOrNull()
        }

    val nowPlayingTitle: String
        get() = activeQueue?.currentItem?.mediaItem?.name
            ?: activeQueue?.currentItem?.name
            ?: activePlayer?.currentMedia?.title
            ?: "Nothing playing"

    val nowPlayingSubtitle: String
        get() {
            val artists = activeQueue?.currentItem?.mediaItem?.artists
                ?.mapNotNull { it.name }?.joinToString(", ").orEmpty()
            if (artists.isNotEmpty()) return artists
            return activePlayer?.currentMedia?.artist
                ?: activeQueue?.currentItem?.mediaItem?.album?.name
                ?: "Choose something from your library"
        }

    val nowPlayingDuration: Double
        get() = activeQueue?.duration?.takeIf { it > 0 }
            ?: activePlayer?.currentMedia?.duration
            ?: 0.0
}

class MeloRemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val gson = Gson()
    private val client = MusicAssistantClient(gson)
    private val preferences = AppPreferences(application)
    private val tokenStore = SecureTokenStore(application)
    private val queueRequestGeneration = AtomicInteger(0)
    private var reconnectJob: Job? = null

    private val _uiState = MutableStateFlow(
        MeloUiState(
            serverAddress = preferences.serverAddress,
            username = preferences.username,
            authMode = preferences.authenticationMode,
            selectedPlayerId = preferences.selectedPlayerId,
        ),
    )
    val uiState: StateFlow<MeloUiState> = _uiState.asStateFlow()

    init {
        client.setEventHandler { event -> viewModelScope.launch { handle(event) } }
        viewModelScope.launch { restoreSession() }
    }

    fun setServerAddress(value: String) = update { copy(serverAddress = value) }
    fun setUsername(value: String) = update { copy(username = value) }
    fun setPassword(value: String) = update { copy(password = value) }
    fun setLongLivedToken(value: String) = update { copy(longLivedToken = value) }
    fun setAuthMode(value: AuthenticationMode) = update { copy(authMode = value) }
    fun clearError() = update { copy(errorMessage = null) }

    /** Loads representative local data for debug screenshots and UI review only. */
    fun loadDemo() {
        check(com.versarepair.meloremote.BuildConfig.DEBUG)
        val artist = MAMediaReference(name = "Nova Coast")
        val album = MAMediaReference(name = "Afterglow Signals")
        val currentTrack = MAMediaItem(
            itemId = "demo-track-1",
            provider = "library",
            name = "Midnight Current",
            mediaType = "track",
            uri = "library://track/demo-1",
            favorite = true,
            duration = 246.0,
            artists = listOf(artist),
            album = album,
        )
        val nextTrack = MAMediaItem(
            itemId = "demo-track-2",
            provider = "library",
            name = "Signals at Dawn",
            mediaType = "track",
            uri = "library://track/demo-2",
            duration = 218.0,
            artists = listOf(MAMediaReference(name = "Northern Lines")),
            album = MAMediaReference(name = "Open Skies"),
        )
        val thirdTrack = MAMediaItem(
            itemId = "demo-track-3",
            provider = "library",
            name = "Golden Hour Drive",
            mediaType = "track",
            uri = "library://track/demo-3",
            duration = 194.0,
            artists = listOf(MAMediaReference(name = "Harbor Lights")),
            album = MAMediaReference(name = "Coastal Routes"),
        )
        val currentQueueItem = MAQueueItem(
            queueItemId = "demo-queue-1",
            queueId = "living-room",
            name = currentTrack.name,
            duration = currentTrack.duration,
            index = 0,
            mediaItem = currentTrack,
        )
        val nextQueueItem = MAQueueItem(
            queueItemId = "demo-queue-2",
            queueId = "living-room",
            name = nextTrack.name,
            duration = nextTrack.duration,
            index = 1,
            mediaItem = nextTrack,
        )
        val thirdQueueItem = MAQueueItem(
            queueItemId = "demo-queue-3",
            queueId = "living-room",
            name = thirdTrack.name,
            duration = thirdTrack.duration,
            index = 2,
            mediaItem = thirdTrack,
        )
        val livingRoom = MAPlayer(
            playerId = "living-room",
            displayName = "Living Room",
            provider = "sonos",
            available = true,
            enabled = true,
            playbackState = "playing",
            powered = true,
            volumeLevel = 38.0,
            activeSource = "living-room",
            currentMedia = MAPlayerMedia(
                uri = currentTrack.uri,
                mediaType = "track",
                title = currentTrack.name,
                artist = artist.name,
                album = album.name,
                duration = currentTrack.duration,
                queueId = "living-room",
                queueItemId = currentQueueItem.queueItemId,
            ),
        )
        val kitchen = MAPlayer(
            playerId = "kitchen",
            displayName = "Kitchen",
            provider = "airplay",
            available = true,
            enabled = true,
            playbackState = "idle",
            powered = true,
            volumeLevel = 24.0,
            activeSource = "kitchen",
        )
        val bedroom = MAPlayer(
            playerId = "bedroom",
            displayName = "Bedroom",
            provider = "snapcast",
            available = false,
            enabled = true,
            playbackState = "idle",
            powered = false,
            volumeLevel = 18.0,
            activeSource = "bedroom",
        )
        val queue = MAPlayerQueue(
            queueId = "living-room",
            active = true,
            displayName = "Living Room",
            available = true,
            itemCount = 3,
            shuffleEnabled = false,
            repeatMode = "all",
            currentIndex = 0,
            elapsedTime = 86.0,
            state = "playing",
            currentItem = currentQueueItem,
            nextItem = nextQueueItem,
        )
        val playlists = listOf(
            MAMediaItem(
                itemId = "101",
                provider = "library",
                name = "Evening Mix",
                mediaType = "playlist",
                uri = "library://playlist/101",
                isEditable = true,
                supportedMediaTypes = listOf("track"),
            ),
            MAMediaItem(
                itemId = "102",
                provider = "library",
                name = "Weekend Favorites",
                mediaType = "playlist",
                uri = "library://playlist/102",
                isEditable = true,
                supportedMediaTypes = listOf("track"),
            ),
            MAMediaItem(
                itemId = "103",
                provider = "library",
                name = "Focus Flow",
                mediaType = "playlist",
                uri = "library://playlist/103",
                isEditable = true,
                supportedMediaTypes = listOf("track"),
            ),
            MAMediaItem(
                itemId = "104",
                provider = "library",
                name = "Sunday Morning",
                mediaType = "playlist",
                uri = "library://playlist/104",
                isEditable = true,
                supportedMediaTypes = listOf("track"),
            ),
        )
        _uiState.value = MeloUiState(
            connectionState = ConnectionState.Connected,
            isAuthenticated = true,
            serverAddress = "https://musicassistant.local",
            serverInfo = MAServerInfo(
                serverId = "demo-server",
                serverVersion = "2.7.3",
                schemaVersion = 29,
                minSupportedSchemaVersion = 27,
                baseUrl = "https://musicassistant.local",
                name = "Home Music",
                status = "running",
            ),
            currentUser = MAUser(
                userId = "demo-user",
                username = "musiclover",
                displayName = "Home Listener",
                role = "admin",
            ),
            players = listOf(livingRoom, kitchen, bedroom),
            queues = listOf(queue),
            queueItems = listOf(currentQueueItem, nextQueueItem, thirdQueueItem),
            playlists = playlists,
            selectedPlayerId = livingRoom.playerId,
        )
    }

    fun login() {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            update {
                copy(
                    isWorking = true,
                    errorMessage = null,
                    connectionState = ConnectionState.Connecting,
                )
            }
            try {
                val snapshot = _uiState.value
                val serverUrl = MusicAssistantClient.normalizedServerUrl(snapshot.serverAddress)
                val info = client.fetchServerInfo(serverUrl)
                if (info.schemaVersion < info.minSupportedSchemaVersion) {
                    throw MAClientException.IncompatibleServer
                }

                val (token, loginUser) = when (snapshot.authMode) {
                    AuthenticationMode.TOKEN -> {
                        val value = snapshot.longLivedToken.trim()
                        if (value.isEmpty()) {
                            throw MAClientException.AuthenticationFailed("Enter a long-lived token.")
                        }
                        value to null
                    }
                    AuthenticationMode.PASSWORD -> {
                        val login = client.login(serverUrl, snapshot.username.trim(), snapshot.password)
                        val value = login.resolvedAccessToken
                            ?: throw MAClientException.AuthenticationFailed(
                                "Music Assistant did not return a login token.",
                            )
                        value to login.user
                    }
                }

                client.connect(serverUrl, token)
                val storedToken = if (snapshot.authMode == AuthenticationMode.PASSWORD) {
                    runCatching {
                        client.sendCommand(
                            "auth/token/create",
                            mapOf("name" to gson.toJsonTree("MeloRemote")),
                        ).asString
                    }.getOrDefault(token)
                } else {
                    token
                }
                tokenStore.save(storedToken)
                preferences.serverAddress = serverUrl.toString().trimEnd('/')
                preferences.username = snapshot.username
                preferences.authenticationMode = snapshot.authMode

                val user = loginUser ?: runCatching {
                    client.sendCommand<MAUser>("auth/me", type = MAUser::class.java)
                }.getOrNull()
                update {
                    copy(
                        serverAddress = preferences.serverAddress,
                        serverInfo = info,
                        currentUser = user,
                        password = "",
                        longLivedToken = "",
                        isAuthenticated = true,
                        connectionState = ConnectionState.Connected,
                    )
                }
                loadInitialState()
            } catch (error: Throwable) {
                client.disconnect()
                update {
                    copy(
                        connectionState = ConnectionState.Failed(error.userMessage()),
                        errorMessage = error.userMessage(),
                    )
                }
            } finally {
                update { copy(isWorking = false) }
            }
        }
    }

    private suspend fun restoreSession() {
        val token = tokenStore.read() ?: return
        val address = _uiState.value.serverAddress
        if (address.isBlank()) return
        update { copy(connectionState = ConnectionState.Connecting) }
        try {
            val serverUrl = MusicAssistantClient.normalizedServerUrl(address)
            val info = client.connect(serverUrl, token)
            val user = runCatching {
                client.sendCommand<MAUser>("auth/me", type = MAUser::class.java)
            }.getOrNull()
            update {
                copy(
                    serverInfo = info,
                    currentUser = user,
                    isAuthenticated = true,
                    connectionState = ConnectionState.Connected,
                )
            }
            loadInitialState()
        } catch (error: Throwable) {
            client.disconnect()
            update {
                copy(
                    connectionState = ConnectionState.Failed(error.userMessage()),
                    errorMessage = "Couldn’t reach Music Assistant. Your saved login is still available.",
                )
            }
        }
    }

    fun reconnectIfNeeded() {
        val state = _uiState.value
        if (!state.isAuthenticated || state.connectionState == ConnectionState.Connected) return
        val token = tokenStore.read() ?: return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val delays = listOf(0L, 1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
            var attempt = 0
            while (_uiState.value.isAuthenticated) {
                delay(delays[attempt.coerceAtMost(delays.lastIndex)])
                try {
                    update { copy(connectionState = ConnectionState.Connecting) }
                    val serverUrl = MusicAssistantClient.normalizedServerUrl(_uiState.value.serverAddress)
                    val info = client.connect(serverUrl, token)
                    update { copy(serverInfo = info, connectionState = ConnectionState.Connected) }
                    loadInitialState()
                    return@launch
                } catch (error: Throwable) {
                    update { copy(connectionState = ConnectionState.Failed(error.userMessage())) }
                    attempt++
                }
            }
        }
    }

    fun logout() {
        reconnectJob?.cancel()
        reconnectJob = null
        tokenStore.delete()
        client.disconnect()
        update {
            copy(
                isAuthenticated = false,
                connectionState = ConnectionState.Disconnected,
                currentUser = null,
                players = emptyList(),
                queues = emptyList(),
                queueItems = emptyList(),
                playlists = emptyList(),
                searchResult = MASearchResult.Empty,
            )
        }
    }

    fun refresh(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = runCatching { loadInitialState() }
            result.exceptionOrNull()?.let { showError(it) }
            onComplete?.invoke(result.isSuccess)
        }
    }

    fun selectPlayer(player: MAPlayer) {
        preferences.selectedPlayerId = player.playerId
        update { copy(selectedPlayerId = player.playerId, queueItems = emptyList()) }
        viewModelScope.launch { loadQueueItems() }
    }

    fun playPause() = sendPlayerCommand("play_pause")
    fun next() = sendPlayerCommand("next")
    fun previous() = sendPlayerCommand("previous")

    fun seek(position: Double) {
        val playerId = _uiState.value.activePlayer?.playerId ?: return
        runCommand {
            client.sendCommand(
                "players/cmd/seek",
                mapOf("player_id" to json(playerId), "position" to json(position)),
            )
        }
    }

    fun setVolume(volume: Double) {
        val player = _uiState.value.activePlayer ?: return
        update {
            copy(players = players.map {
                if (it.playerId != player.playerId) it
                else if (it.groupVolume != null) it.copy(groupVolume = volume)
                else it.copy(volumeLevel = volume)
            })
        }
        val command = if (player.groupVolume != null) "players/cmd/group_volume" else "players/cmd/volume_set"
        runCommand {
            client.sendCommand(
                command,
                mapOf("player_id" to json(player.playerId), "volume_level" to json(volume)),
            )
        }
    }

    fun toggleMute() {
        val player = _uiState.value.activePlayer ?: return
        val command = if (player.groupVolumeMuted != null) {
            "players/cmd/group_volume_mute"
        } else {
            "players/cmd/volume_mute"
        }
        runCommand {
            client.sendCommand(
                command,
                mapOf("player_id" to json(player.playerId), "muted" to json(!player.isMuted)),
            )
        }
    }

    fun toggleShuffle() {
        val queue = _uiState.value.activeQueue ?: return
        val enabled = queue.shuffleEnabled != true
        update {
            copy(queues = queues.map { if (it.queueId == queue.queueId) it.copy(shuffleEnabled = enabled) else it })
        }
        runCommand {
            client.sendCommand(
                "player_queues/shuffle",
                mapOf("queue_id" to json(queue.queueId), "shuffle_enabled" to json(enabled)),
            )
        }
    }

    fun cycleRepeatMode() {
        val queue = _uiState.value.activeQueue ?: return
        val next = RepeatMode.fromWire(queue.repeatMode).next()
        update {
            copy(queues = queues.map { if (it.queueId == queue.queueId) it.copy(repeatMode = next.wireValue) else it })
        }
        runCommand {
            client.sendCommand(
                "player_queues/repeat",
                mapOf("queue_id" to json(queue.queueId), "repeat_mode" to json(next.wireValue)),
            )
        }
    }

    fun play(
        item: MAMediaItem,
        option: QueuePlaybackOption = QueuePlaybackOption.REPLACE,
        radioMode: Boolean = false,
    ) {
        val uri = item.uri
        if (!item.canPlay || uri == null) {
            update { copy(errorMessage = "This item cannot be played.") }
            return
        }
        val queueId = _uiState.value.activeQueue?.queueId ?: _uiState.value.activePlayer?.playerId
        if (queueId == null) {
            update { copy(errorMessage = "Choose an available speaker before starting playback.") }
            return
        }
        runCommand {
            client.sendCommand(
                "player_queues/play_media",
                mapOf(
                    "queue_id" to json(queueId),
                    "media" to gson.toJsonTree(listOf(uri)),
                    "option" to json(option.wireValue),
                    "radio_mode" to json(radioMode),
                ),
            )
            refreshActiveQueueFromServer()
        }
    }

    fun startRadio(item: MAMediaItem) {
        if (!item.canStartRadio) {
            update { copy(errorMessage = "Music Assistant cannot start radio from this item.") }
            return
        }
        play(item, radioMode = true)
    }

    fun toggleFavorite(item: MAMediaItem) {
        val mediaType = item.mediaType
        if (!item.canBeFavorited || mediaType == null) {
            update { copy(errorMessage = "This item cannot be favorited.") }
            return
        }
        runCommand {
            if (item.favorite != true) {
                client.sendCommand(
                    "music/favorites/add_item",
                    mapOf("item" to json(item.uri ?: throw MAClientException.InvalidResponse)),
                )
            } else {
                client.sendCommand(
                    "music/favorites/remove_item",
                    mapOf(
                        "library_item_id" to json(item.itemId ?: throw MAClientException.InvalidResponse),
                        "media_type" to json(mediaType),
                    ),
                )
            }
            loadInitialState()
        }
    }

    fun toggleLibraryMembership(item: MAMediaItem) {
        val mediaType = item.mediaType
        if (!item.canChangeLibraryMembership || mediaType == null) {
            update { copy(errorMessage = "This item cannot be changed in the library.") }
            return
        }
        runCommand {
            if (item.isInLibrary) {
                client.sendCommand(
                    "music/library/remove_item",
                    mapOf(
                        "library_item_id" to json(item.itemId ?: throw MAClientException.InvalidResponse),
                        "media_type" to json(mediaType),
                    ),
                )
            } else {
                client.sendCommand(
                    "music/library/add_item",
                    mapOf("item" to json(item.uri ?: throw MAClientException.InvalidResponse)),
                )
            }
            loadInitialState()
        }
    }

    fun playlistTargets(
        item: MAMediaItem,
        playlists: List<MAMediaItem> = _uiState.value.playlists,
    ): List<MAMediaItem> =
        if (!item.canBeAddedToPlaylist) emptyList()
        else playlists
            .filter { it.id != item.id && it.canReceive(item) }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }

    fun addToPlaylist(item: MAMediaItem, playlist: MAMediaItem) {
        val playlistId = playlist.itemId?.toIntOrNull()
        val uri = item.uri
        if (playlistId == null || uri == null) {
            update { copy(errorMessage = "This item cannot be added to a playlist.") }
            return
        }
        runCommand {
            client.sendCommand(
                "music/playlists/add_playlist_tracks",
                mapOf("db_playlist_id" to json(playlistId), "uris" to gson.toJsonTree(listOf(uri))),
            )
        }
    }

    fun playQueueItem(item: MAQueueItem) {
        val queueId = _uiState.value.activeQueue?.queueId ?: return
        runCommand {
            client.sendCommand(
                "player_queues/play_index",
                mapOf("queue_id" to json(queueId), "index" to json(item.queueItemId)),
            )
            refreshActiveQueueFromServer()
        }
    }

    fun removeQueueItem(item: MAQueueItem) {
        val queueId = _uiState.value.activeQueue?.queueId ?: return
        runCommand {
            client.sendCommand(
                "player_queues/delete_item",
                mapOf("queue_id" to json(queueId), "item_id_or_index" to json(item.queueItemId)),
            )
            refreshActiveQueueFromServer()
        }
    }

    fun clearQueue() {
        val queueId = _uiState.value.activeQueue?.queueId ?: return
        runCommand {
            client.sendCommand("player_queues/clear", mapOf("queue_id" to json(queueId)))
            refreshActiveQueueFromServer()
        }
    }

    suspend fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            update { copy(searchResult = MASearchResult.Empty) }
            return
        }
        runCatching {
            client.sendCommand<MASearchResult>(
                "music/search",
                mapOf(
                    "search_query" to json(trimmed.replace('-', ' ')),
                    "media_types" to gson.toJsonTree(
                        listOf("track", "album", "artist", "playlist", "radio", "podcast", "audiobook"),
                    ),
                    "limit" to json(20),
                    "library_only" to json(true),
                ),
                MASearchResult::class.java,
            )
        }.onSuccess { result -> update { copy(searchResult = result) } }
            .onFailure(::showError)
    }

    suspend fun playlistTracks(playlist: MAMediaItem): List<MAMediaItem> {
        val itemId = playlist.itemId ?: throw MAClientException.InvalidResponse
        val provider = playlist.provider ?: throw MAClientException.InvalidResponse
        return client.sendCommand(
            "music/playlists/playlist_tracks",
            mapOf(
                "item_id" to json(itemId),
                "provider_instance_id_or_domain" to json(provider),
                "force_refresh" to json(false),
            ),
            mediaItemListType,
        )
    }

    fun imageUrl(item: MAMediaItem, size: Int = 400): String? =
        item.preferredImage?.let { imageUrl(it, size) }

    fun imageUrl(item: MAQueueItem, size: Int = 400): String? =
        item.preferredImage?.let { imageUrl(it, size) }

    fun artworkUrl(): String? {
        val state = _uiState.value
        state.activeQueue?.currentItem?.preferredImage?.let { return imageUrl(it, 1024) }
        return state.activePlayer?.currentMedia?.imageUrl?.let(::rebaseServerUrl)
    }

    fun imageUrl(image: MAMediaImage, size: Int = 400): String? {
        val path = image.path ?: return null
        val serverUrl = runCatching {
            MusicAssistantClient.normalizedServerUrl(_uiState.value.serverAddress)
        }.getOrNull()
        val proxySize = normalizedImageProxySize(size)
        if (image.proxyId != null && serverUrl != null) {
            return serverUrl.newBuilder()
                .addPathSegment("imageproxy")
                .addPathSegment(image.proxyId)
                .addQueryParameter("size", proxySize.toString())
                .addQueryParameter("fmt", "jpeg")
                .build().toString()
        }
        if (image.remotelyAccessible == true && path.toHttpUrlOrNull() != null) return path
        if (path.contains("/imageproxy") || path.startsWith("imageproxy/")) return rebaseServerUrl(path)
        if (serverUrl == null || image.provider == null) return null
        return serverUrl.newBuilder()
            .addPathSegment("imageproxy")
            .addQueryParameter("path", path)
            .addQueryParameter("provider", image.provider)
            .addQueryParameter("size", proxySize.toString())
            .addQueryParameter("fmt", "jpeg")
            .addQueryParameter("checksum", "")
            .build().toString()
    }

    private suspend fun loadInitialState() {
        val playersDeferred = viewModelScope.async {
            client.sendCommand<List<MAPlayer>>("players/all", type = playerListType)
        }
        val queuesDeferred = viewModelScope.async {
            client.sendCommand<List<MAPlayerQueue>>("player_queues/all", type = queueListType)
        }
        val playlistsDeferred = viewModelScope.async {
            client.sendCommand<List<MAMediaItem>>(
                "music/playlists/library_items",
                mapOf("limit" to json(100), "offset" to json(0), "order_by" to json("name")),
                mediaItemListType,
            )
        }
        val players = playersDeferred.await()
        val queues = queuesDeferred.await()
        val playlists = playlistsDeferred.await()
        update { copy(players = players, queues = queues, playlists = playlists) }
        if (_uiState.value.activePlayer == null) {
            _uiState.value.visiblePlayers.firstOrNull()?.let { player ->
                preferences.selectedPlayerId = player.playerId
                update { copy(selectedPlayerId = player.playerId) }
            }
        }
        loadQueueItems()
    }

    private suspend fun refreshActiveQueueFromServer() {
        val playersDeferred = viewModelScope.async {
            client.sendCommand<List<MAPlayer>>("players/all", type = playerListType)
        }
        val queuesDeferred = viewModelScope.async {
            client.sendCommand<List<MAPlayerQueue>>("player_queues/all", type = queueListType)
        }
        update { copy(players = playersDeferred.await(), queues = queuesDeferred.await()) }
        loadQueueItems()
    }

    private suspend fun loadQueueItems(queueId: String? = null) {
        val targetQueueId = queueId ?: _uiState.value.activeQueue?.queueId
        if (targetQueueId == null) {
            queueRequestGeneration.incrementAndGet()
            update { copy(queueItems = emptyList()) }
            return
        }
        val generation = queueRequestGeneration.incrementAndGet()
        runCatching {
            client.sendCommand<List<MAQueueItem>>(
                "player_queues/items",
                mapOf("queue_id" to json(targetQueueId), "limit" to json(250), "offset" to json(0)),
                queueItemListType,
            )
        }.onSuccess { items ->
            if (generation == queueRequestGeneration.get() && _uiState.value.activeQueue?.queueId == targetQueueId) {
                update { copy(queueItems = items) }
            }
        }.onFailure { error ->
            if (generation == queueRequestGeneration.get() && _uiState.value.activeQueue?.queueId == targetQueueId) {
                showError(error)
            }
        }
    }

    private fun sendPlayerCommand(command: String) {
        val playerId = _uiState.value.activePlayer?.playerId ?: return
        runCommand {
            client.sendCommand("players/cmd/$command", mapOf("player_id" to json(playerId)))
        }
    }

    private fun runCommand(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure(::showError) }
    }

    private suspend fun handle(event: MAEventEnvelope) {
        when (event.event) {
            "player_added", "player_updated" -> event.data?.let { data ->
                runCatching { gson.fromJson(data, MAPlayer::class.java) }.getOrNull()?.let { player ->
                    update {
                        copy(players = players.toMutableList().apply {
                            val index = indexOfFirst { it.playerId == player.playerId }
                            if (index >= 0) set(index, player) else add(player)
                        })
                    }
                }
            }
            "player_removed" -> event.objectId?.let { id ->
                update { copy(players = players.filterNot { it.playerId == id }) }
            }
            "queue_added", "queue_updated" -> {
                val queue = event.data?.let { runCatching { gson.fromJson(it, MAPlayerQueue::class.java) }.getOrNull() }
                if (queue != null) replaceQueue(queue)
                else if (shouldRefreshQueueItems(eventQueueId(event))) runCatching { refreshActiveQueueFromServer() }
            }
            "queue_items_updated" -> {
                val queue = event.data?.let { runCatching { gson.fromJson(it, MAPlayerQueue::class.java) }.getOrNull() }
                if (queue != null) replaceQueue(queue)
                val queueId = queue?.queueId ?: eventQueueId(event)
                if (shouldRefreshQueueItems(queueId)) loadQueueItems(queueId)
            }
            "queue_time_updated" -> {
                val id = event.objectId
                val elapsed = event.data?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
                if (id != null && elapsed != null) {
                    update {
                        copy(queues = queues.map {
                            if (it.queueId == id) it.copy(
                                elapsedTime = elapsed,
                                elapsedTimeLastUpdated = System.currentTimeMillis() / 1_000.0,
                            ) else it
                        })
                    }
                }
            }
            "disconnected" -> {
                update { copy(connectionState = ConnectionState.Failed("Connection lost")) }
                reconnectIfNeeded()
            }
        }
    }

    private fun replaceQueue(queue: MAPlayerQueue) {
        update {
            copy(queues = queues.toMutableList().apply {
                val index = indexOfFirst { it.queueId == queue.queueId }
                if (index >= 0) set(index, queue) else add(queue)
            })
        }
    }

    private fun eventQueueId(event: MAEventEnvelope): String? {
        val data = event.data
        if (data != null && data.isJsonObject) {
            data.asJsonObject["queue_id"]?.let { if (!it.isJsonNull) return it.asString }
            data.asJsonObject["queue_id_or_player_id"]?.let { if (!it.isJsonNull) return it.asString }
        }
        if (data != null && data.isJsonPrimitive && data.asJsonPrimitive.isString) return data.asString
        return event.objectId
    }

    private fun shouldRefreshQueueItems(queueId: String?): Boolean =
        queueId == null || queueId == _uiState.value.activeQueue?.queueId

    private fun rebaseServerUrl(rawValue: String): String? {
        val raw = rawValue.toHttpUrlOrNull()
        if (raw == null) {
            val server = runCatching {
                MusicAssistantClient.normalizedServerUrl(_uiState.value.serverAddress)
            }.getOrNull() ?: return null
            return server.resolve(rawValue)?.toString()
        }
        if (!raw.encodedPath.contains("imageproxy")) return rawValue
        val server = runCatching {
            MusicAssistantClient.normalizedServerUrl(_uiState.value.serverAddress)
        }.getOrNull() ?: return rawValue
        return server.newBuilder().encodedPath(raw.encodedPath).encodedQuery(raw.encodedQuery).build().toString()
    }

    private fun showError(error: Throwable) {
        update { copy(errorMessage = error.userMessage()) }
    }

    private fun json(value: Any): JsonElement = gson.toJsonTree(value)

    private inline fun update(transform: MeloUiState.() -> MeloUiState) {
        _uiState.value = _uiState.value.transform()
    }

    override fun onCleared() {
        client.disconnect()
    }

    companion object {
        private val playerListType = object : TypeToken<List<MAPlayer>>() {}.type
        private val queueListType = object : TypeToken<List<MAPlayerQueue>>() {}.type
        private val queueItemListType = object : TypeToken<List<MAQueueItem>>() {}.type
        private val mediaItemListType = object : TypeToken<List<MAMediaItem>>() {}.type

        fun normalizedImageProxySize(requestedSize: Int): Int = when {
            requestedSize <= 0 -> 0
            requestedSize <= 80 -> 80
            requestedSize <= 160 -> 160
            requestedSize <= 256 -> 256
            requestedSize <= 512 -> 512
            else -> 1024
        }
    }
}

private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() }
    ?: "Something went wrong while communicating with Music Assistant."

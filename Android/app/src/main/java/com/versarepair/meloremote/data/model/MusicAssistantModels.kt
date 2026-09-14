package com.versarepair.meloremote.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.util.Locale
import kotlin.math.max

data class MAServerInfo(
    @SerializedName("server_id") val serverId: String,
    @SerializedName("server_version") val serverVersion: String,
    @SerializedName("schema_version") val schemaVersion: Int,
    @SerializedName("min_supported_schema_version") val minSupportedSchemaVersion: Int,
    @SerializedName("base_url") val baseUrl: String,
    val name: String? = null,
    val status: String? = null,
)

data class MAUser(
    @SerializedName("user_id") val userId: String? = null,
    val username: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    val role: String? = null,
) {
    val title: String get() = displayName ?: username ?: "Music Assistant User"
}

data class MALoginResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    val token: String? = null,
    val user: MAUser? = null,
    val success: Boolean? = null,
    val error: String? = null,
) {
    val resolvedAccessToken: String? get() = accessToken ?: token
}

enum class AuthenticationMode(val title: String) {
    TOKEN("Token"),
    PASSWORD("Account"),
}

data class MAPlayer(
    @SerializedName("player_id") val playerId: String,
    var name: String? = null,
    @SerializedName("display_name") var displayName: String? = null,
    var provider: String? = null,
    var available: Boolean? = null,
    var enabled: Boolean? = null,
    var hidden: Boolean? = null,
    @SerializedName("hide_in_ui") var hideInUi: Boolean? = null,
    @SerializedName("playback_state") var playbackState: String? = null,
    var state: String? = null,
    var powered: Boolean? = null,
    @SerializedName("volume_level") var volumeLevel: Double? = null,
    @SerializedName("volume_muted") var volumeMuted: Boolean? = null,
    @SerializedName("group_volume") var groupVolume: Double? = null,
    @SerializedName("group_volume_muted") var groupVolumeMuted: Boolean? = null,
    @SerializedName("active_source") var activeSource: String? = null,
    @SerializedName("active_group") var activeGroup: String? = null,
    @SerializedName("synced_to") var syncedTo: String? = null,
    @SerializedName("supported_features") var supportedFeatures: List<String>? = null,
    @SerializedName("current_media") var currentMedia: MAPlayerMedia? = null,
) {
    val title: String get() = displayName ?: name ?: playerId
    val isAvailable: Boolean get() = available ?: false
    val isHidden: Boolean get() = hidden == true || hideInUi == true || enabled == false
    val effectiveState: String get() = playbackState ?: state ?: "idle"
    val effectiveVolume: Double get() = groupVolume ?: volumeLevel ?: 0.0
    val isMuted: Boolean get() = groupVolumeMuted ?: volumeMuted ?: false
}

data class MAPlayerMedia(
    val uri: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val duration: Double? = null,
    @SerializedName("queue_id") val queueId: String? = null,
    @SerializedName("queue_item_id") val queueItemId: String? = null,
)

data class MAPlayerQueue(
    @SerializedName("queue_id") val queueId: String,
    var active: Boolean? = null,
    @SerializedName("display_name") var displayName: String? = null,
    var available: Boolean? = null,
    @SerializedName("items") var itemCount: Int? = null,
    @SerializedName("shuffle_enabled") var shuffleEnabled: Boolean? = null,
    @SerializedName("repeat_mode") var repeatMode: String? = null,
    @SerializedName("dont_stop_the_music_enabled") var dontStopTheMusicEnabled: Boolean? = null,
    @SerializedName("current_index") var currentIndex: Int? = null,
    @SerializedName("elapsed_time") var elapsedTime: Double? = null,
    @SerializedName("elapsed_time_last_updated") var elapsedTimeLastUpdated: Double? = null,
    @SerializedName("playback_speed") var playbackSpeed: Double? = null,
    var state: String? = null,
    @SerializedName("current_item") var currentItem: MAQueueItem? = null,
    @SerializedName("next_item") var nextItem: MAQueueItem? = null,
) {
    val isPlaying: Boolean get() = state == "playing"
    val duration: Double get() = currentItem?.duration ?: 0.0

    fun correctedElapsedTime(nowEpochSeconds: Double = System.currentTimeMillis() / 1_000.0): Double {
        if (!isPlaying || elapsedTimeLastUpdated == null) return elapsedTime ?: 0.0
        val delta = nowEpochSeconds - requireNotNull(elapsedTimeLastUpdated)
        return max(0.0, (elapsedTime ?: 0.0) + delta * (playbackSpeed ?: 1.0))
    }
}

data class MAQueueItem(
    @SerializedName("queue_item_id") val queueItemId: String,
    @SerializedName("queue_id") val queueId: String? = null,
    val name: String? = null,
    val duration: Double? = null,
    val index: Int? = null,
    val available: Boolean? = null,
    @SerializedName("media_item") val mediaItem: MAMediaItem? = null,
    val image: MAMediaImage? = null,
) {
    val title: String get() = mediaItem?.name ?: name ?: "Unknown item"
    val subtitle: String
        get() = mediaItem?.artists?.mapNotNull { it.name }?.joinToString(", ")
            ?.takeIf { it.isNotEmpty() }
            ?: mediaItem?.album?.name.orEmpty()
    val preferredImage: MAMediaImage? get() = image ?: mediaItem?.preferredImage
}

data class MAMediaItem(
    @SerializedName("item_id") val itemId: String? = null,
    val provider: String? = null,
    val name: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    val uri: String? = null,
    val favorite: Boolean? = null,
    val duration: Double? = null,
    val year: Int? = null,
    val metadata: MAMediaMetadata? = null,
    val image: MAMediaImage? = null,
    val artists: List<MAMediaReference>? = null,
    val album: MAMediaReference? = null,
    @SerializedName("is_editable") val isEditable: Boolean? = null,
    @SerializedName("is_dynamic") val isDynamic: Boolean? = null,
    @SerializedName("is_playable") val isPlayable: Boolean? = null,
    @SerializedName("supported_mediatypes") val supportedMediaTypes: List<String>? = null,
) {
    val id: String get() = uri ?: "${provider ?: "unknown"}:${itemId ?: name ?: "item"}"
    val title: String get() = name ?: "Unknown item"
    val subtitle: String
        get() {
            val artistNames = artists?.mapNotNull { it.name }?.joinToString(", ").orEmpty()
            if (artistNames.isNotEmpty()) return artistNames
            album?.name?.let { return it }
            return mediaType?.replace('_', ' ')?.titlecase(Locale.getDefault()).orEmpty()
        }
    val preferredImage: MAMediaImage?
        get() = image ?: album?.image ?: album?.metadata?.images?.firstOrNull()
            ?: metadata?.images?.firstOrNull()
    val isInLibrary: Boolean get() = provider == "library"
    val canPlay: Boolean get() = uri != null && isPlayable != false
    val canStartRadio: Boolean
        get() = uri != null && when (mediaType) {
            "track", "album", "artist" -> true
            "playlist" -> isDynamic != true
            else -> false
        }
    val canBeFavorited: Boolean
        get() = mediaType != null && if (favorite == true) itemId != null else uri != null
    val canChangeLibraryMembership: Boolean
        get() = mediaType != null && if (isInLibrary) itemId != null else uri != null
    val canBeAddedToPlaylist: Boolean
        get() = uri != null && mediaType in setOf(
            "album", "audiobook", "playlist", "podcast_episode", "radio", "track",
        )
    val canReceivePlaylistItems: Boolean
        get() = mediaType == "playlist" && itemId?.toIntOrNull() != null && isEditable != false

    fun canReceive(item: MAMediaItem): Boolean {
        if (!canReceivePlaylistItems) return false
        val supported = supportedMediaTypes.orEmpty()
        if (supported.isEmpty()) return true
        return when (item.mediaType) {
            "album", "playlist" -> "track" in supported
            null -> true
            else -> item.mediaType in supported
        }
    }
}

data class MAMediaReference(
    @SerializedName("item_id") val itemId: String? = null,
    val provider: String? = null,
    val name: String? = null,
    val uri: String? = null,
    val image: MAMediaImage? = null,
    val metadata: MAMediaMetadata? = null,
)

data class MAMediaMetadata(
    val description: String? = null,
    val images: List<MAMediaImage>? = null,
    val genres: List<String>? = null,
)

data class MAMediaImage(
    val type: String? = null,
    val path: String? = null,
    val provider: String? = null,
    @SerializedName("remotely_accessible") val remotelyAccessible: Boolean? = null,
    @SerializedName("proxy_id") val proxyId: String? = null,
)

data class MASearchResult(
    val artists: List<MAMediaItem>? = emptyList(),
    val albums: List<MAMediaItem>? = emptyList(),
    val tracks: List<MAMediaItem>? = emptyList(),
    val playlists: List<MAMediaItem>? = emptyList(),
    val podcasts: List<MAMediaItem>? = emptyList(),
    val audiobooks: List<MAMediaItem>? = emptyList(),
    val radio: List<MAMediaItem>? = emptyList(),
) {
    companion object {
        val Empty = MASearchResult()
    }
}

data class MAEventEnvelope(
    val event: String,
    val objectId: String?,
    val data: JsonElement?,
)

enum class QueuePlaybackOption(val wireValue: String) {
    REPLACE("replace"),
    PLAY("play"),
    NEXT("next"),
    ADD("add"),
    REPLACE_NEXT("replace_next"),
}

enum class RepeatMode(val wireValue: String) {
    OFF("off"),
    ONE("one"),
    ALL("all");

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun fromWire(value: String?): RepeatMode = entries.firstOrNull { it.wireValue == value } ?: OFF
    }
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

sealed class MAClientException(message: String) : Exception(message) {
    data object InvalidServerAddress : MAClientException("Enter a valid Music Assistant server address.")
    data object InvalidResponse : MAClientException("Music Assistant returned an unexpected response.")
    data object IncompatibleServer : MAClientException("This server API is not compatible with the app.")
    data object Disconnected : MAClientException("The connection to Music Assistant was closed.")
    class AuthenticationFailed(message: String) : MAClientException(message)
    class Api(message: String) : MAClientException(message)
}

fun Double.formattedDuration(): String {
    if (!isFinite() || this <= 0) return "0:00"
    val total = kotlin.math.round(this).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun String.titlecase(locale: Locale): String =
    split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

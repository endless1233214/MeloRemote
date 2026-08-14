import Foundation

struct MAServerInfo: Codable, Sendable {
    let serverID: String
    let serverVersion: String
    let schemaVersion: Int
    let minSupportedSchemaVersion: Int
    let baseURL: String
    let name: String?
    let status: String?

    enum CodingKeys: String, CodingKey {
        case serverID = "server_id"
        case serverVersion = "server_version"
        case schemaVersion = "schema_version"
        case minSupportedSchemaVersion = "min_supported_schema_version"
        case baseURL = "base_url"
        case name
        case status
    }
}

struct MAUser: Codable, Sendable {
    let userID: String?
    let username: String?
    let displayName: String?
    let role: String?

    enum CodingKeys: String, CodingKey {
        case userID = "user_id"
        case username
        case displayName = "display_name"
        case role
    }

    var title: String {
        displayName ?? username ?? "Music Assistant User"
    }
}

struct MALoginResponse: Codable, Sendable {
    let accessToken: String?
    let token: String?
    let user: MAUser?
    let success: Bool?
    let error: String?

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case token
        case user
        case success
        case error
    }

    var resolvedAccessToken: String? {
        accessToken ?? token
    }
}

enum MAAuthenticationMode: String, CaseIterable, Identifiable {
    case token
    case password

    var id: String { rawValue }

    var title: String {
        switch self {
        case .token:
            return "Token"
        case .password:
            return "Account"
        }
    }
}

struct MAPlayer: Codable, Identifiable, Sendable {
    let playerID: String
    var name: String?
    var displayName: String?
    var provider: String?
    var available: Bool?
    var enabled: Bool?
    var hidden: Bool?
    var hideInUI: Bool?
    var playbackState: String?
    var state: String?
    var powered: Bool?
    var volumeLevel: Double?
    var volumeMuted: Bool?
    var groupVolume: Double?
    var groupVolumeMuted: Bool?
    var activeSource: String?
    var activeGroup: String?
    var syncedTo: String?
    var supportedFeatures: [String]?
    var currentMedia: MAPlayerMedia?

    enum CodingKeys: String, CodingKey {
        case playerID = "player_id"
        case name
        case displayName = "display_name"
        case provider
        case available
        case enabled
        case hidden
        case hideInUI = "hide_in_ui"
        case playbackState = "playback_state"
        case state
        case powered
        case volumeLevel = "volume_level"
        case volumeMuted = "volume_muted"
        case groupVolume = "group_volume"
        case groupVolumeMuted = "group_volume_muted"
        case activeSource = "active_source"
        case activeGroup = "active_group"
        case syncedTo = "synced_to"
        case supportedFeatures = "supported_features"
        case currentMedia = "current_media"
    }

    var id: String { playerID }
    var title: String { displayName ?? name ?? playerID }
    var isAvailable: Bool { available ?? false }
    var isHidden: Bool { hidden == true || hideInUI == true || enabled == false }
    var effectiveState: String { playbackState ?? state ?? "idle" }
    var effectiveVolume: Double { groupVolume ?? volumeLevel ?? 0 }
    var isMuted: Bool { groupVolumeMuted ?? volumeMuted ?? false }
}

struct MAPlayerMedia: Codable, Sendable {
    let uri: String?
    let mediaType: String?
    let title: String?
    let artist: String?
    let album: String?
    let imageURL: String?
    let duration: Double?
    let queueID: String?
    let queueItemID: String?

    enum CodingKeys: String, CodingKey {
        case uri
        case mediaType = "media_type"
        case title
        case artist
        case album
        case imageURL = "image_url"
        case duration
        case queueID = "queue_id"
        case queueItemID = "queue_item_id"
    }
}

struct MAPlayerQueue: Codable, Identifiable, Sendable {
    let queueID: String
    var active: Bool?
    var displayName: String?
    var available: Bool?
    var itemCount: Int?
    var shuffleEnabled: Bool?
    var repeatMode: String?
    var dontStopTheMusicEnabled: Bool?
    var currentIndex: Int?
    var elapsedTime: Double?
    var elapsedTimeLastUpdated: Double?
    var playbackSpeed: Double?
    var state: String?
    var currentItem: MAQueueItem?
    var nextItem: MAQueueItem?

    enum CodingKeys: String, CodingKey {
        case queueID = "queue_id"
        case active
        case displayName = "display_name"
        case available
        case itemCount = "items"
        case shuffleEnabled = "shuffle_enabled"
        case repeatMode = "repeat_mode"
        case dontStopTheMusicEnabled = "dont_stop_the_music_enabled"
        case currentIndex = "current_index"
        case elapsedTime = "elapsed_time"
        case elapsedTimeLastUpdated = "elapsed_time_last_updated"
        case playbackSpeed = "playback_speed"
        case state
        case currentItem = "current_item"
        case nextItem = "next_item"
    }

    var id: String { queueID }
    var isPlaying: Bool { state == "playing" }
    var duration: Double { currentItem?.duration ?? 0 }

    var correctedElapsedTime: Double {
        guard isPlaying, let lastUpdated = elapsedTimeLastUpdated else {
            return elapsedTime ?? 0
        }
        let delta = Date().timeIntervalSince1970 - lastUpdated
        return max(0, (elapsedTime ?? 0) + delta * (playbackSpeed ?? 1))
    }
}

struct MAQueueItem: Codable, Identifiable, Sendable {
    let queueItemID: String
    let queueID: String?
    let name: String?
    let duration: Double?
    let index: Int?
    let available: Bool?
    let mediaItem: MAMediaItem?
    let image: MAMediaImage?

    enum CodingKeys: String, CodingKey {
        case queueItemID = "queue_item_id"
        case queueID = "queue_id"
        case name
        case duration
        case index
        case available
        case mediaItem = "media_item"
        case image
    }

    var id: String { queueItemID }
    var title: String { mediaItem?.name ?? name ?? "Unknown item" }
    var subtitle: String {
        mediaItem?.artists?.compactMap(\.name).joined(separator: ", ")
            ?? mediaItem?.album?.name
            ?? ""
    }
    var preferredImage: MAMediaImage? {
        image ?? mediaItem?.preferredImage
    }
}

struct MAMediaItem: Codable, Identifiable, Hashable, Sendable {
    let itemID: String?
    let provider: String?
    let name: String?
    let mediaType: String?
    let uri: String?
    let favorite: Bool?
    let duration: Double?
    let year: Int?
    let metadata: MAMediaMetadata?
    let image: MAMediaImage?
    let artists: [MAMediaReference]?
    let album: MAMediaReference?
    let isEditable: Bool?
    let isDynamic: Bool?
    let isPlayable: Bool?
    let supportedMediaTypes: [String]?

    enum CodingKeys: String, CodingKey {
        case itemID = "item_id"
        case provider
        case name
        case mediaType = "media_type"
        case uri
        case favorite
        case duration
        case year
        case metadata
        case image
        case artists
        case album
        case isEditable = "is_editable"
        case isDynamic = "is_dynamic"
        case isPlayable = "is_playable"
        case supportedMediaTypes = "supported_mediatypes"
    }

    var id: String {
        uri ?? "\(provider ?? "unknown"):\(itemID ?? name ?? "item")"
    }

    var title: String { name ?? "Unknown item" }
    var subtitle: String {
        if let artists, !artists.isEmpty {
            return artists.compactMap(\.name).joined(separator: ", ")
        }
        if let albumName = album?.name {
            return albumName
        }
        return mediaType?.replacingOccurrences(of: "_", with: " ").capitalized ?? ""
    }

    var preferredImage: MAMediaImage? {
        image ?? album?.image ?? album?.metadata?.images?.first ?? metadata?.images?.first
    }

    var isInLibrary: Bool {
        provider == "library"
    }

    var canPlay: Bool {
        uri != nil && isPlayable != false
    }

    var canStartRadio: Bool {
        guard uri != nil else { return false }
        switch mediaType {
        case "track", "album", "artist":
            return true
        case "playlist":
            return isDynamic != true
        default:
            return false
        }
    }

    var canBeFavorited: Bool {
        guard mediaType != nil else { return false }
        if favorite == true {
            return itemID != nil
        }
        return uri != nil
    }

    var canChangeLibraryMembership: Bool {
        guard mediaType != nil else { return false }
        if isInLibrary {
            return itemID != nil
        }
        return uri != nil
    }

    var canBeAddedToPlaylist: Bool {
        guard uri != nil else { return false }
        switch mediaType {
        case "album", "audiobook", "playlist", "podcast_episode", "radio", "track":
            return true
        default:
            return false
        }
    }

    var canReceivePlaylistItems: Bool {
        guard mediaType == "playlist", let itemID, Int(itemID) != nil else {
            return false
        }
        return isEditable != false
    }

    func canReceive(_ item: MAMediaItem) -> Bool {
        guard canReceivePlaylistItems else { return false }
        guard let supportedMediaTypes, !supportedMediaTypes.isEmpty else { return true }
        switch item.mediaType {
        case "album", "playlist":
            return supportedMediaTypes.contains("track")
        case let type?:
            return supportedMediaTypes.contains(type)
        default:
            return true
        }
    }
}

struct MAMediaReference: Codable, Hashable, Sendable {
    let itemID: String?
    let provider: String?
    let name: String?
    let uri: String?
    let image: MAMediaImage?
    let metadata: MAMediaMetadata?

    enum CodingKeys: String, CodingKey {
        case itemID = "item_id"
        case provider
        case name
        case uri
        case image
        case metadata
    }
}

struct MAMediaMetadata: Codable, Hashable, Sendable {
    let description: String?
    let images: [MAMediaImage]?
    let genres: [String]?
}

struct MAMediaImage: Codable, Hashable, Sendable {
    let type: String?
    let path: String?
    let provider: String?
    let remotelyAccessible: Bool?
    let proxyID: String?

    enum CodingKeys: String, CodingKey {
        case type
        case path
        case provider
        case remotelyAccessible = "remotely_accessible"
        case proxyID = "proxy_id"
    }
}

struct MASearchResult: Codable, Sendable {
    let artists: [MAMediaItem]?
    let albums: [MAMediaItem]?
    let tracks: [MAMediaItem]?
    let playlists: [MAMediaItem]?
    let podcasts: [MAMediaItem]?
    let audiobooks: [MAMediaItem]?
    let radio: [MAMediaItem]?

    static let empty = MASearchResult(
        artists: [],
        albums: [],
        tracks: [],
        playlists: [],
        podcasts: [],
        audiobooks: [],
        radio: []
    )
}

struct MAEventEnvelope: Sendable {
    let event: String
    let objectID: String?
    let data: JSONValue?
}

enum MARepeatMode: String, CaseIterable {
    case off
    case one
    case all

    var symbol: String {
        self == .one ? "repeat.1" : "repeat"
    }

    func next() -> MARepeatMode {
        switch self {
        case .off: return .all
        case .all: return .one
        case .one: return .off
        }
    }
}

enum MAConnectionState: Equatable {
    case disconnected
    case connecting
    case connected
    case failed(String)
}

enum MAClientError: LocalizedError {
    case invalidServerAddress
    case invalidResponse
    case authenticationFailed(String)
    case incompatibleServer
    case disconnected
    case api(String)

    var errorDescription: String? {
        switch self {
        case .invalidServerAddress:
            return "Enter a valid Music Assistant server address."
        case .invalidResponse:
            return "Music Assistant returned an unexpected response."
        case .authenticationFailed(let message):
            return message
        case .incompatibleServer:
            return "This server API is not compatible with the app."
        case .disconnected:
            return "The connection to Music Assistant was closed."
        case .api(let message):
            return message
        }
    }
}

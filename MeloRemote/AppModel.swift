import Foundation
import SwiftUI

enum MAQueuePlaybackOption: String, Sendable {
    case replace
    case play
    case next
    case add
    case replaceNext = "replace_next"
}

@MainActor
final class AppModel: ObservableObject {
    @Published var connectionState: MAConnectionState = .disconnected
    @Published var isAuthenticated = false
    @Published var isWorking = false
    @Published var serverAddress: String
    @Published var username: String
    @Published var password = ""
    @Published var longLivedToken = ""
    @Published var authMode: MAAuthenticationMode
    @Published var serverInfo: MAServerInfo?
    @Published var currentUser: MAUser?
    @Published var players: [MAPlayer] = []
    @Published var queues: [MAPlayerQueue] = []
    @Published var queueItems: [MAQueueItem] = []
    @Published var playlists: [MAMediaItem] = []
    @Published var searchResult = MASearchResult.empty
    @Published var selectedPlayerID: String?
    @Published var errorMessage: String?

    private let client = MusicAssistantClient()
    private static let defaultServerAddress = ""
    private let tokenAccount = "music-assistant-token"
    private let defaults = UserDefaults.standard
    private var reconnectTask: Task<Void, Never>?
    private var queueItemsRequestGeneration = 0

    private static let reconnectDelays: [Duration] = [
        .seconds(1),
        .seconds(2),
        .seconds(5),
        .seconds(10),
        .seconds(30)
    ]

    let isDemoMode: Bool

    init() {
        serverAddress = defaults.string(forKey: "serverAddress") ?? Self.defaultServerAddress
        username = defaults.string(forKey: "username") ?? ""
        authMode = MAAuthenticationMode(rawValue: defaults.string(forKey: "authMode") ?? "")
            ?? .token
        selectedPlayerID = defaults.string(forKey: "selectedPlayerID")
        isDemoMode = ProcessInfo.processInfo.arguments.contains("-demo")

        if isDemoMode {
            loadDemoData()
            return
        }

        Task { [weak self] in
            guard let self else { return }
            await client.setEventHandler { [weak self] event in
                Task { @MainActor in
                    self?.handle(event)
                }
            }
            await restoreSession()
        }
    }

    var visiblePlayers: [MAPlayer] {
        players
            .filter { !$0.isHidden }
            .sorted {
                if $0.isAvailable != $1.isAvailable {
                    return $0.isAvailable && !$1.isAvailable
                }
                return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
            }
    }

    var activePlayer: MAPlayer? {
        if let selectedPlayerID,
           let selected = players.first(where: { $0.playerID == selectedPlayerID }) {
            return selected
        }
        return visiblePlayers.first(where: \.isAvailable) ?? visiblePlayers.first
    }

    var activeQueue: MAPlayerQueue? {
        guard let player = activePlayer else { return queues.first }
        if let activeSource = player.activeSource,
           let queue = queues.first(where: { $0.queueID == activeSource }) {
            return queue
        }
        if let queue = queues.first(where: { $0.queueID == player.playerID }) {
            return queue
        }
        return queues.first(where: { $0.active == true }) ?? queues.first
    }

    var nowPlayingTitle: String {
        activeQueue?.currentItem?.mediaItem?.name
            ?? activeQueue?.currentItem?.name
            ?? activePlayer?.currentMedia?.title
            ?? "Nothing playing"
    }

    var nowPlayingSubtitle: String {
        if let artists = activeQueue?.currentItem?.mediaItem?.artists, !artists.isEmpty {
            return artists.compactMap(\.name).joined(separator: ", ")
        }
        return activePlayer?.currentMedia?.artist
            ?? activeQueue?.currentItem?.mediaItem?.album?.name
            ?? "Choose something from your library"
    }

    var nowPlayingDuration: Double {
        activeQueue?.duration ?? activePlayer?.currentMedia?.duration ?? 0
    }

    var nowPlayingMediaItem: MAMediaItem? {
        activeQueue?.currentItem?.mediaItem
    }

    var artworkURL: URL? {
        if let image = activeQueue?.currentItem?.preferredImage {
            return imageURL(for: image, size: 1024)
        }
        if let rawURL = activePlayer?.currentMedia?.imageURL {
            return rebasedServerURL(rawURL)
        }
        return nil
    }

    func login() async {
        guard !isWorking else { return }
        isWorking = true
        errorMessage = nil
        connectionState = .connecting

        do {
            let serverURL = try MusicAssistantClient.normalizedServerURL(from: serverAddress)
            let info = try await MusicAssistantClient.fetchServerInfo(at: serverURL)
            guard info.schemaVersion >= info.minSupportedSchemaVersion else {
                throw MAClientError.incompatibleServer
            }

            let token: String
            let loginUser: MAUser?
            switch authMode {
            case .token:
                let trimmedToken = longLivedToken.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmedToken.isEmpty else {
                    throw MAClientError.authenticationFailed("Enter a long-lived token.")
                }
                token = trimmedToken
                loginUser = nil
            case .password:
                let login = try await MusicAssistantClient.login(
                    at: serverURL,
                    username: username.trimmingCharacters(in: .whitespacesAndNewlines),
                    password: password
                )
                guard let accessToken = login.resolvedAccessToken else {
                    throw MAClientError.authenticationFailed("Music Assistant did not return a login token.")
                }
                token = accessToken
                loginUser = login.user
            }

            _ = try await client.connect(serverURL: serverURL, token: token)

            let storedToken: String
            switch authMode {
            case .token:
                storedToken = token
            case .password:
                do {
                    storedToken = try await client.sendCommand(
                        "auth/token/create",
                        args: ["name": .string("MeloRemote")],
                        as: String.self
                    )
                } catch {
                    storedToken = token
                }
            }

            try KeychainStore.save(storedToken, account: tokenAccount)
            defaults.set(serverURL.absoluteString, forKey: "serverAddress")
            defaults.set(username, forKey: "username")
            defaults.set(authMode.rawValue, forKey: "authMode")
            serverAddress = serverURL.absoluteString
            serverInfo = info
            if let loginUser {
                currentUser = loginUser
            } else {
                currentUser = try? await client.sendCommand("auth/me", as: MAUser.self)
            }
            password = ""
            longLivedToken = ""
            isAuthenticated = true
            connectionState = .connected
            try await loadInitialState()
        } catch {
            connectionState = .failed(error.localizedDescription)
            errorMessage = error.localizedDescription
            await client.disconnect()
        }
        isWorking = false
    }

    func restoreSession() async {
        guard let token = KeychainStore.read(account: tokenAccount) else { return }
        connectionState = .connecting

        do {
            let serverURL = try MusicAssistantClient.normalizedServerURL(from: serverAddress)
            let info = try await client.connect(serverURL: serverURL, token: token)
            serverInfo = info
            currentUser = try? await client.sendCommand("auth/me", as: MAUser.self)
            isAuthenticated = true
            connectionState = .connected
            try await loadInitialState()
        } catch {
            connectionState = .failed(error.localizedDescription)
            errorMessage = "Couldn’t reach Music Assistant. Your saved login is still available."
            await client.disconnect()
        }
    }

    func reconnectIfNeeded() {
        guard !isDemoMode,
              isAuthenticated,
              connectionState != .connected
        else {
            return
        }

        reconnectTask?.cancel()

        reconnectTask = Task { [weak self] in
            guard let self,
                  let token = KeychainStore.read(account: tokenAccount)
            else {
                return
            }

            var attempt = 0

            while !Task.isCancelled && isAuthenticated {
                if attempt > 0 {
                    let delay = Self.reconnectDelays[
                        min(
                            attempt - 1,
                            Self.reconnectDelays.count - 1
                        )
                    ]

                    do {
                        try await Task.sleep(for: delay)
                    } catch {
                        return
                    }
                }

                guard !Task.isCancelled else { return }

                do {
                    connectionState = .connecting

                    let serverURL =
                        try MusicAssistantClient.normalizedServerURL(
                            from: serverAddress
                        )

                    serverInfo = try await client.connect(
                        serverURL: serverURL,
                        token: token
                    )

                    connectionState = .connected
                    try await loadInitialState()

                    reconnectTask = nil
                    return
                } catch {
                    connectionState = .failed(
                        error.localizedDescription
                    )

                    attempt += 1
                }
            }
        }
    }

    func logout() {
        reconnectTask?.cancel()
        reconnectTask = nil

        KeychainStore.delete(account: tokenAccount)
        isAuthenticated = false
        connectionState = .disconnected
        currentUser = nil
        players = []
        queues = []
        queueItems = []
        playlists = []
        searchResult = .empty
        Task { await client.disconnect() }
    }

    func refresh() async {
        guard !isDemoMode else { return }
        do {
            try await loadInitialState()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func selectPlayer(_ player: MAPlayer) {
        selectedPlayerID = player.playerID
        defaults.set(player.playerID, forKey: "selectedPlayerID")
        queueItems = []
        Task { await loadQueueItems() }
    }

    func playPause() {
        sendPlayerCommand("play_pause")
    }

    func next() {
        sendPlayerCommand("next")
    }

    func previous() {
        sendPlayerCommand("previous")
    }

    func seek(to position: Double) {
        guard let playerID = activePlayer?.playerID else { return }
        run {
            _ = try await self.client.sendCommand(
                "players/cmd/seek",
                args: [
                    "player_id": .string(playerID),
                    "position": .number(position)
                ]
            )
        }
    }

    func setVolume(_ volume: Double) {
        guard let player = activePlayer else { return }
        updatePlayer(player.playerID) { current in
            if current.groupVolume != nil {
                current.groupVolume = volume
            } else {
                current.volumeLevel = volume
            }
        }
        let command = player.groupVolume != nil ? "players/cmd/group_volume" : "players/cmd/volume_set"
        run {
            _ = try await self.client.sendCommand(
                command,
                args: [
                    "player_id": .string(player.playerID),
                    "volume_level": .number(volume)
                ]
            )
        }
    }

    func toggleMute() {
        guard let player = activePlayer else { return }
        let muted = !player.isMuted
        let command = player.groupVolumeMuted != nil
            ? "players/cmd/group_volume_mute"
            : "players/cmd/volume_mute"
        run {
            _ = try await self.client.sendCommand(
                command,
                args: [
                    "player_id": .string(player.playerID),
                    "muted": .bool(muted)
                ]
            )
        }
    }

    func toggleShuffle() {
        guard let queue = activeQueue else { return }
        let enabled = !(queue.shuffleEnabled ?? false)
        updateQueue(queue.queueID) { $0.shuffleEnabled = enabled }
        run {
            _ = try await self.client.sendCommand(
                "player_queues/shuffle",
                args: [
                    "queue_id": .string(queue.queueID),
                    "shuffle_enabled": .bool(enabled)
                ]
            )
        }
    }

    func cycleRepeatMode() {
        guard let queue = activeQueue else { return }
        let current = MARepeatMode(rawValue: queue.repeatMode ?? "off") ?? .off
        let next = current.next()
        updateQueue(queue.queueID) { $0.repeatMode = next.rawValue }
        run {
            _ = try await self.client.sendCommand(
                "player_queues/repeat",
                args: [
                    "queue_id": .string(queue.queueID),
                    "repeat_mode": .string(next.rawValue)
                ]
            )
        }
    }

    func play(
        _ item: MAMediaItem,
        option: MAQueuePlaybackOption = .replace,
        radioMode: Bool = false
    ) {
        guard item.canPlay, let uri = item.uri else {
            errorMessage = "This item cannot be played."
            return
        }
        guard let queueID = activeQueue?.queueID ?? activePlayer?.playerID else {
            errorMessage = "Choose an available speaker before starting playback."
            return
        }
        run {
            _ = try await self.client.sendCommand(
                "player_queues/play_media",
                args: [
                    "queue_id": .string(queueID),
                    "media": .array([.string(uri)]),
                    "option": .string(option.rawValue),
                    "radio_mode": .bool(radioMode)
                ]
            )
            try await self.refreshActiveQueueFromServer()
        }
    }

    func startRadio(from item: MAMediaItem) {
        guard item.canStartRadio else {
            errorMessage = "Music Assistant cannot start radio from this item."
            return
        }
        play(item, option: .replace, radioMode: true)
    }

    func toggleFavorite(_ item: MAMediaItem) {
        guard item.canBeFavorited, let mediaType = item.mediaType else {
            errorMessage = "This item cannot be favorited."
            return
        }

        let shouldFavorite = item.favorite != true
        run {
            if shouldFavorite {
                guard let uri = item.uri else {
                    throw MAClientError.invalidResponse
                }
                _ = try await self.client.sendCommand(
                    "music/favorites/add_item",
                    args: ["item": .string(uri)]
                )
            } else {
                guard let itemID = item.itemID else {
                    throw MAClientError.invalidResponse
                }
                _ = try await self.client.sendCommand(
                    "music/favorites/remove_item",
                    args: [
                        "library_item_id": .string(itemID),
                        "media_type": .string(mediaType)
                    ]
                )
            }
            try await self.loadInitialState()
        }
    }

    func toggleLibraryMembership(_ item: MAMediaItem) {
        guard item.canChangeLibraryMembership, let mediaType = item.mediaType else {
            errorMessage = "This item cannot be changed in the library."
            return
        }

        run {
            if item.isInLibrary {
                guard let itemID = item.itemID else {
                    throw MAClientError.invalidResponse
                }
                _ = try await self.client.sendCommand(
                    "music/library/remove_item",
                    args: [
                        "library_item_id": .string(itemID),
                        "media_type": .string(mediaType)
                    ]
                )
            } else {
                guard let uri = item.uri else {
                    throw MAClientError.invalidResponse
                }
                _ = try await self.client.sendCommand(
                    "music/library/add_item",
                    args: ["item": .string(uri)]
                )
            }
            try await self.loadInitialState()
        }
    }

    func playlistTargets(for item: MAMediaItem) -> [MAMediaItem] {
        guard item.canBeAddedToPlaylist else { return [] }
        return playlists
            .filter { $0.id != item.id && $0.canReceive(item) }
            .sorted {
                $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
            }
    }

    func add(_ item: MAMediaItem, to playlist: MAMediaItem) {
        guard let rawPlaylistID = playlist.itemID,
              let playlistID = Int(rawPlaylistID),
              let uri = item.uri else {
            errorMessage = "This item cannot be added to a playlist."
            return
        }
        run {
            _ = try await self.client.sendCommand(
                "music/playlists/add_playlist_tracks",
                args: [
                    "db_playlist_id": .number(Double(playlistID)),
                    "uris": .array([.string(uri)])
                ]
            )
        }
    }

    func playQueueItem(_ item: MAQueueItem) {
        guard let queueID = activeQueue?.queueID else { return }
        run {
            _ = try await self.client.sendCommand(
                "player_queues/play_index",
                args: [
                    "queue_id": .string(queueID),
                    "index": .string(item.queueItemID)
                ]
            )
            try await self.refreshActiveQueueFromServer()
        }
    }

    func removeQueueItem(_ item: MAQueueItem) {
        guard let queueID = activeQueue?.queueID else { return }
        run {
            _ = try await self.client.sendCommand(
                "player_queues/delete_item",
                args: [
                    "queue_id": .string(queueID),
                    "item_id_or_index": .string(item.queueItemID)
                ]
            )
            try await self.refreshActiveQueueFromServer()
        }
    }

    func clearQueue() {
        guard let queueID = activeQueue?.queueID else { return }
        run {
            _ = try await self.client.sendCommand(
                "player_queues/clear",
                args: ["queue_id": .string(queueID)]
            )
            try await self.refreshActiveQueueFromServer()
        }
    }

    func search(_ query: String) async {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else {
            searchResult = .empty
            return
        }
        if isDemoMode {
            searchResult = DemoData.search
            return
        }
        do {
            searchResult = try await client.sendCommand(
                "music/search",
                args: [
                    "search_query": .string(trimmed.replacingOccurrences(of: "-", with: " ")),
                    "media_types": .array([
                        "track",
                        "album",
                        "artist",
                        "playlist",
                        "radio",
                        "podcast",
                        "audiobook"
                    ].map(JSONValue.string)),
                    "limit": .number(20),
                    "library_only": .bool(true)
                ],
                as: MASearchResult.self
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func playlistTracks(for playlist: MAMediaItem) async throws -> [MAMediaItem] {
        if isDemoMode { return DemoData.playlistTracks }
        guard let itemID = playlist.itemID, let provider = playlist.provider else {
            throw MAClientError.invalidResponse
        }
        return try await client.sendCommand(
            "music/playlists/playlist_tracks",
            args: [
                "item_id": .string(itemID),
                "provider_instance_id_or_domain": .string(provider),
                "force_refresh": .bool(false)
            ],
            as: [MAMediaItem].self
        )
    }

    func imageURL(for item: MAMediaItem, size: Int = 400) -> URL? {
        guard let image = item.preferredImage else { return nil }
        return imageURL(for: image, size: size)
    }

    func imageURL(for item: MAQueueItem, size: Int = 400) -> URL? {
        guard let image = item.preferredImage else { return nil }
        return imageURL(for: image, size: size)
    }

    func imageURL(for image: MAMediaImage, size: Int = 400) -> URL? {
        guard let path = image.path else { return nil }
        let serverURL = try? MusicAssistantClient.normalizedServerURL(from: serverAddress)
        let proxySize = Self.normalizedImageProxySize(size)
        if let proxyID = image.proxyID, let serverURL {
            return serverURL
                .appending(path: "imageproxy")
                .appending(path: proxyID)
                .appending(queryItems: [
                    URLQueryItem(name: "size", value: String(proxySize)),
                    URLQueryItem(name: "fmt", value: "jpeg")
                ])
        }
        if let directURL = directImageURL(path: path, remotelyAccessible: image.remotelyAccessible) {
            return directURL
        }
        guard let serverURL else { return nil }
        guard let provider = image.provider else { return nil }
        return serverURL.appending(path: "imageproxy").appending(queryItems: [
            URLQueryItem(name: "path", value: path),
            URLQueryItem(name: "provider", value: provider),
            URLQueryItem(name: "size", value: String(proxySize)),
            URLQueryItem(name: "fmt", value: "jpeg"),
            URLQueryItem(name: "checksum", value: "")
        ])
    }

    nonisolated static func normalizedImageProxySize(_ requestedSize: Int) -> Int {
        if requestedSize <= 0 { return 0 }
        if requestedSize <= 80 { return 80 }
        if requestedSize <= 160 { return 160 }
        if requestedSize <= 256 { return 256 }
        if requestedSize <= 512 { return 512 }
        return 1024
    }

    private func loadInitialState() async throws {
        async let fetchedPlayers: [MAPlayer] = client.sendCommand("players/all", as: [MAPlayer].self)
        async let fetchedQueues: [MAPlayerQueue] = client.sendCommand(
            "player_queues/all",
            as: [MAPlayerQueue].self
        )
        async let fetchedPlaylists: [MAMediaItem] = client.sendCommand(
            "music/playlists/library_items",
            args: [
                "limit": .number(100),
                "offset": .number(0),
                "order_by": .string("name")
            ],
            as: [MAMediaItem].self
        )

        players = try await fetchedPlayers
        queues = try await fetchedQueues
        playlists = try await fetchedPlaylists

        if activePlayer == nil, let first = visiblePlayers.first {
            selectedPlayerID = first.playerID
        }
        await loadQueueItems()
    }

    private func refreshActiveQueueFromServer() async throws {
        async let fetchedPlayers: [MAPlayer] = client.sendCommand("players/all", as: [MAPlayer].self)
        async let fetchedQueues: [MAPlayerQueue] = client.sendCommand(
            "player_queues/all",
            as: [MAPlayerQueue].self
        )

        players = try await fetchedPlayers
        queues = try await fetchedQueues
        await loadQueueItems()
    }

    private func loadQueueItems(for queueID: String? = nil) async {
        guard !isDemoMode else { return }

        guard let targetQueueID = queueID ?? activeQueue?.queueID else {
            queueItemsRequestGeneration &+= 1
            queueItems = []
            return
        }

        queueItemsRequestGeneration &+= 1
        let generation = queueItemsRequestGeneration

        do {
            let fetchedItems: [MAQueueItem] = try await client.sendCommand(
                "player_queues/items",
                args: [
                    "queue_id": .string(targetQueueID),
                    "limit": .number(250),
                    "offset": .number(0)
                ],
                as: [MAQueueItem].self
            )

            guard generation == queueItemsRequestGeneration,
                  activeQueue?.queueID == targetQueueID
            else {
                return
            }

            queueItems = fetchedItems
        } catch {
            guard generation == queueItemsRequestGeneration,
                  activeQueue?.queueID == targetQueueID
            else {
                return
            }

            errorMessage = error.localizedDescription
        }
    }

    private func sendPlayerCommand(_ command: String) {
        guard let playerID = activePlayer?.playerID else { return }
        run {
            _ = try await self.client.sendCommand(
                "players/cmd/\(command)",
                args: ["player_id": .string(playerID)]
            )
        }
    }

    private func run(_ operation: @escaping @Sendable () async throws -> Void) {
        guard !isDemoMode else { return }
        Task {
            do {
                try await operation()
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func handle(_ event: MAEventEnvelope) {
        do {
            switch event.event {
            case "player_added", "player_updated":
                if let data = event.data {
                    let player = try data.decoded(as: MAPlayer.self)
                    replace(player)
                }
            case "player_removed":
                if let id = event.objectID {
                    players.removeAll { $0.playerID == id }
                }
            case "queue_added", "queue_updated":
                if let data = event.data,
                   let queue = try? data.decoded(as: MAPlayerQueue.self) {
                    replace(queue)
                } else if shouldRefreshQueueItems(for: eventQueueID(from: event)) {
                    Task {
                        try? await refreshActiveQueueFromServer()
                    }
                }
            case "queue_items_updated":
                if let data = event.data,
                   let queue = try? data.decoded(as: MAPlayerQueue.self) {
                    replace(queue)
                    if shouldRefreshQueueItems(for: queue.queueID) {
                        Task { await loadQueueItems(for: queue.queueID) }
                    }
                } else {
                    let queueID = eventQueueID(from: event)
                    if shouldRefreshQueueItems(for: queueID) {
                        Task { await loadQueueItems(for: queueID) }
                    }
                }
            case "queue_time_updated":
                if let id = event.objectID, let elapsed = event.data?.doubleValue {
                    updateQueue(id) {
                        $0.elapsedTime = elapsed
                        $0.elapsedTimeLastUpdated = Date().timeIntervalSince1970
                    }
                }
            case "disconnected":
                connectionState = .failed("Connection lost")
                reconnectIfNeeded()
            default:
                break
            }
        } catch {
            // Ignore a malformed optional event and keep the live connection running.
        }
    }

    private func eventQueueID(from event: MAEventEnvelope) -> String? {
        event.data?["queue_id"]?.stringValue
            ?? event.data?["queue_id_or_player_id"]?.stringValue
            ?? event.data?.stringValue
            ?? event.objectID
    }

    private func shouldRefreshQueueItems(for queueID: String?) -> Bool {
        guard let queueID else { return true }
        return queueID == activeQueue?.queueID
    }

    private func replace(_ player: MAPlayer) {
        if let index = players.firstIndex(where: { $0.playerID == player.playerID }) {
            players[index] = player
        } else {
            players.append(player)
        }
    }

    private func replace(_ queue: MAPlayerQueue) {
        if let index = queues.firstIndex(where: { $0.queueID == queue.queueID }) {
            queues[index] = queue
        } else {
            queues.append(queue)
        }
    }

    private func updatePlayer(_ id: String, change: (inout MAPlayer) -> Void) {
        guard let index = players.firstIndex(where: { $0.playerID == id }) else { return }
        change(&players[index])
    }

    private func updateQueue(_ id: String, change: (inout MAPlayerQueue) -> Void) {
        guard let index = queues.firstIndex(where: { $0.queueID == id }) else { return }
        change(&queues[index])
    }

    private func directImageURL(path: String, remotelyAccessible: Bool?) -> URL? {
        if remotelyAccessible == true,
           let url = URL(string: path),
           url.scheme != nil {
            return url
        }
        guard path.contains("/imageproxy") || path.hasPrefix("imageproxy/") else {
            return nil
        }
        return rebasedServerURL(path)
    }

    private func rebasedServerURL(_ rawValue: String) -> URL? {
        guard let rawURL = URL(string: rawValue) else { return nil }
        guard rawURL.path.contains("imageproxy"),
              let serverURL = try? MusicAssistantClient.normalizedServerURL(from: serverAddress),
              var components = URLComponents(url: serverURL, resolvingAgainstBaseURL: false)
        else {
            return rawURL
        }
        components.path = rawURL.path
        components.query = rawURL.query
        return components.url
    }

    private func loadDemoData() {
        serverInfo = DemoData.serverInfo
        currentUser = DemoData.user
        players = DemoData.players
        queues = DemoData.queues
        if !queues.isEmpty {
            queues[0].elapsedTimeLastUpdated = Date().timeIntervalSince1970
        }
        queueItems = DemoData.queueItems
        playlists = DemoData.playlists
        searchResult = DemoData.search
        selectedPlayerID = DemoData.players.first?.playerID
        isAuthenticated = true
        connectionState = .connected
    }
}

private enum DemoData {
    static let serverInfo: MAServerInfo = decode(
        """
        {
          "server_id":"demo","server_version":"2.8.9","schema_version":29,
          "min_supported_schema_version":28,"base_url":"http://musicassistant.local:8095",
          "name":"Music Assistant","status":"running"
        }
        """
    )

    static let user: MAUser = decode(
        """
        {"user_id":"demo","username":"demo","display_name":"Demo User","role":"admin"}
        """
    )

    static let players: [MAPlayer] = decode(
        """
        [
          {"player_id":"speaker","display_name":"Speaker","provider":"snapcast","available":true,
           "enabled":true,"state":"playing","volume_level":38,"volume_muted":false,
           "active_source":"speaker","supported_features":["volume_set","pause","next_previous"]},
          {"player_id":"living-room","display_name":"Living Room TV","provider":"hass_players",
           "available":true,"enabled":true,"state":"idle","volume_level":22,"volume_muted":false},
          {"player_id":"mac","display_name":"Mac","provider":"airplay","available":false,
           "enabled":true,"state":"idle","volume_level":50,"volume_muted":false}
        ]
        """
    )

    static let queues: [MAPlayerQueue] = decode(
        """
        [
          {
            "queue_id":"speaker","active":true,"display_name":"Speaker","available":true,
            "items":8,"shuffle_enabled":false,"repeat_mode":"all","current_index":2,
            "elapsed_time":73,"elapsed_time_last_updated":1780860000,"state":"playing",
            "current_item":{
              "queue_id":"speaker","queue_item_id":"q3","name":"Birds of a Feather",
              "duration":210,"index":2,
              "media_item":{
                "item_id":"3","provider":"library","name":"Birds of a Feather",
                "media_type":"track","uri":"library://track/3","duration":210,
                "artists":[{"name":"Billie Eilish"}],
                "album":{"name":"HIT ME HARD AND SOFT"}
              }
            }
          }
        ]
        """
    )

    static let queueItems: [MAQueueItem] = decode(
        """
        [
          {"queue_item_id":"q1","name":"For the First Time in Forever","duration":225,"index":0,
           "media_item":{"item_id":"1","provider":"library","name":"For the First Time in Forever",
           "media_type":"track","uri":"library://track/1","artists":[{"name":"Kristen Bell, Idina Menzel"}]}},
          {"queue_item_id":"q2","name":"Good Luck, Babe!","duration":218,"index":1,
           "media_item":{"item_id":"2","provider":"library","name":"Good Luck, Babe!",
           "media_type":"track","uri":"library://track/2","artists":[{"name":"Chappell Roan"}]}},
          {"queue_item_id":"q3","name":"Birds of a Feather","duration":210,"index":2,
           "media_item":{"item_id":"3","provider":"library","name":"Birds of a Feather",
           "media_type":"track","uri":"library://track/3","artists":[{"name":"Billie Eilish"}]}},
          {"queue_item_id":"q4","name":"Pink Pony Club","duration":258,"index":3,
           "media_item":{"item_id":"4","provider":"library","name":"Pink Pony Club",
           "media_type":"track","uri":"library://track/4","artists":[{"name":"Chappell Roan"}]}}
        ]
        """
    )

    static let playlists: [MAMediaItem] = decode(
        """
        [
          {"item_id":"morning","provider":"library","name":"Morning Mix","media_type":"playlist",
           "uri":"library://playlist/morning","favorite":true},
          {"item_id":"favorites","provider":"library","name":"Favorites Mix","media_type":"playlist",
           "uri":"library://playlist/favorites","favorite":true},
          {"item_id":"weekend","provider":"library","name":"Weekend Kitchen","media_type":"playlist",
           "uri":"library://playlist/weekend","favorite":false},
          {"item_id":"quiet","provider":"library","name":"Quiet Hours","media_type":"playlist",
           "uri":"library://playlist/quiet","favorite":false}
        ]
        """
    )

    static let playlistTracks: [MAMediaItem] = decode(
        """
        [
          {"item_id":"1","provider":"library","name":"For the First Time in Forever",
           "media_type":"track","uri":"library://track/1","duration":225,
           "artists":[{"name":"Kristen Bell, Idina Menzel"}]},
          {"item_id":"2","provider":"library","name":"Good Luck, Babe!","media_type":"track",
           "uri":"library://track/2","duration":218,"artists":[{"name":"Chappell Roan"}]},
          {"item_id":"3","provider":"library","name":"Birds of a Feather","media_type":"track",
           "uri":"library://track/3","duration":210,"artists":[{"name":"Billie Eilish"}]}
        ]
        """
    )

    static let search: MASearchResult = decode(
        """
        {
          "artists":[{"item_id":"a1","provider":"library","name":"Billie Eilish",
            "media_type":"artist","uri":"library://artist/a1"}],
          "albums":[{"item_id":"al1","provider":"library","name":"HIT ME HARD AND SOFT",
            "media_type":"album","uri":"library://album/al1","artists":[{"name":"Billie Eilish"}]}],
          "tracks":[{"item_id":"3","provider":"library","name":"Birds of a Feather",
            "media_type":"track","uri":"library://track/3","duration":210,
            "artists":[{"name":"Billie Eilish"}]}],
          "playlists":[],"podcasts":[],"audiobooks":[],"radio":[]
        }
        """
    )

    private static func decode<T: Decodable>(_ json: String) -> T {
        try! JSONDecoder().decode(T.self, from: Data(json.utf8))
    }
}

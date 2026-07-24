import SwiftUI

struct LibraryView: View {
    @EnvironmentObject private var model: AppModel
    @State private var mode: Mode = .playlists
    @State private var query = ""
    @State private var addToPlaylistItem: MAMediaItem?

    private enum Mode: String, CaseIterable, Identifiable {
        case playlists = "Playlists"
        case search = "Search"
        var id: String { rawValue }
    }

    var body: some View {
        NavigationStack {
            Group {
                switch mode {
                case .playlists:
                    playlists
                case .search:
                    searchResults
                }
            }
            .navigationTitle("Library")
            .safeAreaInset(edge: .top, spacing: 0) {
                Picker("Library view", selection: $mode) {
                    ForEach(Mode.allCases) { mode in
                        Text(mode.rawValue).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.bottom, 10)
                .background(.bar)
            }
            .searchable(
                text: $query,
                isPresented: Binding(
                    get: { mode == .search },
                    set: { isPresented in
                        if isPresented {
                            mode = .search
                        } else if mode == .search {
                            query = ""
                            mode = .playlists
                        }
                    }
                ),
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Artists, albums, tracks, playlists"
            )
            .onChange(of: query) { _, newValue in
                if newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    if mode == .search {
                        mode = .playlists
                    }
                } else if mode != .search {
                    mode = .search
                }
            }
            .onChange(of: mode) { _, newValue in
                if newValue == .playlists && !query.isEmpty {
                    query = ""
                }
            }
            .task(id: query) {
                guard mode == .search else { return }
                try? await Task.sleep(for: .milliseconds(350))
                guard !Task.isCancelled else { return }
                await model.search(query)
            }
            .refreshable {
                await model.refresh()
            }
            .sheet(item: $addToPlaylistItem) { item in
                AddToPlaylistSheet(item: item)
            }
        }
    }

    private var playlists: some View {
        ScrollView {
            LazyVGrid(
                columns: [
                    GridItem(
                        .adaptive(minimum: 160, maximum: 240),
                        spacing: 18
                    )
                ],
                spacing: 22
            ) {
                ForEach(model.playlists) { playlist in
                    NavigationLink {
                        PlaylistDetailView(playlist: playlist)
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            ArtworkView(url: model.imageURL(for: playlist, size: 420))
                            Text(playlist.title)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            Text(playlist.favorite == true ? "Favorite playlist" : "Playlist")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        MediaContextActions(item: playlist) {
                            addToPlaylistItem = playlist
                        }
                    }
                }
            }
            .padding(16)
        }
        .overlay {
            if model.playlists.isEmpty {
                ContentUnavailableView(
                    "No Playlists",
                    systemImage: "music.note.list",
                    description: Text("Playlists from Music Assistant will appear here.")
                )
            }
        }
    }

    private var searchResults: some View {
        List {
            if query.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 {
                ContentUnavailableView.search(text: query)
                    .listRowBackground(Color.clear)
            } else {
                mediaSection("Tracks", items: model.searchResult.tracks ?? [])
                mediaSection("Albums", items: model.searchResult.albums ?? [])
                mediaSection("Artists", items: model.searchResult.artists ?? [])
                mediaSection("Playlists", items: model.searchResult.playlists ?? [])
                mediaSection("Radio", items: model.searchResult.radio ?? [])
                mediaSection("Podcasts", items: model.searchResult.podcasts ?? [])
                mediaSection("Audiobooks", items: model.searchResult.audiobooks ?? [])
            }
        }
        .listStyle(.plain)
    }

    @ViewBuilder
    private func mediaSection(_ title: String, items: [MAMediaItem]) -> some View {
        if !items.isEmpty {
            Section(title) {
                ForEach(items) { item in
                    Button {
                        model.play(item)
                    } label: {
                        MediaRow(item: item, trailingSymbol: "play.fill")
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        MediaContextActions(item: item) {
                            addToPlaylistItem = item
                        }
                    }
                }
            }
        }
    }
}

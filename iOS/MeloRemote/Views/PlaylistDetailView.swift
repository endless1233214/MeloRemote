import SwiftUI

struct PlaylistDetailView: View {
    @EnvironmentObject private var model: AppModel
    let playlist: MAMediaItem
    @State private var tracks: [MAMediaItem] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var addToPlaylistItem: MAMediaItem?

    var body: some View {
        List {
            Section {
                VStack(spacing: 14) {
                    ArtworkView(url: model.imageURL(for: playlist, size: 700))
                        .frame(maxWidth: 280)

                    Text(playlist.title)
                        .font(.title2.bold())
                        .multilineTextAlignment(.center)

                    HStack(spacing: 12) {
                        Button {
                            model.play(playlist)
                        } label: {
                            Text("Play")
                                .font(.headline.weight(.semibold))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(AppTheme.accent, in: Capsule())
                        }
                        .buttonStyle(.plain)

                        Menu {
                            MediaContextActions(item: playlist) {
                                addToPlaylistItem = playlist
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .frame(width: 42, height: 42)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("Playlist actions")
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .listRowBackground(Color.clear)
            }

            Section("Tracks") {
                if isLoading {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                } else if let loadError {
                    ContentUnavailableView(
                        "Unable to Load",
                        systemImage: "exclamationmark.triangle",
                        description: Text(loadError)
                    )
                } else {
                    ForEach(tracks) { track in
                        Button {
                            model.play(track)
                        } label: {
                            MediaRow(item: track, showArtwork: false)
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            MediaContextActions(item: track) {
                                addToPlaylistItem = track
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button {
                                model.play(track, option: .add)
                            } label: {
                                Label("Queue", systemImage: "text.badge.plus")
                            }
                            .tint(AppTheme.blue)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(playlist.title)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            do {
                tracks = try await model.playlistTracks(for: playlist)
            } catch {
                loadError = error.localizedDescription
            }
            isLoading = false
        }
        .sheet(item: $addToPlaylistItem) { item in
            AddToPlaylistSheet(item: item)
        }
    }
}

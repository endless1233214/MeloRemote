import SwiftUI

struct AppMark: View {
    let size: CGFloat

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.18)
                .fill(Color(uiColor: .label))
            Circle()
                .stroke(AppTheme.accent, lineWidth: size * 0.08)
                .frame(width: size * 0.58, height: size * 0.58)
            Circle()
                .fill(AppTheme.mint)
                .frame(width: size * 0.18, height: size * 0.18)
            Image(systemName: "waveform")
                .font(.system(size: size * 0.23, weight: .bold))
                .foregroundStyle(Color(uiColor: .systemBackground))
        }
        .frame(width: size, height: size)
        .shadow(color: .black.opacity(0.16), radius: 12, y: 6)
        .accessibilityHidden(true)
    }
}

struct ArtworkView: View {
    let url: URL?
    var cornerRadius: CGFloat = 7

    var body: some View {
        GeometryReader { proxy in
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                default:
                    ZStack {
                        Color(uiColor: .secondarySystemBackground)
                        Image(systemName: "music.note")
                            .font(.system(size: min(proxy.size.width, proxy.size.height) * 0.28))
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(.primary.opacity(0.08), lineWidth: 0.5)
            }
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

struct ConnectionPill: View {
    let state: MAConnectionState

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 7, height: 7)
            Text(label)
                .font(.caption.weight(.semibold))
        }
        .foregroundStyle(.secondary)
        .accessibilityLabel(label)
    }

    private var color: Color {
        switch state {
        case .connected: return AppTheme.mint
        case .connecting: return AppTheme.amber
        case .disconnected, .failed: return .red
        }
    }

    private var label: String {
        switch state {
        case .connected: return "Connected"
        case .connecting: return "Connecting"
        case .disconnected: return "Offline"
        case .failed: return "Connection lost"
        }
    }
}

struct MediaRow: View {
    @EnvironmentObject private var model: AppModel
    let item: MAMediaItem
    var showArtwork = true
    var trailingSymbol: String? = nil

    var body: some View {
        HStack(spacing: 12) {
            if showArtwork {
                ArtworkView(url: model.imageURL(for: item, size: 160), cornerRadius: 5)
                    .frame(width: 52, height: 52)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(.body.weight(.medium))
                    .lineLimit(1)
                if !item.subtitle.isEmpty {
                    Text(item.subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            if let trailingSymbol {
                Image(systemName: trailingSymbol)
                    .foregroundStyle(.secondary)
            } else if let duration = item.duration {
                Text(duration.formattedDuration)
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
        }
        .contentShape(Rectangle())
    }
}

struct MediaContextActions: View {
    @EnvironmentObject private var model: AppModel
    let item: MAMediaItem
    var includePlayback = true
    var includePlayNow = true
    var addToPlaylist: (() -> Void)?

    var body: some View {
        if includePlayback && item.canPlay {
            if includePlayNow {
                Button {
                    model.play(item, option: .replace)
                } label: {
                    Label("Play Now", systemImage: "play.fill")
                }
            }

            Button {
                model.play(item, option: .play)
            } label: {
                Label("Play After Current", systemImage: "play.square.stack")
            }

            Button {
                model.play(item, option: .next)
            } label: {
                Label("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward")
            }

            Button {
                model.play(item, option: .add)
            } label: {
                Label("Add to Queue", systemImage: "text.badge.plus")
            }
        }

        if includePlayback && item.canStartRadio {
            Button {
                model.startRadio(from: item)
            } label: {
                Label("Start Radio", systemImage: "dot.radiowaves.left.and.right")
            }
        }

        if includePlayback && (item.canPlay || item.canStartRadio) {
            Divider()
        }

        if item.canChangeLibraryMembership {
            Button(role: item.isInLibrary ? .destructive : nil) {
                model.toggleLibraryMembership(item)
            } label: {
                Label(
                    item.isInLibrary ? "Remove from Library" : "Add to Library",
                    systemImage: item.isInLibrary ? "minus.circle" : "plus.circle"
                )
            }
        }

        if item.canBeFavorited {
            Button {
                model.toggleFavorite(item)
            } label: {
                Label(
                    item.favorite == true ? "Unfavorite" : "Favorite",
                    systemImage: item.favorite == true ? "heart.slash" : "heart"
                )
            }
        }

        if item.canBeAddedToPlaylist, let addToPlaylist {
            Button {
                addToPlaylist()
            } label: {
                Label("Add to Playlist", systemImage: "music.note.list")
            }
        }
    }
}

struct AddToPlaylistSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    let item: MAMediaItem

    var body: some View {
        NavigationStack {
            List {
                ForEach(model.playlistTargets(for: item)) { playlist in
                    Button {
                        model.add(item, to: playlist)
                        dismiss()
                    } label: {
                        MediaRow(item: playlist)
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle("Add to Playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .overlay {
                if model.playlistTargets(for: item).isEmpty {
                    ContentUnavailableView(
                        "No Editable Playlists",
                        systemImage: "music.note.list",
                        description: Text("Music Assistant did not return a playlist that can accept this item.")
                    )
                }
            }
        }
    }
}

extension Double {
    var formattedDuration: String {
        guard isFinite, self > 0 else { return "0:00" }
        let total = Int(self.rounded())
        let hours = total / 3600
        let minutes = (total % 3600) / 60
        let seconds = total % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%d:%02d", minutes, seconds)
    }
}

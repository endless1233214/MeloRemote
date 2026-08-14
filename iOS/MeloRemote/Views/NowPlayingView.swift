import SwiftUI

struct NowPlayingView: View {
    @EnvironmentObject private var model: AppModel
    @State private var addToPlaylistItem: MAMediaItem?

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                playerHeader

                ArtworkView(url: model.artworkURL)
                    .frame(maxWidth: 430)
                    .shadow(color: .black.opacity(0.18), radius: 18, y: 10)

                VStack(spacing: 5) {
                    Text(model.nowPlayingTitle)
                        .font(.title2.bold())
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                    Text(model.nowPlayingSubtitle)
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)

                ProgressControl()

                transportControls

                HStack {
                    Button {
                        model.toggleShuffle()
                    } label: {
                        Image(systemName: "shuffle")
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(model.activeQueue?.shuffleEnabled == true ? AppTheme.accent : .secondary)
                    .accessibilityLabel("Shuffle")

                    Spacer()

                    Button {
                        model.cycleRepeatMode()
                    } label: {
                        Image(systemName: repeatMode.symbol)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(repeatMode == .off ? .secondary : AppTheme.accent)
                    .accessibilityLabel("Repeat")
                }
                .font(.title3.weight(.semibold))
                .padding(.horizontal, 24)
                .frame(maxWidth: 430)

                VolumeControl()
                    .frame(maxWidth: 430)
            }
            .padding(.horizontal, 22)
            .padding(.top, 10)
            .padding(.bottom, 30)
            .frame(maxWidth: 620)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemBackground))
        .navigationTitle("Now Playing")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if let item = model.nowPlayingMediaItem {
                    Menu {
                        MediaContextActions(item: item, includePlayNow: false) {
                            addToPlaylistItem = item
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                    .accessibilityLabel("Track actions")
                }
                PlayerMenu()
            }
        }
        .refreshable {
            await model.refresh()
        }
        .sheet(item: $addToPlaylistItem) { item in
            AddToPlaylistSheet(item: item)
        }
    }

    private var playerHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(model.activePlayer?.title ?? "No speaker")
                    .font(.subheadline.weight(.semibold))
                Text(model.activePlayer?.isAvailable == true ? "Available" : "Unavailable")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()
            ConnectionPill(state: model.connectionState)
        }
    }

    private var transportControls: some View {
        HStack(spacing: 38) {
            Button {
                model.previous()
            } label: {
                Image(systemName: "backward.fill")
                    .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Previous")

            Button {
                model.playPause()
            } label: {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundStyle(playPauseSymbolColor)
                    .frame(width: 70, height: 70)
                    .background(playPauseBackgroundColor, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPlaying ? "Pause" : "Play")

            Button {
                model.next()
            } label: {
                Image(systemName: "forward.fill")
                    .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Next")
        }
        .font(.title2)
        .foregroundStyle(.primary)
    }

    private var isPlaying: Bool {
        model.activeQueue?.isPlaying == true
            || model.activePlayer?.effectiveState == "playing"
    }

    private var playPauseBackgroundColor: Color {
        isPlaying ? AppTheme.accent : Color(uiColor: .label)
    }

    private var playPauseSymbolColor: Color {
        isPlaying ? .white : Color(uiColor: .systemBackground)
    }

    private var repeatMode: MARepeatMode {
        MARepeatMode(rawValue: model.activeQueue?.repeatMode ?? "off") ?? .off
    }
}

private struct PlayerMenu: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Menu {
            ForEach(model.visiblePlayers) { player in
                Button {
                    model.selectPlayer(player)
                } label: {
                    Label(
                        player.title,
                        systemImage: model.activePlayer?.playerID == player.playerID
                            ? "checkmark.circle.fill"
                            : "hifispeaker.fill"
                    )
                }
                .disabled(!player.isAvailable)
            }
        } label: {
            Image(systemName: "airplayaudio")
        }
        .accessibilityLabel("Choose speaker")
    }
}

private struct ProgressControl: View {
    @EnvironmentObject private var model: AppModel
    @State private var draftPosition = 0.0
    @State private var isEditing = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { _ in
            let duration = max(model.nowPlayingDuration, 1)
            let livePosition = min(model.activeQueue?.correctedElapsedTime ?? 0, duration)
            VStack(spacing: 6) {
                Slider(
                    value: Binding(
                        get: { isEditing ? draftPosition : livePosition },
                        set: { draftPosition = $0 }
                    ),
                    in: 0...duration,
                    onEditingChanged: { editing in
                        if editing {
                            draftPosition = livePosition
                            isEditing = true
                        } else {
                            isEditing = false
                            model.seek(to: draftPosition)
                        }
                    }
                )
                .disabled(model.nowPlayingDuration <= 0)

                HStack {
                    Text((isEditing ? draftPosition : livePosition).formattedDuration)
                    Spacer()
                    Text(model.nowPlayingDuration.formattedDuration)
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            }
        }
    }
}

private struct VolumeControl: View {
    @EnvironmentObject private var model: AppModel
    @State private var draftVolume = 0.0
    @State private var isEditing = false

    var body: some View {
        HStack(spacing: 12) {
            Button {
                model.toggleMute()
            } label: {
                Image(systemName: model.activePlayer?.isMuted == true ? "speaker.slash.fill" : "speaker.fill")
                    .frame(width: 30, height: 30)
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .accessibilityLabel("Mute")

            Slider(
                value: Binding(
                    get: { isEditing ? draftVolume : model.activePlayer?.effectiveVolume ?? 0 },
                    set: { draftVolume = $0 }
                ),
                in: 0...100,
                step: 1,
                onEditingChanged: { editing in
                    if editing {
                        draftVolume = model.activePlayer?.effectiveVolume ?? 0
                        isEditing = true
                    } else {
                        isEditing = false
                        model.setVolume(draftVolume)
                    }
                }
            )

            Image(systemName: "speaker.wave.3.fill")
                .foregroundStyle(.secondary)
                .frame(width: 30, height: 30)
                .accessibilityHidden(true)
        }
    }
}

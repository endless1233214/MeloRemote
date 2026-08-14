import SwiftUI

struct PlayersView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        List {
            Section {
                ForEach(model.visiblePlayers) { player in
                    Button {
                        model.selectPlayer(player)
                    } label: {
                        HStack(spacing: 14) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 7)
                                    .fill(iconColor(player).opacity(0.14))
                                Image(systemName: playerIcon(player))
                                    .font(.title3)
                                    .foregroundStyle(iconColor(player))
                            }
                            .frame(width: 46, height: 46)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(player.title)
                                    .font(.body.weight(.semibold))
                                    .foregroundStyle(.primary)
                                HStack(spacing: 5) {
                                    Circle()
                                        .fill(player.isAvailable ? AppTheme.mint : Color.secondary)
                                        .frame(width: 6, height: 6)
                                    Text(statusText(player))
                                }
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            }

                            Spacer()

                            if model.activePlayer?.playerID == player.playerID {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.title3)
                                    .foregroundStyle(AppTheme.accent)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!player.isAvailable)
                }
            } header: {
                Text("Output")
            }

            if let player = model.activePlayer {
                Section("Selected Speaker") {
                    LabeledContent("Provider", value: providerName(player.provider))
                    LabeledContent("Volume", value: "\(Int(player.effectiveVolume.rounded()))%")
                    LabeledContent(
                        "Playback",
                        value: player.effectiveState.replacingOccurrences(of: "_", with: " ").capitalized
                    )
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Speakers")
        .refreshable {
            await model.refresh()
        }
    }

    private func statusText(_ player: MAPlayer) -> String {
        guard player.isAvailable else { return "Unavailable" }
        switch player.effectiveState {
        case "playing": return "Playing"
        case "paused": return "Paused"
        default: return "Ready"
        }
    }

    private func playerIcon(_ player: MAPlayer) -> String {
        switch player.provider {
        case let provider? where provider.contains("airplay"):
            return "airplayaudio"
        case let provider? where provider.contains("hass"):
            return "tv.fill"
        case let provider? where provider.contains("snapcast"):
            return "hifispeaker.fill"
        default:
            return "speaker.wave.2.fill"
        }
    }

    private func iconColor(_ player: MAPlayer) -> Color {
        guard player.isAvailable else { return .secondary }
        switch player.effectiveState {
        case "playing": return AppTheme.accent
        case "paused": return AppTheme.amber
        default: return AppTheme.blue
        }
    }

    private func providerName(_ provider: String?) -> String {
        provider?
            .replacingOccurrences(of: "_", with: " ")
            .capitalized
            ?? "Unknown"
    }
}

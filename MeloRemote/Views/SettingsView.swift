import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel

    @State private var isRefreshing = false
    @State private var refreshSucceeded = false

    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    AppMark(size: 52)

                    VStack(alignment: .leading, spacing: 3) {
                        Text("MeloRemote")
                            .font(.headline)

                        Text("Unofficial Music Assistant client")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        ConnectionPill(state: model.connectionState)
                    }
                }
                .padding(.vertical, 4)
            }

            Section("Server") {
                LabeledContent(
                    "Address",
                    value: model.serverAddress
                )

                LabeledContent(
                    "Version",
                    value: model.serverInfo?.serverVersion ?? "Unknown"
                )

                LabeledContent(
                    "API Schema",
                    value: model.serverInfo.map {
                        String($0.schemaVersion)
                    } ?? "Unknown"
                )
            }

            Section("Account") {
                LabeledContent(
                    "User",
                    value: model.currentUser?.title ?? model.username
                )

                if let role = model.currentUser?.role {
                    LabeledContent(
                        "Role",
                        value: role.capitalized
                    )
                }
            }

            #if targetEnvironment(macCatalyst)
            Section("Keyboard Shortcuts") {
                LabeledContent("Play / Pause", value: "Space")
                LabeledContent("Previous Track", value: "⌘ ←")
                LabeledContent("Next Track", value: "⌘ →")

                LabeledContent("Listen", value: "⌘ 1")
                LabeledContent("Library", value: "⌘ 2")
                LabeledContent("Queue", value: "⌘ 3")
                LabeledContent("Speakers", value: "⌘ 4")
                LabeledContent("Settings", value: "⌘ 5")

                LabeledContent("Refresh", value: "⌘ R")
            }
            #endif

            Section {
                Button {
                    guard !isRefreshing else { return }

                    isRefreshing = true
                    refreshSucceeded = false
                    model.errorMessage = nil

                    Task {
                        await model.refresh()
                        isRefreshing = false

                        if model.errorMessage == nil {
                            withAnimation {
                                refreshSucceeded = true
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 10) {
                        if isRefreshing {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }

                        Text(
                            isRefreshing
                                ? "Refreshing Server Data…"
                                : "Refresh Server Data"
                        )
                    }
                }
                .disabled(isRefreshing)

                if refreshSucceeded {
                    Label(
                        "Server data refreshed",
                        systemImage: "checkmark.circle.fill"
                    )
                    .font(.footnote)
                    .foregroundStyle(.green)
                    .transition(
                        .opacity.combined(
                            with: .move(edge: .top)
                        )
                    )
                }

                Button(role: .destructive) {
                    model.logout()
                } label: {
                    Label(
                        "Sign Out",
                        systemImage: "rectangle.portrait.and.arrow.right"
                    )
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Settings")
    }
}

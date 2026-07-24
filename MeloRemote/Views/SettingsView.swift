import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel

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
                    Task {
                        await model.refresh()
                    }
                } label: {
                    Label(
                        "Refresh Server Data",
                        systemImage: "arrow.clockwise"
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

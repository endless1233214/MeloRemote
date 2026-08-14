import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    @State private var isShowingError = false
    @State private var displayedError = ""

    var body: some View {
        Group {
            if model.isAuthenticated {
                MainTabView()
            } else {
                LoginView()
            }
        }
        .onChange(of: model.errorMessage) { _, newMessage in
            guard let newMessage else { return }

            displayedError = newMessage
            isShowingError = true
        }
        .alert(
            "Music Assistant",
            isPresented: $isShowingError
        ) {
            Button("OK", role: .cancel) {
                model.errorMessage = nil
            }
        } message: {
            Text(displayedError)
        }
    }
}

private struct MainTabView: View {
    @State private var selection: Int

    init() {
        let arguments = ProcessInfo.processInfo.arguments
        let initialSelection: Int
        if arguments.contains("-demo-library") {
            initialSelection = 1
        } else if arguments.contains("-demo-queue") {
            initialSelection = 2
        } else if arguments.contains("-demo-speakers") {
            initialSelection = 3
        } else if arguments.contains("-demo-settings") {
            initialSelection = 4
        } else {
            initialSelection = 0
        }
        _selection = State(initialValue: initialSelection)
    }

    var body: some View {
        TabView(selection: $selection) {
            NavigationStack {
                NowPlayingView()
            }
            .tag(0)
            .tabItem {
                Label("Listen", systemImage: "play.circle.fill")
            }

            LibraryView()
                .tag(1)
                .tabItem {
                    Label("Library", systemImage: "music.note.list")
                }

            NavigationStack {
                QueueView()
            }
            .tag(2)
            .tabItem {
                Label("Queue", systemImage: "text.line.first.and.arrowtriangle.forward")
            }

            NavigationStack {
                PlayersView()
            }
            .tag(3)
            .tabItem {
                Label("Speakers", systemImage: "hifispeaker.2.fill")
            }

            NavigationStack {
                SettingsView()
            }
            .tag(4)
            .tabItem {
                Label("Settings", systemImage: "gearshape.fill")
            }
        }
    }
}

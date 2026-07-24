import SwiftUI

@main
struct MeloRemoteApp: App {
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .tint(AppTheme.accent)
                .preferredColorScheme(nil)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                model.reconnectIfNeeded()
            }
        }
    }
}

enum AppTheme {
    static let accent = Color(red: 0.91, green: 0.29, blue: 0.25)
    static let mint = Color(red: 0.15, green: 0.66, blue: 0.52)
    static let blue = Color(red: 0.24, green: 0.49, blue: 0.86)
    static let amber = Color(red: 0.92, green: 0.62, blue: 0.17)
}

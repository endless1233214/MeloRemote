import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var model: AppModel
    @FocusState private var focusedField: Field?

    private enum Field {
        case server
        case token
        case username
        case password
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 28) {
                    Spacer(minLength: 44)

                    AppMark(size: 92)

                    VStack(spacing: 5) {
                        Text("MeloRemote")
                            .font(.largeTitle.bold())
                        Text("Unofficial remote for Music Assistant")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }

                    Picker("Sign-in Method", selection: $model.authMode) {
                        ForEach(MAAuthenticationMode.allCases) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)

                    VStack(spacing: 14) {
                        LabeledContent {
                            TextField("http://musicassistant.local:8095", text: $model.serverAddress)
                                .textContentType(.URL)
                                .keyboardType(.URL)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .multilineTextAlignment(.trailing)
                                .focused($focusedField, equals: .server)
                                .submitLabel(.next)
                                .onSubmit {
                                    focusedField = model.authMode == .token ? .token : .username
                                }
                        } label: {
                            Label("Server", systemImage: "server.rack")
                        }

                        if model.authMode == .token {
                            Divider()

                            LabeledContent {
                                SecureField("Paste token", text: $model.longLivedToken)
                                    .textContentType(.password)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                                    .multilineTextAlignment(.trailing)
                                    .focused($focusedField, equals: .token)
                                    .submitLabel(.go)
                                    .onSubmit { Task { await model.login() } }
                            } label: {
                                Label("Token", systemImage: "key.viewfinder")
                            }
                        } else {
                            Divider()

                            LabeledContent {
                                TextField("Username", text: $model.username)
                                    .textContentType(.username)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                                    .multilineTextAlignment(.trailing)
                                    .focused($focusedField, equals: .username)
                                    .submitLabel(.next)
                                    .onSubmit { focusedField = .password }
                            } label: {
                                Label("Username", systemImage: "person.fill")
                            }

                            Divider()

                            LabeledContent {
                                SecureField("Password", text: $model.password)
                                    .textContentType(.password)
                                    .multilineTextAlignment(.trailing)
                                    .focused($focusedField, equals: .password)
                                    .submitLabel(.go)
                                    .onSubmit { Task { await model.login() } }
                            } label: {
                                Label("Password", systemImage: "key.fill")
                            }
                        }
                    }
                    .padding(18)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
                    .animation(.snappy, value: model.authMode)

                    Button {
                        focusedField = nil
                        Task { await model.login() }
                    } label: {
                        HStack(spacing: 10) {
                            if model.isWorking {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Image(systemName: "arrow.right.circle.fill")
                            }
                            Text(model.isWorking ? "Connecting" : "Sign In")
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(signInDisabled)

                    connectionStatus
                    Spacer(minLength: 28)
                }
                .padding(.horizontal, 22)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .background(Color(uiColor: .systemGroupedBackground))
        }
    }

    private var signInDisabled: Bool {
        if model.isWorking || model.serverAddress.isEmpty {
            return true
        }
        switch model.authMode {
        case .token:
            return model.longLivedToken.isEmpty
        case .password:
            return model.username.isEmpty || model.password.isEmpty
        }
    }

    @ViewBuilder
    private var connectionStatus: some View {
        switch model.connectionState {
        case .failed(let message):
            Label(message, systemImage: "exclamationmark.triangle.fill")
                .font(.footnote)
                .foregroundStyle(.red)
                .multilineTextAlignment(.center)
        default:
            EmptyView()
        }
    }
}

import Foundation

actor MusicAssistantClient {
    typealias EventHandler = @Sendable (MAEventEnvelope) -> Void
    
    private let session: URLSession
    private var webSocket: URLSessionWebSocketTask?
    private var listenerTask: Task<Void, Never>?
    private var pingTask: Task<Void, Never>?
    private var pending: [
        String: CheckedContinuation<JSONValue, Error>
    ] = [:]
    
    private var timeoutTasks: [
        String: Task<Void, Never>
    ] = [:]
    
    private var partialResults: [String: [JSONValue]] = [:]
    private var eventHandler: EventHandler?
    private(set) var baseURL: URL?
    
    init() {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.waitsForConnectivity = true
        session = URLSession(configuration: configuration)
    }
    
    func setEventHandler(_ handler: EventHandler?) {
        eventHandler = handler
    }
    
    static func normalizedServerURL(from address: String) throws -> URL {
        var value = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { throw MAClientError.invalidServerAddress }
        if !value.contains("://") {
            value = "http://\(value)"
        }
        guard var components = URLComponents(string: value),
              let scheme = components.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              components.host != nil
        else {
            throw MAClientError.invalidServerAddress
        }
        components.path = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = components.url else { throw MAClientError.invalidServerAddress }
        return url
    }
    
    static func fetchServerInfo(at serverURL: URL) async throws -> MAServerInfo {
        let url = serverURL.appending(path: "info")
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw MAClientError.invalidResponse
        }
        return try JSONDecoder().decode(MAServerInfo.self, from: data)
    }
    
    static func login(
        at serverURL: URL,
        username: String,
        password: String
    ) async throws -> MALoginResponse {
        let requests = try loginRequests(
            for: serverURL.appending(path: "auth/login"),
            username: username,
            password: password
        )
        var firstError: Error?
        
        for request in requests {
            do {
                return try await performLoginRequest(request)
            } catch {
                firstError = firstError ?? error
            }
        }
        
        throw firstError ?? MAClientError.authenticationFailed("Login failed.")
    }
    
    static func loginRequests(
        for url: URL,
        username: String,
        password: String
    ) throws -> [URLRequest] {
        var currentJSONRequest = loginRequest(url: url, contentType: "application/json")
        currentJSONRequest.httpBody = try JSONSerialization.data(withJSONObject: [
            "provider_id": "builtin",
            "device_name": "MeloRemote",
            "credentials": [
                "username": username,
                "password": password
            ]
        ])
        
        var legacyJSONRequest = loginRequest(url: url, contentType: "application/json")
        legacyJSONRequest.httpBody = try JSONSerialization.data(withJSONObject: [
            "provider_id": "builtin",
            "device_name": "MeloRemote",
            "username": username,
            "password": password
        ])
        
        var formRequest = loginRequest(
            url: url,
            contentType: "application/x-www-form-urlencoded"
        )
        var form = URLComponents()
        form.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password)
        ]
        formRequest.httpBody = Data((form.percentEncodedQuery ?? "").utf8)
        
        return [currentJSONRequest, legacyJSONRequest, formRequest]
    }
    
    private static func loginRequest(url: URL, contentType: String) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 15
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        return request
    }
    
    private static func performLoginRequest(_ request: URLRequest) async throws -> MALoginResponse {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw MAClientError.invalidResponse
        }
        
        let authError = decodedAuthError(from: data)
        if http.statusCode == 401 {
            throw MAClientError.authenticationFailed(
                authError ?? "The username or password is incorrect."
            )
        }
        guard http.statusCode == 200 else {
            throw MAClientError.authenticationFailed(
                authError ?? "Login failed with status \(http.statusCode)."
            )
        }
        
        let result = try JSONDecoder().decode(MALoginResponse.self, from: data)
        guard result.resolvedAccessToken != nil else {
            throw MAClientError.authenticationFailed(result.error ?? "Music Assistant did not return a token.")
        }
        return result
    }
    
    private static func decodedAuthError(from data: Data) -> String? {
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return object["error"] as? String
        ?? object["message"] as? String
        ?? object["detail"] as? String
    }
    
    func connect(serverURL: URL, token: String) async throws -> MAServerInfo {
        disconnect()
        baseURL = serverURL
        
        guard var components = URLComponents(url: serverURL, resolvingAgainstBaseURL: false) else {
            throw MAClientError.invalidServerAddress
        }
        components.scheme = components.scheme == "https" ? "wss" : "ws"
        components.path = "/ws"
        guard let webSocketURL = components.url else {
            throw MAClientError.invalidServerAddress
        }
        
        let socket = session.webSocketTask(with: webSocketURL)
        webSocket = socket
        socket.resume()
        
        let firstMessage = try await receiveValue(from: socket)
        let serverInfo = try firstMessage.decoded(as: MAServerInfo.self)
        guard serverInfo.schemaVersion >= serverInfo.minSupportedSchemaVersion else {
            throw MAClientError.incompatibleServer
        }
        
        listenerTask = Task { [weak self] in
            await self?.listen(on: socket)
        }
        pingTask = Task { [weak self] in
            await self?.keepAlive(on: socket)
        }
        
        _ = try await sendCommand(
            "auth",
            args: [
                "token": .string(token),
                "device_name": .string("MeloRemote")
            ]
        )
        return serverInfo
    }
    
    func disconnect() {
        listenerTask?.cancel()
        pingTask?.cancel()
        listenerTask = nil
        pingTask = nil
        webSocket?.cancel(with: .goingAway, reason: nil)
        webSocket = nil
        baseURL = nil
        failAllPending(with: MAClientError.disconnected)
    }
    
    func sendCommand<T: Decodable>(
        _ command: String,
        args: [String: JSONValue] = [:],
        as type: T.Type
    ) async throws -> T {
        let value = try await sendCommand(command, args: args)
        return try value.decoded(as: type)
    }
    
    func sendCommand(
        _ command: String,
        args: [String: JSONValue] = [:]
    ) async throws -> JSONValue {
        guard webSocket != nil else { throw MAClientError.disconnected }
        let messageID = UUID().uuidString.replacingOccurrences(of: "-", with: "")
        let payload: JSONValue = .object([
            "message_id": .string(messageID),
            "command": .string(command),
            "args": .object(args)
        ])
        let data = try JSONEncoder().encode(payload)
        guard let text = String(data: data, encoding: .utf8) else {
            throw MAClientError.invalidResponse
        }
        
        return try await withCheckedThrowingContinuation { continuation in
            pending[messageID] = continuation
            
            timeoutTasks[messageID] = Task { [weak self] in
                do {
                    try await Task.sleep(for: .seconds(20))
                } catch {
                    return
                }
                
                guard !Task.isCancelled else { return }
                
                await self?.resolvePending(
                    messageID,
                    with: .failure(
                        MAClientError.api(
                            "Music Assistant did not respond in time."
                        )
                    )
                )
            }
            
            Task { [weak self] in
                await self?.transmit(text, messageID: messageID)
            }
        }
    }
    
    private func transmit(_ text: String, messageID: String) async {
        guard let webSocket else {
            resolvePending(messageID, with: .failure(MAClientError.disconnected))
            return
        }
        do {
            try await webSocket.send(.string(text))
        } catch {
            resolvePending(messageID, with: .failure(error))
        }
    }
    
    private func listen(on socket: URLSessionWebSocketTask) async {
        do {
            while !Task.isCancelled {
                let value = try await receiveValue(from: socket)
                handle(value)
            }
        } catch {
            guard !Task.isCancelled else { return }
            failAllPending(with: error)
            eventHandler?(MAEventEnvelope(event: "disconnected", objectID: nil, data: nil))
        }
    }
    
    private func keepAlive(on socket: URLSessionWebSocketTask) async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(30))
            guard !Task.isCancelled else { return }
            do {
                try await withCheckedThrowingContinuation {
                    (continuation: CheckedContinuation<Void, Error>) in
                    socket.sendPing { error in
                        if let error {
                            continuation.resume(throwing: error)
                        } else {
                            continuation.resume()
                        }
                    }
                }
            } catch {
                return
            }
        }
    }
    
    private func receiveValue(from socket: URLSessionWebSocketTask) async throws -> JSONValue {
        let message = try await socket.receive()
        let data: Data
        switch message {
        case .string(let text):
            data = Data(text.utf8)
        case .data(let messageData):
            data = messageData
        @unknown default:
            throw MAClientError.invalidResponse
        }
        return try JSONDecoder().decode(JSONValue.self, from: data)
    }
    
    private func handle(_ value: JSONValue) {
        guard let object = value.objectValue else { return }
        
        if let messageID = object["message_id"]?.stringValue {
            if let code = object["error_code"]?.doubleValue {
                let details = object["details"]?.stringValue ?? "Music Assistant API error \(Int(code))."
                resolvePending(messageID, with: .failure(MAClientError.api(details)))
                return
            }
            
            let isPartial = object["partial"]?.boolValue == true
            let result = object["result"] ?? .null
            if isPartial {
                if let values = result.arrayValue {
                    partialResults[messageID, default: []].append(contentsOf: values)
                }
                return
            }
            
            if let accumulated = partialResults.removeValue(forKey: messageID) {
                let finalValues = accumulated + (result.arrayValue ?? [])
                resolvePending(messageID, with: .success(.array(finalValues)))
            } else {
                resolvePending(messageID, with: .success(result))
            }
            return
        }
        
        if let event = object["event"]?.stringValue {
            eventHandler?(
                MAEventEnvelope(
                    event: event,
                    objectID: object["object_id"]?.stringValue,
                    data: object["data"]
                )
            )
        }
    }
    
    private func resolvePending(
        _ messageID: String,
        with result: Result<JSONValue, Error>
    ) {
        guard let continuation =
                pending.removeValue(forKey: messageID)
        else {
            return
        }
        
        timeoutTasks.removeValue(forKey: messageID)?.cancel()
        partialResults.removeValue(forKey: messageID)
        switch result {
        case .success(let value):
            continuation.resume(returning: value)
        case .failure(let error):
            continuation.resume(throwing: error)
        }
    }
    
    private func failAllPending(with error: Error) {
        let continuations = pending.values
        
        timeoutTasks.values.forEach {
            $0.cancel()
        }
        
        timeoutTasks.removeAll()
        pending.removeAll()
        partialResults.removeAll()
        
        continuations.forEach {
            $0.resume(throwing: error)
        }
    }
}

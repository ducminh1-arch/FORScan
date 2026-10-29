import Foundation
import Network

public final class WiFiTransport: Transport, @unchecked Sendable {
    public private(set) var state: TransportState = .disconnected
    public let host: String
    public let port: UInt16

    private var connection: NWConnection?
    private let queue = DispatchQueue(label: "com.carscan.wifitransport")
    private var receiveBuffer = ""
    private let lock = NSLock()

    public init(host: String = "192.168.0.10", port: UInt16 = 35000) {
        self.host = host
        self.port = port
    }

    public func connect() async throws {
        lock.lock()
        state = .connecting
        lock.unlock()

        let endpointHost = NWEndpoint.Host(host)
        guard let endpointPort = NWEndpoint.Port(rawValue: port) else {
            throw TransportError.commandFailed("Invalid port: \(port)")
        }

        let tcpOptions = NWProtocolTCP.Options()
        tcpOptions.noDelay = true
        let params = NWParameters(tls: nil, tcp: tcpOptions)

        let conn = NWConnection(host: endpointHost, port: endpointPort, using: params)
        self.connection = conn

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            var resumed = false
            conn.stateUpdateHandler = { [weak self] newState in
                guard let self = self else { return }
                switch newState {
                case .ready:
                    self.lock.lock()
                    self.state = .connected
                    self.lock.unlock()
                    if !resumed {
                        resumed = true
                        continuation.resume()
                    }
                case .failed(let error):
                    self.lock.lock()
                    self.state = .error(error.localizedDescription)
                    self.lock.unlock()
                    if !resumed {
                        resumed = true
                        continuation.resume(throwing: error)
                    }
                case .cancelled:
                    self.lock.lock()
                    self.state = .disconnected
                    self.lock.unlock()
                default:
                    break
                }
            }
            conn.start(queue: self.queue)
        }
    }

    public func disconnect() {
        lock.lock()
        defer { lock.unlock() }
        connection?.cancel()
        connection = nil
        state = .disconnected
        receiveBuffer = ""
    }

    public func send(command: String, timeoutSec: TimeInterval) async throws -> String {
        guard let conn = connection, state == .connected else {
            throw TransportError.notConnected
        }

        let payload = command.hasSuffix("\r") ? command : command + "\r"
        guard let data = payload.data(using: .utf8) else {
            throw TransportError.commandFailed("Failed to encode command")
        }

        // Send data
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            conn.send(content: data, completion: .contentProcessed { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            })
        }

        // Read until prompt '>'
        return try await readUntilPrompt(timeoutSec: timeoutSec)
    }

    private func readUntilPrompt(timeoutSec: TimeInterval) async throws -> String {
        guard let conn = connection else { throw TransportError.notConnected }

        return try await withThrowingTaskGroup(of: String.self) { group in
            group.addTask { [weak self] in
                guard let self = self else { throw TransportError.connectionLost }
                var accumulated = ""
                while !accumulated.contains(">") {
                    let chunk = try await self.readChunk(conn: conn)
                    accumulated += chunk
                }
                return accumulated
            }

            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(timeoutSec * 1_000_000_000))
                throw TransportError.timeout
            }

            let result = try await group.next()!
            group.cancelAll()
            return result
        }
    }

    private func readChunk(conn: NWConnection) async throws -> String {
        return try await withCheckedThrowingContinuation { continuation in
            conn.receive(minimumIncompleteLength: 1, maximumLength: 1024) { content, _, isComplete, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                if let data = content, let str = String(data: data, encoding: .utf8) {
                    continuation.resume(returning: str)
                } else if isComplete {
                    continuation.resume(throwing: TransportError.connectionLost)
                } else {
                    continuation.resume(returning: "")
                }
            }
        }
    }
}

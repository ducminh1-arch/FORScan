import Foundation

public enum TransportState: Equatable, Sendable {
    case disconnected
    case connecting
    case connected
    case error(String)
}

public enum TransportError: LocalizedError, Equatable {
    case notConnected
    case timeout
    case commandFailed(String)
    case connectionLost
    case invalidResponse(String)
    case noData
    case canError
    case busBusy
    case unableToConnect
    case adapterStopped
    case bufferFull
    case busSwitchFailed(String)

    public var errorDescription: String? {
        switch self {
        case .notConnected:
            return "Adapter transport is not connected."
        case .timeout:
            return "Command timed out waiting for adapter prompt."
        case .commandFailed(let msg):
            return "Command execution failed: \(msg)"
        case .connectionLost:
            return "Transport connection was abruptly lost."
        case .invalidResponse(let res):
            return "Invalid adapter response: \(res)"
        case .noData:
            return "NO DATA: No response received from target ECU."
        case .canError:
            return "CAN ERROR: Bus framing or hardware error."
        case .busBusy:
            return "BUS BUSY: CAN bus is busy."
        case .unableToConnect:
            return "UNABLE TO CONNECT: Failed to connect to CAN bus."
        case .adapterStopped:
            return "STOPPED: Adapter interrupted execution."
        case .bufferFull:
            return "BUFFER FULL: Adapter RX buffer overrun."
        case .busSwitchFailed(let msg):
            return "Bus switch failed: \(msg)"
        }
    }
}

public protocol Transport: AnyObject, Sendable {
    var state: TransportState { get }
    func connect() async throws
    func disconnect()
    func send(command: String, timeoutSec: TimeInterval) async throws -> String
}

public extension Transport {
    func send(command: String) async throws -> String {
        return try await send(command: command, timeoutSec: 3.0)
    }
}

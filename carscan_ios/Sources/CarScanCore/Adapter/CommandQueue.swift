import Foundation

/// Thread-safe single command queue ensuring only one command is pending at any given time.
/// Prevents command collision on the serial ELM327 / OBD2 bus.
public actor CommandQueue {
    public private(set) var isBusy: Bool = false
    private var isCancelled: Bool = false

    public init() {}

    public func execute<T>(_ block: @Sendable () async throws -> T) async throws -> T {
        while isBusy {
            try await Task.sleep(nanoseconds: 10_000_000) // 10ms wait
        }

        isBusy = true
        defer { isBusy = false }

        if isCancelled {
            throw TransportError.commandFailed("Command queue was cancelled.")
        }

        return try await block()
    }

    public func cancelAll() {
        isCancelled = true
        isBusy = false
    }

    public func reset() {
        isCancelled = false
        isBusy = false
    }
}

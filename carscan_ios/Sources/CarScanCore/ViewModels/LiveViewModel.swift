import Foundation
import SwiftUI
import Combine

public struct DataPoint: Identifiable, Sendable {
    public let id = UUID()
    public let timestamp: Date
    public let value: Double
}

@MainActor
public final class LiveViewModel: ObservableObject {
    @Published public var supportedPids: [PIDDefinition] = []
    @Published public var selectedPidsForGraph: [String] = ["PID_010C", "PID_010D"] // Default RPM & Speed (max 4)
    @Published public var liveValues: [String: LivePIDValue] = [:]
    @Published public var graphBuffers: [String: [DataPoint]] = [:] // Max 300 data points per PID
    @Published public var isPolling: Bool = false
    @Published public var minMaxHistory: [String: (min: Double, max: Double)] = [:]

    private let maxBufferPoints = 300
    private var pollTask: Task<Void, Never>?
    private let session = SessionManager.shared

    public init() {
        loadDefaultSpecPids()
    }

    public func loadDefaultSpecPids() {
        // Core standard PIDs
        let pids: [PIDDefinition] = [
            PIDDefinition(id: "PID_010C", mode: "01", pid: "0C", name: "Engine RPM", shortName: "RPM", category: "Engine", module: "PCM", byteCount: 2, formula: "((A * 256) + B) / 4.0", unit: "RPM", imperialUnit: "RPM", min: 0, max: 8000, precision: 0, description: "Engine crankshaft RPM"),
            PIDDefinition(id: "PID_010D", mode: "01", pid: "0D", name: "Vehicle Speed", shortName: "Speed", category: "Speed/Fuel", module: "PCM", byteCount: 1, formula: "A", unit: "km/h", imperialUnit: "mph", min: 0, max: 255, precision: 0, description: "Vehicle speed"),
            PIDDefinition(id: "PID_0105", mode: "01", pid: "05", name: "Coolant Temp (ECT)", shortName: "ECT", category: "Temperature", module: "PCM", byteCount: 1, formula: "A - 40.0", unit: "°C", imperialUnit: "°F", min: -40, max: 215, precision: 1, description: "Engine Coolant Temp"),
            PIDDefinition(id: "PID_010F", mode: "01", pid: "0F", name: "Intake Air Temp (IAT)", shortName: "IAT", category: "Temperature", module: "PCM", byteCount: 1, formula: "A - 40.0", unit: "°C", imperialUnit: "°F", min: -40, max: 215, precision: 1, description: "Intake Air Temp"),
            PIDDefinition(id: "PID_010B", mode: "01", pid: "0B", name: "Manifold Pressure (MAP)", shortName: "MAP", category: "Engine", module: "PCM", byteCount: 1, formula: "A", unit: "kPa", imperialUnit: "psi", min: 0, max: 255, precision: 1, description: "Manifold Absolute Pressure"),
            PIDDefinition(id: "PID_0111", mode: "01", pid: "11", name: "Throttle Position (TP)", shortName: "Throttle", category: "Engine", module: "PCM", byteCount: 1, formula: "(A * 100.0) / 255.0", unit: "%", imperialUnit: "%", min: 0, max: 100, precision: 1, description: "Throttle Position"),
            PIDDefinition(id: "PID_0104", mode: "01", pid: "04", name: "Calculated Engine Load", shortName: "Load", category: "Engine", module: "PCM", byteCount: 1, formula: "(A * 100.0) / 255.0", unit: "%", imperialUnit: "%", min: 0, max: 100, precision: 1, description: "Engine Load"),
            PIDDefinition(id: "PID_012F", mode: "01", pid: "2F", name: "Fuel Tank Level Input", shortName: "Fuel", category: "Speed/Fuel", module: "PCM", byteCount: 1, formula: "(A * 100.0) / 255.0", unit: "%", imperialUnit: "%", min: 0, max: 100, precision: 1, description: "Fuel Tank Level"),
            PIDDefinition(id: "PID_0142", mode: "01", pid: "42", name: "Module Voltage (VPWR)", shortName: "VPWR", category: "Electrical", module: "PCM", byteCount: 2, formula: "((A * 256) + B) / 1000.0", unit: "V", imperialUnit: "V", min: 0, max: 20.0, precision: 2, description: "ECU Voltage")
        ]
        self.supportedPids = pids
    }

    public func discoverVehicleSupportedPids() async {
        guard let obd = session.obd2, session.connectionState == .connected else { return }
        do {
            let bitmaskSet = try await session.queue.execute {
                try await obd.discoverSupportedPids()
            }
            // Filter supported PIDs to only those enabled in bitmask
            if !bitmaskSet.isEmpty {
                let filtered = self.supportedPids.filter { bitmaskSet.contains($0.pid) }
                if !filtered.isEmpty {
                    self.supportedPids = filtered
                }
            }
        } catch {
            print("[WARN] Supported PIDs discovery failed: \(error)")
        }
    }

    public func startPolling(pidsToPoll: [PIDDefinition]) {
        stopPolling()
        guard session.connectionState == .connected, let obd = session.obd2 else { return }

        isPolling = true
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self = self, self.session.connectionState == .connected else { break }

                do {
                    let imperial = self.session.isImperial
                    let batch = try await self.session.queue.execute {
                        try await obd.readPIDsBatch(pidsToPoll, imperial: imperial)
                    }

                    // Update live values and rolling graph buffers
                    let now = Date()
                    for (pidId, val) in batch {
                        self.liveValues[pidId] = val

                        // Record to CSV file if logging is active
                        if self.session.isLoggingPIDs {
                            let def = pidsToPoll.first { $0.id == pidId }
                            let code = def?.pid ?? pidId
                            let name = def?.name ?? pidId
                            self.session.csvLogger.logPID(pid: code, name: name, value: val.numericValue, unit: val.unit, timestamp: Date(timeIntervalSince1970: now))
                        }

                        // Update Min/Max history
                        if var hist = self.minMaxHistory[pidId] {
                            hist.min = min(hist.min, val.numericValue)
                            hist.max = max(hist.max, val.numericValue)
                            self.minMaxHistory[pidId] = hist
                        } else {
                            self.minMaxHistory[pidId] = (min: val.numericValue, max: val.numericValue)
                        }

                        // Update Graph buffer if selected
                        if self.selectedPidsForGraph.contains(pidId) {
                            var buf = self.graphBuffers[pidId] ?? []
                            buf.append(DataPoint(timestamp: now, value: val.numericValue))
                            if buf.count > self.maxBufferPoints {
                                buf.removeFirst(buf.count - self.maxBufferPoints)
                            }
                            self.graphBuffers[pidId] = buf
                        }
                    }
                } catch {
                    if self.session.connectionState == .connected {
                        print("[WARN] Live poll error: \(error)")
                    }
                }

                // Poll rate throttle: ~100ms
                try? await Task.sleep(nanoseconds: 100_000_000)
            }
        }
    }

    public func stopPolling() {
        pollTask?.cancel()
        pollTask = nil
        isPolling = false
    }

    public func toggleGraphPid(_ pidId: String) {
        if selectedPidsForGraph.contains(pidId) {
            if selectedPidsForGraph.count > 1 {
                selectedPidsForGraph.removeAll { $0 == pidId }
            }
        } else {
            if selectedPidsForGraph.count < 4 {
                selectedPidsForGraph.append(pidId)
            }
        }
    }
}

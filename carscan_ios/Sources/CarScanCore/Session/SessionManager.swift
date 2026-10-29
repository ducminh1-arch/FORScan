import Foundation
import SwiftUI
import Combine

public enum ConnectionMode: String, CaseIterable, Identifiable, Sendable {
    case wifi = "Wi-Fi (192.168.0.10)"
    case ble = "Bluetooth Low Energy"
    case demo = "Demo Mode (Mock)"

    public var id: String { rawValue }
}

@MainActor
public final class SessionManager: ObservableObject {
    public static let shared = SessionManager()

    @Published public var connectionState: TransportState = .disconnected
    @Published public var isDemoMode: Bool = true {
        didSet {
            if isDemoMode && connectionState == .connected {
                disconnect()
            }
        }
    }
    @Published public var connectionMode: ConnectionMode = .demo
    @Published public var wifiHost: String = "192.168.0.10"
    @Published public var wifiPort: UInt16 = 35000
    @Published public var selectedBleName: String = "Auto-detect (vLinker / Veepeak)"
    @Published public var selectedBleId: String? = nil
    @Published public var isImperial: Bool = false
    @Published public var vehicleInfo: VehicleInfo?
    @Published public var adapterVoltage: Double = 0.0
    @Published public var ecuVoltage: Double = 0.0
    @Published public var errorMessage: String?
    @Published public var showErrorAlert: Bool = false
    @Published public var adapterProfile: AdapterProfile?
    @Published public var currentBus: String = "HS-CAN"
    @Published public var manualSwitchPrompt: String?

    // Active screen tracking for polling management
    @Published public var activeScreen: String = "Home"

    @Published public var isLoggingPIDs: Bool = false
    @Published public var isTracingCommands: Bool = false
    public let csvLogger = CSVLogService()
    public let rawTraceLogger = RawTraceLogger()

    public private(set) var driver: ELM327Driver?
    public private(set) var obd2: OBD2Service?
    public private(set) var uds: UDSService?
    public let queue = CommandQueue()

    private var activeTransport: (any Transport)?
    private var cancellables = Set<AnyCancellable>()

    public func startPIDLogging(directory: URL, sessionName: String? = nil) throws -> URL {
        let url = try csvLogger.startLogging(outputDirectory: directory, sessionName: sessionName)
        isLoggingPIDs = true
        return url
    }

    public func stopPIDLogging() -> URL? {
        let url = csvLogger.stopLogging()
        isLoggingPIDs = false
        return url
    }

    public func startRawTracing(directory: URL, name: String? = nil) throws -> URL {
        let url = try rawTraceLogger.startTrace(outputDirectory: directory, name: name)
        isTracingCommands = true
        driver?.traceLogger = rawTraceLogger
        return url
    }

    public func stopRawTracing() -> URL? {
        let url = rawTraceLogger.stopTrace()
        isTracingCommands = false
        driver?.traceLogger = nil
        return url
    }

    public init() {}

    public func connect() async {
        guard connectionState != .connected && connectionState != .connecting else { return }
        connectionState = .connecting
        errorMessage = nil

        let transport: any Transport
        if isDemoMode || connectionMode == .demo {
            let mock = MockTransport()
            mock.simulatedVoltage = 12.6
            transport = mock
        } else if connectionMode == .wifi {
            transport = WiFiTransport(host: wifiHost, port: wifiPort)
        } else {
            let targetUuid = selectedBleId.flatMap { UUID(uuidString: $0) }
            transport = BleTransport(targetPeripheralId: targetUuid)
        }

        self.activeTransport = transport

        do {
            try await transport.connect()
            let drv = ELM327Driver(transport: transport)
            if isTracingCommands {
                drv.traceLogger = rawTraceLogger
            }
            try await drv.initialize()
            let obd = OBD2Service(driver: drv)
            let u = UDSService(driver: drv)

            self.driver = drv
            self.obd2 = obd
            self.uds = u
            self.adapterVoltage = drv.batteryVoltage
            self.adapterProfile = drv.adapterProfile
            self.currentBus = drv.currentBus
            self.connectionState = .connected

            // Automatically read vehicle info in background
            Task {
                await fetchInitialVehicleData()
            }
        } catch {
            self.connectionState = .error(error.localizedDescription)
            self.errorMessage = "Failed to connect to adapter: \(error.localizedDescription)"
            self.showErrorAlert = true
            disconnect()
        }
    }

    public func switchBus(to targetBus: String) async throws {
        guard let drv = driver else { return }
        try await queue.execute {
            try await drv.switchBus(to: targetBus) { prompt in
                Task { @MainActor in
                    self.manualSwitchPrompt = prompt
                }
            }
            Task { @MainActor in
                self.currentBus = drv.currentBus
            }
        }
    }

    public func dismissManualSwitchPrompt() {
        self.manualSwitchPrompt = nil
    }

    public func disconnect() {
        queue.cancelAll()
        activeTransport?.disconnect()
        activeTransport = nil
        driver = nil
        obd2 = nil
        uds = nil
        adapterProfile = nil
        connectionState = .disconnected
        queue.reset()
    }

    public func fetchInitialVehicleData() async {
        guard let obd = obd2, connectionState == .connected else { return }
        do {
            // Read VIN
            let vin = try await queue.execute {
                try await obd.readVIN()
            }

            // Discover ECUs (7E8..7EF)
            let ecus = try await queue.execute {
                try await obd.discoverECUs()
            }

            // Read VPWR (PID 0142)
            let vpwrPid = PIDDefinition(
                id: "PID_0142",
                mode: "01",
                pid: "42",
                name: "Control Module Voltage",
                shortName: "VPWR",
                category: "Electrical",
                module: "PCM",
                byteCount: 2,
                formula: "((A * 256) + B) / 1000.0",
                unit: "V",
                imperialUnit: "V",
                min: 0,
                max: 20.0,
                precision: 2,
                description: ""
            )
            let vpwrVal = try? await queue.execute {
                try await obd.readPID(vpwrPid)
            }
            if let v = vpwrVal {
                self.ecuVoltage = v.numericValue
            }

            // Update VehicleInfo
            let decodedYear = VINDecoder.decodeModelYear(from: vin) ?? 2021
            let decodedMake = VINDecoder.decodeMake(from: vin)
            self.vehicleInfo = VehicleInfo(
                vin: vin,
                make: decodedMake,
                model: vin.hasPrefix("1FA") ? "Mustang EcoBoost" : (vin.hasPrefix("WF0") ? "Focus" : "CX-5"),
                year: decodedYear,
                batteryVoltage: adapterVoltage > 0 ? adapterVoltage : (ecuVoltage > 0 ? ecuVoltage : 12.6),
                detectedModules: ecus
            )
        } catch {
            print("[WARN] Failed to fetch vehicle info: \(error)")
        }
    }

    public func notifyConnectionLost(reason: String) {
        disconnect()
        self.errorMessage = "Connection to OBD-II adapter was lost: \(reason)"
        self.showErrorAlert = true
    }
}

import Foundation
import SwiftUI
import Combine

@MainActor
public final class VehicleViewModel: ObservableObject {
    @Published public var vin: String = "Not Read"
    @Published public var make: String = "Ford"
    @Published public var model: String = "Mustang EcoBoost"
    @Published public var year: String = "2021"
    @Published public var atrvVoltage: Double = 0.0
    @Published public var vpwrVoltage: Double = 0.0
    @Published public var detectedModules: [ModuleItem] = []
    @Published public var isLoading: Bool = false
    @Published public var errorText: String?

    public struct ModuleItem: Identifiable, Equatable {
        public var id: String { canId }
        public let canId: String
        public let name: String
        public let acronym: String
        public let bus: String
    }

    private let session = SessionManager.shared

    public init() {}

    public func loadData() async {
        guard session.connectionState == .connected, let obd = session.obd2 else {
            errorText = "Adapter not connected. Please connect from Home screen."
            return
        }

        isLoading = true
        errorText = nil

        do {
            // 1. Read ATRV voltage (OBD pin 16 direct voltage)
            if let vRaw = try? await session.queue.execute({ try await obd.driver.executeWithRetry(command: "ATRV") }) {
                let cleanV = vRaw.replacingOccurrences(of: "V", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
                atrvVoltage = Double(cleanV) ?? 12.6
            }

            // 2. Read VPWR (PID 0142 module internal voltage)
            let vpwrDef = PIDDefinition(
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
            if let vpwrVal = try? await session.queue.execute({ try await obd.readPID(vpwrDef) }) {
                vpwrVoltage = vpwrVal.numericValue
            }

            // 3. Read VIN via Mode 09 PID 02
            let vinStr = try await session.queue.execute {
                try await obd.readVIN()
            }
            vin = vinStr
            if let decodedYear = VINDecoder.decodeModelYear(from: vinStr) {
                year = "\(decodedYear)"
            }
            make = VINDecoder.decodeMake(from: vinStr)
            if vinStr.hasPrefix("1FA") {
                model = "Mustang EcoBoost"
            } else if vinStr.hasPrefix("WF0") {
                model = "Focus"
            }

            // 4. Discover Standard ECUs (7E8..7EF) via broadcast 7DF
            let ecus = try await session.queue.execute {
                try await obd.discoverECUs()
            }

            var items: [ModuleItem] = []
            for id in ecus {
                switch id {
                case "7E8":
                    items.append(ModuleItem(canId: "7E8", name: "Powertrain Control Module", acronym: "PCM", bus: "HS-CAN"))
                case "7E9":
                    items.append(ModuleItem(canId: "7E9", name: "Transmission Control Module", acronym: "TCM", bus: "HS-CAN"))
                default:
                    items.append(ModuleItem(canId: id, name: "Electronic Control Unit (\(id))", acronym: "ECU", bus: "HS-CAN"))
                }
            }
            detectedModules = items
        } catch {
            errorText = "Vehicle scan error: \(error.localizedDescription)"
        }

        isLoading = false
    }
}

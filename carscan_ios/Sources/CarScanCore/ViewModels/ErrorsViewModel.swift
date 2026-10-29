import Foundation
import SwiftUI
import Combine

@MainActor
public final class ErrorsViewModel: ObservableObject {
    @Published public var scanResults: [ModuleScanResult] = []
    @Published public var isScanning: Bool = false
    @Published public var isClearing: Bool = false
    @Published public var scanningModuleAcronym: String?

    // Module-specific DTC clear confirmation
    @Published public var selectedModuleForClear: ModuleDefinition?
    @Published public var showClearConfirmDialog: Bool = false
    @Published public var alertMessage: String?
    @Published public var showAlert: Bool = false

    private let session = SessionManager.shared

    public let targetModules: [ModuleDefinition] = [
        ModuleDefinition(
            id: "FORD_PCM",
            name: "Powertrain Control Module",
            acronym: "PCM",
            make: "Ford",
            bus: "HS-CAN",
            txId: "7E0",
            rxId: "7E8",
            protocol: "ISO_15765_4_11BIT_500K",
            udsSupported: true,
            description: "Engine and powertrain management ECU.",
            verified: false
        ),
        ModuleDefinition(
            id: "FORD_TCM",
            name: "Transmission Control Module",
            acronym: "TCM",
            make = "Ford",
            bus: "HS-CAN",
            txId: "7E1",
            rxId: "7E9",
            protocol: "ISO_15765_4_11BIT_500K",
            udsSupported: true,
            description: "Automatic / dual-clutch transmission control module.",
            verified: false
        ),
        ModuleDefinition(
            id: "FORD_ABS",
            name: "Antilock Braking System",
            acronym: "ABS",
            make = "Ford",
            bus: "HS-CAN",
            txId: "760",
            rxId: "768",
            protocol: "ISO_15765_4_11BIT_500K",
            udsSupported: true,
            description: "ABS and Traction Control module.",
            verified: false
        ),
        ModuleDefinition(
            id: "FORD_RCM",
            name: "Restraint Control Module",
            acronym: "RCM",
            make = "Ford",
            bus: "HS-CAN",
            txId: "737",
            rxId: "73F",
            protocol: "ISO_15765_4_11BIT_500K",
            udsSupported: true,
            description: "Airbag and restraint system module.",
            verified: false
        ),
        ModuleDefinition(
            id: "FORD_BCM",
            name: "Body Control Module",
            acronym: "BCM",
            make = "Ford",
            bus: "MS-CAN",
            txId: "726",
            rxId: "72E",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "Body electrical and battery monitoring module.",
            verified: false
        ),
        ModuleDefinition(
            id: "FORD_IPC",
            name: "Instrument Panel Cluster",
            acronym: "IPC",
            make = "Ford",
            bus: "MS-CAN",
            txId: "720",
            rxId: "728",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "Dashboard instrument cluster display.",
            verified: false
        ),
        ModuleDefinition(
            id: "FORD_APIM",
            name: "Accessory Protocol Interface Module",
            acronym: "APIM",
            make = "Ford",
            bus: "MS-CAN",
            txId: "7D0",
            rxId: "7D8",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "Ford SYNC infotainment unit.",
            verified: false
        ),
        ModuleDefinition(
            id: "FORD_PAM",
            name: "Parking Aid Module",
            acronym: "PAM",
            make = "Ford",
            bus: "MS-CAN",
            txId: "736",
            rxId: "73E",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "Ultrasonic park distance control module.",
            verified: false
        ),
        ModuleDefinition(
            id: "FORD_TCU",
            name: "Telematic Control Unit",
            acronym: "TCU",
            make = "Ford",
            bus: "MS-CAN",
            txId: "754",
            rxId: "75C",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "FordPass modem module.",
            verified: false
        )
    ]

    public init() {}

    public func scanDTCs() async {
        guard session.connectionState == .connected else {
            alertMessage = "Please connect to adapter before scanning error codes."
            showAlert = true
            return
        }

        isScanning = true
        scanResults.removeAll()

        let hsModules = targetModules.filter { $0.bus == "HS-CAN" }
        let msModules = targetModules.filter { $0.bus == "MS-CAN" }

        var results: [ModuleScanResult] = []

        // --- 1. HS-CAN Scan ---
        if !session.isDemoMode {
            do {
                try await session.switchBus(to: "HS-CAN")
            } catch {
                alertMessage = "Bus switch failed"
                showAlert = true
                isScanning = false
                return
            }
        }

        for m in hsModules {
            scanningModuleAcronym = m.acronym
            let res: ModuleScanResult
            if session.isDemoMode {
                res = simulateDemoResult(for: m)
            } else if let uds = session.uds {
                res = await session.queue.execute {
                    await uds.readDTCsForModule(module: m)
                }
            } else {
                res = ModuleScanResult(module: m, status: .scanError("UDS service unavailable"))
            }
            results.append(res)
            self.scanResults = results
        }

        // --- 2. MS-CAN Scan ---
        let currentProfile = session.adapterProfile ?? session.driver?.adapterProfile
        if !session.isDemoMode && !msModules.isEmpty {
            if let prof = currentProfile, !prof.hasMsCan {
                alertMessage = "Adapter does not support MS-CAN (\(prof.name)). MS-CAN modules skipped."
                showAlert = true
                for m in msModules {
                    results.append(ModuleScanResult(module: m, status: .scanError("Adapter does not support MS-CAN (\(prof.name))")))
                }
                self.scanResults = results
            } else {
                do {
                    try await session.switchBus(to: "MS-CAN")
                } catch {
                    alertMessage = "Bus switch failed"
                    showAlert = true
                    isScanning = false
                    return
                }

                for m in msModules {
                    scanningModuleAcronym = m.acronym
                    let res: ModuleScanResult
                    if let uds = session.uds {
                        res = await session.queue.execute {
                            await uds.readDTCsForModule(module: m)
                        }
                    } else {
                        res = ModuleScanResult(module: m, status: .scanError("UDS service unavailable"))
                    }
                    results.append(res)
                    self.scanResults = results
                }
            }
        } else if session.isDemoMode && !msModules.isEmpty {
            for m in msModules {
                results.append(simulateDemoResult(for: m))
                self.scanResults = results
            }
        }

        // Return to HS-CAN
        if !session.isDemoMode {
            try? await session.switchBus(to: "HS-CAN")
        }

        isScanning = false
        scanningModuleAcronym = nil
    }

    private func simulateDemoResult(for m: ModuleDefinition) -> ModuleScanResult {
        switch m.acronym {
        case "PCM":
            return ModuleScanResult(
                module: m,
                status: .faultsFound([
                    DTCRecord(code: "P0171:00", system: "Powertrain", description: "System Too Lean (Bank 1)", severity: "Confirmed", symptom: "Fuel trim exceeded rich limits", statusByte: 0x2F),
                    DTCRecord(code: "P0300:00", system: "Powertrain", description: "Random/Multiple Cylinder Misfire Detected", severity: "Pending", symptom: "Engine misfire detected during last cycle", statusByte: 0x28)
                ])
            )
        case "TCM":
            return ModuleScanResult(
                module: m,
                status: .faultsFound([
                    DTCRecord(code: "P0700:00", system: "Powertrain", description: "Transmission Control System (MIL Request)", severity: "Confirmed", statusByte: 0x2F)
                ])
            )
        case "BCM":
            return ModuleScanResult(
                module: m,
                status: .faultsFound([
                    DTCRecord(code: "B11D9:09-0A", system: "Body", description: "Vehicle Battery - Component Failure / Low Resistance", severity: "Confirmed", symptom: "Ford BMS reports 12V battery health degraded, low state of charge.", statusByte: 0x2F)
                ])
            )
        case "ABS":
            return ModuleScanResult(
                module: m,
                status: .faultsFound([
                    DTCRecord(code: "C0031:01", system: "Chassis", description: "Left Front Wheel Speed Sensor - General Electrical Failure", severity: "Confirmed", statusByte: 0x2F)
                ])
            )
        case "IPC", "APIM":
            return ModuleScanResult(module: m, status: .clean)
        case "PAM", "TCU", "RCM":
            return ModuleScanResult(module: m, status: .offlineNoResponse)
        default:
            return ModuleScanResult(module: m, status: .offlineNoResponse)
        }
    }

    public func requestClearDTCs(for module: ModuleDefinition) {
        selectedModuleForClear = module
        showClearConfirmDialog = true
    }

    public func dismissClearDialog() {
        showClearConfirmDialog = false
        selectedModuleForClear = nil
    }

    public func executeClearDTCsForSelectedModule() async {
        guard let module = selectedModuleForClear, session.connectionState == .connected else { return }

        isClearing = true
        do {
            if session.isDemoMode {
                // In demo mode: clear DTCs for this module
                self.scanResults = self.scanResults.map { item in
                    if item.module.id == module.id {
                        return ModuleScanResult(module: item.module, status: .clean)
                    }
                    return item
                }
                alertMessage = "Diagnostic trouble codes cleared for \(module.acronym)."
                showAlert = true
            } else if let uds = session.uds {
                let prof = session.adapterProfile ?? session.driver?.adapterProfile
                if module.bus.uppercased() == "MS-CAN", let prof = prof, !prof.hasMsCan {
                    alertMessage = "Cannot clear DTC: Adapter \(prof.name) does not support MS-CAN."
                    showAlert = true
                    isClearing = false
                    return
                }
                do {
                    try await session.switchBus(to: module.bus)
                } catch {
                    alertMessage = "Bus switch failed"
                    showAlert = true
                    isClearing = false
                    return
                }
                _ = try await session.queue.execute {
                    try await uds.clearDTCsForModule(module: module)
                }

                // Re-read DTCs for this module
                let fresh = await session.queue.execute {
                    await uds.readDTCsForModule(module: module)
                }
                self.scanResults = self.scanResults.map { item in
                    item.module.id == module.id ? fresh : item
                }
                try? await session.switchBus(to: "HS-CAN")
                alertMessage = "Diagnostic trouble codes cleared successfully for \(module.acronym)."
                showAlert = true
            }
        } catch {
            alertMessage = "Failed to clear DTCs for \(module.acronym): \(error.localizedDescription)"
            showAlert = true
        }

        isClearing = false
        showClearConfirmDialog = false
        selectedModuleForClear = nil
    }

    public func exportLog() -> String {
        var log = "CarScan Lite - Multi-Module Diagnostic Trouble Codes Report\n"
        log += "Generated: \(Date().formatted())\n"
        log += "VIN: \(session.vehicleInfo?.vin ?? "Unknown")\n\n"
        for r in scanResults {
            let verifiedTag = r.module.isVerified ? "[Verified]" : "[Unverified: false]"
            log += "Module: \(r.module.name) (\(r.module.acronym)) [TX: \(r.module.txId), Bus: \(r.module.bus)] \(verifiedTag)\n"
            log += "Status: \(r.status.displayText)\n"
            if case .faultsFound(let dtcs) = r.status {
                for d in dtcs {
                    log += "  - \(d.code): \(d.description) (\(d.severity))\n"
                    if let s = d.symptom {
                        log += "    Symptom: \(s)\n"
                    }
                }
            }
            log += "\n"
        }
        return log
    }
}

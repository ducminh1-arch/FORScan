import Foundation

public struct SpikeReport: Sendable {
    public let isSuccess: Bool
    public let adapterVersion: String
    public let batteryVoltage: Double
    public let vin: String
    public let sampleRpm: Double
    public let dtcCount: Int
    public let log: [String]
}

public final class Phase0Spike: Sendable {
    public init() {}

    public func run(transport: any Transport) async throws -> SpikeReport {
        var log: [String] = []
        log.append("Starting Phase 0 Spike with transport: \(type(of: transport))")

        // 1. Connect
        try await transport.connect()
        log.append("Transport connected successfully.")

        // 2. Initialize Driver
        let driver = ELM327Driver(transport: transport)
        try await driver.initialize()
        log.append("Driver initialized. Adapter version: '\(driver.adapterVersion)', Voltage: \(driver.batteryVoltage)V")

        // 3. Read VIN via OBD2 Mode 09 PID 02
        let obd2 = OBD2Service(driver: driver)
        let vin = try await obd2.readVIN()
        log.append("VIN successfully decoded via ISO-TP: '\(vin)'")

        // 4. Sample Live RPM
        let rpmDef = PIDDefinition(
            id: "PID_010C",
            mode: "01",
            pid: "0C",
            name: "Engine RPM",
            shortName: "RPM",
            category: "Engine",
            module: "PCM",
            byteCount: 2,
            formula: "((A * 256) + B) / 4.0",
            unit: "RPM",
            imperialUnit: "RPM",
            min: 0,
            max: 8000,
            precision: 0,
            description: "Engine RPM"
        )
        let rpmVal = try await obd2.readPID(rpmDef)
        log.append("Sample Live RPM: \(rpmVal.formattedString) \(rpmVal.unit)")

        // 5. Read Stored DTCs
        let dtcs = try await obd2.readStoredDTCs()
        log.append("Stored DTCs read: \(dtcs.map { $0.code }.joined(separator: ", "))")

        return SpikeReport(
            isSuccess: true,
            adapterVersion: driver.adapterVersion,
            batteryVoltage: driver.batteryVoltage,
            vin: vin,
            sampleRpm: rpmVal.numericValue,
            dtcCount: dtcs.count,
            log: log
        )
    }
}

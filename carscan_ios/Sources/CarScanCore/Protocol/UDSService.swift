import Foundation

public final class UDSService: Sendable {
    public let driver: ELM327Driver
    public let isoTP: ISOTPHandler

    public init(driver: ELM327Driver) {
        self.driver = driver
        self.isoTP = ISOTPHandler()
    }

    /// DiagnosticSessionControl (0x10): strictly default session (0x01).
    /// No security access (0x27) is permitted or implemented.
    public func startSession(sessionType: UInt8 = 0x01) async throws {
        let cmd = String(format: "10%02X", sessionType)
        let raw = try await driver.executeWithRetry(command: cmd, retries: 2)
        let messages = isoTP.reassemble(response: raw)
        guard let first = messages.first, first.payload.first == 0x50 else {
            throw TransportError.commandFailed("Session control rejected: \(raw)")
        }
    }

    /// Reads Diagnostic Trouble Codes (UDS 0x19 0x02 reportDTCByStatusMask) for a specific module.
    /// Sets CAN header to the target module's txId.
    /// Correctly handles NRC 0x78 (ResponsePending) without failing.
    /// Distinguishes between:
    /// - Unresponsive / offline module (NO DATA / Timeout) -> .offlineNoResponse
    /// - Module responding with zero DTCs -> .clean
    /// - Module responding with faults -> .faultsFound
    public func readDTCsForModule(
        module: ModuleDefinition,
        statusMask: UInt8 = 0xAF
    ) async -> ModuleScanResult {
        do {
            try await driver.setHeader(module.txId)
        } catch {
            return ModuleScanResult(module: module, status: .scanError("Failed to set header: \(error.localizedDescription)"))
        }

        let cmd = String(format: "1902%02X", statusMask)
        var rawResponse: String
        do {
            rawResponse = try await driver.executeWithRetry(command: cmd, retries: 2, timeoutSec: 4.0)
        } catch let err as TransportError where err == .noData || err == .timeout {
            return ModuleScanResult(module: module, status: .offlineNoResponse)
        } catch {
            return ModuleScanResult(module: module, status: .scanError(error.localizedDescription))
        }

        var messages = isoTP.reassemble(response: rawResponse)

        // Check for NRC 0x78 (RequestCorrectlyReceived - ResponsePending)
        let hasNrc78 = messages.contains { msg in
            let p = msg.payload
            return p.count >= 3 && p[0] == 0x7F && p[1] == 0x19 && p[2] == 0x78
        }

        let hasPositive = messages.contains { msg in
            return msg.payload.first == 0x59
        }

        if hasNrc78 && !hasPositive {
            // Server needs more time: wait and query or receive following frame
            try? await Task.sleep(nanoseconds: 400_000_000)
            if let nextRaw = try? await driver.executeWithRetry(command: cmd, retries: 1, timeoutSec: 3.0) {
                messages = isoTP.reassemble(response: nextRaw)
            }
        }

        guard let positiveMsg = messages.first(where: { msg in
            let p = msg.payload
            return p.count >= 2 && p[0] == 0x59 && p[1] == 0x02
        }) else {
            // Check for other negative response
            if let neg = messages.first(where: { $0.payload.first == 0x7F }), neg.payload.count >= 3 {
                let nrc = neg.payload[2]
                if nrc != 0x78 {
                    return ModuleScanResult(module: module, status: .scanError(String(format: "UDS NRC 0x%02X", nrc)))
                }
            }
            return ModuleScanResult(module: module, status: .offlineNoResponse)
        }

        let p = positiveMsg.payload
        if p.count <= 3 {
            // Positive response with zero DTCs
            return ModuleScanResult(module: module, status: .clean)
        }

        var records: [DTCRecord] = []
        var idx = 3
        while idx + 3 < p.count {
            let b0 = p[idx]
            let b1 = p[idx + 1]
            let b2 = p[idx + 2]
            let status = p[idx + 3]
            idx += 4

            let baseCode = PIDDecoder.decodeDTC(b0: b0, b1: b1)
            let fullCode = (b2 != 0) ? String(format: "%@:%02X", baseCode, b2) : baseCode
            let prefix = String(baseCode.prefix(1))

            records.append(DTCRecord(
                code: fullCode,
                system: prefix == "B" ? "Body" : (prefix == "C" ? "Chassis" : (prefix == "U" ? "Network" : "Powertrain")),
                description: "UDS Diagnostic Trouble Code",
                severity: (status & 0x08 != 0) ? "Confirmed" : "Pending",
                statusByte: status
            ))
        }

        return records.isEmpty
            ? ModuleScanResult(module: module, status: .clean)
            : ModuleScanResult(module: module, status: .faultsFound(records))
    }

    /// Clear Diagnostic Information (UDS 0x14 0xFF 0xFF 0xFF).
    /// STRICTLY executed per individual module (txId). NEVER broadcast to 7DF.
    /// Handles NRC 0x78 (ResponsePending).
    public func clearDTCsForModule(module: ModuleDefinition) async throws -> Bool {
        guard module.txId.uppercased() != "7DF" else {
            throw TransportError.commandFailed("Safety restriction: UDS 0x14 ClearDTC must never be broadcast to 7DF. Target specific module only.")
        }

        try await driver.setHeader(module.txId)
        let cmd = "14FFFFFF"
        let raw = try await driver.executeWithRetry(command: cmd, retries: 2, timeoutSec: 4.5)
        var messages = isoTP.reassemble(response: raw)

        let hasNrc78 = messages.contains { msg in
            let p = msg.payload
            return p.count >= 3 && p[0] == 0x7F && p[1] == 0x14 && p[2] == 0x78
        }

        if hasNrc78 && !messages.contains(where: { $0.payload.first == 0x54 }) {
            try? await Task.sleep(nanoseconds: 400_000_000)
            if let nextRaw = try? await driver.executeWithRetry(command: cmd, retries: 1, timeoutSec: 3.0) {
                messages = isoTP.reassemble(response: nextRaw)
            }
        }

        guard messages.contains(where: { $0.payload.first == 0x54 }) else {
            throw TransportError.commandFailed("Module \(module.acronym) rejected DTC clear: \(raw)")
        }

        return true
    }

    /// ReadDTCInformation (0x19 0x02) by status mask (e.g. 0xAF or 0xFF)
    public func readDTCsByStatusMask(statusMask: UInt8 = 0xAF) async throws -> [DTCRecord] {
        let cmd = String(format: "1902%02X", statusMask)
        let raw = try await driver.executeWithRetry(command: cmd, retries: 2, timeoutSec: 3.5)
        let messages = isoTP.reassemble(response: raw)

        var records: [DTCRecord] = []
        for msg in messages {
            guard msg.payload.count >= 3, msg.payload[0] == 0x59, msg.payload[1] == 0x02 else { continue }
            var idx = 3
            while idx + 3 < msg.payload.count {
                let b0 = msg.payload[idx]
                let b1 = msg.payload[idx + 1]
                let status = msg.payload[idx + 3]
                idx += 4

                let code = PIDDecoder.decodeDTC(b0: b0, b1: b1)
                let prefix = String(code.prefix(1))

                records.append(DTCRecord(
                    code: code,
                    system: prefix == "B" ? "Body" : (prefix == "C" ? "Chassis" : "Powertrain"),
                    description: "UDS Diagnostic Trouble Code",
                    severity: (status & 0x08 != 0) ? "High" : "Medium",
                    statusByte: status
                ))
            }
        }
        return records
    }

    public func ecuReset(resetType: UInt8 = 0x01) async throws {
        let cmd = String(format: "11%02X", resetType)
        let raw = try await driver.executeWithRetry(command: cmd, retries: 1, timeoutSec: 3.0)
        let messages = isoTP.reassemble(response: raw)
        guard let first = messages.first, first.payload.first == 0x51 else {
            throw TransportError.commandFailed("ECU reset rejected: \(raw)")
        }
    }

    public func startRoutine(routineId: String) async throws {
        let cmd = "3101\(routineId.uppercased())"
        let raw = try await driver.executeWithRetry(command: cmd, retries: 1, timeoutSec: 5.0)
        let messages = isoTP.reassemble(response: raw)
        guard let first = messages.first, first.payload.first == 0x71 else {
            throw TransportError.commandFailed("Routine start rejected: \(raw)")
        }
    }

    public func testerPresent() async throws {
        let raw = try await driver.executeWithRetry(command: "3E00", retries: 1, timeoutSec: 1.5)
        let messages = isoTP.reassemble(response: raw)
        guard let first = messages.first, first.payload.first == 0x7E else {
            throw TransportError.commandFailed("TesterPresent rejected")
        }
    }
}

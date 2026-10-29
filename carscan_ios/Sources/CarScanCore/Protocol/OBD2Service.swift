import Foundation

public final class OBD2Service: Sendable {
    public let driver: ELM327Driver
    public let isoTP: ISOTPHandler
    public let decoder: PIDDecoder

    public init(driver: ELM327Driver) {
        self.driver = driver
        self.isoTP = ISOTPHandler()
        self.decoder = PIDDecoder()
    }

    /// Reads Vehicle Identification Number (VIN) using Mode 09 PID 02
    public func readVIN() async throws -> String {
        let raw = try await driver.executeWithRetry(command: "0902", retries: 2, timeoutSec: 4.0)
        let messages = isoTP.reassemble(response: raw)

        for msg in messages {
            // Mode 09 PID 02 positive response begins with 0x49 0x02 0x01
            if msg.payload.count >= 20 && msg.payload[0] == 0x49 && msg.payload[1] == 0x02 {
                let vinBytes = Array(msg.payload[3..<20])
                let vin = vinBytes.compactMap { byte -> String? in
                    guard byte >= 32 && byte <= 126 else { return nil }
                    return String(UnicodeScalar(byte))
                }.joined()

                if vin.count == 17 {
                    return vin
                }
            }
        }

        // Direct fallback check if payload already stripped
        for msg in messages {
            let ascii = msg.asciiString
            if ascii.count >= 17 {
                return String(ascii.suffix(17))
            }
        }

        throw TransportError.invalidResponse("Could not extract valid 17-character VIN from response: \(raw)")
    }

    /// Polls a live PID value
    public func readPID(_ pid: PIDDefinition, imperial: Bool = false) async throws -> LivePIDValue {
        if pid.id == "PID_ATRV" {
            let rawV = try await driver.executeWithRetry(command: "ATRV")
            let v = Double(rawV.replacingOccurrences(of: "V", with: "").trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0.0
            return LivePIDValue(
                pidId: pid.id,
                numericValue: v,
                formattedString: String(format: "%.1f", v),
                unit: "V"
            )
        }

        let cmd = "\(pid.mode)\(pid.pid)"
        let raw = try await driver.executeWithRetry(command: cmd, retries: 1, timeoutSec: 2.0)
        let messages = isoTP.reassemble(response: raw)

        guard let firstMsg = messages.first, !firstMsg.payload.isEmpty else {
            throw TransportError.invalidResponse("No data payload for PID \(pid.name)")
        }

        // Drop service echo bytes (e.g. 0x41 0x0C for Mode 01 PID 0C)
        let dataBytes: [UInt8]
        if firstMsg.payload.count >= 2 && firstMsg.payload[0] == 0x41 {
            dataBytes = Array(firstMsg.payload.dropFirst(2))
        } else if firstMsg.payload.count >= 3 && firstMsg.payload[0] == 0x62 {
            // Mode 22 response (0x62 DID_HI DID_LO)
            dataBytes = Array(firstMsg.payload.dropFirst(3))
        } else {
            dataBytes = firstMsg.payload
        }

        guard let val = decoder.decode(pid: pid, dataBytes: dataBytes, imperial: imperial) else {
            throw TransportError.invalidResponse("Failed to calculate value for PID \(pid.name)")
        }

        let unit = (imperial ? pid.imperialUnit : pid.unit) ?? pid.unit
        let prec = pid.precision ?? 1
        let fmt = String(format: "%.\(prec)f", val)

        return LivePIDValue(pidId: pid.id, numericValue: val, formattedString: fmt, unit: unit)
    }

    /// Discovers active ECUs responding in standard 7E8..7EF range via broadcast header 7DF
    public func discoverECUs() async throws -> [String] {
        let prevHeader = driver.activeHeader
        defer {
            Task {
                try? await driver.setHeader(prevHeader)
            }
        }

        // Set broadcast header 7DF
        try await driver.setHeader("7DF")
        let raw = try await driver.executeWithRetry(command: "0100", retries: 2, timeoutSec: 2.5)
        let messages = isoTP.reassemble(response: raw)

        var detectedECUs = Set<String>()
        for msg in messages {
            if let canId = msg.canId?.uppercased() {
                // Check if in 7E8..7EF
                if canId >= "7E8" && canId <= "7EF" {
                    detectedECUs.insert(canId)
                }
            }
        }

        // Always fallback to standard 7E8 (PCM) if none detected
        if detectedECUs.isEmpty {
            detectedECUs.insert("7E8")
        }

        return detectedECUs.sorted()
    }

    /// Queries supported PIDs bitmask (0100, 0120, 0140...)
    public func discoverSupportedPids() async throws -> Set<String> {
        let detector = SupportedPidsDetector()
        var supported = Set<String>()
        var currentBase = 0x00

        while currentBase <= 0xC0 {
            let cmd = String(format: "01%02X", currentBase)
            guard let raw = try? await driver.executeWithRetry(command: cmd, retries: 1, timeoutSec: 2.0) else {
                break
            }
            let messages = isoTP.reassemble(response: raw)
            guard let firstMsg = messages.first, firstMsg.payload.count >= 6, firstMsg.payload[0] == 0x41 else {
                break
            }

            let maskBytes = Array(firstMsg.payload[2..<min(6, firstMsg.payload.count)])
            let (pagePids, hasNext) = detector.parseBitmask(bytes: maskBytes, basePid: currentBase)
            supported.formUnion(pagePids)

            if !hasNext {
                break
            }
            currentBase += 32
        }

        return supported
    }

    /// Polls a batch of up to 6 PIDs in a single request to reduce bus latency
    public func readPIDsBatch(_ pids: [PIDDefinition], imperial: Bool = false) async throws -> [String: LivePIDValue] {
        guard !pids.isEmpty else { return [:] }

        var results: [String: LivePIDValue] = [:]
        // Handle virtual / AT PIDs separately
        let standardPids = pids.filter { $0.mode == "01" }
        for atPid in pids.filter({ $0.mode != "01" }) {
            if let val = try? await readPID(atPid, imperial: imperial) {
                results[atPid.id] = val
            }
        }

        guard !standardPids.isEmpty else { return results }

        var i = 0
        while i < standardPids.count {
            let batchSize = max(1, maxBatchSize)
            let chunk = Array(standardPids[i..<min(i + batchSize, standardPids.count)])
            let cmd = "01" + chunk.map { $0.pid }.joined()

            guard let raw = try? await driver.executeWithRetry(command: cmd, retries: 1, timeoutSec: 2.0) else {
                if maxBatchSize > 1 {
                    reduceBatchSize()
                    continue
                } else {
                    i += 1
                    continue
                }
            }

            let upperRaw = raw.uppercased()
            if upperRaw.contains("?") || upperRaw.contains("BUFFER FULL") {
                if maxBatchSize > 1 {
                    reduceBatchSize()
                    continue
                } else {
                    i += 1
                    continue
                }
            }

            let messages = isoTP.reassemble(response: raw)
            guard let firstMsg = messages.first, firstMsg.payload.count >= 2, firstMsg.payload[0] == 0x41 else {
                if maxBatchSize > 1 {
                    reduceBatchSize()
                    continue
                } else {
                    i += 1
                    continue
                }
            }

            // Parse response bytes
            // In multi-PID response: 41 [PID1] [BYTES1] [PID2] [BYTES2]...
            var idx = 1
            let payload = firstMsg.payload
            var parsedAny = false
            while idx < payload.count {
                let pidHex = String(format: "%02X", payload[idx])
                idx += 1
                guard let targetDef = chunk.first(where: { $0.pid == pidHex }) else { break }
                let bytesCount = targetDef.byteCount
                guard idx + bytesCount <= payload.count else { break }
                let dataBytes = Array(payload[idx..<idx + bytesCount])
                idx += bytesCount

                if let val = decoder.decode(pid: targetDef, dataBytes: dataBytes, imperial: imperial) {
                    let unit = (imperial ? targetDef.imperialUnit : targetDef.unit) ?? targetDef.unit
                    let prec = targetDef.precision ?? 1
                    let fmt = String(format: "%.\(prec)f", val)
                    results[targetDef.id] = LivePIDValue(pidId: targetDef.id, numericValue: val, formattedString: fmt, unit: unit)
                    parsedAny = true
                }
            }

            if !parsedAny && maxBatchSize > 1 {
                reduceBatchSize()
                continue
            }

            i += chunk.count
        }

        return results
    }

    public var maxBatchSize: Int = 6

    private func reduceBatchSize() {
        if maxBatchSize > 4 {
            maxBatchSize = 4
        } else if maxBatchSize > 2 {
            maxBatchSize = 2
        } else {
            maxBatchSize = 1
        }
    }

    /// Reads stored DTCs using Mode 03
    public func readStoredDTCs() async throws -> [DTCRecord] {
        return try await readDTCsByMode(command: "03", expectedPrefixByte: 0x43, defaultType: "Confirmed")
    }

    /// Reads pending DTCs using Mode 07
    public func readPendingDTCs() async throws -> [DTCRecord] {
        return try await readDTCsByMode(command: "07", expectedPrefixByte: 0x47, defaultType: "Pending")
    }

    /// Reads permanent DTCs using Mode 0A
    public func readPermanentDTCs() async throws -> [DTCRecord] {
        return try await readDTCsByMode(command: "0A", expectedPrefixByte: 0x4A, defaultType: "Permanent")
    }

    /// Clears Diagnostic Trouble Codes and resets Readiness Monitor status using Mode 04
    public func clearAllDTCs() async throws {
        _ = try await driver.executeWithRetry(command: "04", retries: 2, timeoutSec: 4.0)
    }

    private func readDTCsByMode(command: String, expectedPrefixByte: UInt8, defaultType: String) async throws -> [DTCRecord] {
        let raw = try await driver.executeWithRetry(command: command, retries: 2, timeoutSec: 3.0)
        let messages = isoTP.reassemble(response: raw)

        var records: [DTCRecord] = []
        for msg in messages {
            guard msg.payload.count >= 2 && msg.payload[0] == expectedPrefixByte else { continue }
            let count = Int(msg.payload[1])
            guard count > 0 else { continue }

            var idx = 2
            while idx + 1 < msg.payload.count && records.count < count {
                let b0 = msg.payload[idx]
                let b1 = msg.payload[idx + 1]
                idx += 2
                if b0 == 0 && b1 == 0 { continue }

                let code = PIDDecoder.decodeDTC(b0: b0, b1: b1)
                let prefix = String(code.prefix(1))

                records.append(DTCRecord(
                    code: code,
                    system: prefix == "P" ? "Powertrain" : (prefix == "B" ? "Body" : "Chassis"),
                    description: "\(defaultType) Diagnostic Trouble Code",
                    severity: defaultType == "Permanent" ? "High" : (defaultType == "Confirmed" ? "Medium" : "Low")
                ))
            }
        }
        return records
    }
}


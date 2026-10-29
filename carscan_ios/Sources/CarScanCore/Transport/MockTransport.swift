import Foundation

public final class MockTransport: Transport, @unchecked Sendable {
    public private(set) var state: TransportState = .disconnected
    public var simulatedLatencySec: TimeInterval = 0.05
    public var simulatedVin: String = "1FA6P8CF5M5123456" // 10th char 'M' = 2021 per ISO 3779
    public var simulatedVoltage: Double = 12.6
    public var simulatedRpm: Double = 850.0
    public var simulatedSpeed: Double = 45.0
    public var simulatedEct: Double = 90.0
    public var headersEnabled: Bool = false
    public var spacesEnabled: Bool = true
    public var linefeedsEnabled: Bool = true
    public var currentHeader: String = "7E0"
    public var simulatedDTCs: [String: [String]] = [
        "7E0": ["P0171", "P0300"],
        "726": ["B11D909"],
        "760": [],
        "7D0": [],
        "7E1": []
    ]

    private let lock = NSLock()

    public init(vin: String = "1FA6P8CF5M5123456") {
        self.simulatedVin = vin
    }

    public func connect() async throws {
        lock.lock()
        defer { lock.unlock() }
        state = .connecting
        if simulatedLatencySec > 0 {
            try? await Task.sleep(nanoseconds: UInt64(simulatedLatencySec * 1_000_000_000))
        }
        state = .connected
    }

    public func disconnect() {
        lock.lock()
        defer { lock.unlock() }
        state = .disconnected
    }

    public var isSTNAdapter: Bool = false
    public var isVlinkerAdapter: Bool = false
    public var rejectBusSwitch: Bool = false
    public var simulateNRC78: Bool = false
    public var forcedError: String? = nil
    public var unresponsiveHeaders: Set<String> = ["736", "754"]
    public var sentCommands: [String] = []
    public var simulatedStdi: String = "OBDLink EX"

    public func send(command: String, timeoutSec: TimeInterval) async throws -> String {
        lock.lock()
        defer { lock.unlock() }

        guard state == .connected else {
            throw TransportError.notConnected
        }

        if simulatedLatencySec > 0 {
            try? await Task.sleep(nanoseconds: UInt64(simulatedLatencySec * 1_000_000_000))
        }

        let clean = command.trimmingCharacters(in: .whitespacesAndNewlines).uppercased().replacingOccurrences(of: " ", with: "")
        sentCommands.append(clean)

        // Handle AT and ST commands
        if clean.hasPrefix("AT") || clean.hasPrefix("ST") {
            return handleATCommand(clean)
        }

        // Handle OBD2 / UDS commands
        return handleOBDCommand(clean)
    }

    private func handleATCommand(_ cmd: String) -> String {
        if rejectBusSwitch && (cmd.hasPrefix("STP") || cmd == "ATHSCAN" || cmd == "ATMSCAN" || cmd == "ATSPB") {
            return "?\r\r>"
        }
        switch cmd {
        case "ATZ", "ATWS":
            headersEnabled = false
            spacesEnabled = true
            linefeedsEnabled = true
            currentHeader = "7E0"
            return "\r\rELM327 v1.5\r\r>"
        case "ATE0":
            return "OK\r\r>"
        case "ATE1":
            return "OK\r\r>"
        case "ATL0":
            linefeedsEnabled = false
            return "OK\r\r>"
        case "ATL1":
            linefeedsEnabled = true
            return "OK\r\r>"
        case "ATS0":
            spacesEnabled = false
            return "OK\r\r>"
        case "ATS1":
            spacesEnabled = true
            return "OK\r\r>"
        case "ATH0":
            headersEnabled = false
            return "OK\r\r>"
        case "ATH1":
            headersEnabled = true
            return "OK\r\r>"
        case let c where c.hasPrefix("ATSP"):
            return "OK\r\r>"
        case "ATDP":
            return "ISO 15765-4 (CAN 11/500)\r\r>"
        case "ATRV":
            return String(format: "%.1fV\r\r>", simulatedVoltage)
        case let c where c.hasPrefix("ATSH"):
            currentHeader = String(c.dropFirst(4))
            return "OK\r\r>"
        case let c where c.hasPrefix("ATCRA"):
            return "OK\r\r>"
        case let c where c.hasPrefix("ATPB"):
            return "OK\r\r>"
        case let c where c.hasPrefix("ATST"):
            return "OK\r\r>"
        case "ATI":
            return isVlinkerAdapter ? "vLinker FS v2.2\r\r>" : "ELM327 v1.5\r\r>"
        case "STI":
            return isSTNAdapter ? "STN2120 v5.1.0\r\r>" : "?\r\r>"
        case "STDI":
            return isSTNAdapter ? "\(simulatedStdi)\r\r>" : "?\r\r>"
        case let c where c.hasPrefix("STP"):
            return "OK\r\r>"
        case "ATHSCAN", "ATMSCAN":
            return "OK\r\r>"
        default:
            return "OK\r\r>"
        }
    }

    private func handleOBDCommand(_ cmd: String) -> String {
        if let err = forcedError {
            return "\(err)\r\r>"
        }

        if unresponsiveHeaders.contains(currentHeader) {
            return "NO DATA\r\r>"
        }

        let rxId: String
        switch currentHeader {
        case "7E0": rxId = "7E8"
        case "7E1": rxId = "7E9"
        case "726": rxId = "72E"
        case "760": rxId = "768"
        case "7D0": rxId = "7D8"
        case "720": rxId = "728"
        case "737": rxId = "73F"
        default: rxId = "7E8"
        }

        if cmd.hasPrefix("01") {
            let rawPids = String(cmd.dropFirst(2))
            if rawPids.count == 2 {
                return handleMode01(rxId: rxId, pid: rawPids)
            } else if rawPids.count > 2 && rawPids.count % 2 == 0 {
                var pids: [String] = []
                var idx = rawPids.startIndex
                while idx < rawPids.endIndex {
                    let next = rawPids.index(idx, offsetBy: 2)
                    pids.append(String(rawPids[idx..<next]))
                    idx = next
                }
                return handleMode01Multi(rxId: rxId, pids: pids)
            }
            return "NO DATA\r\r>"
        } else if cmd == "03" {
            return handleMode03(rxId: rxId, txId: currentHeader)
        } else if cmd == "04" {
            simulatedDTCs[currentHeader] = []
            return formatSingleFrame(rxId: rxId, tokens: ["44"])
        } else if cmd.hasPrefix("14") {
            simulatedDTCs[currentHeader] = []
            if simulateNRC78 {
                let nrcLine = headersEnabled ? formatLine(header: rxId, tokens: ["03", "7F", "14", "78"]) : "7F 14 78"
                let okLine = headersEnabled ? formatLine(header: rxId, tokens: ["01", "54"]) : "54"
                return "\(nrcLine)\r\(okLine)\r\r>"
            } else {
                return formatSingleFrame(rxId: rxId, tokens: ["54"])
            }
        } else if cmd == "07" {
            return formatSingleFrame(rxId: rxId, tokens: ["47", "00"])
        } else if cmd == "0A" {
            return formatSingleFrame(rxId: rxId, tokens: ["4A", "00"])
        } else if cmd.hasPrefix("09") {
            let pid = String(cmd.dropFirst(2).prefix(2))
            return handleMode09(rxId: rxId, pid: pid)
        } else if cmd.hasPrefix("22") {
            let did = String(cmd.dropFirst(2))
            return handleMode22(rxId: rxId, did: did)
        } else if cmd.hasPrefix("19") {
            return handleMode19(rxId: rxId, txId: currentHeader)
        } else if cmd.hasPrefix("31") {
            let sub = String(cmd.dropFirst(2).prefix(2))
            let routine = String(cmd.dropFirst(4).prefix(4))
            let r1 = String(routine.prefix(2))
            let r2 = String(routine.suffix(2))
            return formatSingleFrame(rxId: rxId, tokens: ["71", sub.isEmpty ? "01" : sub, r1.isEmpty ? "00" : r1, r2.isEmpty ? "00" : r2, "00"])
        } else if cmd.hasPrefix("11") {
            let sub = String(cmd.dropFirst(2).prefix(2))
            return formatSingleFrame(rxId: rxId, tokens: ["51", sub.isEmpty ? "01" : sub])
        } else if cmd.hasPrefix("3E") {
            return formatSingleFrame(rxId: rxId, tokens: ["7E", "00"])
        } else if cmd.hasPrefix("10") {
            let sub = String(cmd.dropFirst(2).prefix(2))
            return formatSingleFrame(rxId: rxId, tokens: ["50", sub.isEmpty ? "01" : sub, "00", "32", "01", "F4"])
        }

        return "NO DATA\r\r>"
    }

    private func getPidDataTokens(pid: String) -> [String]? {
        switch pid {
        case "00":
            return ["BE", "3F", "B8", "13"]
        case "20":
            return ["80", "07", "20", "01"]
        case "40":
            return ["7E", "D0", "00", "00"]
        case "60":
            return ["00", "00", "00", "00"]
        case "0C":
            let jitter = Double.random(in: -15...15)
            let rpm = max(600, simulatedRpm + jitter)
            let raw = Int(rpm * 4.0)
            return [String(format: "%02X", (raw >> 8) & 0xFF), String(format: "%02X", raw & 0xFF)]
        case "0D":
            return [String(format: "%02X", Int(simulatedSpeed))]
        case "05":
            return [String(format: "%02X", Int(simulatedEct + 40.0))]
        case "0F":
            return [String(format: "%02X", Int(28.0 + 40.0))]
        case "0B":
            return ["23"]
        case "10":
            let val = Int(4.5 * 100.0)
            return [String(format: "%02X", (val >> 8) & 0xFF), String(format: "%02X", val & 0xFF)]
        case "11":
            return ["26"]
        case "04":
            return ["38"]
        case "2F":
            return ["AD"]
        case "42":
            let rawV = Int(simulatedVoltage * 1000.0)
            return [String(format: "%02X", (rawV >> 8) & 0xFF), String(format: "%02X", rawV & 0xFF)]
        case "46":
            return ["41"] // 25°C
        default:
            return nil
        }
    }

    private func handleMode01(rxId: String, pid: String) -> String {
        if pid == "00" && currentHeader == "7DF" {
            let eol = "\r"
            let l1 = headersEnabled ? formatLine(header: "7E8", tokens: ["06", "41", "00", "BE", "3F", "B8", "13"]) : "41 00 BE 3F B8 13"
            let l2 = headersEnabled ? formatLine(header: "7E9", tokens: ["06", "41", "00", "80", "00", "00", "01"]) : "41 00 80 00 00 01"
            return "\(l1)\(eol)\(l2)\(eol)>"
        }

        if let tokens = getPidDataTokens(pid: pid) {
            return formatSingleFrame(rxId: rxId, tokens: ["41", pid] + tokens)
        }
        return "NO DATA\r\r>"
    }

    public var multiPidRejectMessage: String? = nil

    private func handleMode01Multi(rxId: String, pids: [String]) -> String {
        if let reject = multiPidRejectMessage, pids.count > 1 {
            return "\(reject)\r\r>"
        }
        var combined: [String] = ["41"]
        for p in pids {
            if let data = getPidDataTokens(pid: p) {
                combined.append(p)
                combined.append(contentsOf: data)
            }
        }
        if combined.count > 1 {
            return formatSingleFrame(rxId: rxId, tokens: combined)
        }
        return "NO DATA\r\r>"
    }

    private func handleMode09(rxId: String, pid: String) -> String {
        if pid == "02" {
            // Mode 09 PID 02 VIN multi-frame ISO-TP
            let asciiBytes = simulatedVin.utf8.map { String(format: "%02X", $0) }
            var payload = ["49", "02", "01"] + asciiBytes // 20 bytes total (0x14)
            let totalLen = payload.count

            let ffData = ["10", String(format: "%02X", totalLen)] + Array(payload[0..<6])
            let cf1Data = ["21"] + Array(payload[6..<min(13, payload.count)])
            let cf2Data = ["22"] + Array(payload[13..<min(20, payload.count)])

            let eol = "\r"
            if headersEnabled {
                let l1 = formatLine(header: rxId, tokens: ffData)
                let l2 = formatLine(header: rxId, tokens: cf1Data)
                let l3 = formatLine(header: rxId, tokens: cf2Data)
                return "\(l1)\(eol)\(l2)\(eol)\(l3)\(eol)>"
            } else {
                let l0 = String(format: "0%02X", totalLen)
                let l1 = "0: " + payload[0..<6].joined(separator: " ")
                let l2 = "1: " + payload[6..<13].joined(separator: " ")
                let l3 = "2: " + payload[13..<20].joined(separator: " ")
                return "\(l0)\(eol)\(l1)\(eol)\(l2)\(eol)\(l3)\(eol)>"
            }
        }
        return "NO DATA\r\r>"
    }

    private func handleMode03(rxId: String, txId: String) -> String {
        let codes = simulatedDTCs[txId] ?? []
        if codes.isEmpty {
            return formatSingleFrame(rxId: rxId, tokens: ["43", "00"])
        }
        var tokens = ["43", String(format: "%02X", codes.count)]
        for code in codes.prefix(3) {
            let first = code.first ?? "P"
            let typeBits: Int
            switch first {
            case "P": typeBits = 0
            case "C": typeBits = 1
            case "B": typeBits = 2
            default: typeBits = 3
            }
            let d1 = Int(String(code.dropFirst().prefix(1))) ?? 0
            let d2 = Int(String(code.dropFirst(2).prefix(1)), radix: 16) ?? 0
            let b0 = (typeBits << 6) | (d1 << 4) | d2
            let b1 = Int(String(code.dropFirst(3).prefix(2)), radix: 16) ?? 0
            tokens.append(String(format: "%02X", b0))
            tokens.append(String(format: "%02X", b1))
        }
        return formatSingleFrame(rxId: rxId, tokens: tokens)
    }

    private func handleMode22(rxId: String, did: String) -> String {
        switch did {
        case "028C": // CHT: 95°C
            return formatSingleFrame(rxId: rxId, tokens: ["62", "02", "8C", "02", "1C"])
        case "1E1C": // TFT: 82°C
            return formatSingleFrame(rxId: rxId, tokens: ["62", "1E", "1C", "07", "A0"])
        case "0367": // SOC: 85%
            return formatSingleFrame(rxId: rxId, tokens: ["62", "03", "67", "55"])
        default:
            return "NO DATA\r\r>"
        }
    }

    private func handleMode19(rxId: String, txId: String) -> String {
        let codes = simulatedDTCs[txId] ?? []
        if codes.isEmpty {
            return formatSingleFrame(rxId: rxId, tokens: ["59", "02", "FF"])
        }
        var tokens = ["59", "02", "FF"]
        for c in codes {
            if c.hasPrefix("B11D9") {
                tokens.append(contentsOf: ["91", "D9", "09", "2F"])
            } else if c.hasPrefix("B311D") {
                tokens.append(contentsOf: ["B1", "1D", "09", "2F"])
            } else if c == "P0171" {
                tokens.append(contentsOf: ["01", "71", "00", "2F"])
            } else if c == "P0300" {
                tokens.append(contentsOf: ["03", "00", "00", "2F"])
            }
        let base = formatSingleFrame(rxId: rxId, tokens: tokens)
        if simulateNRC78 {
            let nrcLine = headersEnabled ? formatLine(header: rxId, tokens: ["03", "7F", "19", "78"]) : "7F 19 78"
            return "\(nrcLine)\r\(base)"
        } else {
            return base
        }
    }

    private func formatSingleFrame(rxId: String, tokens: [String]) -> String {
        let lenStr = String(format: "%02X", tokens.count)
        let sfTokens = [lenStr] + tokens
        let line = headersEnabled ? formatLine(header: rxId, tokens: sfTokens) : formatDataOnly(tokens: tokens)
        return "\(line)\r\r>"
    }

    private func formatLine(header: String, tokens: [String]) -> String {
        if spacesEnabled {
            return "\(header) " + tokens.joined(separator: " ")
        } else {
            return "\(header)" + tokens.joined()
        }
    }

    private func formatDataOnly(tokens: [String]) -> String {
        if spacesEnabled {
            return tokens.joined(separator: " ")
        } else {
            return tokens.joined()
        }
    }
}

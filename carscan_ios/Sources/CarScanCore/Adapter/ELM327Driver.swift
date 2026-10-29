import Foundation

public final class ELM327Driver: @unchecked Sendable {
    public let transport: any Transport
    public private(set) var isInitialized: Bool = false
    public private(set) var adapterVersion: String = ""
    public private(set) var batteryVoltage: Double = 0.0
    public private(set) var activeHeader: String = "7E0"
    public private(set) var currentBus: String = "HS-CAN"
    public private(set) var adapterProfile: AdapterProfile = AdapterProfile(
        id: "ELM327_GENERIC",
        name: "Standard ELM327",
        family: .elm327Generic,
        autoBusSwitch: false,
        verified: false
    )

    public var traceLogger: RawTraceLogger? = nil

    public init(transport: any Transport) {
        self.transport = transport
    }

    /// Executes the standard Phase 0 initialization sequence:
    /// ATZ -> ATE0 -> ATL0 -> ATS0 -> ATH1 -> ATSP6 -> ATRV -> detectAdapterProfile
    public func initialize() async throws {
        // Step 1: Software Reset
        let resetResponse = try await executeWithRetry(command: "ATZ", retries: 2, timeoutSec: 3.0)
        adapterVersion = cleanResponse(resetResponse)

        // Step 2: Echo Off
        _ = try await executeWithRetry(command: "ATE0", retries: 2)

        // Step 3: Linefeeds Off
        _ = try await executeWithRetry(command: "ATL0", retries: 2)

        // Step 4: Spaces Off
        _ = try await executeWithRetry(command: "ATS0", retries: 2)

        // Step 5: Headers On
        _ = try await executeWithRetry(command: "ATH1", retries: 2)

        // Step 6: Protocol ISO 15765-4 (CAN 11-bit 500k)
        _ = try await executeWithRetry(command: "ATSP6", retries: 2)
        currentBus = "HS-CAN"

        // Step 7: Read Voltage
        if let vStr = try? await executeWithRetry(command: "ATRV", retries: 1) {
            batteryVoltage = parseVoltage(vStr)
        }

        // Step 8: Detect Hardware Profile
        adapterProfile = try await detectAdapterProfile()

        isInitialized = true
    }

    /// Detects adapter capabilities via ATI / STI / ATZ
    public func detectAdapterProfile() async throws -> AdapterProfile {
        // 1. Check ATI for vLinker
        if let atiResp = try? await executeWithRetry(command: "ATI", retries: 1, timeoutSec: 1.5) {
            let upper = cleanResponse(atiResp).uppercased()
            if upper.contains("VLINKER") || upper.contains("VGATE") {
                return AdapterProfile(
                    id: "VLINKER_FS",
                    name: "vLinker (FS/MC/FD)",
                    family: .vlinkerFS,
                    autoBusSwitch: true,
                    verified: false
                )
            }
        }

        // 2. Check STI / STDI for STN / OBDLink
        if let stiResp = try? await executeWithRetry(command: "STI", retries: 1, timeoutSec: 1.5) {
            let upper = cleanResponse(stiResp).uppercased()
            if upper.contains("STN") || upper.contains("OBDLINK") {
                var modelName = "OBDLink (STN)"
                var hasMsCan = true
                var autoBusSwitch = true

                if let stdiResp = try? await executeWithRetry(command: "STDI", retries: 1, timeoutSec: 1.5) {
                    let combined = "\(upper) \(cleanResponse(stdiResp).uppercased())"
                    if combined.contains("LX") || combined.contains("CX") || combined.contains("SX") || combined.contains("STN1110") {
                        hasMsCan = false
                        autoBusSwitch = false
                        modelName = combined.contains("CX") ? "OBDLink CX" : (combined.contains("LX") ? "OBDLink LX" : "OBDLink (HS-CAN Only)")
                    } else if combined.contains("EX") {
                        hasMsCan = true
                        modelName = "OBDLink EX"
                    } else if combined.contains("MX+") {
                        hasMsCan = true
                        modelName = "OBDLink MX+"
                    } else if combined.contains("MX") {
                        hasMsCan = true
                        modelName = "OBDLink MX"
                    }
                }

                return AdapterProfile(
                    id: "OBDLINK_STN",
                    name: modelName,
                    family: .obdlinkSTN,
                    autoBusSwitch: autoBusSwitch,
                    verified: false,
                    hasMsCan: hasMsCan
                )
            }
        }

        return AdapterProfile(
            id: "ELM327_GENERIC",
            name: "Standard ELM327",
            family: .elm327Generic,
            autoBusSwitch: false,
            verified: false,
            hasMsCan: true
        )
    }

    /// Switches between HS-CAN (500k) and MS-CAN (125k) buses
    public func switchBus(to targetBus: String, onManualSwitchPrompt: ((String) -> Void)? = nil) async throws {
        let normalized = targetBus.uppercased()
        guard normalized != currentBus.uppercased() else { return }

        do {
            switch normalized {
            case "MS-CAN":
                guard adapterProfile.hasMsCan else {
                    throw TransportError.busSwitchFailed("Adapter does not support MS-CAN (\(adapterProfile.name)). Bus switch aborted.")
                }
                switch adapterProfile.family {
                case .obdlinkSTN:
                    // STP 53: ISO 15765-4, 11-bit, 125 kbps, secondary pins 3/11 (OBDLink FRPM rev F, 8.6, p.34)
                    let r = try await executeWithRetry(command: "STP53", retries: 2)
                    if isRejectedOrError(r) { throw TransportError.busSwitchFailed("Adapter rejected STP53") }
                case .vlinkerFS:
                    // UNVERIFIED: ATHSCAN & ATMSCAN lack official manufacturer documentation
                    let r1 = try await executeWithRetry(command: "ATMSCAN", retries: 2)
                    if isRejectedOrError(r1) { throw TransportError.busSwitchFailed("Adapter rejected ATMSCAN with '?'") }
                    let r2 = try await executeWithRetry(command: "ATSPB", retries: 2)
                    if isRejectedOrError(r2) { throw TransportError.busSwitchFailed("Adapter rejected ATSPB with '?'") }
                default:
                    let prompt = "Flip adapter hardware switch to MS-CAN (OBD pins 3/11)"
                    onManualSwitchPrompt?(prompt)
                    let r = try await executeWithRetry(command: "ATSPB", retries: 2)
                    if isRejectedOrError(r) { throw TransportError.busSwitchFailed("Adapter rejected ATSPB") }
                }
                currentBus = "MS-CAN"
            case "HS-CAN":
                switch adapterProfile.family {
                case .obdlinkSTN:
                    // STP 33: ISO 15765-4, 11-bit, 500 kbps, primary pins 6/14 (OBDLink FRPM rev F, 8.6, p.34)
                    let r = try await executeWithRetry(command: "STP33", retries: 2)
                    if isRejectedOrError(r) { throw TransportError.busSwitchFailed("Adapter rejected STP33") }
                case .vlinkerFS:
                    // UNVERIFIED: ATHSCAN & ATMSCAN lack official manufacturer documentation
                    let r1 = try await executeWithRetry(command: "ATHSCAN", retries: 2)
                    if isRejectedOrError(r1) { throw TransportError.busSwitchFailed("Adapter rejected ATHSCAN with '?'") }
                    let r2 = try await executeWithRetry(command: "ATSP6", retries: 2)
                    if isRejectedOrError(r2) { throw TransportError.busSwitchFailed("Adapter rejected ATSP6 with '?'") }
                default:
                    let prompt = "Flip adapter hardware switch to HS-CAN (OBD pins 6/14)"
                    onManualSwitchPrompt?(prompt)
                    let r = try await executeWithRetry(command: "ATSP6", retries: 2)
                    if isRejectedOrError(r) { throw TransportError.busSwitchFailed("Adapter rejected ATSP6") }
                }
                currentBus = "HS-CAN"
            default:
                throw TransportError.busSwitchFailed("Unsupported bus: \(targetBus)")
            }
        } catch {
            if let tErr = error as? TransportError, case .busSwitchFailed = tErr {
                throw tErr
            }
            throw TransportError.busSwitchFailed(error.localizedDescription)
        }
    }

    private func isRejectedOrError(_ resp: String) -> Bool {
        let upper = resp.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return upper == "?" || upper.contains("ERROR") || upper.contains("UNABLE")
    }

    /// Sets the active CAN transmit header (e.g. 7E0 for PCM, 726 for BCM)
    public func setHeader(_ header: String) async throws {
        let clean = header.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard clean != activeHeader else { return }
        let resp = try await executeWithRetry(command: "ATSH \(clean)", retries: 2)
        if cleanResponse(resp).contains("OK") {
            activeHeader = clean
        } else {
            throw TransportError.commandFailed("Failed to set CAN header: \(clean)")
        }
    }

    /// Sends a command to the adapter with automatic retry on timeout and error classification
    public func executeWithRetry(command: String, retries: Int = 2, timeoutSec: TimeInterval = 3.0) async throws -> String {
        var lastError: Error?
        for attempt in 0...retries {
            do {
                traceLogger?.logTx(command)
                let response = try await transport.send(command: command, timeoutSec: timeoutSec)
                traceLogger?.logRx(response)
                var cleaned = cleanResponse(response)

                if cleaned.contains("SEARCHING...") {
                    cleaned = cleaned.replacingOccurrences(of: "SEARCHING...", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
                }

                try checkAdapterError(cleaned)
                return cleaned
            } catch let error as TransportError where error == .noData {
                traceLogger?.logRx("NO DATA")
                throw error
            } catch {
                traceLogger?.logRx("ERROR: \(error.localizedDescription)")
                lastError = error
                if attempt < retries {
                    let backoff = (error as? TransportError == .busBusy) ? 250_000_000 : 150_000_000
                    try? await Task.sleep(nanoseconds: UInt64(backoff))
                }
            }
        }
        throw lastError ?? TransportError.timeout
    }

    /// Cleans raw adapter output by stripping `>` prompt, echo artifacts, and whitespace
    public func cleanResponse(_ raw: String) -> String {
        var text = raw.replacingOccurrences(of: ">", with: "")
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return text
    }

    /// Classifies adapter error strings into strongly typed TransportErrors
    public func checkAdapterError(_ text: String) throws {
        let upper = text.uppercased()
        if upper == "NO DATA" || upper.hasPrefix("NO DATA") {
            throw TransportError.noData
        } else if upper.contains("CAN ERROR") {
            throw TransportError.canError
        } else if upper.contains("BUS BUSY") {
            throw TransportError.busBusy
        } else if upper.contains("UNABLE TO CONNECT") {
            throw TransportError.unableToConnect
        } else if upper.contains("STOPPED") {
            throw TransportError.adapterStopped
        } else if upper.contains("BUFFER FULL") {
            throw TransportError.bufferFull
        } else if upper.contains("BUS INIT: ERROR") || upper == "?" {
            throw TransportError.commandFailed(text)
        }
    }

    /// Checks if response indicates an adapter error
    public func isErrorResponse(_ text: String) -> Bool {
        let upper = text.uppercased()
        return upper.contains("CAN ERROR") ||
               upper.contains("BUS INIT: ERROR") ||
               upper.contains("UNABLE TO CONNECT") ||
               upper.contains("STOPPED") ||
               upper.contains("BUS BUSY") ||
               upper.contains("BUFFER FULL") ||
               upper == "?"
    }

    private func parseVoltage(_ str: String) -> Double {
        let cleaned = str.replacingOccurrences(of: "V", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        return Double(cleaned) ?? 0.0
    }
}

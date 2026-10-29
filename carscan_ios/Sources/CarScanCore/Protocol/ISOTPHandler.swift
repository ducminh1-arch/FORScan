import Foundation

public enum ISOTPFrameType: Int, Sendable {
    case single = 0
    case first = 1
    case consecutive = 2
    case flowControl = 3
}

public struct ISOTPMessage: Sendable, Equatable {
    public let canId: String?
    public let payload: [UInt8]

    public var asciiString: String {
        return payload.compactMap { byte -> String? in
            guard byte >= 32 && byte <= 126 else { return nil }
            return String(UnicodeScalar(byte))
        }.joined()
    }
}

public final class ISOTPHandler: Sendable {
    public init() {}

    /// Parses raw ELM327 response lines into an assembled ISO-TP message payload
    public func reassemble(response: String) -> [ISOTPMessage] {
        let lines = response.replacingOccurrences(of: "\r", with: "\n")
            .split(separator: "\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.contains(">") }

        var completed: [ISOTPMessage] = []
        var canId: String?
        var totalLength: Int = 0
        var accumulatedPayload: [UInt8] = []
        var nextExpectedSeq: Int = 1

        for line in lines {
            var lineCanId: String?
            let tokens = tokenizeHexLine(line, detectedCanId: &lineCanId)
            if let id = lineCanId { canId = id }
            guard let firstToken = tokens.first, let b0 = UInt8(firstToken, radix: 16) else {
                continue
            }

            let frameTypeNibble = Int((b0 >> 4) & 0x0F)
            guard let frameType = ISOTPFrameType(rawValue: frameTypeNibble) else {
                continue
            }

            switch frameType {
            case .single:
                let len = Int(b0 & 0x0F)
                let dataTokens = tokens.dropFirst()
                let bytes = dataTokens.prefix(len).compactMap { UInt8($0, radix: 16) }
                completed.append(ISOTPMessage(canId: canId, payload: bytes))

            case .first:
                guard tokens.count >= 2, let b1 = UInt8(tokens[1], radix: 16) else { continue }
                totalLength = (Int(b0 & 0x0F) << 8) | Int(b1)
                let dataTokens = tokens.dropFirst(2)
                accumulatedPayload = dataTokens.compactMap { UInt8($0, radix: 16) }
                nextExpectedSeq = 1

            case .consecutive:
                let seq = Int(b0 & 0x0F)
                let dataTokens = tokens.dropFirst()
                let bytes = dataTokens.compactMap { UInt8($0, radix: 16) }
                accumulatedPayload.append(contentsOf: bytes)
                nextExpectedSeq = (seq + 1) & 0x0F
                if totalLength > 0 && accumulatedPayload.count >= totalLength {
                    let finalBytes = Array(accumulatedPayload.prefix(totalLength))
                    completed.append(ISOTPMessage(canId: canId, payload: finalBytes))
                    accumulatedPayload.removeAll()
                    totalLength = 0
                }

            case .flowControl:
                break
            }
        }

        if !accumulatedPayload.isEmpty {
            let len = totalLength > 0 ? min(totalLength, accumulatedPayload.count) : accumulatedPayload.count
            completed.append(ISOTPMessage(canId: canId, payload: Array(accumulatedPayload.prefix(len))))
        }

        return completed
    }

    /// Tokenizes a line into 2-digit hex strings, separating the CAN ID if present
    public func tokenizeHexLine(_ line: String, detectedCanId: inout String?) -> [String] {
        var clean = line.trimmingCharacters(in: .whitespacesAndNewlines)

        if clean.contains(" ") {
            var parts = clean.split(separator: " ").map { String($0) }
            if let first = parts.first, (first.count == 3 || first.count == 8) {
                detectedCanId = first
                parts.removeFirst()
            }
            return parts
        } else {
            // Unspaced compact format (ATS0)
            if clean.count >= 5 {
                let first3 = String(clean.prefix(3))
                if Int(first3, radix: 16) != nil {
                    detectedCanId = first3
                    clean = String(clean.dropFirst(3))
                }
            }
            var tokens: [String] = []
            var index = clean.startIndex
            while index < clean.endIndex {
                let nextIndex = clean.index(index, offsetBy: 2, limitedBy: clean.endIndex) ?? clean.endIndex
                tokens.append(String(clean[index..<nextIndex]))
                index = nextIndex
            }
            return tokens
        }
    }
}

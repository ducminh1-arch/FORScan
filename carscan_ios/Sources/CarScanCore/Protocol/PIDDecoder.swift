import Foundation

public final class PIDDecoder: Sendable {
    public init() {}

    public func decode(pid: PIDDefinition, dataBytes: [UInt8], imperial: Bool = false) -> Double? {
        guard !dataBytes.isEmpty else { return nil }

        let a = Double(dataBytes[0])
        let b = dataBytes.count > 1 ? Double(dataBytes[1]) : 0.0

        var metricValue: Double = 0.0

        switch pid.id {
        case "PID_010C": // Engine RPM
            metricValue = ((a * 256.0) + b) / 4.0
            return metricValue

        case "PID_010D": // Vehicle Speed (km/h)
            metricValue = a
            return imperial ? (metricValue * 0.621371) : metricValue

        case "PID_0105", "PID_010F", "PID_0146": // Temperatures (°C)
            metricValue = a - 40.0
            return imperial ? ((metricValue * 1.8) + 32.0) : metricValue

        case "PID_010B": // MAP (kPa)
            metricValue = a
            return imperial ? (metricValue * 0.145038) : metricValue

        case "PID_0110": // MAF (g/s)
            metricValue = ((a * 256.0) + b) / 100.0
            return imperial ? (metricValue * 0.132277) : metricValue

        case "PID_0111", "PID_0104", "PID_012F": // Percentages 0-100%
            metricValue = (a * 100.0) / 255.0
            return metricValue

        case "PID_0142": // Control module voltage (V)
            metricValue = ((a * 256.0) + b) / 1000.0
            return metricValue

        case "PID_FORD_22028C": // Ford Cylinder Head Temp (CHT)
            metricValue = (((a * 256.0) + b) / 4.0) - 40.0
            return imperial ? ((metricValue * 1.8) + 32.0) : metricValue

        case "PID_FORD_221E1C": // Ford Transmission Fluid Temp (TFT)
            metricValue = (((a * 256.0) + b) / 16.0) - 40.0
            return imperial ? ((metricValue * 1.8) + 32.0) : metricValue

        case "PID_FORD_220367": // Ford Battery SOC (%)
            metricValue = a
            return metricValue

        default:
            // Fallback for single byte
            return a
        }
    }

    public static func decodeDTC(b0: UInt8, b1: UInt8) -> String {
        let typeBits = (b0 >> 6) & 0x03
        let prefix: String
        switch typeBits {
        case 0: prefix = "P"
        case 1: prefix = "C"
        case 2: prefix = "B"
        default: prefix = "U"
        }
        let d1 = (b0 >> 4) & 0x03
        let d2 = b0 & 0x0F
        let d3 = (b1 >> 4) & 0x0F
        let d4 = b1 & 0x0F
        return String(format: "%@%X%X%X%X", prefix, d1, d2, d3, d4)
    }

    public static func decodeDTC(bytes: [UInt8]) -> String {
        guard bytes.count >= 2 else { return "" }
        return decodeDTC(b0: bytes[0], b1: bytes[1])
    }
}


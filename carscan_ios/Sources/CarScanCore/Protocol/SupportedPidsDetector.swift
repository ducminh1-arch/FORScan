import Foundation

public final class SupportedPidsDetector: Sendable {
    public init() {}

    /// Parses 4 bytes of SAE J1979 bitmask into a set of supported PID hex strings.
    /// basePid is e.g. 0x00 for 01-20, 0x20 for 21-40, 0x40 for 41-60.
    public func parseBitmask(bytes: [UInt8], basePid: Int) -> (supportedPids: Set<String>, hasNextPage: Bool) {
        guard bytes.count >= 4 else { return ([], false) }
        var result = Set<String>()

        for byteIdx in 0..<4 {
            let byteVal = bytes[byteIdx]
            for bitIdx in 0..<8 {
                let mask: UInt8 = 1 << (7 - bitIdx)
                if (byteVal & mask) != 0 {
                    let pidNum = basePid + (byteIdx * 8) + (bitIdx + 1)
                    let pidHex = String(format: "%02X", pidNum)
                    result.insert(pidHex)
                }
            }
        }

        let nextPagePid = basePid + 32
        let nextPageHex = String(format: "%02X", nextPagePid)
        let hasNext = result.contains(nextPageHex)

        return (result, hasNext)
    }
}

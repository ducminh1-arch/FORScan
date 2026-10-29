import XCTest
@testable import CarScanCore

final class ISOTPTests: XCTestCase {
    func testReassembleSpacedVINMultiFrame() {
        let handler = ISOTPHandler()
        let sampleLog = """
        7E8 10 14 49 02 01 31 46 41
        7E8 21 36 50 38 43 46 35 48
        7E8 22 35 31 32 33 34 35 36
        >
        """

        let messages = handler.reassemble(response: sampleLog)
        XCTAssertEqual(messages.count, 1)

        let msg = messages[0]
        XCTAssertEqual(msg.canId, "7E8")
        XCTAssertEqual(msg.payload.count, 20)

        // Check Mode 09 positive response header
        XCTAssertEqual(msg.payload[0], 0x49)
        XCTAssertEqual(msg.payload[1], 0x02)
        XCTAssertEqual(msg.payload[2], 0x01)

        // Check VIN characters
        let vinBytes = Array(msg.payload[3..<20])
        let vinStr = String(bytes: vinBytes, encoding: .ascii)
        XCTAssertEqual(vinStr, "1FA6P8CF5H5123456")
    }

    func testReassembleUnspacedVINMultiFrame() {
        let handler = ISOTPHandler()
        let sampleLog = """
        7E81014490201314641
        7E82136503843463548
        7E82235313233343536
        >
        """

        let messages = handler.reassemble(response: sampleLog)
        XCTAssertEqual(messages.count, 1)

        let msg = messages[0]
        XCTAssertEqual(msg.canId, "7E8")
        XCTAssertEqual(msg.payload.count, 20)

        let vinBytes = Array(msg.payload[3..<20])
        let vinStr = String(bytes: vinBytes, encoding: .ascii)
        XCTAssertEqual(vinStr, "1FA6P8CF5H5123456")
    }

    func testSingleFrame() {
        let handler = ISOTPHandler()
        let sampleLog = "7E8 04 41 0C 0D 48\r\r>"
        let messages = handler.reassemble(response: sampleLog)
        XCTAssertEqual(messages.count, 1)
        XCTAssertEqual(messages[0].canId, "7E8")
        XCTAssertEqual(messages[0].payload, [0x41, 0x0C, 0x0D, 0x48])
    }
}

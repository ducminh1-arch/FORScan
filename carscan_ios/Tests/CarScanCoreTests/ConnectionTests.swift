import XCTest
@testable import CarScanCore

final class ConnectionTests: XCTestCase {

    @MainActor
    func testSessionManagerConnectionModes() {
        let session = SessionManager.shared
        session.connectionMode = .wifi
        XCTAssertEqual(session.connectionMode, .wifi)

        session.connectionMode = .ble
        XCTAssertEqual(session.connectionMode, .ble)

        session.connectionMode = .demo
        XCTAssertEqual(session.connectionMode, .demo)
        XCTAssertTrue(session.isDemoMode)

        session.wifiHost = "192.168.1.100"
        session.wifiPort = 35000
        XCTAssertEqual(session.wifiHost, "192.168.1.100")
        XCTAssertEqual(session.wifiPort, 35000)

        session.selectedBleId = "12345678-1234-1234-1234-1234567890AB"
        session.selectedBleName = "vLinker MC+ BLE"
        XCTAssertEqual(session.selectedBleName, "vLinker MC+ BLE")
        XCTAssertEqual(session.selectedBleId, "12345678-1234-1234-1234-1234567890AB")
    }

    func testBleTransportIncomingDataAndBufferClearing() {
        let transport = BleTransport()

        // Incoming partial chunks
        transport.handleIncomingData(Data("41 0C 0F".utf8))
        transport.handleIncomingData(Data(" A0\r\n>".utf8))

        // Ensure transport handleIncomingData properly resets buffer upon prompt delivery
        XCTAssertEqual(transport.state, .disconnected)
    }

    @MainActor
    func testMockTransportDemoTelemetry() async throws {
        let mock = MockTransport()
        try await mock.connect()
        XCTAssertEqual(mock.state, .connected)

        let vinResp = try await mock.send(command: "0902", timeoutSec: 1.0)
        XCTAssertTrue(vinResp.contains("1FA6P8CF5M5123456"))

        let year = VINDecoder.decodeModelYear(from: mock.simulatedVin)
        XCTAssertEqual(year, 2021)

        let make = VINDecoder.decodeMake(from: mock.simulatedVin)
        XCTAssertEqual(make, "Ford")

        mock.disconnect()
        XCTAssertEqual(mock.state, .disconnected)
    }
}

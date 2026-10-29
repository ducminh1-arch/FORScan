import XCTest
@testable import CarScanCore

final class ELM327DriverTests: XCTestCase {
    func testDriverInitSequence() async throws {
        let mock = MockTransport()
        let driver = ELM327Driver(transport: mock)

        try await mock.connect()
        try await driver.initialize()

        XCTAssertTrue(driver.isInitialized)
        XCTAssertTrue(driver.adapterVersion.contains("ELM327"))
        XCTAssertEqual(driver.batteryVoltage, 12.6, accuracy: 0.1)
    }

    func testSetHeader() async throws {
        let mock = MockTransport()
        let driver = ELM327Driver(transport: mock)

        try await mock.connect()
        try await driver.initialize()
        try await driver.setHeader("726")

        XCTAssertEqual(driver.activeHeader, "726")
        XCTAssertEqual(mock.currentHeader, "726")
    }

    func testCleanResponse() {
        let driver = ELM327Driver(transport: MockTransport())
        let dirty = "  OK\r\n\r\n>  "
        XCTAssertEqual(driver.cleanResponse(dirty), "OK")
    }

    func testErrorDetection() {
        let driver = ELM327Driver(transport: MockTransport())
        XCTAssertTrue(driver.isErrorResponse("CAN ERROR"))
        XCTAssertTrue(driver.isErrorResponse("BUS INIT: ERROR"))
        XCTAssertTrue(driver.isErrorResponse("?"))
        XCTAssertFalse(driver.isErrorResponse("41 0C 0D 48"))
    }
}

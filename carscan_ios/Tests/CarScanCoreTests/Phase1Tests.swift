import XCTest
@testable import CarScanCore

final class Phase1Tests: XCTestCase {
    func testSupportedPidsBitmaskParsing() {
        let detector = SupportedPidsDetector()
        // Bitmask for 01-20: BE 3F B8 13
        // Binary:
        // BE = 1011 1110 (01, 03, 04, 05, 06, 07)
        // 3F = 0011 1111 (0B, 0C, 0D, 0E, 0F, 10)
        // B8 = 1011 1000 (11, 13, 14, 15)
        // 13 = 0001 0011 (1C, 1F, 20) -> 20 is bit 32!
        let bytes: [UInt8] = [0xBE, 0x3F, 0xB8, 0x13]
        let (pids, hasNext) = detector.parseBitmask(bytes: bytes, basePid: 0)

        XCTAssertTrue(hasNext, "PID 20 must indicate hasNextPage")
        XCTAssertTrue(pids.contains("0C"), "Must contain Engine RPM (0C)")
        XCTAssertTrue(pids.contains("0D"), "Must contain Speed (0D)")
        XCTAssertTrue(pids.contains("05"), "Must contain ECT (05)")
        XCTAssertTrue(pids.contains("11"), "Must contain Throttle (11)")
    }

    func testECUDiscovery() async throws {
        let mock = MockTransport()
        let driver = ELM327Driver(transport: mock)
        let obd = OBD2Service(driver: driver)

        try await mock.connect()
        try await driver.initialize()

        let ecus = try await obd.discoverECUs()
        XCTAssertTrue(ecus.contains("7E8"), "Must detect PCM on 7E8")
        XCTAssertTrue(ecus.contains("7E9"), "Must detect TCM on 7E9")
    }

    func testMultiPidBatchQuery() async throws {
        let mock = MockTransport()
        let driver = ELM327Driver(transport: mock)
        let obd = OBD2Service(driver: driver)

        try await mock.connect()
        try await driver.initialize()

        let rpmPid = PIDDefinition(id: "PID_010C", mode: "01", pid: "0C", name: "RPM", shortName: "RPM", category: "Engine", module: "PCM", byteCount: 2, formula: "((A * 256) + B) / 4.0", unit: "RPM", imperialUnit: "RPM", min: 0, max: 8000, precision: 0, description: "")
        let spdPid = PIDDefinition(id: "PID_010D", mode: "01", pid: "0D", name: "Speed", shortName: "Speed", category: "Speed", module: "PCM", byteCount: 1, formula: "A", unit: "km/h", imperialUnit: "mph", min: 0, max: 255, precision: 0, description: "")
        let ectPid = PIDDefinition(id: "PID_0105", mode: "01", pid: "05", name: "ECT", shortName: "ECT", category: "Temperature", module: "PCM", byteCount: 1, formula: "A - 40.0", unit: "°C", imperialUnit: "°F", min: -40, max: 215, precision: 1, description: "")

        let batch = try await obd.readPIDsBatch([rpmPid, spdPid, ectPid])
        XCTAssertNotNil(batch["PID_010C"])
        XCTAssertNotNil(batch["PID_010D"])
        XCTAssertNotNil(batch["PID_0105"])

        XCTAssertEqual(batch["PID_010D"]?.numericValue, 45.0)
        XCTAssertEqual(batch["PID_0105"]?.numericValue, 90.0)
    }

    func testMode03AndMode07DTCs() async throws {
        let mock = MockTransport()
        let driver = ELM327Driver(transport: mock)
        let obd = OBD2Service(driver: driver)

        try await mock.connect()
        try await driver.initialize()

        let stored = try await obd.readStoredDTCs()
        XCTAssertEqual(stored.count, 2)
        XCTAssertEqual(stored[0].code, "P0171")
        XCTAssertEqual(stored[1].code, "P0300")

        let pending = try await obd.readPendingDTCs()
        XCTAssertEqual(pending.count, 0)
    }

    func testAdaptiveFallbackWhenAdapterReturnsQuestionMark() async throws {
        let mock = MockTransport()
        mock.multiPidRejectMessage = "?"
        let driver = ELM327Driver(transport: mock)
        let obd = OBD2Service(driver: driver)

        try await mock.connect()
        try await driver.initialize()

        let rpmPid = PIDDefinition(id: "PID_010C", mode: "01", pid: "0C", name: "RPM", shortName: "RPM", category: "Engine", module: "PCM", byteCount: 2, formula: "((A * 256) + B) / 4.0", unit: "RPM", imperialUnit: "RPM", min: 0, max: 8000, precision: 0, description: "")
        let spdPid = PIDDefinition(id: "PID_010D", mode: "01", pid: "0D", name: "Speed", shortName = "Speed", category: "Speed", module: "PCM", byteCount: 1, formula: "A", unit: "km/h", imperialUnit: "mph", min: 0, max: 255, precision: 0, description: "")

        XCTAssertEqual(obd.maxBatchSize, 6)

        let batch = await obd.readPIDsBatch(pids: [rpmPid, spdPid])
        XCTAssertEqual(obd.maxBatchSize, 1)
        XCTAssertNotNil(batch["PID_010C"])
        XCTAssertNotNil(batch["PID_010D"])
        XCTAssertEqual(batch["PID_010D"]?.numericValue, 45.0)
    }

    func testAdaptiveFallbackWhenAdapterReturnsBufferFull() async throws {
        let mock = MockTransport()
        mock.multiPidRejectMessage = "BUFFER FULL"
        let driver = ELM327Driver(transport: mock)
        let obd = OBD2Service(driver: driver)

        try await mock.connect()
        try await driver.initialize()

        let spdPid = PIDDefinition(id: "PID_010D", mode: "01", pid: "0D", name: "Speed", shortName = "Speed", category: "Speed", module = "PCM", byteCount: 1, formula: "A", unit: "km/h", imperialUnit: "mph", min: 0, max: 255, precision: 0, description: "")
        let ectPid = PIDDefinition(id: "PID_0105", mode: "01", pid: "05", name: "ECT", shortName = "ECT", category: "Temperature", module = "PCM", byteCount: 1, formula: "A - 40.0", unit: "°C", imperialUnit: "°F", min: -40, max: 215, precision: 1, description: "")

        let batch = await obd.readPIDsBatch(pids: [spdPid, ectPid])
        XCTAssertEqual(obd.maxBatchSize, 1)
        XCTAssertNotNil(batch["PID_010D"])
        XCTAssertNotNil(batch["PID_0105"])
        XCTAssertEqual(batch["PID_0105"]?.numericValue, 90.0)
    }
}


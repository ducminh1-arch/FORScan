import XCTest
@testable import CarScanCore

final class Phase0IntegrationTests: XCTestCase {
    func testPhase0SpikeWithMockTransport() async throws {
        let mockTransport = MockTransport(vin: "1FA6P8CF5M5123456")
        mockTransport.simulatedLatencySec = 0.001 // Fast for tests

        let spike = Phase0Spike()
        let report = try await spike.run(transport: mockTransport)

        XCTAssertTrue(report.isSuccess)
        XCTAssertEqual(report.vin, "1FA6P8CF5M5123456")
        XCTAssertEqual(VINDecoder.decodeModelYear(from: report.vin), 2021)
        XCTAssertEqual(report.batteryVoltage, 12.6, accuracy: 0.1)
        XCTAssertGreaterThan(report.sampleRpm, 700.0)
        XCTAssertLessThan(report.sampleRpm, 1000.0)
        XCTAssertEqual(report.dtcCount, 2) // P0171, P0300
    }
}

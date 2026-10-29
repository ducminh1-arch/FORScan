import XCTest
@testable import CarScanCore

final class PIDDecoderTests: XCTestCase {
    let decoder = PIDDecoder()

    func testRPMDecoding() {
        let rpmPid = PIDDefinition(
            id: "PID_010C",
            mode: "01",
            pid: "0C",
            name: "Engine RPM",
            shortName: "RPM",
            category: "Engine",
            module: "PCM",
            byteCount: 2,
            formula: "((A * 256) + B) / 4.0",
            unit: "RPM",
            imperialUnit: "RPM",
            min: 0,
            max: 8000,
            precision: 0,
            description: ""
        )
        // Raw bytes 0x0D 0x48 = (13 * 256 + 72) / 4 = 3400 / 4 = 850 RPM
        let val = decoder.decode(pid: rpmPid, dataBytes: [0x0D, 0x48])
        XCTAssertEqual(val, 850.0)
    }

    func testSpeedDecoding() {
        let spdPid = PIDDefinition(
            id: "PID_010D",
            mode: "01",
            pid: "0D",
            name: "Vehicle Speed",
            shortName: "VSS",
            category: "Speed",
            module: "PCM",
            byteCount: 1,
            formula: "A",
            unit: "km/h",
            imperialUnit: "mph",
            min: 0,
            max: 255,
            precision: 0,
            description: ""
        )
        let metric = decoder.decode(pid: spdPid, dataBytes: [100], imperial: false)
        XCTAssertEqual(metric, 100.0)

        let imperial = decoder.decode(pid: spdPid, dataBytes: [100], imperial: true)
        XCTAssertEqual(imperial!, 62.1371, accuracy: 0.01)
    }

    func testCoolantTempDecoding() {
        let ectPid = PIDDefinition(
            id: "PID_0105",
            mode: "01",
            pid: "05",
            name: "Engine Coolant Temperature",
            shortName: "ECT",
            category: "Temperature",
            module: "PCM",
            byteCount: 1,
            formula: "A - 40.0",
            unit: "°C",
            imperialUnit: "°F",
            min: -40,
            max: 215,
            precision: 1,
            description: ""
        )
        // 130 - 40 = 90°C
        let metric = decoder.decode(pid: ectPid, dataBytes: [130], imperial: false)
        XCTAssertEqual(metric, 90.0)

        // 90°C = 194°F
        let imperial = decoder.decode(pid: ectPid, dataBytes: [130], imperial: true)
        XCTAssertEqual(imperial, 194.0)
    }

    func testVoltageDecoding() {
        let vpwrPid = PIDDefinition(
            id: "PID_0142",
            mode: "01",
            pid: "42",
            name: "Control Module Voltage",
            shortName: "VPWR",
            category: "Electrical",
            module: "PCM",
            byteCount: 2,
            formula: "((A * 256) + B) / 1000.0",
            unit: "V",
            imperialUnit: "V",
            min: 0,
            max: 20.0,
            precision: 2,
            description: ""
        )
        // 0x35 0xE8 = (53 * 256 + 232) = 13800 mV = 13.8V
        let val = decoder.decode(pid: vpwrPid, dataBytes: [0x35, 0xE8])
        XCTAssertEqual(val!, 13.8, accuracy: 0.01)
    }

    func testDTCDecoding() {
        // User case 1: [0x91, 0xD9] -> B11D9
        let dtcB11D9 = PIDDecoder.decodeDTC(bytes: [0x91, 0xD9])
        XCTAssertEqual(dtcB11D9, "B11D9")

        // User case 2: [0xB1, 0x1D] -> B311D
        let dtcB311D = PIDDecoder.decodeDTC(bytes: [0xB1, 0x1D])
        XCTAssertEqual(dtcB311D, "B311D")

        // Standard OBD-II codes
        XCTAssertEqual(PIDDecoder.decodeDTC(bytes: [0x01, 0x71]), "P0171")
        XCTAssertEqual(PIDDecoder.decodeDTC(bytes: [0x03, 0x00]), "P0300")
        XCTAssertEqual(PIDDecoder.decodeDTC(bytes: [0x40, 0x31]), "C0031")
        XCTAssertEqual(PIDDecoder.decodeDTC(bytes: [0xC1, 0x00]), "U0100")
    }
}


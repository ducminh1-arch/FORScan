import XCTest
@testable import CarScanCore

final class Phase3Tests: XCTestCase {

    var tempDirectory: URL!

    override func setUpWithError() throws {
        tempDirectory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: tempDirectory)
    }

    func testCSVHeaderAndRecordFormatting() throws {
        let logger = CSVLogService()
        let fileURL = try logger.startLogging(outputDirectory: tempDirectory, sessionName: "session_test")

        XCTAssertTrue(FileManager.default.fileExists(atPath: fileURL.path))
        XCTAssertEqual(fileURL.lastPathComponent, "session_test.csv")

        let fixedDate1 = Date(timeIntervalSince1970: 1700000000)
        let fixedDate2 = Date(timeIntervalSince1970: 1700000001)

        logger.logPID(pid: "0C", name: "Engine RPM", value: 750.5, unit: "RPM", timestamp: fixedDate1)
        logger.logPID(pid: "0D", name: "Vehicle Speed, Primary", value: 45.0, unit: "km/h", timestamp: fixedDate2)
        _ = logger.stopLogging()

        let records = logger.readLogRecords(fileURL: fileURL)
        XCTAssertEqual(records.count, 2)

        XCTAssertEqual(records[0].pid, "0C")
        XCTAssertEqual(records[0].name, "Engine RPM")
        XCTAssertEqual(records[0].value, 750.5, accuracy: 0.001)
        XCTAssertEqual(records[0].unit, "RPM")

        // Commas properly handled
        XCTAssertEqual(records[1].pid, "0D")
        XCTAssertEqual(records[1].name, "Vehicle Speed, Primary")
        XCTAssertEqual(records[1].value, 45.0, accuracy: 0.001)
        XCTAssertEqual(records[1].unit, "km/h")

        // Check header
        let content = try String(contentsOf: fileURL, encoding: .utf8)
        let firstLine = content.components(separatedBy: .newlines).first
        XCTAssertEqual(firstLine, "timestamp,pid,name,value,unit")
    }

    func testCSVFileSizeLimitEnforcementAndRotation() throws {
        // Enforce limit of 300 bytes
        let limit: Int64 = 300
        let logger = CSVLogService(maxFileSizeBytes: limit)
        _ = try logger.startLogging(outputDirectory: tempDirectory, sessionName: "rotation_test")

        for i in 1...15 {
            logger.logPID(pid: "0C", name: "Engine RPM", value: 1000.0 + Double(i), unit: "RPM")
        }
        _ = logger.stopLogging()

        let files = try FileManager.default.contentsOfDirectory(at: tempDirectory, includingPropertiesForKeys: nil)
        XCTAssertTrue(files.count > 1, "Must rotate into multiple part files when size limit is reached")

        for f in files {
            let content = try String(contentsOf: f, encoding: .utf8)
            let lines = content.components(separatedBy: .newlines).filter { !$0.isEmpty }
            XCTAssertEqual(lines.first, "timestamp,pid,name,value,unit")
            XCTAssertTrue(lines.count > 1)
        }
    }

    func testRawTraceLoggerFormattingAndRotation() throws {
        let limit: Int64 = 400
        let traceLogger = RawTraceLogger(maxFileSizeBytes: limit)
        let traceFile = try traceLogger.startTrace(outputDirectory: tempDirectory, name: "debug_trace")

        XCTAssertTrue(FileManager.default.fileExists(atPath: traceFile.path))

        for _ in 1...10 {
            traceLogger.logTx("010C")
            traceLogger.logRx("41 0C 0E 20")
        }
        _ = traceLogger.stopTrace()

        let files = try FileManager.default.contentsOfDirectory(at: tempDirectory, includingPropertiesForKeys: nil)
        XCTAssertTrue(files.count > 1, "Must rotate trace files when limit is reached")

        let entries = traceLogger.readTrace(fileURL: traceFile)
        XCTAssertTrue(!entries.isEmpty)
        XCTAssertEqual(entries[0].direction, .tx)
        XCTAssertEqual(entries[0].text, "010C")
        XCTAssertEqual(entries[1].direction, .rx)
        XCTAssertEqual(entries[1].text, "41 0C 0E 20")
    }

    func testLogListingAndDeletion() throws {
        let csvLogger = CSVLogService()
        let traceLogger = RawTraceLogger()

        let csvFile = try csvLogger.startLogging(outputDirectory: tempDirectory, sessionName: "run_01")
        csvLogger.logPID(pid: "05", name: "ECT", value: 90.0, unit: "°C")
        _ = csvLogger.stopLogging()

        let traceFile = try traceLogger.startTrace(outputDirectory: tempDirectory, name: "trace_01")
        traceLogger.logTx("ATI")
        traceLogger.logRx("ELM327 v1.5")
        _ = traceLogger.stopTrace()

        let csvList = csvLogger.listSavedLogs(directory: tempDirectory)
        XCTAssertEqual(csvList.count, 1)
        XCTAssertEqual(csvList.first?.name, "run_01.csv")

        let traceList = traceLogger.listSavedTraces(directory: tempDirectory)
        XCTAssertEqual(traceList.count, 1)
        XCTAssertEqual(traceList.first?.name, "trace_01.log")

        // Delete files
        try csvLogger.deleteLog(fileURL: csvFile)
        XCTAssertFalse(FileManager.default.fileExists(atPath: csvFile.path))
        XCTAssertEqual(csvLogger.listSavedLogs(directory: tempDirectory).count, 0)

        try traceLogger.deleteTrace(fileURL: traceFile)
        XCTAssertFalse(FileManager.default.fileExists(atPath: traceFile.path))
        XCTAssertEqual(traceLogger.listSavedTraces(directory: tempDirectory).count, 0)
    }
}

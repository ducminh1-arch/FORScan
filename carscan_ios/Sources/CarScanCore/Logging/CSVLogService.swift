import Foundation

public struct CSVLogRecord: Equatable, Sendable {
    public let timestamp: String
    public let pid: String
    public let name: String
    public let value: Double
    public let unit: String

    public init(timestamp: String, pid: String, name: String, value: Double, unit: String) {
        self.timestamp = timestamp
        self.pid = pid
        self.name = name
        self.value = value
        self.unit = unit
    }
}

public struct LogFileInfo: Identifiable, Equatable, Sendable {
    public var id: String { fileURL.path }
    public let fileURL: URL
    public let name: String
    public let sizeBytes: Int64
    public let lastModified: Date
    public let recordCount: Int
    public let isRawTrace: Bool

    public init(fileURL: URL, name: String, sizeBytes: Int64, lastModified: Date, recordCount: Int, isRawTrace: Bool = false) {
        self.fileURL = fileURL
        self.name = name
        self.sizeBytes = sizeBytes
        self.lastModified = lastModified
        self.recordCount = recordCount
        self.isRawTrace = isRawTrace
    }
}

public final class CSVLogService: @unchecked Sendable {
    public static let csvHeader = "timestamp,pid,name,value,unit"
    public var maxFileSizeBytes: Int64

    private let lock = NSLock()
    private var fileHandle: FileHandle?
    public private(set) var currentFileURL: URL?
    private var baseSessionName: String = ""
    private var partIndex: Int = 1
    private var currentFileBytes: Int64 = 0
    private var targetDirectory: URL?
    public private(set) var isLoggingActive: Bool = false

    private let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    public init(maxFileSizeBytes: Int64 = 5 * 1024 * 1024) {
        self.maxFileSizeBytes = maxFileSizeBytes
    }

    public func startLogging(outputDirectory: URL, sessionName: String? = nil) throws -> URL {
        lock.lock()
        defer { lock.unlock() }

        _stopLoggingLocked()
        if !FileManager.default.fileExists(atPath: outputDirectory.path) {
            try FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: true)
        }
        targetDirectory = outputDirectory
        partIndex = 1

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        let timeStamp = formatter.string(from: Date())
        baseSessionName = sessionName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? sessionName! : "live_log_\(timeStamp)"

        return try _createNewFileAndHandleLocked()
    }

    private func _createNewFileAndHandleLocked() throws -> URL {
        guard let dir = targetDirectory else {
            throw NSError(domain: "CSVLogService", code: 1, userInfo: [NSLocalizedDescriptionKey: "Directory not set"])
        }
        let fileName = partIndex == 1 ? "\(baseSessionName).csv" : "\(baseSessionName)_part\(partIndex).csv"
        let fileURL = dir.appendingPathComponent(fileName)
        currentFileURL = fileURL

        let headerData = (Self.csvHeader + "\n").data(using: .utf8)!
        FileManager.default.createFile(atPath: fileURL.path, contents: headerData, attributes: nil)

        let handle = try FileHandle(forWritingTo: fileURL)
        handle.seekToEndOfFile()
        fileHandle = handle
        currentFileBytes = Int64(headerData.count)
        isLoggingActive = true
        return fileURL
    }

    public func logPID(pid: String, name: String, value: Double, unit: String, timestamp: Date = Date()) {
        lock.lock()
        defer { lock.unlock() }

        guard isLoggingActive, let handle = fileHandle else { return }

        let timeStr = isoFormatter.string(from: timestamp)
        let safeName = name.contains(",") ? "\"\(name)\"" : name
        let safeUnit = unit.contains(",") ? "\"\(unit)\"" : unit
        let line = "\(timeStr),\(pid),\(safeName),\(value),\(safeUnit)\n"
        guard let lineData = line.data(using: .utf8) else { return }

        // Size check & rotate
        if currentFileBytes + Int64(lineData.count) > maxFileSizeBytes {
            try? handle.close()
            partIndex += 1
            _ = try? _createNewFileAndHandleLocked()
        }

        if let activeHandle = fileHandle {
            activeHandle.write(lineData)
            currentFileBytes += Int64(lineData.count)
        }
    }

    public func stopLogging() -> URL? {
        lock.lock()
        defer { lock.unlock() }
        return _stopLoggingLocked()
    }

    @discardableResult
    private func _stopLoggingLocked() -> URL? {
        let file = currentFileURL
        try? fileHandle?.close()
        fileHandle = nil
        isLoggingActive = false
        return file
    }

    public func listSavedLogs(directory: URL) -> [LogFileInfo] {
        guard let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey], options: .skipsHiddenFiles) else {
            return []
        }
        let csvFiles = files.filter { $0.pathExtension.lowercased() == "csv" }
        return csvFiles.map { url in
            let attrs = (try? FileManager.default.attributesOfItem(atPath: url.path)) ?? [:]
            let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
            let date = (attrs[.modificationDate] as? Date) ?? Date()
            let count = countRecords(fileURL: url)
            return LogFileInfo(fileURL: url, name: url.lastPathComponent, sizeBytes: size, lastModified: date, recordCount: count, isRawTrace: false)
        }.sorted { $0.lastModified > $1.lastModified }
    }

    public func readLogRecords(fileURL: URL) -> [CSVLogRecord] {
        guard let content = try? String(contentsOf: fileURL, encoding: .utf8) else { return [] }
        var records: [CSVLogRecord] = []
        let lines = content.components(separatedBy: .newlines)
        for (idx, line) in lines.enumerated() {
            if idx == 0 || line.trimmingCharacters(in: .whitespaces).isEmpty { continue }
            if let rec = parseCSVLine(line) {
                records.append(rec)
            }
        }
        return records
    }

    public func parseCSVLine(_ line: String) -> CSVLogRecord? {
        var parts: [String] = []
        var inQuotes = false
        var current = ""
        for char in line {
            if char == "\"" {
                inQuotes.toggle()
            } else if char == "," && !inQuotes {
                parts.append(current.trimmingCharacters(in: .whitespaces))
                current = ""
            } else {
                current.append(char)
            }
        }
        parts.append(current.trimmingCharacters(in: .whitespaces))

        if parts.count >= 5 {
            let ts = parts[0]
            let pid = parts[1]
            let name = parts[2]
            let value = Double(parts[3]) ?? 0.0
            let unit = parts[4]
            return CSVLogRecord(timestamp: ts, pid: pid, name: name, value: value, unit: unit)
        }
        return nil
    }

    private func countRecords(fileURL: URL) -> Int {
        guard let content = try? String(contentsOf: fileURL, encoding: .utf8) else { return 0 }
        let lines = content.components(separatedBy: .newlines).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        return max(0, lines.count - 1)
    }

    public func deleteLog(fileURL: URL) throws {
        lock.lock()
        if currentFileURL?.path == fileURL.path {
            _ = _stopLoggingLocked()
        }
        lock.unlock()
        try FileManager.default.removeItem(at: fileURL)
    }
}

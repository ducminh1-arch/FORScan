import Foundation

public enum TraceDirection: String, Sendable {
    case tx = "TX"
    case rx = "RX"
}

public struct TraceEntry: Equatable, Sendable {
    public let timestamp: String
    public let direction: TraceDirection
    public let text: String

    public init(timestamp: String, direction: TraceDirection, text: String) {
        self.timestamp = timestamp
        self.direction = direction
        self.text = text
    }
}

public final class RawTraceLogger: @unchecked Sendable {
    public var maxFileSizeBytes: Int64

    private let lock = NSLock()
    private var fileHandle: FileHandle?
    public private(set) var currentFileURL: URL?
    private var baseName: String = ""
    private var partIndex: Int = 1
    private var currentFileBytes: Int64 = 0
    private var targetDirectory: URL?
    public private(set) var isTracingActive: Bool = false

    private let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    public init(maxFileSizeBytes: Int64 = 10 * 1024 * 1024) {
        self.maxFileSizeBytes = maxFileSizeBytes
    }

    public func startTrace(outputDirectory: URL, name: String? = nil) throws -> URL {
        lock.lock()
        defer { lock.unlock() }

        _stopTraceLocked()
        if !FileManager.default.fileExists(atPath: outputDirectory.path) {
            try FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: true)
        }
        targetDirectory = outputDirectory
        partIndex = 1

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        let timeStamp = formatter.string(from: Date())
        baseName = name?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? name! : "carscan_raw_trace_\(timeStamp)"

        return try _createTraceFileLocked()
    }

    private func _createTraceFileLocked() throws -> URL {
        guard let dir = targetDirectory else {
            throw NSError(domain: "RawTraceLogger", code: 1, userInfo: [NSLocalizedDescriptionKey: "Trace directory not configured"])
        }
        let fileName = partIndex == 1 ? "\(baseName).log" : "\(baseName)_part\(partIndex).log"
        let fileURL = dir.appendingPathComponent(fileName)
        currentFileURL = fileURL

        let header = "# CarScan Lite Raw Command Trace Log\n# Started: \(isoFormatter.string(from: Date()))\n# Format: [TIMESTAMP] <TX|RX>: <COMMAND|RESPONSE>\n\n"
        let headerData = header.data(using: .utf8)!
        FileManager.default.createFile(atPath: fileURL.path, contents: headerData, attributes: nil)

        let handle = try FileHandle(forWritingTo: fileURL)
        handle.seekToEndOfFile()
        fileHandle = handle
        currentFileBytes = Int64(headerData.count)
        isTracingActive = true
        return fileURL
    }

    public func logTx(_ command: String, timestamp: Date = Date()) {
        logLine(direction: .tx, content: command, timestamp: timestamp)
    }

    public func logRx(_ response: String, timestamp: Date = Date()) {
        logLine(direction: .rx, content: response, timestamp: timestamp)
    }

    private func logLine(direction: TraceDirection, content: String, timestamp: Date) {
        lock.lock()
        defer { lock.unlock() }

        guard isTracingActive, let handle = fileHandle else { return }

        let timeStr = isoFormatter.string(from: timestamp)
        let line = "[\(timeStr)] \(direction.rawValue): \(content)\n"
        guard let lineData = line.data(using: .utf8) else { return }

        // Size check & rotate
        if currentFileBytes + Int64(lineData.count) > maxFileSizeBytes {
            try? handle.close()
            partIndex += 1
            _ = try? _createTraceFileLocked()
        }

        if let activeHandle = fileHandle {
            activeHandle.write(lineData)
            currentFileBytes += Int64(lineData.count)
        }
    }

    public func stopTrace() -> URL? {
        lock.lock()
        defer { lock.unlock() }
        return _stopTraceLocked()
    }

    @discardableResult
    private func _stopTraceLocked() -> URL? {
        let file = currentFileURL
        try? fileHandle?.close()
        fileHandle = nil
        isTracingActive = false
        return file
    }

    public func listSavedTraces(directory: URL) -> [LogFileInfo] {
        guard let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey, .fileSizeKey], options: .skipsHiddenFiles) else {
            return []
        }
        let logFiles = files.filter { $0.pathExtension.lowercased() == "log" }
        return logFiles.map { url in
            let attrs = (try? FileManager.default.attributesOfItem(atPath: url.path)) ?? [:]
            let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
            let date = (attrs[.modificationDate] as? Date) ?? Date()
            let count = countLines(fileURL: url)
            return LogFileInfo(fileURL: url, name: url.lastPathComponent, sizeBytes: size, lastModified: date, recordCount: count, isRawTrace: true)
        }.sorted { $0.lastModified > $1.lastModified }
    }

    public func readTrace(fileURL: URL) -> [TraceEntry] {
        guard let content = try? String(contentsOf: fileURL, encoding: .utf8) else { return [] }
        var entries: [TraceEntry] = []
        let lines = content.components(separatedBy: .newlines)
        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("[") && trimmed.contains("]") {
                if let closeIdx = trimmed.firstIndex(of: "]") {
                    let tsStart = trimmed.index(after: trimmed.startIndex)
                    let ts = String(trimmed[tsStart..<closeIdx])
                    let rest = String(trimmed[trimmed.index(after: closeIdx)...]).trimmingCharacters(in: .whitespaces)
                    if let colonIdx = rest.firstIndex(of: ":") {
                        let dirStr = String(rest[..<colonIdx]).trimmingCharacters(in: .whitespaces)
                        let payload = String(rest[rest.index(after: colonIdx)...]).trimmingCharacters(in: .whitespaces)
                        let dir: TraceDirection = dirStr.uppercased() == "TX" ? .tx : .rx
                        entries.append(TraceEntry(timestamp: ts, direction: dir, text: payload))
                    }
                }
            }
        }
        return entries
    }

    private func countLines(fileURL: URL) -> Int {
        guard let content = try? String(contentsOf: fileURL, encoding: .utf8) else { return 0 }
        return content.components(separatedBy: .newlines).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.count
    }

    public func deleteTrace(fileURL: URL) throws {
        lock.lock()
        if currentFileURL?.path == fileURL.path {
            _ = _stopTraceLocked()
        }
        lock.unlock()
        try FileManager.default.removeItem(at: fileURL)
    }
}

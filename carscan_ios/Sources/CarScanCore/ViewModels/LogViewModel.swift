import Foundation
import Combine

@MainActor
public final class LogViewModel: ObservableObject {
    @Published public var savedLogs: [LogFileInfo] = []
    @Published public var selectedLogFile: URL?
    @Published public var selectedLogRecords: [CSVLogRecord] = []
    @Published public var selectedTraceEntries: [TraceEntry] = []
    @Published public var isViewingTrace: Bool = false
    @Published public var statusMessage: String?
    @Published public var isLoggingPIDs: Bool = false
    @Published public var isTracingCommands: Bool = false

    private let session: SessionManager
    private let logDirectory: URL
    private var cancellables = Set<AnyCancellable>()

    public init(session: SessionManager = .shared, logDirectory: URL? = nil) {
        self.session = session
        let defaultDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.appendingPathComponent("logs") ?? URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("logs")
        self.logDirectory = logDirectory ?? defaultDir

        session.$isLoggingPIDs.assign(to: &$isLoggingPIDs)
        session.$isTracingCommands.assign(to: &$isTracingCommands)

        refreshLogs()
    }

    public func refreshLogs() {
        let csvLogs = session.csvLogger.listSavedLogs(directory: logDirectory)
        let traceLogs = session.rawTraceLogger.listSavedTraces(directory: logDirectory)
        savedLogs = (csvLogs + traceLogs).sorted { $0.lastModified > $1.lastModified }
    }

    public func startPIDLogging(name: String? = nil) {
        do {
            let url = try session.startPIDLogging(directory: logDirectory, sessionName: name)
            statusMessage = "Recording live PIDs to \(url.lastPathComponent)"
            refreshLogs()
        } catch {
            statusMessage = "Failed to start logging: \(error.localizedDescription)"
        }
    }

    public func stopPIDLogging() {
        if let url = session.stopPIDLogging() {
            statusMessage = "Saved log to \(url.lastPathComponent)"
        } else {
            statusMessage = "Logging stopped"
        }
        refreshLogs()
    }

    public func startRawTrace(name: String? = nil) {
        do {
            let url = try session.startRawTracing(directory: logDirectory, name: name)
            statusMessage = "Recording raw command trace to \(url.lastPathComponent)"
            refreshLogs()
        } catch {
            statusMessage = "Failed to start trace: \(error.localizedDescription)"
        }
    }

    public func stopRawTrace() {
        if let url = session.stopRawTracing() {
            statusMessage = "Saved trace to \(url.lastPathComponent)"
        } else {
            statusMessage = "Trace stopped"
        }
        refreshLogs()
    }

    public func openLog(info: LogFileInfo) {
        selectedLogFile = info.fileURL
        isViewingTrace = info.isRawTrace
        if info.isRawTrace {
            selectedTraceEntries = session.rawTraceLogger.readTrace(fileURL: info.fileURL)
            selectedLogRecords = []
        } else {
            selectedLogRecords = session.csvLogger.readLogRecords(fileURL: info.fileURL)
            selectedTraceEntries = []
        }
    }

    public func closeLogViewer() {
        selectedLogFile = nil
        selectedLogRecords = []
        selectedTraceEntries = []
        isViewingTrace = false
    }

    public func deleteLog(info: LogFileInfo) {
        do {
            if info.isRawTrace {
                try session.rawTraceLogger.deleteTrace(fileURL: info.fileURL)
            } else {
                try session.csvLogger.deleteLog(fileURL: info.fileURL)
            }
            if selectedLogFile?.path == info.fileURL.path {
                closeLogViewer()
            }
            refreshLogs()
            statusMessage = "Deleted \(info.name)"
        } catch {
            statusMessage = "Failed to delete: \(error.localizedDescription)"
        }
    }
}

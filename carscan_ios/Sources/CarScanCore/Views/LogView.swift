import SwiftUI

public struct LogView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = LogViewModel()
    @ObservedObject private var session = SessionManager.shared
    @State private var fileToDelete: LogFileInfo?

    public init() {}

    public var body: some View {
        ZStack {
            Color(hex: "12151A").ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Bar
                HStack {
                    Button(action: { dismiss() }) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Back")
                        }
                        .foregroundColor(.cyan)
                    }
                    Spacer()
                    HStack(spacing: 8) {
                        Text("Diagnostics Logs & Trace")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                        if session.isDemoMode {
                            DemoChip()
                        }
                    }
                    Spacer()
                    Button(action: { viewModel.refreshLogs() }) {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(.cyan)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(hex: "1A202C"))

                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        // Status message banner
                        if let msg = viewModel.statusMessage {
                            Text(msg)
                                .font(.system(size: 13))
                                .foregroundColor(Color(hex: "64B5F6"))
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color(hex: "233044"))
                                .cornerRadius(8)
                        }

                        // Quick Action Buttons
                        HStack(spacing: 12) {
                            Button(action: {
                                if viewModel.isLoggingPIDs {
                                    viewModel.stopPIDLogging()
                                } else {
                                    viewModel.startPIDLogging()
                                }
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: viewModel.isLoggingPIDs ? "stop.circle.fill" : "record.circle")
                                    Text(viewModel.isLoggingPIDs ? "Stop PID Log" : "Record PIDs")
                                }
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(viewModel.isLoggingPIDs ? Color.red : Color.green)
                                .cornerRadius(8)
                            }

                            Button(action: {
                                if viewModel.isTracingCommands {
                                    viewModel.stopRawTrace()
                                } else {
                                    viewModel.startRawTrace()
                                }
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: viewModel.isTracingCommands ? "stop.circle.fill" : "terminal.fill")
                                    Text(viewModel.isTracingCommands ? "Stop Trace" : "Raw Trace")
                                }
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(viewModel.isTracingCommands ? Color.orange : Color.blue)
                                .cornerRadius(8)
                            }
                        }

                        // Detail Viewer if a log is selected
                        if let selectedFile = viewModel.selectedLogFile {
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(selectedFile.lastPathComponent)
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                        Text(viewModel.isViewingTrace ? "\(viewModel.selectedTraceEntries.count) command lines" : "\(viewModel.selectedLogRecords.count) PID records")
                                            .font(.system(size: 12))
                                            .foregroundColor(.gray)
                                    }
                                    Spacer()
                                    ShareLink(item: selectedFile) {
                                        Image(systemName: "square.and.arrow.up")
                                            .foregroundColor(.cyan)
                                    }
                                    Button(action: { viewModel.closeLogViewer() }) {
                                        Image(systemName: "xmark.circle.fill")
                                            .foregroundColor(.gray)
                                    }
                                }
                                Divider().background(Color.gray.opacity(0.3))

                                if viewModel.isViewingTrace {
                                    ForEach(Array(viewModel.selectedTraceEntries.prefix(100).enumerated()), id: \.offset) { _, entry in
                                        HStack(alignment: .top, spacing: 6) {
                                            Text(entry.direction.rawValue + ":")
                                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                                .foregroundColor(entry.direction == .tx ? .cyan : .green)
                                            Text(entry.text)
                                                .font(.system(size: 11, design: .monospaced))
                                                .foregroundColor(.white)
                                        }
                                        .padding(.vertical, 1)
                                    }
                                } else {
                                    HStack {
                                        Text("Time").font(.system(size: 11, weight: .bold)).foregroundColor(.gray).frame(maxWidth: .infinity, alignment: .leading)
                                        Text("PID").font(.system(size: 11, weight: .bold)).foregroundColor(.gray).frame(width: 45, alignment: .leading)
                                        Text("Name").font(.system(size: 11, weight: .bold)).foregroundColor(.gray).frame(maxWidth: .infinity, alignment: .leading)
                                        Text("Value").font(.system(size: 11, weight: .bold)).foregroundColor(.gray).frame(width: 60, alignment: .trailing)
                                    }
                                    Divider().background(Color.gray.opacity(0.3))
                                    ForEach(Array(viewModel.selectedLogRecords.prefix(100).enumerated()), id: \.offset) { _, rec in
                                        HStack {
                                            Text(rec.timestamp.components(separatedBy: "T").last?.replacingOccurrences(of: "Z", with: "") ?? rec.timestamp)
                                                .font(.system(size: 11, design: .monospaced))
                                                .foregroundColor(.white)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                            Text(rec.pid)
                                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                                .foregroundColor(.green)
                                                .frame(width: 45, alignment: .leading)
                                            Text(rec.name)
                                                .font(.system(size: 11))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                                .frame(maxWidth: .infinity, alignment: .leading)
                                            Text(String(format: "%.1f %@", rec.value, rec.unit))
                                                .font(.system(size: 11, weight: .semibold))
                                                .foregroundColor(.cyan)
                                                .frame(width: 60, alignment: .trailing)
                                        }
                                        .padding(.vertical, 1)
                                    }
                                }
                            }
                            .padding(14)
                            .background(Color(hex: "1E2430"))
                            .cornerRadius(12)
                        } else {
                            // Saved Files List
                            Text("Saved Sessions (\(viewModel.savedLogs.count))")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)

                            if viewModel.savedLogs.isEmpty {
                                VStack(spacing: 8) {
                                    Image(systemName: "tray")
                                        .font(.system(size: 32))
                                        .foregroundColor(.gray)
                                    Text("No logs recorded yet.\nTap 'Record PIDs' or 'Raw Trace' above.")
                                        .font(.system(size: 13))
                                        .foregroundColor(.gray)
                                        .multilineTextAlignment(.center)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 40)
                            } else {
                                ForEach(viewModel.savedLogs) { item in
                                    HStack(spacing: 12) {
                                        Image(systemName: item.isRawTrace ? "terminal.fill" : "tablecells.fill")
                                            .font(.system(size: 24))
                                            .foregroundColor(item.isRawTrace ? .orange : .green)

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(item.name)
                                                .font(.system(size: 14, weight: .semibold))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                            Text("\(formatBytes(item.sizeBytes)) • \(item.recordCount) \(item.isRawTrace ? "lines" : "records")")
                                                .font(.system(size: 12))
                                                .foregroundColor(.gray)
                                        }

                                        Spacer()

                                        ShareLink(item: item.fileURL) {
                                            Image(systemName: "square.and.arrow.up")
                                                .foregroundColor(.cyan)
                                        }

                                        Button(action: { fileToDelete = item }) {
                                            Image(systemName: "trash")
                                                .foregroundColor(.red.opacity(0.8))
                                        }
                                    }
                                    .padding(12)
                                    .background(Color(hex: "1E2430"))
                                    .cornerRadius(8)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        viewModel.openLog(info: item)
                                    }
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            }
        }
        .navigationBarHidden(true)
        .alert(item: $fileToDelete) { item in
            Alert(
                title: Text("Delete Log"),
                message: Text("Are you sure you want to delete \(item.name)?"),
                primaryButton: .destructive(Text("Delete")) {
                    viewModel.deleteLog(info: item)
                },
                secondaryButton: .cancel()
            )
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        if bytes >= 1024 * 1024 {
            return String(format: "%.2f MB", Double(bytes) / (1024.0 * 1024.0))
        } else if bytes >= 1024 {
            return String(format: "%.1f KB", Double(bytes) / 1024.0)
        } else {
            return "\(bytes) B"
        }
    }
}

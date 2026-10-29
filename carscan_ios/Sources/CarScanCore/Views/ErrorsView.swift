import SwiftUI

public struct ErrorsView: View {
    @StateObject private var vm = ErrorsViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var expandedModules = Set<String>()

    public init() {}

    public var body: some View {
        ZStack {
            Color(hex: "12151A").ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Header
                HStack {
                    Button(action: { dismiss() }) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Back")
                        }
                        .foregroundColor(.cyan)
                    }

                    Spacer()

                    VStack(spacing: 2) {
                        HStack(spacing: 6) {
                            Text("Module DTC Diagnostics")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundColor(.white)
                            if SessionManager.shared.isDemoMode {
                                DemoChip()
                            }
                        }
                        Text("UDS 0x19 / Multi-bus CAN")
                            .font(.system(size: 11))
                            .foregroundColor(.gray)
                    }

                    Spacer()

                    Button(action: { Task { await vm.scanDTCs() } }) {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(vm.isScanning ? .gray : .cyan)
                    }
                    .disabled(vm.isScanning)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(hex: "1A202C"))

                if vm.isScanning {
                    VStack(spacing: 14) {
                        Spacer()
                        ProgressView().tint(.cyan)
                        Text("Querying \(vm.scanningModuleAcronym ?? "modules") via UDS 0x19...")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                        Text("Switching HS-CAN / MS-CAN buses automatically")
                            .font(.system(size: 12))
                            .foregroundColor(.gray)
                        Spacer()
                    }
                } else if vm.scanResults.isEmpty {
                    VStack(spacing: 16) {
                        Spacer()
                        Image(systemName: "checkmark.shield.fill")
                            .font(.system(size: 48))
                            .foregroundColor(.green)
                        Text("No Error Scan Performed Yet")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                        Text("Tap 'Scan Modules' below to query vehicle ECUs across HS-CAN and MS-CAN.")
                            .font(.system(size: 13))
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                        Button(action: { Task { await vm.scanDTCs() } }) {
                            Text("Scan Modules Now")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 12)
                                .background(Color.cyan)
                                .cornerRadius(10)
                        }
                        Spacer()
                    }
                } else {
                    // Module list
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(vm.scanResults) { result in
                                ModuleScanCard(
                                    result: result,
                                    isExpanded: expandedModules.contains(result.id),
                                    onToggle: {
                                        if expandedModules.contains(result.id) {
                                            expandedModules.remove(result.id)
                                        } else {
                                            expandedModules.insert(result.id)
                                        }
                                    },
                                    onClearDTCs: {
                                        vm.requestClearDTCs(for: result.module)
                                    }
                                )
                            }
                            // Bottom breathing room spacer so IPC is never obscured
                            Spacer().frame(height: 32)
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 16)
                        .padding(.bottom, 40)
                    }

                    // Bottom Action Toolbar
                    HStack(spacing: 16) {
                        Button(action: { Task { await vm.scanDTCs() } }) {
                            HStack {
                                Image(systemName: "arrow.clockwise")
                                Text("Re-scan All Modules")
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color(hex: "1E88E5"))
                            .cornerRadius(10)
                        }

                        ShareLink(item: vm.exportLog()) {
                            HStack {
                                Image(systemName: "square.and.arrow.up")
                                Text("Share Report")
                            }
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color(hex: "273142"))
                            .cornerRadius(10)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Color(hex: "161A22"))
                }
            }
        }
        .navigationBarHidden(true)
        .confirmationDialog(
            "Clear DTCs for \(vm.selectedModuleForClear?.acronym ?? "Module")?",
            isPresented: $vm.showClearConfirmDialog,
            titleVisibility: .visible
        ) {
            Button("Clear \(vm.selectedModuleForClear?.acronym ?? "") DTCs (UDS 0x14)", role: .destructive) {
                Task { await vm.executeClearDTCsForSelectedModule() }
            }
            Button("Cancel", role: .cancel) {
                vm.dismissClearDialog()
            }
        } message: {
            Text("SAFETY WARNING: Clearing diagnostic information (UDS 0x14) will reset fault logs, freeze frames, and readiness adaptation parameters for this specific module.")
        }
        .alert("Notice", isPresented: $vm.showAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(vm.alertMessage ?? "")
        }
        .task {
            if vm.scanResults.isEmpty {
                await vm.scanDTCs()
            }
        }
    }
}

struct ModuleScanCard: View {
    let result: ModuleScanResult
    let isExpanded: Bool
    let onToggle: () -> Void
    let onClearDTCs: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onToggle) {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(result.module.acronym) - \(result.module.name)")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)

                        HStack(spacing: 6) {
                            Text(result.module.bus)
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.cyan)

                            Text("•")
                                .font(.system(size: 11))
                                .foregroundColor(.gray)

                            Text(result.status.displayText)
                                .font(.system(size: 12))
                                .foregroundColor(colorForStatus(result.status))
                        }
                    }

                    Spacer()

                    if case .faultsFound(let dtcs) = result.status {
                        Text("\(dtcs.count)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.orange)
                            .clipShape(Capsule())
                    }

                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundColor(.gray)
                        .font(.system(size: 12))
                }
                .padding(14)
            }

            if isExpanded {
                Divider().background(Color.white.opacity(0.06))

                VStack(spacing: 10) {
                    // Header metadata
                    HStack {
                        Text("TX: \(result.module.txId) | RX: \(result.module.rxId)")
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundColor(.gray)
                        Spacer()
                        Text(result.module.isVerified ? "Verified" : "Bench unverified")
                            .font(.system(size: 10))
                            .foregroundColor(result.module.isVerified ? .green : .gray)
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 8)

                    if case .faultsFound(let dtcs) = result.status {
                        ForEach(dtcs) { dtc in
                            HStack(alignment: .top, spacing: 10) {
                                Text(dtc.code)
                                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                                    .foregroundColor(.red)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.red.opacity(0.15))
                                    .cornerRadius(6)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(dtc.description)
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(.white)
                                    if let sym = dtc.symptom {
                                        Text(sym)
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 2)
                        }

                        // Clear DTCs button for this module
                        Button(action: onClearDTCs) {
                            HStack {
                                Image(systemName: "trash")
                                Text("Clear \(result.module.acronym) DTCs (UDS 0x14)")
                            }
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(Color.red.opacity(0.15))
                            .cornerRadius(8)
                        }
                        .padding(.horizontal, 14)
                        .padding(.top, 6)
                        .padding(.bottom, 8)
                    }
                }
                .padding(.bottom, 6)
            }
        }
        .background(Color(hex: "1C212B"))
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }

    private func colorForStatus(_ status: ModuleScanStatus) -> Color {
        switch status {
        case .clean: return Color(hex: "4CAF50")
        case .faultsFound: return Color(hex: "FF9800")
        case .offlineNoResponse: return Color(hex: "9E9E9E")
        case .scanError: return Color(hex: "E57373")
        }
    }
}

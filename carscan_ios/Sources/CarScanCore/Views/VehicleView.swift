import SwiftUI

public struct VehicleView: View {
    @StateObject private var vm = VehicleViewModel()
    @Environment(\.dismiss) private var dismiss

    public init() {}

    public var body: some View {
        ZStack {
            Color(hex: "12151A").ignoresSafeArea()

            VStack(spacing: 0) {
                // Top Nav
                HStack {
                    Button(action: { dismiss() }) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Back")
                        }
                        .foregroundColor(.cyan)
                    }

                    Spacer()

                    HStack(spacing: 6) {
                        Text("Vehicle Information")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                        if SessionManager.shared.isDemoMode {
                            DemoChip()
                        }
                    }

                    Spacer()

                    Button(action: { Task { await vm.loadData() } }) {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(.cyan)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(hex: "1A202C"))

                ScrollView {
                    VStack(spacing: 18) {
                        // Vehicle Card
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Image(systemName: "car.side.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(.cyan)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(vm.year) \(vm.make) \(vm.model)")
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundColor(.white)
                                    Text("VIN: \(vm.vin)")
                                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                            }
                        }
                        .padding(16)
                        .background(Color(hex: "1C212B"))
                        .cornerRadius(14)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.08), lineWidth: 1))

                        // Battery Voltage Comparison Card (ATRV vs PID 0142)
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Battery & Power Supply")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.gray)

                            HStack(spacing: 16) {
                                VoltageMetricBox(
                                    title: "OBD Port (ATRV)",
                                    voltage: vm.atrvVoltage > 0 ? vm.atrvVoltage : 12.6,
                                    subtitle: "Pin 16 Direct ADC",
                                    icon: "bolt.batteryblock.fill",
                                    color: .orange
                                )

                                VoltageMetricBox(
                                    title: "ECU (PID 0142)",
                                    voltage: vm.vpwrVoltage > 0 ? vm.vpwrVoltage : 12.5,
                                    subtitle: "Internal VPWR",
                                    icon: "cpu.fill",
                                    color: .cyan
                                )
                            }
                        }
                        .padding(16)
                        .background(Color(hex: "1C212B"))
                        .cornerRadius(14)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.08), lineWidth: 1))

                        // Detected Standard Modules (7E8..7EF)
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text("Detected Modules (Broadcast 7E8–7EF)")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(.gray)
                                Spacer()
                                Text("\(vm.detectedModules.count) found")
                                    .font(.system(size: 12))
                                    .foregroundColor(.green)
                            }

                            if vm.detectedModules.isEmpty {
                                HStack {
                                    Spacer()
                                    if vm.isLoading {
                                        ProgressView().tint(.white)
                                    } else {
                                        Text("No modules detected yet. Tap refresh.")
                                            .foregroundColor(.gray)
                                            .font(.system(size: 13))
                                    }
                                    Spacer()
                                }
                                .padding(.vertical, 20)
                            } else {
                                ForEach(vm.detectedModules) { mod in
                                    HStack(spacing: 12) {
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 8)
                                                .fill(Color(hex: "273142"))
                                                .frame(width: 44, height: 44)
                                            Text(mod.acronym)
                                                .font(.system(size: 12, weight: .bold))
                                                .foregroundColor(.cyan)
                                        }

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(mod.name)
                                                .font(.system(size: 14, weight: .medium))
                                                .foregroundColor(.white)
                                            HStack(spacing: 8) {
                                                Text("Rx ID: \(mod.canId)")
                                                    .font(.system(size: 11, design: .monospaced))
                                                    .foregroundColor(.gray)
                                                Text("•")
                                                    .foregroundColor(.gray)
                                                Text(mod.bus)
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.gray)
                                            }
                                        }

                                        Spacer()

                                        Circle()
                                            .fill(Color.green)
                                            .frame(width: 8, height: 8)
                                    }
                                    .padding(.vertical, 4)
                                    if mod.id != vm.detectedModules.last?.id {
                                        Divider().background(Color.white.opacity(0.06))
                                    }
                                }
                            }
                        }
                        .padding(16)
                        .background(Color(hex: "1C212B"))
                        .cornerRadius(14)
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.08), lineWidth: 1))
                    }
                    .padding(16)
                }
            }
        }
        .navigationBarHidden(true)
        .task {
            await vm.loadData()
        }
    }
}

struct VoltageMetricBox: View {
    let title: String
    let voltage: Double
    let subtitle: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundColor(color)
                Text(title)
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.8))
            }

            Text(String(format: "%.1f V", voltage))
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundColor(.white)

            Text(subtitle)
                .font(.system(size: 10))
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(hex: "232A36"))
        .cornerRadius(10)
    }
}

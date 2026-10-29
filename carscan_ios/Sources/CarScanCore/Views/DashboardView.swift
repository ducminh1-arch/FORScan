import SwiftUI

public struct DashboardView: View {
    @StateObject private var liveVM = LiveViewModel()
    @ObservedObject private var session = SessionManager.shared
    @Environment(\.dismiss) private var dismiss

    public init() {}

    private var dashboardPids: [PIDDefinition] {
        liveVM.supportedPids.filter {
            ["PID_010C", "PID_010D", "PID_0105", "PID_0111", "PID_0104", "PID_0142"].contains($0.id)
        }
    }

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

                    HStack(spacing: 8) {
                        Text("Dashboard")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                        if session.isDemoMode {
                            DemoChip()
                        }
                    }

                    Spacer()

                    // Metric / Imperial unit switch
                    Button(action: { session.isImperial.toggle() }) {
                        Text(session.isImperial ? "US" : "METRIC")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.cyan.opacity(0.3))
                            .cornerRadius(8)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(hex: "1A202C"))

                ScrollView {
                    VStack(spacing: 20) {
                        // 2 Primary Circular Gauges (RPM and Speed)
                        HStack(spacing: 16) {
                            let rpmVal = liveVM.liveValues["PID_010C"]?.numericValue ?? 0.0
                            CircularGaugeView(
                                title: "RPM",
                                value: rpmVal,
                                unit: "RPM",
                                minVal: 0,
                                maxVal: 8000,
                                color: .orange,
                                formatted: String(format: "%.0f", rpmVal)
                            )

                            let spdVal = liveVM.liveValues["PID_010D"]?.numericValue ?? 0.0
                            CircularGaugeView(
                                title: "SPEED",
                                value: spdVal,
                                unit: session.isImperial ? "mph" : "km/h",
                                minVal: 0,
                                maxVal: session.isImperial ? 160 : 240,
                                color: .cyan,
                                formatted: String(format: "%.0f", spdVal)
                            )
                        }

                        // Secondary Grid Cards (ECT, Throttle, Load, Voltage)
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                            let ectVal = liveVM.liveValues["PID_0105"]?.numericValue ?? 90.0
                            MetricCard(
                                title: "Coolant (ECT)",
                                value: String(format: "%.1f", ectVal),
                                unit: session.isImperial ? "°F" : "°C",
                                icon: "thermometer.medium",
                                color: ectVal > 105 ? .red : .green
                            )

                            let thrVal = liveVM.liveValues["PID_0111"]?.numericValue ?? 15.0
                            MetricCard(
                                title: "Throttle (TP)",
                                value: String(format: "%.1f", thrVal),
                                unit: "%",
                                icon: "gauge.open.with.lines.needle.33percent",
                                color: .blue
                            )

                            let loadVal = liveVM.liveValues["PID_0104"]?.numericValue ?? 22.0
                            MetricCard(
                                title: "Engine Load",
                                value: String(format: "%.1f", loadVal),
                                unit: "%",
                                icon: "speedometer",
                                color: .yellow
                            )

                            let vVal = liveVM.liveValues["PID_0142"]?.numericValue ?? 12.6
                            MetricCard(
                                title: "ECU Voltage",
                                value: String(format: "%.1f", vVal),
                                unit: "V",
                                icon: "bolt.fill",
                                color: vVal < 11.5 ? .red : .mint
                            )
                        }
                    }
                    .padding(16)
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            liveVM.startPolling(pidsToPoll: dashboardPids)
        }
        .onDisappear {
            liveVM.stopPolling()
        }
    }
}

struct CircularGaugeView: View {
    let title: String
    let value: Double
    let unit: String
    let minVal: Double
    let maxVal: Double
    let color: Color
    let formatted: String

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .stroke(Color.white.opacity(0.1), lineWidth: 10)

                let pct = max(0.0, min(1.0, (value - minVal) / (maxVal - minVal)))
                Circle()
                    .trim(from: 0.0, to: pct * 0.75)
                    .stroke(
                        AngularGradient(
                            gradient: Gradient(colors: [color.opacity(0.7), color]),
                            center: .center,
                            startAngle: .degrees(135),
                            endAngle: .degrees(405)
                        ),
                        style: StrokeStyle(lineWidth: 10, lineCap: .round)
                    )
                    .rotationEffect(.degrees(135))

                VStack(spacing: 2) {
                    Text(title)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.gray)
                    Text(formatted)
                        .font(.system(size: 24, weight: .bold, design: .rounded))
                        .foregroundColor(.white)
                    Text(unit)
                        .font(.system(size: 10))
                        .foregroundColor(.gray)
                }
            }
            .frame(width: 155, height: 155)
            .padding(12)
            .background(Color(hex: "1C212B"))
            .cornerRadius(16)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08), lineWidth: 1))
        }
    }
}

struct MetricCard: View {
    let title: String
    let value: String
    let unit: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                    .font(.system(size: 14))
                Text(title)
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
                Spacer()
            }

            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(value)
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                Text(unit)
                    .font(.system(size: 11))
                    .foregroundColor(.gray)
            }
        }
        .padding(14)
        .background(Color(hex: "1C212B"))
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.08), lineWidth: 1))
    }
}

import SwiftUI

public struct GraphView: View {
    @StateObject private var liveVM = LiveViewModel()
    @ObservedObject private var session = SessionManager.shared
    @Environment(\.dismiss) private var dismiss

    private let seriesColors: [Color] = [.cyan, .orange, .green, .purple]

    public init() {}

    private var activeGraphedPids: [PIDDefinition] {
        liveVM.supportedPids.filter { liveVM.selectedPidsForGraph.contains($0.id) }
    }

    private var timeSpanSec: Int {
        let allPoints = activeGraphedPids.flatMap { liveVM.graphBuffers[$0.id] ?? [] }
        guard allPoints.count >= 2 else { return 60 }
        let timestamps = allPoints.map { $0.timestamp.timeIntervalSince1970 }
        guard let minT = timestamps.min(), let maxT = timestamps.max() else { return 60 }
        let span = Int(maxT - minT)
        return max(10, span)
    }

    private func pidScale(for pid: PIDDefinition) -> (min: Int, max: Int, unit: String) {
        let unit = (session.isImperial ? pid.imperialUnit : pid.unit) ?? pid.unit
        if session.isImperial && pid.imperialUnit != nil && pid.unit == "km/h" {
            return (Int(round(pid.min * 0.621371)), Int(round(pid.max * 0.621371)), unit)
        } else {
            return (Int(round(pid.min)), Int(round(pid.max)), unit)
        }
    }

    public var body: some View {
        ZStack {
            Color(hex: "12151A").ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
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
                        Text("Graph Plotter")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                        if session.isDemoMode {
                            DemoChip()
                        }
                    }

                    Spacer()

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

                // PID Selection Chips (Max 4)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(liveVM.supportedPids) { pid in
                            let isSelected = liveVM.selectedPidsForGraph.contains(pid.id)
                            Button(action: { liveVM.toggleGraphPid(pid.id) }) {
                                HStack(spacing: 6) {
                                    if isSelected {
                                        Circle()
                                            .fill(getColorForPid(pid.id))
                                            .frame(width: 6, height: 6)
                                    }
                                    Text(pid.shortName)
                                        .font(.system(size: 12, weight: isSelected ? .bold : .regular))
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(isSelected ? Color(hex: "2A3548") : Color(hex: "1C212B"))
                                .foregroundColor(isSelected ? .white : .gray)
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(isSelected ? getColorForPid(pid.id).opacity(0.6) : Color.white.opacity(0.06), lineWidth: 1)
                                )
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }

                // Dedicated Scale & Value Cards per active PID
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(Array(activeGraphedPids.enumerated()), id: \.element.id) { index, pid in
                            let color = seriesColors[index % seriesColors.count]
                            let live = liveVM.liveValues[pid.id]?.formattedString ?? "--"
                            let scale = pidScale(for: pid)

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Circle().fill(color).frame(width: 8, height: 8)
                                    Text("\(pid.shortName): \(live) \(scale.unit)")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(color)
                                }
                                Text("Scale: \(scale.min)..\(scale.max) \(scale.unit)")
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundColor(.gray)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color(hex: "1C212B"))
                            .cornerRadius(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(color.opacity(0.5), lineWidth: 1)
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
                }

                // Canvas Chart Container
                VStack(spacing: 6) {
                    HStack(spacing: 4) {
                        // Left Y-Axis Scale Labels (Primary PID)
                        if let leftPid = activeGraphedPids.first {
                            let color0 = seriesColors[0]
                            let scale0 = pidScale(for: leftPid)
                            let mid0 = (scale0.min + scale0.max) / 2

                            VStack(alignment: .trailing) {
                                Text("\(scale0.max) \(scale0.unit)")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(color0)
                                Spacer()
                                Text("\(mid0)")
                                    .font(.system(size: 9))
                                    .foregroundColor(color0.opacity(0.6))
                                Spacer()
                                Text("\(scale0.min) \(scale0.unit)")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(color0)
                            }
                            .frame(width: 48)
                            .padding(.vertical, 6)
                        }

                        // Plot Area
                        GeometryReader { geo in
                            ZStack {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(Color(hex: "161A22"))
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.08), lineWidth: 1))

                                // Horizontal grid lines (5 levels)
                                VStack(spacing: 0) {
                                    ForEach(0..<4) { _ in
                                        Divider().background(Color.white.opacity(0.08))
                                        Spacer()
                                    }
                                    Divider().background(Color.white.opacity(0.08))
                                }
                                .padding(.vertical, 2)

                                // Vertical time grid lines (5 levels)
                                HStack(spacing: 0) {
                                    ForEach(0..<4) { _ in
                                        Rectangle().fill(Color.white.opacity(0.08)).frame(width: 1)
                                        Spacer()
                                    }
                                    Rectangle().fill(Color.white.opacity(0.08)).frame(width: 1)
                                }
                                .padding(.horizontal, 2)

                                // Multi-series line traces
                                ForEach(Array(activeGraphedPids.enumerated()), id: \.element.id) { index, pid in
                                    let color = seriesColors[index % seriesColors.count]
                                    let points = liveVM.graphBuffers[pid.id] ?? []
                                    if points.count > 1 {
                                        LineChartView(
                                            points: points,
                                            color: color,
                                            width: geo.size.width,
                                            height: geo.size.height,
                                            minVal: pid.min,
                                            maxVal: pid.max
                                        )
                                    }
                                }
                            }
                        }

                        // Right Y-Axis Scale Labels (Secondary PID if available)
                        if activeGraphedPids.count > 1 {
                            let rightPid = activeGraphedPids[1]
                            let color1 = seriesColors[1]
                            let scale1 = pidScale(for: rightPid)
                            let mid1 = (scale1.min + scale1.max) / 2

                            VStack(alignment: .leading) {
                                Text("\(scale1.max) \(scale1.unit)")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(color1)
                                Spacer()
                                Text("\(mid1)")
                                    .font(.system(size: 9))
                                    .foregroundColor(color1.opacity(0.6))
                                Spacer()
                                Text("\(scale1.min) \(scale1.unit)")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(color1)
                            }
                            .frame(width: 48)
                            .padding(.vertical, 6)
                        }
                    }
                    .frame(maxHeight: .infinity)

                    // X-Axis Time Labels Row
                    HStack {
                        let t = timeSpanSec
                        Text("-\(t)s")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(.gray)
                        Spacer()
                        Text("-\((t * 3) / 4)s")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(.gray)
                        Spacer()
                        Text("-\(t / 2)s")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(.gray)
                        Spacer()
                        Text("-\(t / 4)s")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(.gray)
                        Spacer()
                        Text("0s (Now)")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.cyan)
                    }
                    .padding(.leading, activeGraphedPids.isEmpty ? 16 : 56)
                    .padding(.trailing, activeGraphedPids.count > 1 ? 56 : 16)
                }
                .padding(10)
                .background(Color(hex: "161A22"))
                .cornerRadius(12)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.06), lineWidth: 1))
                .padding(.horizontal, 16)
                .padding(.vertical, 6)

                // Buffer status badge
                HStack {
                    Text("Time window: ~\(timeSpanSec)s | Buffer: 300 pts")
                        .font(.system(size: 11))
                        .foregroundColor(.gray)
                    Spacer()
                    Text(activeGraphedPids.isEmpty ? "Select PID above" : "\(activeGraphedPids.count) PID(s) plotted")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(activeGraphedPids.isEmpty ? .gray : .cyan)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            liveVM.startPolling(pidsToPoll: activeGraphedPids)
        }
        .onDisappear {
            liveVM.stopPolling()
        }
        .onChange(of: liveVM.selectedPidsForGraph) { _ in
            liveVM.startPolling(pidsToPoll: activeGraphedPids)
        }
    }

    private func getColorForPid(_ pidId: String) -> Color {
        guard let idx = activeGraphedPids.firstIndex(where: { $0.id == pidId }) else { return .gray }
        return seriesColors[idx % seriesColors.count]
    }
}

struct LineChartView: View {
    let points: [DataPoint]
    let color: Color
    let width: CGFloat
    let height: CGFloat
    let minVal: Double
    let maxVal: Double

    var body: some View {
        Path { path in
            guard points.count > 1 else { return }
            let stepX = width / CGFloat(max(1, points.count - 1))
            let range = max(1.0, maxVal - minVal)

            for (idx, pt) in points.enumerated() {
                let normY = (pt.value - minVal) / range
                let clampedNormY = max(0.0, min(1.0, normY))
                let x = CGFloat(idx) * stepX
                let y = height - (CGFloat(clampedNormY) * height)

                if idx == 0 {
                    path.move(to: CGPoint(x: x, y: y))
                } else {
                    path.addLine(to: CGPoint(x: x, y: y))
                }
            }
        }
        .stroke(color, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
    }
}

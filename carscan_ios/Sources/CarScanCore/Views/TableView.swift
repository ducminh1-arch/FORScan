import SwiftUI

public struct TableView: View {
    @StateObject private var liveVM = LiveViewModel()
    @ObservedObject private var session = SessionManager.shared
    @Environment(\.dismiss) private var dismiss
    @State private var searchText: String = ""

    public init() {}

    private var filteredPids: [PIDDefinition] {
        if searchText.isEmpty {
            return liveVM.supportedPids
        }
        return liveVM.supportedPids.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.shortName.localizedCaseInsensitiveContains(searchText) ||
            $0.pid.localizedCaseInsensitiveContains(searchText)
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

                    HStack(spacing: 8) {
                        Text("Live Data Table")
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

                // Search Bar
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.gray)
                    TextField("Search PIDs (RPM, Speed, Temp...)", text: $searchText)
                        .foregroundColor(.white)
                }
                .padding(10)
                .background(Color(hex: "1C212B"))
                .cornerRadius(10)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                // Column Headers
                HStack {
                    Text("PID / PARAMETER")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.gray)
                    Spacer()
                    Text("CURRENT")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.gray)
                        .frame(width: 80, alignment: .trailing)
                    Text("MIN / MAX")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.gray)
                        .frame(width: 85, alignment: .trailing)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 6)

                // Table List
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(filteredPids) { pid in
                            let live = liveVM.liveValues[pid.id]
                            let hist = liveVM.minMaxHistory[pid.id]
                            let unit = (session.isImperial ? pid.imperialUnit : pid.unit) ?? pid.unit

                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(pid.name)
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.white)
                                    HStack(spacing: 6) {
                                        Text("01\(pid.pid)")
                                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                                            .foregroundColor(.cyan)
                                        Text("•")
                                            .foregroundColor(.gray)
                                        Text(pid.category)
                                            .font(.system(size: 10))
                                            .foregroundColor(.gray)
                                    }
                                }

                                Spacer()

                                // Live Value
                                VStack(alignment: .trailing, spacing: 2) {
                                    Text(live?.formattedString ?? "--")
                                        .font(.system(size: 15, weight: .bold, design: .rounded))
                                        .foregroundColor(.white)
                                    Text(unit)
                                        .font(.system(size: 9))
                                        .foregroundColor(.gray)
                                }
                                .frame(width: 80, alignment: .trailing)

                                // Min / Max
                                VStack(alignment: .trailing, spacing: 2) {
                                    if let h = hist {
                                        Text(String(format: "%.0f / %.0f", h.min, h.max))
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundColor(.gray)
                                    } else {
                                        Text("--")
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                    }
                                }
                                .frame(width: 85, alignment: .trailing)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(Color(hex: "1C212B"))
                            .cornerRadius(8)
                        }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            liveVM.startPolling(pidsToPoll: filteredPids)
        }
        .onDisappear {
            liveVM.stopPolling()
        }
    }
}

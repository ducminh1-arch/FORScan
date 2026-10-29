import SwiftUI

public struct HomeView: View {
    @StateObject private var session = SessionManager.shared
    @State private var selectedTab: String? = nil

    public init() {}

    private let gridItems = [
        GridItem(.flexible(), spacing: 16),
        GridItem(.flexible(), spacing: 16),
        GridItem(.flexible(), spacing: 16)
    ]

    public var body: some View {
        NavigationStack {
            ZStack {
                // Automotive dark charcoal gradient background
                LinearGradient(
                    colors: [Color(hex: "22262E"), Color(hex: "181B20"), Color(hex: "121418")],
                    startPoint: .top,
                    endPoint: .bottom
                ).ignoresSafeArea()

                VStack(spacing: 0) {
                    // Header Bar (Automotive Top Console)
                    HStack {
                        // Left: Connection status dot
                        HStack(spacing: 5) {
                            Circle()
                                .fill(session.connectionState == .connected ? Color(hex: "00E676") : Color(hex: "FF5252"))
                                .frame(width: 9, height: 9)
                            Text(session.connectionState == .connected ? (session.isDemoMode ? "DEMO" : "LIVE") : "OFFLINE")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(session.connectionState == .connected ? Color(hex: "00E676") : Color(hex: "9098A8"))
                                .lineLimit(1)
                        }

                        Spacer()

                        // Center: App Title
                        Text("CarScan Lite")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)

                        Spacer()

                        // Right: Demo Pill + Info Button
                        HStack(spacing: 6) {
                            Button(action: { session.isDemoMode.toggle() }) {
                                Text("Demo")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(session.isDemoMode ? Color(hex: "FFB300") : .gray)
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 4)
                                    .background(Color(hex: "15181F"))
                                    .cornerRadius(10)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(session.isDemoMode ? Color(hex: "FFB300").opacity(0.6) : Color(hex: "3A4150"), lineWidth: 1)
                                    )
                            }

                            NavigationLink(destination: VehicleView()) {
                                Image(systemName: "info.circle.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(Color(hex: "D0D5E0"))
                                    .frame(width: 28, height: 28)
                                    .background(LinearGradient(colors: [Color(hex: "353C4A"), Color(hex: "20242D")], startPoint: .top, endPoint: .bottom))
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color(hex: "4A5265"), lineWidth: 1)
                                    )
                            }

                            NavigationLink(destination: SettingsView()) {
                                Image(systemName: "gearshape.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(Color(hex: "D0D5E0"))
                                    .frame(width: 28, height: 28)
                                    .background(LinearGradient(colors: [Color(hex: "353C4A"), Color(hex: "20242D")], startPoint: .top, endPoint: .bottom))
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color(hex: "4A5265"), lineWidth: 1)
                                    )
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(LinearGradient(colors: [Color(hex: "2C313C"), Color(hex: "1C2027")], startPoint: .top, endPoint: .bottom))
                    .overlay(
                        Rectangle()
                            .frame(height: 1)
                            .foregroundColor(Color(hex: "383E4C").opacity(0.6)),
                        alignment: .bottom
                    )

                    // 8-Icon Grid (Skeuomorphic 3D Plates)
                    ScrollView {
                        LazyVGrid(columns: gridItems, spacing: 18) {
                            NavigationLink(destination: VehicleView()) {
                                SkeuomorphicTile(title: "Vehicle", type: .vehicle)
                            }

                            NavigationLink(destination: LogView()) {
                                SkeuomorphicTile(title: "Log", type: .log)
                            }

                            NavigationLink(destination: ErrorsView()) {
                                SkeuomorphicTile(title: "Errors", type: .errors)
                            }

                            NavigationLink(destination: DashboardView()) {
                                SkeuomorphicTile(title: "Dashboard", type: .dashboard)
                            }

                            NavigationLink(destination: TableView()) {
                                SkeuomorphicTile(title: "Table", type: .table)
                            }

                            NavigationLink(destination: GraphView()) {
                                SkeuomorphicTile(title: "Graph", type: .graph)
                            }

                            NavigationLink(destination: TestsView()) {
                                SkeuomorphicTile(title: "Tests", type: .tests)
                            }

                            NavigationLink(destination: ServicesView()) {
                                SkeuomorphicTile(title: "Services", type: .services)
                            }
                        }
                        .padding(.horizontal, 18)
                        .padding(.top, 18)
                    }

                    Spacer()

                    // Bottom Automotive Console with Center Analog Gauge
                    BottomConsoleBar(
                        isConnected: session.connectionState == .connected,
                        isConnecting: session.connectionState == .connecting,
                        voltage: session.adapterVoltage,
                        onConnect: {
                            Task { await session.connect() }
                        },
                        onDisconnect: {
                            session.disconnect()
                        }
                    )
                }
            }
            .alert("Connection Alert", isPresented: $session.showErrorAlert) {
                Button("OK", role: .cancel) {}
                Button("Retry") {
                    Task { await session.connect() }
                }
            } message: {
                Text(session.errorMessage ?? "An error occurred during communication.")
            }
        }
    }
}

enum TileType {
    case vehicle, log, errors, dashboard, table, graph, tests, services
}

struct SkeuomorphicTile: View {
    let title: String
    let type: TileType

    var body: some View {
        VStack(spacing: 6) {
            ZStack {
                // Beveled 3D Button Plate
                RoundedRectangle(cornerRadius: 18)
                    .fill(LinearGradient(
                        colors: [Color(hex: "2C323E"), Color(hex: "191C22")],
                        startPoint: .top,
                        endPoint: .bottom
                    ))
                    .frame(width: 88, height: 88)
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(LinearGradient(colors: [Color(hex: "4D5568"), Color(hex: "242730")], startPoint: .top, endPoint: .bottom), lineWidth: 1.5)
                    )
                    .shadow(color: .black.opacity(0.45), radius: 6, x: 0, y: 4)

                // Custom Rich Icon Graphic
                tileGraphic(for: type)
            }

            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(Color(hex: "E0E4EC"))
        }
    }

    @ViewBuilder
    private func tileGraphic(for type: TileType) -> some View {
        switch type {
        case .vehicle:
            ZStack {
                Circle()
                    .fill(RadialGradient(colors: [Color(hex: "29B6F6"), Color(hex: "0277BD")], center: .center, startRadius: 0, endRadius: 22))
                    .frame(width: 44, height: 44)
                    .overlay(Circle().stroke(Color.white.opacity(0.7), lineWidth: 1.5))
                Image(systemName: "car.fill")
                    .font(.system(size: 22))
                    .foregroundColor(.white)
            }
        case .log:
            ZStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(hex: "FFD54F"))
                    .frame(width: 32, height: 40)
                    .overlay(
                        VStack(spacing: 4) {
                            Rectangle().fill(Color(hex: "D32F2F")).frame(height: 6)
                            ForEach(0..<4) { _ in
                                Rectangle().fill(Color(hex: "795548").opacity(0.4)).frame(height: 1.5).padding(.horizontal, 4)
                            }
                            Spacer()
                        }
                    )
                Image(systemName: "pencil")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(hex: "1E88E5"))
                    .offset(x: 10, y: -4)
            }
        case .errors:
            ZStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 38))
                    .foregroundColor(Color(hex: "FFC107"))
                Image(systemName: "exclamationmark")
                    .font(.system(size: 16, weight: .black))
                    .foregroundColor(Color(hex: "212121"))
                    .offset(y: 2)
            }
        case .dashboard:
            ZStack {
                Circle()
                    .fill(Color(hex: "1A1C20"))
                    .frame(width: 44, height: 44)
                    .overlay(Circle().stroke(Color(hex: "90A4AE"), lineWidth: 2))

                Circle()
                    .trim(from: 0.1, to: 0.8)
                    .stroke(AngularGradient(colors: [.green, .yellow, .red], center: .center), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .frame(width: 36, height: 36)

                // Needle
                Rectangle()
                    .fill(Color(hex: "FF3D00"))
                    .frame(width: 2.5, height: 16)
                    .offset(y: -8)
                    .rotationEffect(.degrees(45))

                Circle()
                    .fill(Color.white)
                    .frame(width: 6, height: 6)
            }
        case .table:
            ZStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(hex: "263238"))
                    .frame(width: 38, height: 32)
                    .overlay(
                        VStack(spacing: 0) {
                            Rectangle().fill(Color(hex: "00B0FF")).frame(height: 8)
                            Divider().background(Color(hex: "546E7A"))
                            HStack {
                                Circle().fill(Color(hex: "00E676")).frame(width: 4, height: 4)
                                Spacer()
                                Rectangle().fill(Color(hex: "546E7A")).frame(height: 2)
                            }.padding(4)
                            HStack {
                                Circle().fill(Color(hex: "FFB300")).frame(width: 4, height: 4)
                                Spacer()
                                Rectangle().fill(Color(hex: "546E7A")).frame(height: 2)
                            }.padding(4)
                        }
                    )
            }
        case .graph:
            ZStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(hex: "0A140F"))
                    .frame(width: 38, height: 32)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Color(hex: "1B5E20").opacity(0.6), lineWidth: 1)
                    )
                Image(systemName: "waveform.path.ecg")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(Color(hex: "00E676"))
            }
        case .tests:
            ZStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(hex: "ECEFF1"))
                    .frame(width: 32, height: 40)
                    .overlay(
                        VStack(spacing: 4) {
                            Rectangle().fill(Color(hex: "B0BEC5")).frame(width: 16, height: 4)
                            ForEach(0..<3) { _ in
                                HStack(spacing: 3) {
                                    Circle().fill(Color(hex: "37474F")).frame(width: 3, height: 3)
                                    Rectangle().fill(Color(hex: "78909C")).frame(height: 1.5)
                                }.padding(.horizontal, 4)
                            }
                            Spacer()
                        }
                    )
                Image(systemName: "checkmark")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(Color(hex: "00C853"))
                    .offset(x: 4, y: 6)
            }
        case .services:
            ZStack {
                Circle()
                    .fill(RadialGradient(colors: [Color(hex: "1976D2"), Color(hex: "0D47A1")], center: .center, startRadius: 0, endRadius: 22))
                    .frame(width: 44, height: 44)
                    .overlay(Circle().stroke(Color(hex: "90CAF9").opacity(0.7), lineWidth: 1.5))
                Image(systemName: "wrench.and.screwdriver.fill")
                    .font(.system(size: 20))
                    .foregroundColor(Color(hex: "ECEFF1"))
            }
        }
    }
}

struct BottomConsoleBar: View {
    let isConnected: Bool
    let isConnecting: Bool
    let voltage: Double
    let onConnect: () -> Void
    let onDisconnect: () -> Void

    var body: some View {
        ZStack(alignment: .top) {
            // Horizontal Bar Base
            VStack {
                Spacer().frame(height: 14)
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(LinearGradient(
                            colors: [Color(hex: "282D37"), Color(hex: "16181E")],
                            startPoint: .top,
                            endPoint: .bottom
                        ))
                        .frame(height: 68)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color(hex: "3C4252"), lineWidth: 1)
                        )

                    HStack {
                        // Left: CONNECT Button
                        Button(action: onConnect) {
                            Text(isConnecting ? "CONNECTING" : "CONNECT")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(!isConnected ? .white : Color(hex: "6B7280"))
                                .frame(width: 110, height: 38)
                                .background(LinearGradient(
                                    colors: !isConnected ? [Color(hex: "383F4E"), Color(hex: "20242D")] : [Color(hex: "22252C"), Color(hex: "16181D")],
                                    startPoint: .top,
                                    endPoint: .bottom
                                ))
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(!isConnected ? Color(hex: "00E676").opacity(0.8) : Color(hex: "3A404E"), lineWidth: 1)
                                )
                        }
                        .disabled(isConnected || isConnecting)

                        Spacer()

                        // Right: DISCONNECT Button
                        Button(action: onDisconnect) {
                            Text("DISCONNECT")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(isConnected ? .white : Color(hex: "6B7280"))
                                .frame(width: 110, height: 38)
                                .background(LinearGradient(
                                    colors: isConnected ? [Color(hex: "383F4E"), Color(hex: "20242D")] : [Color(hex: "22252C"), Color(hex: "16181D")],
                                    startPoint: .top,
                                    endPoint: .bottom
                                ))
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(isConnected ? Color(hex: "FF5252").opacity(0.8) : Color(hex: "3A404E"), lineWidth: 1)
                                )
                        }
                        .disabled(!isConnected)
                    }
                    .padding(.horizontal, 16)
                }
            }

            // Protruding Analog Speedometer Dial in Center
            Button(action: {
                if isConnected { onDisconnect() } else { onConnect() }
            }) {
                ZStack {
                    // Outer Bezel Ring
                    Circle()
                        .fill(LinearGradient(
                            colors: [Color(hex: "555C6E"), Color(hex: "1A1C22"), Color(hex: "454B5A")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ))
                        .frame(width: 78, height: 78)
                        .overlay(Circle().stroke(Color(hex: "70788C"), lineWidth: 2))
                        .shadow(color: .black.opacity(0.5), radius: 8, x: 0, y: 4)

                    // Dial Face
                    Circle()
                        .fill(RadialGradient(
                            colors: [Color(hex: "14171E"), Color(hex: "090B0E")],
                            center: .center,
                            startRadius: 0,
                            endRadius: 36
                        ))
                        .frame(width: 70, height: 70)

                    // Perimeter Arc
                    Circle()
                        .trim(from: 0.1, to: 0.8)
                        .stroke(
                            AngularGradient(colors: [Color(hex: "00E676"), Color(hex: "FFB300"), Color(hex: "FF5252")], center: .center),
                            style: StrokeStyle(lineWidth: 3, lineCap: .round)
                        )
                        .frame(width: 58, height: 58)
                        .rotationEffect(.degrees(40))

                    // Needle
                    Rectangle()
                        .fill(Color(hex: "FF3D00"))
                        .frame(width: 2.5, height: 26)
                        .offset(y: -13)
                        .rotationEffect(.degrees(isConnected ? 45 : -80))

                    // Center Hub
                    Circle()
                        .fill(LinearGradient(colors: [Color(hex: "BAC2D1"), Color(hex: "454B57")], startPoint: .top, endPoint: .bottom))
                        .frame(width: 12, height: 12)
                    Circle()
                        .fill(Color(hex: "FF3D00"))
                        .frame(width: 5, height: 5)

                    // Live voltage display
                    if isConnected && voltage > 0 {
                        Text(String(format: "%.1fV", voltage))
                            .font(.system(size: 8, weight: .bold))
                            .foregroundColor(Color(hex: "00E676"))
                            .offset(y: 20)
                    }
                }
            }
            .offset(y: -4)
        }
        .frame(height: 88)
    }
}

extension Color {
    init(hex: String) {
        let scanner = Scanner(string: hex)
        var rgbValue: UInt64 = 0
        scanner.scanHexInt64(&rgbValue)
        let r = Double((rgbValue & 0xFF0000) >> 16) / 255.0
        let g = Double((rgbValue & 0x00FF00) >> 8) / 255.0
        let b = Double(rgbValue & 0x0000FF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}

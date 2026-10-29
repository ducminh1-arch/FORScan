import SwiftUI
import CoreBluetooth

public struct SettingsView: View {
    @ObservedObject private var session = SessionManager.shared
    @Environment(\.dismiss) private var dismiss
    @StateObject private var bleScanner = BleScanner()

    @State private var tempWifiHost: String = ""
    @State private var tempWifiPort: String = ""

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

                    HStack(spacing: 6) {
                        Text("Settings & Connection")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)
                        if session.isDemoMode {
                            DemoChip()
                        }
                    }

                    Spacer()

                    // Placeholder to balance back button
                    Text("Back")
                        .opacity(0)
                        .padding(.horizontal, 4)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(hex: "1A202C"))

                ScrollView {
                    VStack(spacing: 20) {
                        // Section 1: Connection Mode
                        VStack(alignment: .leading, spacing: 12) {
                            Text("ADAPTER CONNECTION TYPE")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.cyan)

                            VStack(spacing: 8) {
                                ForEach(ConnectionMode.allCases) { mode in
                                    let isSelected = session.connectionMode == mode
                                    Button(action: {
                                        session.connectionMode = mode
                                        if mode == .demo {
                                            session.isDemoMode = true
                                        } else {
                                            session.isDemoMode = false
                                        }
                                    }) {
                                        HStack(spacing: 12) {
                                            Image(systemName: iconForMode(mode))
                                                .font(.system(size: 18))
                                                .foregroundColor(isSelected ? .cyan : .gray)
                                                .frame(width: 28)

                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(modeTitle(mode))
                                                    .font(.system(size: 14, weight: .semibold))
                                                    .foregroundColor(.white)
                                                Text(modeDescription(mode))
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.gray)
                                            }

                                            Spacer()

                                            Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                                .font(.system(size: 18))
                                                .foregroundColor(isSelected ? .cyan : Color.white.opacity(0.2))
                                        }
                                        .padding(14)
                                        .background(isSelected ? Color(hex: "242C3B") : Color(hex: "1A202C"))
                                        .cornerRadius(10)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 10)
                                                .stroke(isSelected ? Color.cyan.opacity(0.5) : Color.white.opacity(0.06), lineWidth: 1)
                                        )
                                    }
                                }
                            }
                        }
                        .padding(16)
                        .background(Color(hex: "161A22"))
                        .cornerRadius(14)

                        // Section 2: Wi-Fi Configuration (if Wi-Fi selected)
                        if session.connectionMode == .wifi {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("WI-FI ADAPTER CONFIGURATION")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.cyan)

                                VStack(spacing: 10) {
                                    HStack {
                                        Text("Host IP:")
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.gray)
                                            .frame(width: 80, alignment: .leading)
                                        TextField("192.168.0.10", text: $tempWifiHost)
                                            .textFieldStyle(.plain)
                                            .padding(10)
                                            .background(Color(hex: "12151A"))
                                            .cornerRadius(8)
                                            .foregroundColor(.white)
                                            .font(.system(size: 13, design: .monospaced))
                                    }

                                    HStack {
                                        Text("TCP Port:")
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundColor(.gray)
                                            .frame(width: 80, alignment: .leading)
                                        TextField("35000", text: $tempWifiPort)
                                            .textFieldStyle(.plain)
                                            .padding(10)
                                            .background(Color(hex: "12151A"))
                                            .cornerRadius(8)
                                            .foregroundColor(.white)
                                            .font(.system(size: 13, design: .monospaced))
                                    }

                                    Button(action: {
                                        session.wifiHost = tempWifiHost.trimmingCharacters(in: .whitespaces)
                                        if let p = UInt16(tempWifiPort.trimmingCharacters(in: .whitespaces)) {
                                            session.wifiPort = p
                                        }
                                    }) {
                                        Text("Save Wi-Fi Settings")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.white)
                                            .frame(maxWidth: .infinity)
                                            .padding(.vertical, 10)
                                            .background(Color.cyan.opacity(0.3))
                                            .cornerRadius(8)
                                    }
                                }
                            }
                            .padding(16)
                            .background(Color(hex: "161A22"))
                            .cornerRadius(14)
                        }

                        // Section 3: BLE Scanner (if BLE selected)
                        if session.connectionMode == .ble {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Text("BLUETOOTH LOW ENERGY SCANNER")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(.cyan)
                                    Spacer()
                                    Button(action: {
                                        if bleScanner.isScanning {
                                            bleScanner.stopScan()
                                        } else {
                                            bleScanner.startScan()
                                        }
                                    }) {
                                        HStack(spacing: 4) {
                                            Image(systemName: bleScanner.isScanning ? "stop.fill" : "arrow.clockwise")
                                            Text(bleScanner.isScanning ? "Scanning..." : "Scan")
                                        }
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.cyan)
                                    }
                                }

                                if bleScanner.discoveredDevices.isEmpty {
                                    VStack(spacing: 8) {
                                        Image(systemName: "antenna.radiowaves.left.and.right")
                                            .font(.system(size: 28))
                                            .foregroundColor(.gray)
                                        Text(bleScanner.isScanning ? "Scanning for ELM327 / vLinker BLE adapters..." : "No devices discovered yet. Tap 'Scan' above.")
                                            .font(.system(size: 12))
                                            .foregroundColor(.gray)
                                            .multilineTextAlignment(.center)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 20)
                                } else {
                                    VStack(spacing: 8) {
                                        ForEach(bleScanner.discoveredDevices) { device in
                                            let isChosen = session.selectedBleId == device.id.uuidString
                                            Button(action: {
                                                session.selectedBleId = device.id.uuidString
                                                session.selectedBleName = device.name
                                            }) {
                                                HStack {
                                                    VStack(alignment: .leading, spacing: 2) {
                                                        Text(device.name)
                                                            .font(.system(size: 13, weight: .semibold))
                                                            .foregroundColor(.white)
                                                        Text("UUID: \(device.id.uuidString.prefix(12))... | RSSI: \(device.rssi) dBm")
                                                            .font(.system(size: 10, design: .monospaced))
                                                            .foregroundColor(.gray)
                                                    }
                                                    Spacer()
                                                    if isChosen {
                                                        Image(systemName: "checkmark.circle.fill")
                                                            .foregroundColor(.cyan)
                                                    }
                                                }
                                                .padding(10)
                                                .background(isChosen ? Color(hex: "242C3B") : Color(hex: "12151A"))
                                                .cornerRadius(8)
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(16)
                            .background(Color(hex: "161A22"))
                            .cornerRadius(14)
                        }

                        // Section 4: Preferences & Units
                        VStack(alignment: .leading, spacing: 12) {
                            Text("PREFERENCES & UNITS")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.cyan)

                            VStack(spacing: 12) {
                                Toggle(isOn: $session.isImperial) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("US Imperial Units")
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundColor(.white)
                                        Text("Display speed in mph, temp in °F, pressure in psi")
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                    }
                                }
                                .tint(.cyan)

                                Divider().background(Color.white.opacity(0.06))

                                Toggle(isOn: $session.isDemoMode) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Demo Simulation Mode")
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundColor(.white)
                                        Text("Simulates vehicle telemetry without hardware")
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                    }
                                }
                                .tint(.orange)
                            }
                        }
                        .padding(16)
                        .background(Color(hex: "161A22"))
                        .cornerRadius(14)

                        // Section 5: Diagnostic Bus & Protocols
                        VStack(alignment: .leading, spacing: 12) {
                            Text("CAN BUS & DIAGNOSTIC PROTOCOLS")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.cyan)

                            VStack(spacing: 8) {
                                ProtocolRow(title: "HS-CAN (High Speed)", desc: "Pins 6/14 • 500 kbps • ISO 15765-4 (STP 33)", badge: "Active")
                                ProtocolRow(title: "MS-CAN (Medium Speed)", desc: "Pins 3/11 • 125 kbps • Auto-switched (STP 53)", badge: "Supported")
                                ProtocolRow(title: "UDS Diagnostics", desc: "Service 0x19 (DTCs), 0x14 (Clear), 0x22 (Read PID)", badge: "ISO 14229")
                            }
                        }
                        .padding(16)
                        .background(Color(hex: "161A22"))
                        .cornerRadius(14)

                        // Section 6: About
                        VStack(spacing: 4) {
                            Text("CarScan Lite for iOS")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(.white)
                            Text("Version 1.0.0 (Build 1) • Xcode 15+ / iOS 16+")
                                .font(.system(size: 11))
                                .foregroundColor(.gray)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                    }
                    .padding(16)
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            tempWifiHost = session.wifiHost
            tempWifiPort = String(session.wifiPort)
        }
    }

    private func iconForMode(_ mode: ConnectionMode) -> String {
        switch mode {
        case .wifi: return "wifi"
        case .ble: return "dot.radiowaves.left.and.right"
        case .demo: return "sparkles"
        }
    }

    private func modeTitle(_ mode: ConnectionMode) -> String {
        switch mode {
        case .wifi: return "Wi-Fi TCP Socket"
        case .ble: return "Bluetooth Low Energy (BLE)"
        case .demo: return "Demo Simulation (Mock)"
        }
    }

    private func modeDescription(_ mode: ConnectionMode) -> String {
        switch mode {
        case .wifi: return "Connect to Wi-Fi OBD adapter (TCP 192.168.0.10:35000)"
        case .ble: return "Connect to vLinker MC+, Veepeak, or iCar Pro BLE adapter"
        case .demo: return "Virtual Ford Mustang EcoBoost 2.3L 2021 with full telemetry"
        }
    }
}

struct ProtocolRow: View {
    let title: String
    let desc: String
    let badge: String

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.white)
                Text(desc)
                    .font(.system(size: 11))
                    .foregroundColor(.gray)
            }
            Spacer()
            Text(badge)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.cyan)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.cyan.opacity(0.15))
                .cornerRadius(4)
        }
        .padding(10)
        .background(Color(hex: "1A202C"))
        .cornerRadius(8)
    }
}

public struct DiscoveredBleDevice: Identifiable, Sendable {
    public let id: UUID
    public let name: String
    public let rssi: Int
}

@MainActor
public final class BleScanner: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published public var isScanning = false
    @Published public var discoveredDevices: [DiscoveredBleDevice] = []

    private var centralManager: CBCentralManager?

    public override init() {
        super.init()
    }

    public func startScan() {
        discoveredDevices.removeAll()
        isScanning = true
        if centralManager == nil {
            centralManager = CBCentralManager(delegate: self, queue: nil)
        } else if centralManager?.state == .poweredOn {
            centralManager?.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        }
    }

    public func stopScan() {
        centralManager?.stopScan()
        isScanning = false
    }

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn && isScanning {
            central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        } else if central.state != .poweredOn {
            isScanning = false
        }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let name = peripheral.name ?? (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? "Unknown OBD Adapter"
        let dev = DiscoveredBleDevice(id: peripheral.identifier, name: name, rssi: RSSI.intValue)

        if !discoveredDevices.contains(where: { $0.id == dev.id }) {
            discoveredDevices.append(dev)
        }
    }
}


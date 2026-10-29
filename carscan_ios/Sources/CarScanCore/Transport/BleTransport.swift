import Foundation
import CoreBluetooth

public struct BleServiceConfig: Sendable {
    public let serviceUUID: CBUUID
    public let writeUUID: CBUUID
    public let notifyUUID: CBUUID

    public static let standardVeepeak = BleServiceConfig(
        serviceUUID: CBUUID(string: "FFF0"),
        writeUUID: CBUUID(string: "FFF2"),
        notifyUUID: CBUUID(string: "FFF1")
    )

    public static let vLinkerBLE = BleServiceConfig(
        serviceUUID: CBUUID(string: "E7810A71-73AE-499D-8C15-FAA9AEF0C3F2"),
        writeUUID: CBUUID(string: "BEF8D6C9-9C21-4C9E-B632-BD58C1009F9F"),
        notifyUUID: CBUUID(string: "BEF8D6C9-9C21-4C9E-B632-BD58C1009F9F")
    )
}

public protocol BleTransportDelegate: AnyObject {
    func bleTransportDidUpdateState(_ state: TransportState)
    func bleTransportDidReceiveData(_ data: Data)
}

public final class BleTransport: NSObject, Transport, CBCentralManagerDelegate, CBPeripheralDelegate, @unchecked Sendable {
    public private(set) var state: TransportState = .disconnected
    public weak var delegate: BleTransportDelegate?
    public var config: BleServiceConfig
    public let targetPeripheralId: UUID?

    private var centralManager: CBCentralManager?
    private var targetPeripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?
    private var notifyCharacteristic: CBCharacteristic?
    private var writeType: CBCharacteristicWriteType = .withResponse

    private var connectContinuation: CheckedContinuation<Void, Error>?
    private var responseContinuation: CheckedContinuation<String, Error>?
    private var receiveBuffer = ""
    private let lock = NSLock()
    private let bleQueue = DispatchQueue(label: "com.carscan.bletransport", qos: .userInitiated)

    public init(config: BleServiceConfig = .standardVeepeak, targetPeripheralId: UUID? = nil) {
        self.config = config
        self.targetPeripheralId = targetPeripheralId
        super.init()
    }

    public func connect() async throws {
        lock.lock()
        if state == .connected {
            lock.unlock()
            return
        }
        state = .connecting
        receiveBuffer = ""
        lock.unlock()

        try await withThrowingTaskGroup(of: Void.self) { group in
            group.addTask {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    self.lock.lock()
                    self.connectContinuation = continuation
                    self.lock.unlock()

                    self.centralManager = CBCentralManager(delegate: self, queue: self.bleQueue)
                }
            }

            // 15 seconds connection timeout
            group.addTask {
                try await Task.sleep(nanoseconds: 15_000_000_000)
                throw TransportError.timeout
            }

            try await group.next()
            group.cancelAll()
        }
    }

    public func disconnect() {
        lock.lock()
        defer { lock.unlock() }

        if let p = targetPeripheral, let cm = centralManager {
            cm.cancelPeripheralConnection(p)
        }
        targetPeripheral = nil
        writeCharacteristic = nil
        notifyCharacteristic = nil
        state = .disconnected
        receiveBuffer = ""
        centralManager?.stopScan()
        centralManager = nil
    }

    public func send(command: String, timeoutSec: TimeInterval) async throws -> String {
        lock.lock()
        guard state == .connected, let p = targetPeripheral, let ch = writeCharacteristic else {
            lock.unlock()
            throw TransportError.notConnected
        }
        // Flush any stale data from previous timed-out command
        receiveBuffer = ""
        let currentWriteType = self.writeType
        lock.unlock()

        let payload = command.hasSuffix("\r") ? command : command + "\r"
        guard let fullData = payload.data(using: .utf8) else {
            throw TransportError.commandFailed("Could not encode command")
        }

        return try await withThrowingTaskGroup(of: String.self) { group in
            group.addTask {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
                    self.lock.lock()
                    self.responseContinuation = continuation
                    self.lock.unlock()

                    // Transmit in MTU chunks
                    let mtu = max(20, p.maximumWriteValueLength(for: currentWriteType))
                    var offset = 0
                    while offset < fullData.count {
                        let chunkSize = min(mtu, fullData.count - offset)
                        let chunk = fullData.subdata(in: offset..<(offset + chunkSize))
                        p.writeValue(chunk, for: ch, type: currentWriteType)
                        offset += chunkSize
                    }
                }
            }

            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(timeoutSec * 1_000_000_000))
                throw TransportError.timeout
            }

            let result = try await group.next()!
            group.cancelAll()
            return result
        }
    }

    public func handleIncomingData(_ data: Data) {
        guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .ascii) else { return }
        lock.lock()
        defer { lock.unlock() }

        receiveBuffer += text
        delegate?.bleTransportDidReceiveData(data)

        if receiveBuffer.contains(">") {
            let res = receiveBuffer
            receiveBuffer = ""
            if let cont = responseContinuation {
                responseContinuation = nil
                cont.resume(returning: res)
            }
        }
    }

    // MARK: - CBCentralManagerDelegate

    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            if let targetId = targetPeripheralId {
                let peripherals = central.retrievePeripherals(withIdentifiers: [targetId])
                if let p = peripherals.first {
                    self.targetPeripheral = p
                    p.delegate = self
                    central.connect(p, options: nil)
                    return
                }
            }
            // Scan for OBD service UUIDs or all nearby devices
            let services = [
                BleServiceConfig.standardVeepeak.serviceUUID,
                BleServiceConfig.vLinkerBLE.serviceUUID
            ]
            central.scanForPeripherals(withServices: services, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])

        case .poweredOff:
            failConnection(TransportError.commandFailed("Bluetooth is powered off"))
        case .unauthorized:
            failConnection(TransportError.commandFailed("Bluetooth permission denied"))
        case .unsupported:
            failConnection(TransportError.commandFailed("Bluetooth Low Energy unsupported on this device"))
        default:
            break
        }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        // If searching for specific device
        if let targetId = targetPeripheralId {
            if peripheral.identifier == targetId {
                central.stopScan()
                self.targetPeripheral = peripheral
                peripheral.delegate = self
                central.connect(peripheral, options: nil)
            }
            return
        }

        // Auto-match ELM327 / vLinker / Veepeak devices
        let name = (peripheral.name ?? (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? "").lowercased()
        if name.contains("obd") || name.contains("vlinker") || name.contains("veepeak") || name.contains("icar") || name.contains("ios-vlink") || name.contains("ble") {
            central.stopScan()
            self.targetPeripheral = peripheral
            peripheral.delegate = self
            central.connect(peripheral, options: nil)
        }
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([
            BleServiceConfig.standardVeepeak.serviceUUID,
            BleServiceConfig.vLinkerBLE.serviceUUID
        ])
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        failConnection(error ?? TransportError.connectionLost)
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        lock.lock()
        state = .disconnected
        lock.unlock()
        delegate?.bleTransportDidUpdateState(.disconnected)
    }

    // MARK: - CBPeripheralDelegate

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            failConnection(error)
            return
        }
        guard let services = peripheral.services, !services.isEmpty else {
            failConnection(TransportError.commandFailed("No supported OBD BLE services found"))
            return
        }
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error = error {
            failConnection(error)
            return
        }
        guard let characteristics = service.characteristics else { return }

        for ch in characteristics {
            if ch.properties.contains(.notify) {
                notifyCharacteristic = ch
                peripheral.setNotifyValue(true, for: ch)
            }
            if ch.properties.contains(.write) {
                writeCharacteristic = ch
                writeType = .withResponse
            } else if ch.properties.contains(.writeWithoutResponse) && writeCharacteristic == nil {
                writeCharacteristic = ch
                writeType = .withoutResponse
            }
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            failConnection(error)
            return
        }
        if characteristic.isNotifying && writeCharacteristic != nil {
            lock.lock()
            state = .connected
            let cont = connectContinuation
            connectContinuation = nil
            lock.unlock()

            cont?.resume()
            delegate?.bleTransportDidUpdateState(.connected)
        }
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value else { return }
        handleIncomingData(data)
    }

    private func failConnection(_ error: Error) {
        lock.lock()
        state = .error(error.localizedDescription)
        let cont = connectContinuation
        connectContinuation = nil
        lock.unlock()

        cont?.resume(throwing: error)
        delegate?.bleTransportDidUpdateState(state)
    }
}

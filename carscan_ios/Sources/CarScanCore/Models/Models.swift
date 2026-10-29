import Foundation

public struct VehicleInfo: Codable, Sendable, Equatable {
    public let vin: String
    public let make: String
    public let model: String?
    public let year: Int?
    public var batteryVoltage: Double
    public var detectedModules: [String]

    public init(vin: String, make: String = "Ford", model: String? = nil, year: Int? = nil, batteryVoltage: Double = 0.0, detectedModules: [String] = []) {
        self.vin = vin
        self.make = make
        self.model = model
        self.year = year
        self.batteryVoltage = batteryVoltage
        self.detectedModules = detectedModules
    }
}

public struct ModuleDefinition: Codable, Sendable, Equatable, Identifiable {
    public let id: String
    public let name: String
    public let acronym: String
    public let make: String
    public let bus: String
    public let txId: String
    public let rxId: String
    public let `protocol`: String
    public let udsSupported: Bool
    public let description: String
    public let verified: Bool?

    public var isVerified: Bool { verified ?? false }
}


public struct PIDDefinition: Codable, Sendable, Equatable, Identifiable {
    public let id: String
    public let mode: String
    public let pid: String
    public let name: String
    public let shortName: String
    public let category: String
    public let module: String
    public let byteCount: Int
    public let formula: String
    public let unit: String
    public let imperialUnit: String?
    public let min: Double
    public let max: Double
    public let precision: Int?
    public let description: String
}

public struct LivePIDValue: Sendable, Equatable {
    public let pidId: String
    public let timestamp: Date
    public let numericValue: Double
    public let formattedString: String
    public let unit: String

    public init(pidId: String, timestamp: Date = Date(), numericValue: Double, formattedString: String, unit: String) {
        self.pidId = pidId
        self.timestamp = timestamp
        self.numericValue = numericValue
        self.formattedString = formattedString
        self.unit = unit
    }
}

public struct DTCRecord: Codable, Sendable, Equatable, Identifiable {
    public var id: String { code }
    public let code: String
    public let system: String
    public let description: String
    public let severity: String
    public let symptom: String?
    public var statusByte: UInt8?

    public init(code: String, system: String, description: String, severity: String, symptom: String? = nil, statusByte: UInt8? = nil) {
        self.code = code
        self.system = system
        self.description = description
        self.severity = severity
        self.symptom = symptom
        self.statusByte = statusByte
    }
}

public enum AdapterFamily: String, Codable, Sendable {
    case elm327Generic = "ELM327_GENERIC"
    case obdlinkSTN = "OBDLINK_STN"
    case vlinkerFS = "VLINKER_FS"
    case unknown = "UNKNOWN"
}

public struct AdapterProfile: Codable, Sendable, Identifiable {
    public var id: String
    public var name: String
    public var family: AdapterFamily
    public var autoBusSwitch: Bool
    public var verified: Bool
    public var hasMsCan: Bool

    public init(id: String, name: String, family: AdapterFamily, autoBusSwitch: Bool, verified: Bool = false, hasMsCan: Bool = true) {
        self.id = id
        self.name = name
        self.family = family
        self.autoBusSwitch = autoBusSwitch
        self.verified = verified
        self.hasMsCan = hasMsCan
    }
}

public enum ModuleScanStatus: Equatable, Sendable {
    case faultsFound([DTCRecord])
    case clean // Responded with 0 DTCs
    case offlineNoResponse // Unresponsive / NO DATA (NEVER display as no error codes)
    case scanError(String)

    public var displayText: String {
        switch self {
        case .faultsFound(let dtcs):
            return "\(dtcs.count) error code\(dtcs.count > 1 ? "s" : "")"
        case .clean:
            return "Successful DTC reading, no error codes"
        case .offlineNoResponse:
            return "No response / Offline"
        case .scanError(let reason):
            return "Error: \(reason)"
        }
    }
}

public struct ModuleScanResult: Identifiable, Sendable {
    public var id: String { module.id }
    public let module: ModuleDefinition
    public let status: ModuleScanStatus
    public let timestamp: Date

    public init(module: ModuleDefinition, status: ModuleScanStatus, timestamp: Date = Date()) {
        self.module = module
        self.status = status
        self.timestamp = timestamp
    }
}


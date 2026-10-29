import Foundation

public struct ModulesSpec: Codable {
    public let version: String
    public let modules: [ModuleDefinition]
}

public struct PidsSpec: Codable {
    public let version: String
    public let pids: [PIDDefinition]
}

public final class SpecLoader: Sendable {
    public init() {}

    /// Returns the bundle containing the spec resources.
    /// Under SwiftPM, this returns `Bundle.module`.
    /// Under Xcode / XcodeGen, `Bundle.module` is not generated, so this returns `Bundle(for: SpecLoader.self)`.
    public static var resourceBundle: Bundle {
        #if SWIFT_PACKAGE
        return Bundle.module
        #else
        return Bundle(for: SpecLoader.self)
        #endif
    }

    /// Locates a spec JSON file by name (with or without .json extension).
    /// Resolves across SwiftPM and XcodeGen resource layouts:
    /// 1. Subdirectory `Spec` (created by SwiftPM `.copy("Resources/Spec")` or Xcode resource folders)
    /// 2. Subdirectory `Resources/Spec`
    /// 3. Bundle root
    /// 4. Fallback to `Bundle.main` if compiled directly into the application bundle.
    public static func url(forSpec specName: String, in bundle: Bundle = resourceBundle) -> URL? {
        let baseName = specName.hasSuffix(".json") ? String(specName.dropLast(5)) : specName
        let ext = "json"

        // 1. Direct search in subdirectory "Spec"
        if let url = bundle.url(forResource: baseName, withExtension: ext, subdirectory: "Spec") {
            return url
        }
        // 2. Direct search in subdirectory "Resources/Spec"
        if let url = bundle.url(forResource: baseName, withExtension: ext, subdirectory: "Resources/Spec") {
            return url
        }
        // 3. Search at bundle root
        if let url = bundle.url(forResource: baseName, withExtension: ext) {
            return url
        }
        // 4. Fallback to Bundle.main if different from framework bundle
        if bundle != Bundle.main {
            if let url = Bundle.main.url(forResource: baseName, withExtension: ext, subdirectory: "Spec") {
                return url
            }
            if let url = Bundle.main.url(forResource: baseName, withExtension: ext, subdirectory: "Resources/Spec") {
                return url
            }
            if let url = Bundle.main.url(forResource: baseName, withExtension: ext) {
                return url
            }
        }
        return nil
    }

    public func loadModules(from url: URL) throws -> [ModuleDefinition] {
        let data = try Data(contentsOf: url)
        let spec = try JSONDecoder().decode(ModulesSpec.self, from: data)
        return spec.modules
    }

    public func loadModules(named name: String = "modules", in bundle: Bundle = resourceBundle) throws -> [ModuleDefinition] {
        guard let url = SpecLoader.url(forSpec: name, in: bundle) else {
            throw NSError(domain: "CarScanCore.SpecLoader", code: 404, userInfo: [NSLocalizedDescriptionKey: "Spec file '\(name).json' not found in bundle \(bundle)"])
        }
        return try loadModules(from: url)
    }

    public func loadPIDs(from url: URL) throws -> [PIDDefinition] {
        let data = try Data(contentsOf: url)
        let spec = try JSONDecoder().decode(PidsSpec.self, from: data)
        return spec.pids
    }

    public func loadPIDs(named name: String = "pids", in bundle: Bundle = resourceBundle) throws -> [PIDDefinition] {
        guard let url = SpecLoader.url(forSpec: name, in: bundle) else {
            throw NSError(domain: "CarScanCore.SpecLoader", code: 404, userInfo: [NSLocalizedDescriptionKey: "Spec file '\(name).json' not found in bundle \(bundle)"])
        }
        return try loadPIDs(from: url)
    }

    public func loadJSON<T: Decodable>(_ type: T.Type, from url: URL) throws -> T {
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(T.self, from: data)
    }

    public func loadJSON<T: Decodable>(_ type: T.Type, named name: String, in bundle: Bundle = resourceBundle) throws -> T {
        guard let url = SpecLoader.url(forSpec: name, in: bundle) else {
            throw NSError(domain: "CarScanCore.SpecLoader", code: 404, userInfo: [NSLocalizedDescriptionKey: "Spec file '\(name).json' not found in bundle \(bundle)"])
        }
        return try loadJSON(type, from: url)
    }
}

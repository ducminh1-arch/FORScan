import XCTest
@testable import CarScanCore

final class Phase2Tests: XCTestCase {

    func testAdapterProfileDetectionGenericELM327() async throws {
        let mock = MockTransport()
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()

        let profile = driver.adapterProfile
        XCTAssertEqual(profile.id, "ELM327_GENERIC")
        XCTAssertEqual(profile.family, .elm327Generic)
        XCTAssertFalse(profile.autoBusSwitch)
        XCTAssertFalse(profile.verified, "Profile must remain unverified until physical vehicle test")
    }

    func testAdapterProfileDetectionSTN() async throws {
        let mock = MockTransport()
        mock.isSTNAdapter = true
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()

        let profile = driver.adapterProfile
        XCTAssertEqual(profile.id, "OBDLINK_STN")
        XCTAssertEqual(profile.family, .obdlinkSTN)
        XCTAssertTrue(profile.autoBusSwitch)
        XCTAssertFalse(profile.verified, "Profile must remain unverified until physical vehicle test")
    }

    func testAdapterProfileDetectionVLinker() async throws {
        let mock = MockTransport()
        mock.isVlinkerAdapter = true
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()

        let profile = driver.adapterProfile
        XCTAssertEqual(profile.id, "VLINKER_FS")
        XCTAssertEqual(profile.family, .vlinkerFS)
        XCTAssertTrue(profile.autoBusSwitch)
        XCTAssertFalse(profile.verified)
    }

    func testSTNBusSwitchingCommands() async throws {
        let mock = MockTransport()
        mock.isSTNAdapter = true
        mock.simulatedStdi = "OBDLink EX"
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()

        XCTAssertTrue(driver.adapterProfile.hasMsCan, "OBDLink EX must support MS-CAN")
        XCTAssertEqual(driver.adapterProfile.name, "OBDLink EX")

        // Switch to MS-CAN: must issue STP53 (ISO 15765-4 125k)
        mock.sentCommands.removeAll()
        try await driver.switchBus(to: "MS-CAN")
        XCTAssertEqual(driver.currentBus, "MS-CAN")
        XCTAssertTrue(mock.sentCommands.contains("STP53"), "Must send STP53 command for MS-CAN")
        XCTAssertFalse(mock.sentCommands.contains("STP51") || mock.sentCommands.contains("STP31"), "Must not send raw CAN STP51/31 for MS-CAN")

        // Switch to HS-CAN: must issue STP33 (ISO 15765-4 500k)
        mock.sentCommands.removeAll()
        try await driver.switchBus(to: "HS-CAN")
        XCTAssertEqual(driver.currentBus, "HS-CAN")
        XCTAssertTrue(mock.sentCommands.contains("STP33"), "Must send STP33 command for HS-CAN")
        XCTAssertFalse(mock.sentCommands.contains("STP31"), "Must not send raw CAN STP31 for HS-CAN")
    }

    func testOBDLinkLXDetectionAndNoMsCanSwitchAttempt() async throws {
        let mock = MockTransport()
        mock.isSTNAdapter = true
        mock.simulatedStdi = "OBDLink LX Bluetooth"
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()

        XCTAssertFalse(driver.adapterProfile.hasMsCan, "OBDLink LX must NOT support MS-CAN")
        XCTAssertFalse(driver.adapterProfile.autoBusSwitch)
        XCTAssertEqual(driver.adapterProfile.name, "OBDLink LX")

        mock.sentCommands.removeAll()
        var didCatchUnsupported = false
        do {
            try await driver.switchBus(to: "MS-CAN")
        } catch let err as TransportError {
            if case .busSwitchFailed(let reason) = err {
                didCatchUnsupported = reason.contains("does not support MS-CAN")
            }
        }
        XCTAssertTrue(didCatchUnsupported, "Must throw busSwitchFailed mentioning MS-CAN unsupported")
        XCTAssertEqual(driver.currentBus, "HS-CAN")
        XCTAssertTrue(mock.sentCommands.filter { $0.hasPrefix("STP") || $0.hasPrefix("ATMSCAN") || $0.hasPrefix("ATSPB") }.isEmpty,
                      "No bus switch command should be sent for unsupported adapter")
    }

    func testGenericELM327ManualSwitchPrompt() async throws {
        let mock = MockTransport()
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()

        var promptReceived: String?
        try await driver.switchBus(to: "MS-CAN") { prompt in
            promptReceived = prompt
        }

        XCTAssertNotNil(promptReceived)
        XCTAssertTrue(promptReceived?.contains("MS-CAN") == true)
        XCTAssertEqual(driver.currentBus, "MS-CAN")
    }

    func testBusSwitchRejectionReturnsQuestionMarkFailsAndDoesNotUpdateBus() async throws {
        let mock = MockTransport()
        mock.isSTNAdapter = true
        mock.rejectBusSwitch = true
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()
        XCTAssertEqual(driver.currentBus, "HS-CAN")

        // switchBus should throw TransportError.busSwitchFailed
        var didCatchBusSwitchFailed = false
        do {
            try await driver.switchBus(to: "MS-CAN")
        } catch let err as TransportError {
            if case .busSwitchFailed = err {
                didCatchBusSwitchFailed = true
            }
        } catch {
            // other error
        }

        XCTAssertTrue(didCatchBusSwitchFailed, "Must throw busSwitchFailed when adapter rejects with '?'")
        // Bus must remain HS-CAN, not set to MS-CAN
        XCTAssertEqual(driver.currentBus, "HS-CAN")
    }

    func testAdapterErrorClassification() {
        let driver = ELM327Driver(transport: MockTransport())

        XCTAssertThrowsError(try driver.checkAdapterError("STOPPED")) { error in
            XCTAssertEqual(error as? TransportError, .adapterStopped)
        }
        XCTAssertThrowsError(try driver.checkAdapterError("UNABLE TO CONNECT")) { error in
            XCTAssertEqual(error as? TransportError, .unableToConnect)
        }
        XCTAssertThrowsError(try driver.checkAdapterError("CAN ERROR")) { error in
            XCTAssertEqual(error as? TransportError, .canError)
        }
        XCTAssertThrowsError(try driver.checkAdapterError("BUS BUSY")) { error in
            XCTAssertEqual(error as? TransportError, .busBusy)
        }
        XCTAssertThrowsError(try driver.checkAdapterError("BUFFER FULL")) { error in
            XCTAssertEqual(error as? TransportError, .bufferFull)
        }
        XCTAssertThrowsError(try driver.checkAdapterError("NO DATA")) { error in
            XCTAssertEqual(error as? TransportError, .noData)
        }
        XCTAssertThrowsError(try driver.checkAdapterError("?")) { error in
            XCTAssertEqual(error as? TransportError, .commandFailed("?"))
        }
    }

    func testUDS19ReadDTCsAndDistinguishOfflineFromClean() async throws {
        let mock = MockTransport()
        mock.headersEnabled = true
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()
        let uds = UDSService(driver: driver)

        let pcmMod = ModuleDefinition(
            id: "FORD_PCM",
            name: "Powertrain Control Module",
            acronym: "PCM",
            make: "Ford",
            bus: "HS-CAN",
            txId: "7E0",
            rxId: "7E8",
            protocol: "ISO_15765_4_11BIT_500K",
            udsSupported: true,
            description: "PCM",
            verified: false
        )

        let bcmMod = ModuleDefinition(
            id = "FORD_BCM",
            name: "Body Control Module",
            acronym: "BCM",
            make: "Ford",
            bus: "MS-CAN",
            txId: "726",
            rxId: "72E",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "BCM",
            verified: false
        )

        let apimMod = ModuleDefinition(
            id: "FORD_APIM",
            name: "Accessory Protocol Interface Module",
            acronym: "APIM",
            make: "Ford",
            bus: "MS-CAN",
            txId: "7D0",
            rxId: "7D8",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "APIM",
            verified: false
        )

        let pamMod = ModuleDefinition(
            id: "FORD_PAM",
            name: "Parking Aid Module",
            acronym: "PAM",
            make: "Ford",
            bus: "MS-CAN",
            txId: "736",
            rxId: "73E",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "PAM",
            verified: false
        )

        // 1. PCM should return FaultsFound (P0171, P0300)
        let pcmRes = await uds.readDTCsForModule(module: pcmMod)
        if case .faultsFound(let dtcs) = pcmRes.status {
            XCTAssertEqual(dtcs.count, 2)
            XCTAssertTrue(dtcs.contains { $0.code.hasPrefix("P0171") })
            XCTAssertTrue(dtcs.contains { $0.code.hasPrefix("P0300") })
        } else {
            XCTFail("PCM should have returned faultsFound")
        }

        // 2. BCM should return B11D9
        let bcmRes = await uds.readDTCsForModule(module: bcmMod)
        if case .faultsFound(let dtcs) = bcmRes.status {
            XCTAssertEqual(dtcs.count, 1)
            XCTAssertTrue(dtcs.first?.code.hasPrefix("B11D9") == true)
        } else {
            XCTFail("BCM should have returned faultsFound")
        }

        // 3. APIM has 0 DTCs -> MUST be Clean (NOT Offline)
        let apimRes = await uds.readDTCsForModule(module: apimMod)
        XCTAssertEqual(apimRes.status, .clean, "APIM responding with 0 DTCs must be Clean")
        XCTAssertEqual(apimRes.status.displayText, "Successful DTC reading, no error codes")

        // 4. PAM is unresponsive -> MUST be OfflineNoResponse (NEVER Clean / no error codes!)
        let pamRes = await uds.readDTCsForModule(module: pamMod)
        XCTAssertEqual(pamRes.status, .offlineNoResponse, "Unresponsive module must be OfflineNoResponse")
        XCTAssertNotEqual(pamRes.status.displayText, "Successful DTC reading, no error codes")
        XCTAssertEqual(pamRes.status.displayText, "No response / Offline")
    }

    func testUDSNRC0x78ResponsePendingDoesNotFail() async throws {
        let mock = MockTransport()
        mock.headersEnabled = true
        mock.simulateNRC78 = true // Prepend 7F 19 78
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()
        let uds = UDSService(driver: driver)

        let bcmMod = ModuleDefinition(
            id: "FORD_BCM",
            name: "Body Control Module",
            acronym: "BCM",
            make: "Ford",
            bus: "MS-CAN",
            txId: "726",
            rxId: "72E",
            protocol: "ISO_15765_4_11BIT_125K",
            udsSupported: true,
            description: "BCM",
            verified: false
        )

        let res = await uds.readDTCsForModule(module: bcmMod)
        if case .faultsFound(let dtcs) = res.status {
            XCTAssertEqual(dtcs.count, 1)
            XCTAssertTrue(dtcs.first?.code.hasPrefix("B11D9") == true)
        } else {
            XCTFail("ResponsePending 7F 19 78 must not cause failure, should have returned faults")
        }
    }

    func testUDS14ClearDTCForIndividualModule() async throws {
        let mock = MockTransport()
        mock.headersEnabled = true
        try await mock.connect()
        let driver = ELM327Driver(transport: mock)
        try await driver.initialize()
        let uds = UDSService(driver: driver)

        let pcmMod = ModuleDefinition(
            id: "FORD_PCM",
            name: "Powertrain Control Module",
            acronym: "PCM",
            make: "Ford",
            bus: "HS-CAN",
            txId: "7E0",
            rxId: "7E8",
            protocol: "ISO_15765_4_11BIT_500K",
            udsSupported: true,
            description: "PCM",
            verified: false
        )

        // Initially has DTCs
        let before = await uds.readDTCsForModule(module: pcmMod)
        if case .faultsFound = before.status {} else {
            XCTFail("PCM should have initial faults")
        }

        // Clear DTCs strictly for PCM
        let clearSuccess = try await uds.clearDTCsForModule(module: pcmMod)
        XCTAssertTrue(clearSuccess)

        // Re-read: should now be Clean
        let after = await uds.readDTCsForModule(module: pcmMod)
        XCTAssertEqual(after.status, .clean, "After clearing, status must be Clean")
    }

    func testUDS14RejectsFunctionalBroadcastAddress() async throws {
        let mock = MockTransport()
        let driver = ELM327Driver(transport: mock)
        let uds = UDSService(driver: driver)

        let broadcastMod = ModuleDefinition(
            id: "BROADCAST",
            name: "All ECUs Broadcast",
            acronym: "ALL",
            make: "Generic",
            bus: "HS-CAN",
            txId: "7DF",
            rxId: "7E8",
            protocol: "ISO_15765_4_11BIT_500K",
            udsSupported: true,
            description: "Broadcast",
            verified: false
        )

        do {
            _ = try await uds.clearDTCsForModule(module: broadcastMod)
            XCTFail("Safety restriction must reject 7DF functional broadcast for UDS 0x14")
        } catch {
            // Expected
        }
    }

    func testEveryModuleHasVerifiedFalseUntilPhysicalVehicleTest() async {
        let vm = await ErrorsViewModel()
        for module in await vm.targetModules {
            XCTAssertFalse(
                module.isVerified,
                "Module \(module.acronym) must have verified: false until tested on physical vehicle"
            )
        }
    }
}

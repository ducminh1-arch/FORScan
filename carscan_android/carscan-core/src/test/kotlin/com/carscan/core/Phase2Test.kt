package com.carscan.core

import com.carscan.core.adapter.ELM327Driver
import com.carscan.core.models.AdapterFamily
import com.carscan.core.models.ModuleDefinition
import com.carscan.core.models.ModuleScanStatus
import com.carscan.core.protocol.UDSService
import com.carscan.core.transport.*
import com.carscan.core.viewmodel.ErrorsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Phase2Test {

    @Test
    fun testAdapterProfileDetectionGenericELM327() {
        runBlocking {
            val mock = MockTransport()
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()

            val profile = driver.adapterProfile
            assertEquals("ELM327_GENERIC", profile.id)
            assertEquals(AdapterFamily.ELM327_GENERIC, profile.family)
            assertFalse(profile.autoBusSwitch)
            assertFalse("Profile must remain unverified until physical vehicle test", profile.verified)
        }
    }

    @Test
    fun testAdapterProfileDetectionSTN() {
        runBlocking {
            val mock = MockTransport()
            mock.isSTNAdapter = true
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()

            val profile = driver.adapterProfile
            assertEquals("OBDLINK_STN", profile.id)
            assertEquals(AdapterFamily.OBDLINK_STN, profile.family)
            assertTrue(profile.autoBusSwitch)
            assertFalse("Profile must remain unverified until physical vehicle test", profile.verified)
        }
    }

    @Test
    fun testAdapterProfileDetectionVLinker() {
        runBlocking {
            val mock = MockTransport()
            mock.isVlinkerAdapter = true
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()

            val profile = driver.adapterProfile
            assertEquals("VLINKER_FS", profile.id)
            assertEquals(AdapterFamily.VLINKER_FS, profile.family)
            assertTrue(profile.autoBusSwitch)
            assertFalse(profile.verified)
        }
    }

    @Test
    fun testSTNBusSwitchingCommands() {
        runBlocking {
            val mock = MockTransport()
            mock.isSTNAdapter = true
            mock.simulatedStdi = "OBDLink EX"
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()

            assertTrue("OBDLink EX must have MS-CAN support", driver.adapterProfile.hasMsCan)
            assertEquals("OBDLink EX", driver.adapterProfile.name)

            // Switch to MS-CAN: must issue STP53 (ISO 15765-4 125k)
            mock.sentCommands.clear()
            driver.switchBus("MS-CAN")
            assertEquals("MS-CAN", driver.currentBus)
            assertTrue("Must send STP53 command for MS-CAN", mock.sentCommands.contains("STP53"))
            assertFalse("Must not send raw CAN STP51 or wrong STP33 for MS-CAN", mock.sentCommands.contains("STP51") || mock.sentCommands.contains("STP31"))

            // Switch to HS-CAN: must issue STP33 (ISO 15765-4 500k)
            mock.sentCommands.clear()
            driver.switchBus("HS-CAN")
            assertEquals("HS-CAN", driver.currentBus)
            assertTrue("Must send STP33 command for HS-CAN", mock.sentCommands.contains("STP33"))
            assertFalse("Must not send raw CAN STP31 for HS-CAN", mock.sentCommands.contains("STP31"))
        }
    }

    @Test
    fun testOBDLinkLXDetectionAndNoMsCanSwitchAttempt() {
        runBlocking {
            val mock = MockTransport()
            mock.isSTNAdapter = true
            mock.simulatedStdi = "OBDLink LX Bluetooth"
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()

            assertFalse("OBDLink LX must NOT have MS-CAN support", driver.adapterProfile.hasMsCan)
            assertFalse(driver.adapterProfile.autoBusSwitch)
            assertEquals("OBDLink LX", driver.adapterProfile.name)

            mock.sentCommands.clear()
            val ex = assertThrows(BusSwitchFailedException::class.java) {
                runBlocking {
                    driver.switchBus("MS-CAN")
                }
            }
            assertTrue(ex.message!!.contains("does not support MS-CAN"))
            assertEquals("HS-CAN", driver.currentBus)
            // Verify no bus switch command was sent to adapter!
            assertTrue("No bus switch commands should be sent for unsupported adapter",
                mock.sentCommands.none { it.startsWith("STP") || it.startsWith("ATMSCAN") || it.startsWith("ATSPB") })
        }
    }

    @Test
    fun testGenericELM327ManualSwitchPrompt() {
        runBlocking {
            val mock = MockTransport()
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()

            var promptReceived: String? = null
            driver.switchBus("MS-CAN") { prompt ->
                promptReceived = prompt
            }

            assertNotNull(promptReceived)
            assertTrue(promptReceived!!.contains("MS-CAN"))
            assertEquals("MS-CAN", driver.currentBus)
        }
    }

    @Test
    fun testBusSwitchRejectionReturnsQuestionMarkFailsAndDoesNotUpdateBus() {
        runBlocking {
            val mock = MockTransport()
            mock.isSTNAdapter = true
            mock.rejectBusSwitch = true // Simulates adapter returning '?' to bus switch command
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()
            assertEquals("HS-CAN", driver.currentBus)

            // When adapter returns '?', switchBus MUST throw BusSwitchFailedException
            assertThrows(BusSwitchFailedException::class.java) {
                runBlocking {
                    driver.switchBus("MS-CAN")
                }
            }

            // Must NOT proceed as if switch succeeded: currentBus remains HS-CAN
            assertEquals("HS-CAN", driver.currentBus)
        }
    }

    @Test
    fun testAdapterErrorClassification() {
        val driver = ELM327Driver(MockTransport())

        assertThrows(AdapterStoppedException::class.java) {
            driver.checkAdapterError("STOPPED")
        }
        assertThrows(UnableToConnectException::class.java) {
            driver.checkAdapterError("UNABLE TO CONNECT")
        }
        assertThrows(CanErrorException::class.java) {
            driver.checkAdapterError("CAN ERROR")
        }
        assertThrows(BusBusyException::class.java) {
            driver.checkAdapterError("BUS BUSY")
        }
        assertThrows(BufferFullException::class.java) {
            driver.checkAdapterError("BUFFER FULL")
        }
        assertThrows(NoDataException::class.java) {
            driver.checkAdapterError("NO DATA")
        }
        assertThrows(CommandFailedException::class.java) {
            driver.checkAdapterError("?")
        }
    }

    @Test
    fun testUDS19ReadDTCsAndDistinguishOfflineFromClean() {
        runBlocking {
            val mock = MockTransport()
            mock.headersEnabled = true
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()
            val uds = UDSService(driver)

            val pcmMod = ModuleDefinition(
                id = "FORD_PCM",
                name = "Powertrain Control Module",
                acronym = "PCM",
                make = "Ford",
                bus = "HS-CAN",
                txId = "7E0",
                rxId = "7E8",
                protocol = "ISO_15765_4_11BIT_500K",
                udsSupported = true,
                description = "PCM",
                verified = false
            )

            val bcmMod = ModuleDefinition(
                id = "FORD_BCM",
                name = "Body Control Module",
                acronym = "BCM",
                make = "Ford",
                bus = "MS-CAN",
                txId = "726",
                rxId = "72E",
                protocol = "ISO_15765_4_11BIT_125K",
                udsSupported = true,
                description = "BCM",
                verified = false
            )

            val apimMod = ModuleDefinition(
                id = "FORD_APIM",
                name = "Accessory Protocol Interface Module",
                acronym = "APIM",
                make = "Ford",
                bus = "MS-CAN",
                txId = "7D0",
                rxId = "7D8",
                protocol = "ISO_15765_4_11BIT_125K",
                udsSupported = true,
                description = "APIM",
                verified = false
            )

            val pamMod = ModuleDefinition(
                id = "FORD_PAM",
                name = "Parking Aid Module",
                acronym = "PAM",
                make = "Ford",
                bus = "MS-CAN",
                txId = "736",
                rxId = "73E",
                protocol = "ISO_15765_4_11BIT_125K",
                udsSupported = true,
                description = "PAM",
                verified = false
            )

            // 1. PCM should return FaultsFound (P0171, P0300)
            val pcmRes = uds.readDTCsForModule(pcmMod)
            assertTrue(pcmRes.status is ModuleScanStatus.FaultsFound)
            val pcmDtcs = (pcmRes.status as ModuleScanStatus.FaultsFound).dtcs
            assertEquals(2, pcmDtcs.size)
            assertTrue(pcmDtcs.any { it.code.startsWith("P0171") })
            assertTrue(pcmDtcs.any { it.code.startsWith("P0300") })

            // 2. BCM should return B11D9
            val bcmRes = uds.readDTCsForModule(bcmMod)
            assertTrue(bcmRes.status is ModuleScanStatus.FaultsFound)
            val bcmDtcs = (bcmRes.status as ModuleScanStatus.FaultsFound).dtcs
            assertEquals(1, bcmDtcs.size)
            assertTrue(bcmDtcs.first().code.startsWith("B11D9"))

            // 3. APIM has 0 DTCs -> MUST be Clean (NOT Offline)
            val apimRes = uds.readDTCsForModule(apimMod)
            assertTrue("APIM responding with 0 DTCs must be Clean", apimRes.status is ModuleScanStatus.Clean)
            assertEquals("Successful DTC reading, no error codes", apimRes.status.displayText)

            // 4. PAM is in unresponsiveHeaders -> MUST be OfflineNoResponse (NEVER Clean / no error codes!)
            val pamRes = uds.readDTCsForModule(pamMod)
            assertTrue("Unresponsive module must be OfflineNoResponse", pamRes.status is ModuleScanStatus.OfflineNoResponse)
            assertNotEquals("Must not display 'no error codes' for offline module", "Successful DTC reading, no error codes", pamRes.status.displayText)
            assertEquals("No response / Offline", pamRes.status.displayText)
        }
    }

    @Test
    fun testUDSNRC0x78ResponsePendingDoesNotFail() {
        runBlocking {
            val mock = MockTransport()
            mock.headersEnabled = true
            mock.simulateNRC78 = true // Prepend 7F 19 78
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()
            val uds = UDSService(driver)

            val bcmMod = ModuleDefinition(
                id = "FORD_BCM",
                name = "Body Control Module",
                acronym = "BCM",
                make = "Ford",
                bus = "MS-CAN",
                txId = "726",
                rxId = "72E",
                protocol = "ISO_15765_4_11BIT_125K",
                udsSupported = true,
                description = "BCM",
                verified = false
            )

            // NRC 0x78 should NOT throw an error; positive response should be parsed
            val res = uds.readDTCsForModule(bcmMod)
            assertTrue("ResponsePending 7F 19 78 must not cause failure", res.status is ModuleScanStatus.FaultsFound)
            val dtcs = (res.status as ModuleScanStatus.FaultsFound).dtcs
            assertEquals(1, dtcs.size)
            assertTrue(dtcs.first().code.startsWith("B11D9"))
        }
    }

    @Test
    fun testUDS14ClearDTCForIndividualModule() {
        runBlocking {
            val mock = MockTransport()
            mock.headersEnabled = true
            mock.connect()
            val driver = ELM327Driver(mock)
            driver.initialize()
            val uds = UDSService(driver)

            val pcmMod = ModuleDefinition(
                id = "FORD_PCM",
                name = "Powertrain Control Module",
                acronym = "PCM",
                make = "Ford",
                bus = "HS-CAN",
                txId = "7E0",
                rxId = "7E8",
                protocol = "ISO_15765_4_11BIT_500K",
                udsSupported = true,
                description = "PCM",
                verified = false
            )

            // Initially has DTCs
            val before = uds.readDTCsForModule(pcmMod)
            assertTrue(before.status is ModuleScanStatus.FaultsFound)

            // Clear DTCs strictly for PCM
            val clearSuccess = uds.clearDTCsForModule(pcmMod)
            assertTrue(clearSuccess)

            // Re-read: should now be Clean
            val after = uds.readDTCsForModule(pcmMod)
            assertTrue("After clearing, status must be Clean", after.status is ModuleScanStatus.Clean)
        }
    }

    @Test
    fun testUDS14RejectsFunctionalBroadcastAddress() {
        runBlocking {
            val mock = MockTransport()
            val driver = ELM327Driver(mock)
            val uds = UDSService(driver)

            val broadcastMod = ModuleDefinition(
                id = "BROADCAST",
                name = "All ECUs Broadcast",
                acronym = "ALL",
                make = "Generic",
                bus = "HS-CAN",
                txId = "7DF",
                rxId = "7E8",
                protocol = "ISO_15765_4_11BIT_500K",
                udsSupported = true,
                description = "Broadcast",
                verified = false
            )

            // Safety policy: UDS 0x14 must never be broadcast
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { uds.clearDTCsForModule(broadcastMod) }
            }
        }
    }

    @Test
    fun testEveryModuleHasVerifiedFalseUntilPhysicalVehicleTest() {
        val vm = ErrorsViewModel()
        for (module in vm.targetModules) {
            assertFalse(
                "Module ${module.acronym} must have verified: false until tested on physical vehicle",
                module.verified
            )
        }
    }
}

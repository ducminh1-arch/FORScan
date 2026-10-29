package com.carscan.core.viewmodel

import com.carscan.core.models.DTCRecord
import com.carscan.core.models.ModuleDefinition
import com.carscan.core.models.ModuleScanResult
import com.carscan.core.models.ModuleScanStatus
import com.carscan.core.transport.ConnectionState
import com.carscan.core.transport.BusSwitchFailedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ErrorsViewModel(private val session: SessionManager = SessionManager.instance) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val scanResults = MutableStateFlow<List<ModuleScanResult>>(emptyList())
    val isScanning = MutableStateFlow(false)
    val isClearing = MutableStateFlow(false)
    val scanningModuleAcronym = MutableStateFlow<String?>(null)

    // Per-module DTC clear confirmation
    val selectedModuleForClear = MutableStateFlow<ModuleDefinition?>(null)
    val showClearConfirmDialog = MutableStateFlow(false)
    val alertMessage = MutableStateFlow<String?>(null)

    val targetModules: List<ModuleDefinition> = listOf(
        ModuleDefinition(
            id = "FORD_PCM",
            name = "Powertrain Control Module",
            acronym = "PCM",
            make = "Ford",
            bus = "HS-CAN",
            txId = "7E0",
            rxId = "7E8",
            protocol = "ISO_15765_4_11BIT_500K",
            udsSupported = true,
            description = "Engine and powertrain management ECU.",
            verified = false
        ),
        ModuleDefinition(
            id = "FORD_TCM",
            name = "Transmission Control Module",
            acronym = "TCM",
            make = "Ford",
            bus = "HS-CAN",
            txId = "7E1",
            rxId = "7E9",
            protocol = "ISO_15765_4_11BIT_500K",
            udsSupported = true,
            description = "Automatic / dual-clutch transmission control module.",
            verified = false
        ),
        ModuleDefinition(
            id = "FORD_ABS",
            name = "Antilock Braking System",
            acronym = "ABS",
            make = "Ford",
            bus = "HS-CAN",
            txId = "760",
            rxId = "768",
            protocol = "ISO_15765_4_11BIT_500K",
            udsSupported = true,
            description = "ABS and Traction Control module.",
            verified = false
        ),
        ModuleDefinition(
            id = "FORD_RCM",
            name = "Restraint Control Module",
            acronym = "RCM",
            make = "Ford",
            bus = "HS-CAN",
            txId = "737",
            rxId = "73F",
            protocol = "ISO_15765_4_11BIT_500K",
            udsSupported = true,
            description = "Airbag and restraint system module.",
            verified = false
        ),
        ModuleDefinition(
            id = "FORD_BCM",
            name = "Body Control Module",
            acronym = "BCM",
            make = "Ford",
            bus = "MS-CAN",
            txId = "726",
            rxId = "72E",
            protocol = "ISO_15765_4_11BIT_125K",
            udsSupported = true,
            description = "Body electrical and battery monitoring module.",
            verified = false
        ),
        ModuleDefinition(
            id = "FORD_IPC",
            name = "Instrument Panel Cluster",
            acronym = "IPC",
            make = "Ford",
            bus = "MS-CAN",
            txId = "720",
            rxId = "728",
            protocol = "ISO_15765_4_11BIT_125K",
            udsSupported = true,
            description = "Dashboard instrument cluster display.",
            verified = false
        ),
        ModuleDefinition(
            id = "FORD_APIM",
            name = "Accessory Protocol Interface Module",
            acronym = "APIM",
            make = "Ford",
            bus = "MS-CAN",
            txId = "7D0",
            rxId = "7D8",
            protocol = "ISO_15765_4_11BIT_125K",
            udsSupported = true,
            description = "Ford SYNC infotainment unit.",
            verified = false
        ),
        ModuleDefinition(
            id = "FORD_PAM",
            name = "Parking Aid Module",
            acronym = "PAM",
            make = "Ford",
            bus = "MS-CAN",
            txId = "736",
            rxId = "73E",
            protocol = "ISO_15765_4_11BIT_125K",
            udsSupported = true,
            description = "Ultrasonic park distance control module.",
            verified = false
        ),
        ModuleDefinition(
            id = "FORD_TCU",
            name = "Telematic Control Unit",
            acronym = "TCU",
            make = "Ford",
            bus = "MS-CAN",
            txId = "754",
            rxId = "75C",
            protocol = "ISO_15765_4_11BIT_125K",
            udsSupported = true,
            description = "FordPass modem module.",
            verified = false
        )
    )

    fun scanDTCs() {
        if (session.connectionState.value !is ConnectionState.Connected) {
            alertMessage.value = "Please connect to adapter before scanning error codes."
            return
        }

        val uds = session.uds
        val isDemo = session.isDemoMode.value

        isScanning.value = true
        scanResults.value = emptyList()

        scope.launch(Dispatchers.IO) {
            val results = mutableListOf<ModuleScanResult>()
            try {
                // Group modules by bus: HS-CAN first, then MS-CAN
                val hsModules = targetModules.filter { it.bus == "HS-CAN" }
                val msModules = targetModules.filter { it.bus == "MS-CAN" }

                // --- 1. HS-CAN Scan ---
                if (!isDemo) {
                    try {
                        session.switchBus("HS-CAN")
                    } catch (e: BusSwitchFailedException) {
                        alertMessage.value = "Bus switch failed"
                        return@launch
                    }
                }

                for (m in hsModules) {
                    scanningModuleAcronym.value = m.acronym
                    val res: ModuleScanResult = if (isDemo) {
                        simulateDemoScanResult(m)
                    } else {
                        if (uds != null) {
                            session.queue.execute { uds.readDTCsForModule(m) }
                        } else {
                            ModuleScanResult(m, ModuleScanStatus.ScanError("UDS service unavailable"))
                        }
                    }
                    results.add(res)
                    scanResults.value = results.toList()
                }

                // --- 2. MS-CAN Scan ---
                val currentProfile = session.adapterProfile.value ?: session.driver?.adapterProfile
                if (!isDemo && msModules.isNotEmpty()) {
                    if (currentProfile != null && !currentProfile.hasMsCan) {
                        alertMessage.value = "Adapter does not support MS-CAN (${currentProfile.name}). MS-CAN modules skipped."
                        for (m in msModules) {
                            results.add(ModuleScanResult(m, ModuleScanStatus.ScanError("Adapter does not support MS-CAN (${currentProfile.name})")))
                        }
                        scanResults.value = results.toList()
                    } else {
                        try {
                            session.switchBus("MS-CAN")
                        } catch (e: BusSwitchFailedException) {
                            alertMessage.value = "Bus switch failed"
                            // Do not proceed with scanning MS-CAN modules!
                            return@launch
                        }

                        for (m in msModules) {
                            scanningModuleAcronym.value = m.acronym
                            val res: ModuleScanResult = if (uds != null) {
                                session.queue.execute { uds.readDTCsForModule(m) }
                            } else {
                                ModuleScanResult(m, ModuleScanStatus.ScanError("UDS service unavailable"))
                            }
                            results.add(res)
                            scanResults.value = results.toList()
                        }
                    }
                } else if (isDemo && msModules.isNotEmpty()) {
                    for (m in msModules) {
                        results.add(simulateDemoScanResult(m))
                        scanResults.value = results.toList()
                    }
                }

                // Switch back to HS-CAN
                if (!isDemo) {
                    try {
                        session.switchBus("HS-CAN")
                    } catch (e: Exception) {
                        // ignore cleanup error
                    }
                }
            } catch (e: BusSwitchFailedException) {
                alertMessage.value = "Bus switch failed"
            } catch (e: Exception) {
                alertMessage.value = "DTC scan interrupted: ${e.message}"
            } finally {
                isScanning.value = false
                scanningModuleAcronym.value = null
            }
        }
    }

    private fun simulateDemoScanResult(m: ModuleDefinition): ModuleScanResult {
        return when (m.acronym) {
            "PCM" -> ModuleScanResult(
                module = m,
                status = ModuleScanStatus.FaultsFound(
                    listOf(
                        DTCRecord(
                            code = "P0171:00",
                            system = "Powertrain",
                            description = "System Too Lean (Bank 1)",
                            severity = "Confirmed",
                            symptom = "Fuel trim exceed rich limits",
                            statusByte = 0x2F
                        ),
                        DTCRecord(
                            code = "P0300:00",
                            system = "Powertrain",
                            description = "Random/Multiple Cylinder Misfire Detected",
                            severity = "Pending",
                            symptom = "Engine misfire detected during last cycle",
                            statusByte = 0x28
                        )
                    )
                )
            )
            "TCM" -> ModuleScanResult(
                module = m,
                status = ModuleScanStatus.FaultsFound(
                    listOf(
                        DTCRecord(
                            code = "P0700:00",
                            system = "Powertrain",
                            description = "Transmission Control System (MIL Request)",
                            severity = "Confirmed",
                            statusByte = 0x2F
                        )
                    )
                )
            )
            "BCM" -> ModuleScanResult(
                module = m,
                status = ModuleScanStatus.FaultsFound(
                    listOf(
                        DTCRecord(
                            code = "B11D9:09-0A",
                            system = "Body",
                            description = "Vehicle Battery - Component Failure / Low Resistance",
                            severity = "Confirmed",
                            symptom = "Ford BMS reports 12V battery health degraded, low state of charge.",
                            statusByte = 0x2F
                        )
                    )
                )
            )
            "ABS" -> ModuleScanResult(
                module = m,
                status = ModuleScanStatus.FaultsFound(
                    listOf(
                        DTCRecord(
                            code = "C0031:01",
                            system = "Chassis",
                            description = "Left Front Wheel Speed Sensor - General Electrical Failure",
                            severity = "Confirmed",
                            statusByte = 0x2F
                        )
                    )
                )
            )
            "IPC", "APIM" -> ModuleScanResult(
                module = m,
                status = ModuleScanStatus.Clean
            )
            "PAM", "TCU", "RCM" -> ModuleScanResult(
                module = m,
                status = ModuleScanStatus.OfflineNoResponse
            )
            else -> ModuleScanResult(m, ModuleScanStatus.OfflineNoResponse)
        }
    }

    /**
     * Prepares module-specific DTC clear with confirmation dialog.
     */
    fun requestClearDTCs(module: ModuleDefinition) {
        selectedModuleForClear.value = module
        showClearConfirmDialog.value = true
    }

    fun dismissClearDialog() {
        showClearConfirmDialog.value = false
        selectedModuleForClear.value = null
    }

    /**
     * Executes UDS 0x14 strictly for the selected module.
     */
    fun executeClearDTCsForModule() {
        val module = selectedModuleForClear.value ?: return
        if (session.connectionState.value !is ConnectionState.Connected) return

        val uds = session.uds
        val isDemo = session.isDemoMode.value

        isClearing.value = true
        scope.launch(Dispatchers.IO) {
            try {
                if (isDemo) {
                    // In demo mode: clear DTCs for this module
                    val updatedList = scanResults.value.map { item ->
                        if (item.module.id == module.id) {
                            item.copy(status = ModuleScanStatus.Clean)
                        } else item
                    }
                    scanResults.value = updatedList
                    alertMessage.value = "Diagnostic trouble codes cleared for ${module.acronym}."
                } else {
                    if (uds == null) throw IllegalStateException("UDS service unavailable")
                    val prof = session.adapterProfile.value ?: session.driver?.adapterProfile
                    if (module.bus.uppercase() == "MS-CAN" && prof != null && !prof.hasMsCan) {
                        alertMessage.value = "Cannot clear DTC: Adapter ${prof.name} does not support MS-CAN."
                        return@launch
                    }
                    // Switch to module's bus if needed
                    try {
                        session.switchBus(module.bus)
                    } catch (e: BusSwitchFailedException) {
                        alertMessage.value = "Bus switch failed"
                        return@launch
                    }
                    session.queue.execute { uds.clearDTCsForModule(module) }

                    // Re-read DTCs for this module
                    val freshResult = session.queue.execute { uds.readDTCsForModule(module) }
                    val updatedList = scanResults.value.map { item ->
                        if (item.module.id == module.id) freshResult else item
                    }
                    scanResults.value = updatedList
                    try {
                        session.switchBus("HS-CAN")
                    } catch (e: Exception) {
                        // ignore
                    }
                    alertMessage.value = "Diagnostic trouble codes cleared successfully for ${module.acronym}."
                }
            } catch (e: BusSwitchFailedException) {
                alertMessage.value = "Bus switch failed"
            } catch (e: Exception) {
                alertMessage.value = "Failed to clear DTCs for ${module.acronym}: ${e.message}"
            } finally {
                isClearing.value = false
                showClearConfirmDialog.value = false
                selectedModuleForClear.value = null
            }
        }
    }

    fun exportLog(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = java.lang.StringBuilder()
        sb.append("CarScan Lite - Multi-Module Diagnostic Trouble Codes Report\n")
        sb.append("Generated: ${sdf.format(Date())}\n")
        sb.append("VIN: ${session.vehicleInfo.value?.vin ?: "Unknown"}\n\n")

        for (r in scanResults.value) {
            val verifiedTag = if (r.module.verified) "[Verified]" else "[Unverified: false]"
            sb.append("Module: ${r.module.name} (${r.module.acronym}) [TX: ${r.module.txId}, Bus: ${r.module.bus}] $verifiedTag\n")
            sb.append("Status: ${r.status.displayText}\n")

            if (r.status is ModuleScanStatus.FaultsFound) {
                for (d in r.status.dtcs) {
                    sb.append("  - ${d.code}: ${d.description} (${d.severity})\n")
                    if (d.symptom != null) {
                        sb.append("    Symptom: ${d.symptom}\n")
                    }
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}

package com.carscan.core.models

import kotlinx.serialization.Serializable

@Serializable
data class VehicleInfo(
    val vin: String,
    val make: String = "Ford",
    val model: String? = null,
    val year: Int? = null,
    val batteryVoltage: Double = 0.0,
    val detectedModules: List<String> = emptyList()
)

@Serializable
data class ModuleDefinition(
    val id: String,
    val name: String,
    val acronym: String,
    val make: String,
    val bus: String,
    val txId: String,
    val rxId: String,
    val `protocol`: String,
    val udsSupported: Boolean,
    val description: String,
    val verified: Boolean = false
)


@Serializable
data class PIDDefinition(
    val id: String,
    val mode: String,
    val pid: String,
    val name: String,
    val shortName: String,
    val category: String,
    val module: String,
    val byteCount: Int,
    val formula: String,
    val unit: String,
    val imperialUnit: String? = null,
    val min: Double,
    val max: Double,
    val precision: Int? = null,
    val description: String
)

data class LivePIDValue(
    val pidId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val numericValue: Double,
    val formattedString: String,
    val unit: String
)

@Serializable
data class DTCRecord(
    val code: String,
    val system: String,
    val description: String,
    val severity: String,
    val symptom: String? = null,
    val statusByte: Int? = null
)

@Serializable
enum class AdapterFamily {
    ELM327_GENERIC,
    OBDLINK_STN,
    VLINKER_FS,
    UNKNOWN
}

@Serializable
data class AdapterProfile(
    val id: String,
    val name: String,
    val family: AdapterFamily,
    val autoBusSwitch: Boolean,
    val verified: Boolean = false,
    val hasMsCan: Boolean = true
)

sealed class ModuleScanStatus {
    data class FaultsFound(val dtcs: List<DTCRecord>) : ModuleScanStatus()
    object Clean : ModuleScanStatus() // Responded with 0 DTCs
    object OfflineNoResponse : ModuleScanStatus() // NO DATA / Timeout (NEVER display as no error codes)
    data class ScanError(val reason: String) : ModuleScanStatus() // CAN ERROR, etc.

    val displayText: String
        get() = when (this) {
            is FaultsFound -> "${dtcs.size} error code(s)"
            is Clean -> "Successful DTC reading, no error codes"
            is OfflineNoResponse -> "No response / Offline"
            is ScanError -> "Error: $reason"
        }
}

data class ModuleScanResult(
    val module: ModuleDefinition,
    val status: ModuleScanStatus,
    val timestamp: Long = System.currentTimeMillis()
)


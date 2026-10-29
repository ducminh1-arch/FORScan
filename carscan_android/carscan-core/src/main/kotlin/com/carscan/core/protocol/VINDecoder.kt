package com.carscan.core.protocol

/**
 * Standard ISO 3779 / SAE J272 VIN Decoder.
 * Decodes vehicle metadata including Model Year from the 10th character of a 17-character VIN.
 */
object VINDecoder {

    /**
     * Decodes the Model Year from the 10th character (index 9) of a 17-character VIN.
     * Maps ISO 3779 year codes to modern model years (2010–2039):
     * A=2010, B=2011, ..., H=2017, J=2018, K=2019, L=2020, M=2021, N=2022, P=2023, R=2024, S=2025, etc.
     * Note: Letters I, O, Q, U, Z are excluded by ISO 3779.
     */
    fun decodeModelYear(vin: String): Int? {
        val cleanVin = vin.trim().uppercase()
        if (cleanVin.length < 10) return null
        return when (cleanVin[9]) {
            'A' -> 2010
            'B' -> 2011
            'C' -> 2012
            'D' -> 2013
            'E' -> 2014
            'F' -> 2015
            'G' -> 2016
            'H' -> 2017
            'J' -> 2018
            'K' -> 2019
            'L' -> 2020
            'M' -> 2021
            'N' -> 2022
            'P' -> 2023
            'R' -> 2024
            'S' -> 2025
            'T' -> 2026
            'V' -> 2027
            'W' -> 2028
            'X' -> 2029
            'Y' -> 2030
            '1' -> 2031
            '2' -> 2032
            '3' -> 2033
            '4' -> 2034
            '5' -> 2035
            '6' -> 2036
            '7' -> 2037
            '8' -> 2038
            '9' -> 2039
            else -> null
        }
    }

    /**
     * Decodes basic manufacturer information from WMI (World Manufacturer Identifier, first 3 chars).
     */
    fun decodeMake(vin: String): String {
        val clean = vin.trim().uppercase()
        return when {
            clean.startsWith("1FA") || clean.startsWith("1FB") || clean.startsWith("1FC") ||
                    clean.startsWith("1FD") || clean.startsWith("1FM") || clean.startsWith("1FT") ||
                    clean.startsWith("2FA") || clean.startsWith("3FA") || clean.startsWith("WF0") -> "Ford"
            clean.startsWith("JM1") || clean.startsWith("JMZ") || clean.startsWith("3MZ") -> "Mazda"
            clean.startsWith("1GN") || clean.startsWith("1GC") -> "Chevrolet"
            clean.startsWith("1J4") -> "Jeep"
            clean.startsWith("WAU") || clean.startsWith("WA1") -> "Audi"
            clean.startsWith("WBA") || clean.startsWith("WBS") -> "BMW"
            clean.startsWith("WDB") || clean.startsWith("WDD") -> "Mercedes-Benz"
            clean.startsWith("WVW") || clean.startsWith("1VW") -> "Volkswagen"
            clean.startsWith("JT") || clean.startsWith("4T") -> "Toyota"
            clean.startsWith("JH") || clean.startsWith("1HG") -> "Honda"
            else -> "Unknown Make"
        }
    }
}

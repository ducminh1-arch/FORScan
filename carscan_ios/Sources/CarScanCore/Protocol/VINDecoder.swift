import Foundation

/// Standard ISO 3779 / SAE J272 VIN Decoder.
/// Decodes vehicle metadata including Model Year from the 10th character of a 17-character VIN.
public enum VINDecoder {

    /// Decodes the Model Year from the 10th character (index 9) of a 17-character VIN.
    /// Maps ISO 3779 year codes to modern model years (2010–2039):
    /// A=2010, B=2011, ..., H=2017, J=2018, K=2019, L=2020, M=2021, N=2022, P=2023, R=2024, S=2025, etc.
    /// Note: Letters I, O, Q, U, Z are excluded by ISO 3779.
    public static func decodeModelYear(from vin: String) -> Int? {
        let clean = vin.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard clean.count >= 10 else { return nil }
        let index = clean.index(clean.startIndex, offsetBy: 9)
        let code = clean[index]

        switch code {
        case "A": return 2010
        case "B": return 2011
        case "C": return 2012
        case "D": return 2013
        case "E": return 2014
        case "F": return 2015
        case "G": return 2016
        case "H": return 2017
        case "J": return 2018
        case "K": return 2019
        case "L": return 2020
        case "M": return 2021
        case "N": return 2022
        case "P": return 2023
        case "R": return 2024
        case "S": return 2025
        case "T": return 2026
        case "V": return 2027
        case "W": return 2028
        case "X": return 2029
        case "Y": return 2030
        case "1": return 2031
        case "2": return 2032
        case "3": return 2033
        case "4": return 2034
        case "5": return 2035
        case "6": return 2036
        case "7": return 2037
        case "8": return 2038
        case "9": return 2039
        default: return nil
        }
    }

    /// Decodes basic manufacturer information from WMI (World Manufacturer Identifier, first 3 chars).
    public static func decodeMake(from vin: String) -> String {
        let clean = vin.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if clean.hasPrefix("1FA") || clean.hasPrefix("1FB") || clean.hasPrefix("1FC") ||
            clean.hasPrefix("1FD") || clean.hasPrefix("1FM") || clean.hasPrefix("1FT") ||
            clean.hasPrefix("2FA") || clean.hasPrefix("3FA") || clean.hasPrefix("WF0") {
            return "Ford"
        }
        if clean.hasPrefix("JM1") || clean.hasPrefix("JMZ") || clean.hasPrefix("3MZ") {
            return "Mazda"
        }
        if clean.hasPrefix("1GN") || clean.hasPrefix("1GC") {
            return "Chevrolet"
        }
        if clean.hasPrefix("1J4") {
            return "Jeep"
        }
        if clean.hasPrefix("WAU") || clean.hasPrefix("WA1") {
            return "Audi"
        }
        if clean.hasPrefix("WBA") || clean.hasPrefix("WBS") {
            return "BMW"
        }
        if clean.hasPrefix("WDB") || clean.hasPrefix("WDD") {
            return "Mercedes-Benz"
        }
        if clean.hasPrefix("WVW") || clean.hasPrefix("1VW") {
            return "Volkswagen"
        }
        if clean.hasPrefix("JT") || clean.hasPrefix("4T") {
            return "Toyota"
        }
        if clean.hasPrefix("JH") || clean.hasPrefix("1HG") {
            return "Honda"
        }
        return "Unknown Make"
    }
}

